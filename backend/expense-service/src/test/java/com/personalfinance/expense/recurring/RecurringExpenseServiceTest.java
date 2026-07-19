package com.personalfinance.expense.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

import com.personalfinance.expense.domain.CategoryEntity;
import com.personalfinance.expense.domain.CategoryRepository;
import com.personalfinance.expense.expense.ExpenseService;

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
        service = new RecurringExpenseService(recurring, categories, expenses);
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
}
