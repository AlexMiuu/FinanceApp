package com.personalfinance.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.expense.dto.ExpenseDto;
import com.personalfinance.expense.dto.ExpenseRequestDto;
import com.personalfinance.expense.dto.PageDto;
import com.personalfinance.expense.entity.CategoryEntity;
import com.personalfinance.expense.entity.ExpenseEntity;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.exception.NotFoundException;
import com.personalfinance.expense.exception.UnprocessableException;
import com.personalfinance.expense.mapper.ExpenseMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.ExpenseRepository;

class ExpenseServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 7, 19);

    private ExpenseRepository expenses;
    private CategoryRepository categories;
    private CategoryService categoryService;
    private List<Object> published;
    private ExpenseService service;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        expenses = mock(ExpenseRepository.class);
        categories = mock(CategoryRepository.class);
        categoryService = mock(CategoryService.class);
        published = new ArrayList<>();

        service = new ExpenseService(expenses, categories, categoryService, new ExpenseMapper(),
                published::add);

        category = new CategoryEntity(userId, null, "Housing", true);
        when(categories.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));
        when(categoryService.snapshotOf(category))
                .thenReturn(new CategoryService.CategorySnapshot("Housing", true));
    }

    @Test
    void createReturnsCreatedAndPublishesASnapshotBearingEvent() {
        ResponseEntity<?> response = service.create(userId,
                new ExpenseRequestDto(12345L, category.getId(), "rent", today));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ExpenseDto body = (ExpenseDto) response.getBody();
        assertThat(body.amount()).isEqualTo(12345);
        assertThat(body.currency()).isEqualTo("RON");
        assertThat(body.categoryId()).isEqualTo(category.getId());

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ExpenseChanged.class, event -> {
            assertThat(event.created()).isTrue();
            assertThat(event.categoryPath()).isEqualTo("Housing");
            assertThat(event.categoryMandatory()).isTrue();
            assertThat(event.routingKey()).isEqualTo("expense.created");
        });
    }

    @Test
    void createRejectsACategoryTheUserDoesNotOwn() {
        UUID foreign = UUID.randomUUID();
        when(categories.findByIdAndUserId(foreign, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, new ExpenseRequestDto(100L, foreign, null, today)))
                .isInstanceOf(NotFoundException.class);
        verify(expenses, never()).save(any());
        assertThat(published).isEmpty();
    }

    @Test
    void updateRewritesTheExpenseAndPublishesAnUpdate() {
        ExpenseEntity expense = new ExpenseEntity(userId, category.getId(), 100, "old", today);
        when(expenses.findByIdAndUserId(expense.getId(), userId)).thenReturn(Optional.of(expense));

        ResponseEntity<?> response = service.update(expense.getId(), userId,
                new ExpenseRequestDto(500L, category.getId(), "new", today.plusDays(1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(expense.getAmount()).isEqualTo(500);
        assertThat(expense.getNote()).isEqualTo("new");
        assertThat(expense.getExpenseDate()).isEqualTo(today.plusDays(1));
        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ExpenseChanged.class,
                event -> assertThat(event.routingKey()).isEqualTo("expense.updated"));
    }

    @Test
    void updatingSomeoneElsesExpenseIsNotFound() {
        UUID id = UUID.randomUUID();
        when(expenses.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, userId,
                new ExpenseRequestDto(500L, category.getId(), "new", today)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteReturnsNoContentAndPublishesDeletion() {
        ExpenseEntity expense = new ExpenseEntity(userId, category.getId(), 100, "rent", today);
        when(expenses.findByIdAndUserId(expense.getId(), userId)).thenReturn(Optional.of(expense));

        ResponseEntity<?> response = service.delete(expense.getId(), userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(expenses).delete(expense);
        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ExpenseDeleted.class,
                event -> assertThat(event.expenseId()).isEqualTo(expense.getId()));
    }

    @Test
    void listReturnsAPageEnvelopeOfDtos() {
        ExpenseEntity expense = new ExpenseEntity(userId, category.getId(), 100, "rent", today);
        when(expenses.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expense), PageRequest.of(0, 50), 1));

        ResponseEntity<?> response = service.list(userId, null, null, null, 0, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageDto<?> body = (PageDto<?>) response.getBody();
        assertThat(body.totalElements()).isEqualTo(1);
        assertThat(body.page()).isZero();
        assertThat(body.size()).isEqualTo(50);
        assertThat(body.items()).singleElement().isInstanceOf(ExpenseDto.class);
    }

    @Test
    void listClampsPageSizeToTheCeiling() {
        when(expenses.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(userId, null, null, null, 0, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenses).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void listRejectsNegativePageAndZeroSizeInsteadOfFailingDeeper() {
        when(expenses.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(userId, null, null, null, -3, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenses).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void listRejectsAnInvertedDateRange() {
        assertThatThrownBy(() -> service.list(userId, today, today.minusDays(1), null, 0, 50))
                .isInstanceOf(UnprocessableException.class);
        verify(expenses, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> service.list(null, null, null, null, 0, 50))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMissingBody() {
        assertThatThrownBy(() -> service.create(userId, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsNullAmountRatherThanThrowingNullPointer() {
        assertThatThrownBy(() -> service.create(userId,
                new ExpenseRequestDto(null, category.getId(), "rent", today)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
