package com.personalfinance.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.expense.dto.CategoryDto;
import com.personalfinance.expense.dto.CategoryRequestDto;
import com.personalfinance.expense.dto.CategoryUpdateRequestDto;
import com.personalfinance.expense.entity.CategoryEntity;
import com.personalfinance.expense.exception.ConflictException;
import com.personalfinance.expense.exception.NotFoundException;
import com.personalfinance.expense.exception.UnprocessableException;
import com.personalfinance.expense.mapper.CategoryMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.ExpenseRepository;

class CategoryServiceTest {

    private final UUID userId = UUID.randomUUID();

    private CategoryRepository categories;
    private ExpenseRepository expenses;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        expenses = mock(ExpenseRepository.class);
        service = new CategoryService(categories, expenses, new CategoryMapper(), event -> { });
    }

    @Test
    void rejectsNestingDeeperThanOneLevel() {
        CategoryEntity parent = new CategoryEntity(userId, null, "Food", false);
        CategoryEntity child = new CategoryEntity(userId, parent.getId(), "Groceries", false);
        when(categories.findByIdAndUserId(child.getId(), userId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.create(userId, new CategoryRequestDto("Bio", child.getId(), false)))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void rejectsUnknownParent() {
        UUID missingParent = UUID.randomUUID();
        when(categories.findByIdAndUserId(missingParent, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, new CategoryRequestDto("Bio", missingParent, false)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsDuplicateNameAtSameLevel() {
        when(categories.existsByUserIdAndParentIdAndNameIgnoreCase(userId, null, "Food")).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, new CategoryRequestDto("Food", null, false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReturnsCreatedWithTheMappedCategory() {
        ResponseEntity<?> response = service.create(userId, new CategoryRequestDto("Food", null, true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOf(CategoryDto.class);
        CategoryDto body = (CategoryDto) response.getBody();
        assertThat(body.name()).isEqualTo("Food");
        assertThat(body.isMandatory()).isTrue();
        assertThat(body.parentId()).isNull();
        verify(categories).save(any(CategoryEntity.class));
    }

    @Test
    void renamingToOwnNameInDifferentCaseIsNotADuplicate() {
        CategoryEntity category = new CategoryEntity(userId, null, "Food", false);
        when(categories.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));

        ResponseEntity<?> response =
                service.update(category.getId(), userId, new CategoryUpdateRequestDto("FOOD", true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(category.getName()).isEqualTo("FOOD");
        assertThat(category.isMandatory()).isTrue();
        verify(categories, never()).existsByUserIdAndParentIdAndNameIgnoreCase(any(), any(), any());
    }

    @Test
    void refusesDeletingCategoryWithSubcategories() {
        CategoryEntity category = new CategoryEntity(userId, null, "Food", false);
        when(categories.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));
        when(categories.existsByParentId(category.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(category.getId(), userId))
                .isInstanceOf(ConflictException.class);
        verify(categories, never()).delete(any(CategoryEntity.class));
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
    void deleteReturnsNoContentWhenNothingBlocksIt() {
        CategoryEntity category = new CategoryEntity(userId, null, "Food", false);
        when(categories.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));

        ResponseEntity<?> response = service.delete(category.getId(), userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(categories).delete(category);
    }

    @Test
    void deletingSomeoneElsesCategoryIsNotFound() {
        UUID id = UUID.randomUUID();
        when(categories.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void seedIsIdempotent() {
        when(categories.existsByUserId(userId)).thenReturn(true);

        service.seedDefaults(userId);

        verify(categories, never()).save(any());
    }

    @Test
    void listSeedsThenReturnsMappedCategories() {
        CategoryEntity category = new CategoryEntity(userId, null, "Food", false);
        when(categories.existsByUserId(userId)).thenReturn(true);
        when(categories.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));

        ResponseEntity<?> response = service.list(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).singleElement()
                .isEqualTo(new CategoryDto(category.getId(), "Food", null, false));
    }

    @Test
    void snapshotOfChildInheritsMandatoryFromParent() {
        CategoryEntity parent = new CategoryEntity(userId, null, "Housing", true);
        CategoryEntity child = new CategoryEntity(userId, parent.getId(), "Rent", false);
        when(categories.findById(parent.getId())).thenReturn(Optional.of(parent));

        CategoryService.CategorySnapshot snapshot = service.snapshotOf(child);

        assertThat(snapshot.path()).isEqualTo("Housing > Rent");
        assertThat(snapshot.effectiveMandatory()).isTrue();
    }

    @Test
    void snapshotFallsBackToOwnNameWhenParentIsMissing() {
        CategoryEntity orphan = new CategoryEntity(userId, UUID.randomUUID(), "Rent", false);
        when(categories.findById(any())).thenReturn(Optional.empty());

        CategoryService.CategorySnapshot snapshot = service.snapshotOf(orphan);

        assertThat(snapshot.path()).isEqualTo("Rent");
        assertThat(snapshot.effectiveMandatory()).isFalse();
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMissingBody() {
        assertThatThrownBy(() -> service.create(userId, null)).isInstanceOf(ResponseStatusException.class);
    }
}
