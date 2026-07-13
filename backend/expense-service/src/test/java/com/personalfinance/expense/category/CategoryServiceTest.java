package com.personalfinance.expense.category;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.expense.domain.CategoryEntity;
import com.personalfinance.expense.domain.CategoryRepository;
import com.personalfinance.expense.domain.ExpenseRepository;
import com.personalfinance.expense.web.ApiExceptions.ConflictException;
import com.personalfinance.expense.web.ApiExceptions.UnprocessableException;

class CategoryServiceTest {

    private final UUID userId = UUID.randomUUID();

    private CategoryRepository categories;
    private ExpenseRepository expenses;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        expenses = mock(ExpenseRepository.class);
        service = new CategoryService(categories, expenses, event -> { });
    }

    @Test
    void rejectsNestingDeeperThanOneLevel() {
        CategoryEntity parent = new CategoryEntity(userId, null, "Food", false);
        CategoryEntity child = new CategoryEntity(userId, parent.getId(), "Groceries", false);
        when(categories.findByIdAndUserId(child.getId(), userId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.create(userId, "Bio", child.getId(), false))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void rejectsDuplicateNameAtSameLevel() {
        when(categories.existsByUserIdAndParentIdAndNameIgnoreCase(userId, null, "Food")).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, "Food", null, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesDeletingCategoryWithExpenses() {
        CategoryEntity category = new CategoryEntity(userId, null, "Food", false);
        when(categories.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));
        when(categories.existsByParentId(category.getId())).thenReturn(false);
        when(expenses.existsByCategoryId(category.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(category.getId(), userId))
                .isInstanceOf(ConflictException.class);
        verify(categories, never()).delete(any(CategoryEntity.class));
    }

    @Test
    void seedIsIdempotent() {
        when(categories.existsByUserId(userId)).thenReturn(true);

        service.seedDefaults(userId);

        verify(categories, never()).save(any());
    }
}
