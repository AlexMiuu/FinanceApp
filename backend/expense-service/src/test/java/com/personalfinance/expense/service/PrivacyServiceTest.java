package com.personalfinance.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.personalfinance.expense.dto.ExpenseDataExportDto;
import com.personalfinance.expense.entity.CategoryEntity;
import com.personalfinance.expense.entity.ExpenseEntity;
import com.personalfinance.expense.entity.RecurringExpenseEntity;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.mapper.CategoryMapper;
import com.personalfinance.expense.mapper.ExpenseMapper;
import com.personalfinance.expense.mapper.RecurringExpenseMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.ExpenseRepository;
import com.personalfinance.expense.repository.RecurringExpenseRepository;

class PrivacyServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 7, 19);

    private ExpenseRepository expenses;
    private CategoryRepository categories;
    private RecurringExpenseRepository recurringExpenses;
    private List<Object> published;
    private PrivacyService service;

    @BeforeEach
    void setUp() {
        expenses = mock(ExpenseRepository.class);
        categories = mock(CategoryRepository.class);
        recurringExpenses = mock(RecurringExpenseRepository.class);
        published = new ArrayList<>();

        service = new PrivacyService(expenses, categories, recurringExpenses,
                new ExpenseMapper(), new CategoryMapper(), new RecurringExpenseMapper(), published::add);
    }

    @Test
    void eraseUserDataDeletesInFkSafeOrderAndPublishesCompletion() {
        UUID erasureRequestId = UUID.randomUUID();

        service.eraseUserData(erasureRequestId, userId);

        InOrder order = inOrder(recurringExpenses, expenses, categories);
        order.verify(recurringExpenses).deleteByUserId(userId);
        order.verify(expenses).deleteByUserId(userId);
        order.verify(categories).deleteByUserIdAndParentIdIsNotNull(userId);
        order.verify(categories).deleteByUserId(userId);

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ErasureCompleted.class, event -> {
            assertThat(event.erasureRequestId()).isEqualTo(erasureRequestId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.service()).isEqualTo("expense");
            assertThat(event.routingKey()).isEqualTo("user.erasure.completed");
        });
    }

    @Test
    void exportUserDataAggregatesAllThreeCollections() {
        CategoryEntity category = new CategoryEntity(userId, null, "Housing", true);
        ExpenseEntity expense = new ExpenseEntity(userId, category.getId(), 100, "rent", today);
        RecurringExpenseEntity recurring = new RecurringExpenseEntity(userId, category.getId(), 100, "rent", today);

        when(categories.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));
        when(expenses.findByUserId(userId)).thenReturn(List.of(expense));
        when(recurringExpenses.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(recurring));

        ExpenseDataExportDto export = service.exportUserData(userId);

        assertThat(export.categories()).singleElement().satisfies(dto -> assertThat(dto.id()).isEqualTo(category.getId()));
        assertThat(export.expenses()).singleElement().satisfies(dto -> assertThat(dto.id()).isEqualTo(expense.getId()));
        assertThat(export.recurringExpenses()).singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(recurring.getId()));
    }

    @Test
    void exportUserDataReturnsEmptyListsForAUserWithNoData() {
        when(categories.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenses.findByUserId(userId)).thenReturn(List.of());
        when(recurringExpenses.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        ExpenseDataExportDto export = service.exportUserData(userId);

        assertThat(export.categories()).isEmpty();
        assertThat(export.expenses()).isEmpty();
        assertThat(export.recurringExpenses()).isEmpty();
    }
}
