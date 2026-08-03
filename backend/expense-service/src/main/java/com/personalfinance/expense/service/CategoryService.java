package com.personalfinance.expense.service;

import static com.personalfinance.expense.service.RequestGuards.requireBody;
import static com.personalfinance.expense.service.RequestGuards.requireId;
import static com.personalfinance.expense.service.RequestGuards.requireUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.dto.CategoryRequestDto;
import com.personalfinance.expense.dto.CategoryUpdateRequestDto;
import com.personalfinance.expense.entity.CategoryEntity;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.exception.ConflictException;
import com.personalfinance.expense.exception.NotFoundException;
import com.personalfinance.expense.exception.UnprocessableException;
import com.personalfinance.expense.mapper.CategoryMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.ExpenseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    /** Seeded for every new user; user can rename/delete freely (FR-5). */
    private static final Map<String, List<String>> DEFAULTS = Map.of(
            "Housing", List.of("Rent", "Utilities"),
            "Food", List.of("Groceries", "Restaurants"),
            "Transport", List.of("Fuel", "Public transport"),
            "Health", List.of(),
            "Entertainment", List.of(),
            "Shopping", List.of());
    private static final List<String> DEFAULT_MANDATORY = List.of("Housing", "Health");

    private final CategoryRepository categories;
    private final ExpenseRepository expenses;
    private final CategoryMapper categoryMapper;
    private final ApplicationEventPublisher events;

    /**
     * Lazily seeds on first read as a fallback for accounts that predate the
     * user.registered event (or if the event was lost).
     */
    @Transactional
    public ResponseEntity<?> list(UUID userId) {
        requireUser(userId);

        seedDefaults(userId);
        List<CategoryEntity> owned = categories.findByUserIdOrderByNameAsc(userId);
        return ResponseEntity.ok(categoryMapper.toDtos(owned));
    }

    @Transactional
    public ResponseEntity<?> create(UUID userId, CategoryRequestDto request) {
        requireUser(userId);
        requireBody(request);

        UUID parentId = request.parentId();
        if (parentId != null) {
            requireTopLevelParent(parentId, userId);
        }
        requireNameFree(userId, parentId, request.name());

        CategoryEntity category = new CategoryEntity(userId, parentId, request.name(), request.isMandatory());
        categories.save(category);
        publishChanged(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toDto(category));
    }

    @Transactional
    public ResponseEntity<?> update(UUID id, UUID userId, CategoryUpdateRequestDto request) {
        requireUser(userId);
        requireId(id, "Category");
        requireBody(request);

        CategoryEntity category = ownedCategory(id, userId);
        if (!category.getName().equalsIgnoreCase(request.name())) {
            requireNameFree(userId, category.getParentId(), request.name());
        }
        category.rename(request.name());
        category.setMandatory(request.isMandatory());
        publishChanged(category);
        return ResponseEntity.ok(categoryMapper.toDto(category));
    }

    @Transactional
    public ResponseEntity<?> delete(UUID id, UUID userId) {
        requireUser(userId);
        requireId(id, "Category");

        CategoryEntity category = ownedCategory(id, userId);
        if (categories.existsByParentId(id)) {
            throw new ConflictException("Delete or move its subcategories first");
        }
        if (expenses.existsByCategoryId(id)) {
            throw new ConflictException("This category still has expenses");
        }
        categories.delete(category);
        events.publishEvent(new Events.CategoryDeleted(id, userId, Instant.now()));
        return ResponseEntity.noContent().build();
    }

    @Transactional
    public void seedDefaults(UUID userId) {
        requireUser(userId);
        if (categories.existsByUserId(userId)) {
            return;
        }
        DEFAULTS.forEach((parentName, children) -> {
            CategoryEntity parent = new CategoryEntity(
                    userId, null, parentName, DEFAULT_MANDATORY.contains(parentName));
            categories.save(parent);
            publishChanged(parent);
            for (String childName : children) {
                CategoryEntity child = new CategoryEntity(userId, parent.getId(), childName, false);
                categories.save(child);
                publishChanged(child);
            }
        });
    }

    public record CategorySnapshot(String path, boolean effectiveMandatory) {
    }

    /**
     * Denormalized view for event snapshots consumed by report/quest
     * projections: "Parent > Child" path, and a mandatory flag that inherits
     * from the parent (a Rent expense is mandatory because Housing is).
     */
    @Transactional(readOnly = true)
    public CategorySnapshot snapshotOf(CategoryEntity category) {
        if (category.getParentId() == null) {
            return new CategorySnapshot(category.getName(), category.isMandatory());
        }
        return categories.findById(category.getParentId())
                .map(parent -> new CategorySnapshot(
                        parent.getName() + " > " + category.getName(),
                        category.isMandatory() || parent.isMandatory()))
                .orElse(new CategorySnapshot(category.getName(), category.isMandatory()));
    }

    private CategoryEntity ownedCategory(UUID id, UUID userId) {
        return categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private void requireTopLevelParent(UUID parentId, UUID userId) {
        CategoryEntity parent = categories.findByIdAndUserId(parentId, userId)
                .orElseThrow(() -> new NotFoundException("Parent category not found"));
        if (parent.getParentId() != null) {
            throw new UnprocessableException("Categories can only be nested one level deep");
        }
    }

    private void requireNameFree(UUID userId, UUID parentId, String name) {
        if (categories.existsByUserIdAndParentIdAndNameIgnoreCase(userId, parentId, name)) {
            throw new ConflictException("A category with this name already exists here");
        }
    }

    private void publishChanged(CategoryEntity category) {
        events.publishEvent(new Events.CategoryChanged(
                category.getId(), category.getUserId(), category.getName(),
                category.getParentId(), category.isMandatory(), Instant.now()));
    }
}
