package com.personalfinance.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.expense.dto.RecurringExpenseDto;
import com.personalfinance.expense.dto.RecurringExpenseRequestDto;
import com.personalfinance.expense.entity.CategoryEntity;
import com.personalfinance.expense.entity.RecurringExpenseEntity;
import com.personalfinance.expense.exception.NotFoundException;
import com.personalfinance.expense.mapper.RecurringExpenseMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.RecurringExpenseRepository;

class RecurringExpenseServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private RecurringExpenseRepository recurring;
    private CategoryRepository categories;
    private ExpenseService expenses;
    private RecurringExpenseService service;

    @BeforeEach
    void setUp() {
        recurring = mock(RecurringExpenseRepository.class);
        categories = mock(CategoryRepository.class);
        expenses = mock(ExpenseService.class);
        service = new RecurringExpenseService(recurring, categories, expenses, new RecurringExpenseMapper());
        when(recurring.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categories.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.of(new CategoryEntity(userId, null, "Housing", true)));
    }

    @Test
    void backdatedTemplateCatchesUpAllMissedMonths() {
        // Rent since Jan 15, created on Apr 20 -> Jan, Feb, Mar, Apr posted
        service.create(userId, categoryId, 180000, "rent",
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 20));

        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenses, times(4)).create(eq(userId), eq(categoryId), anyLong(), anyString(), dates.capture());
        assertThat(dates.getAllValues()).containsExactly(
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));
    }

    @Test
    void endOfMonthAnchorClampsAndRecovers() {
        // Day 31: Jan 31 -> Feb 28 -> Mar 31
        service.create(userId, categoryId, 5000, "sub",
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 4, 10));

        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenses, times(3)).create(eq(userId), eq(categoryId), anyLong(), anyString(), dates.capture());
        assertThat(dates.getAllValues()).containsExactly(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }

    @Test
    void futureStartPostsNothingYet() {
        RecurringExpenseEntity template = service.create(userId, categoryId, 5000, "sub",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 19));

        verify(expenses, times(0)).create(any(), any(), anyLong(), any(), any());
        assertThat(template.getNextRun()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void runDuePostsAndAdvancesDueTemplates() {
        RecurringExpenseEntity due = new RecurringExpenseEntity(userId, categoryId, 5000, "sub",
                LocalDate.of(2026, 7, 10));
        when(recurring.findByActiveTrueAndNextRunLessThanEqual(LocalDate.of(2026, 7, 19)))
                .thenReturn(List.of(due));

        int posted = service.runDue(LocalDate.of(2026, 7, 19));

        assertThat(posted).isEqualTo(1);
        assertThat(due.getNextRun()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void runDueStopsAtTheCatchUpCeiling() {
        RecurringExpenseEntity ancient = new RecurringExpenseEntity(userId, categoryId, 5000, "sub",
                LocalDate.of(1990, 1, 1));
        when(recurring.findByActiveTrueAndNextRunLessThanEqual(any())).thenReturn(List.of(ancient));

        int posted = service.runDue(LocalDate.of(2026, 7, 19));

        assertThat(posted).isEqualTo(120);
    }

    @Test
    void createReturnsCreatedWithTheMappedTemplate() {
        ResponseEntity<?> response = service.create(userId,
                new RecurringExpenseRequestDto(5000L, categoryId, "sub", LocalDate.of(2026, 8, 1)),
                LocalDate.of(2026, 7, 19));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RecurringExpenseDto body = (RecurringExpenseDto) response.getBody();
        assertThat(body.amount()).isEqualTo(5000);
        assertThat(body.dayOfMonth()).isEqualTo(1);
        assertThat(body.nextRun()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(body.active()).isTrue();
    }

    @Test
    void rejectsTemplateForAnotherUsersCategory() {
        UUID foreignCategory = UUID.randomUUID();
        when(categories.findByIdAndUserId(foreignCategory, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, foreignCategory, 5000, "sub",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(NotFoundException.class);
        verify(recurring, never()).save(any());
    }

    @Test
    void deletingSomeoneElsesTemplateIsNotFound() {
        UUID id = UUID.randomUUID();
        when(recurring.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, userId)).isInstanceOf(NotFoundException.class);
        verify(recurring, never()).delete(any());
    }

    @Test
    void deleteReturnsNoContent() {
        RecurringExpenseEntity template = new RecurringExpenseEntity(userId, categoryId, 5000, "sub",
                LocalDate.of(2026, 8, 1));
        when(recurring.findByIdAndUserId(template.getId(), userId)).thenReturn(Optional.of(template));

        ResponseEntity<?> response = service.delete(template.getId(), userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(recurring).delete(template);
    }

    @Test
    void listReturnsMappedTemplates() {
        RecurringExpenseEntity template = new RecurringExpenseEntity(userId, categoryId, 5000, "sub",
                LocalDate.of(2026, 8, 1));
        when(recurring.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(template));

        ResponseEntity<?> response = service.list(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).singleElement()
                .isEqualTo(new RecurringExpenseDto(template.getId(), categoryId, 5000, "sub", 1,
                        LocalDate.of(2026, 8, 1), true));
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.create(userId,
                new RecurringExpenseRequestDto(0L, categoryId, "sub", LocalDate.of(2026, 8, 1)),
                LocalDate.of(2026, 7, 19)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
