package com.personalfinance.expense.category;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.domain.CategoryEntity;
import com.personalfinance.expense.domain.CategoryRepository;
import com.personalfinance.expense.domain.ExpenseRepository;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.web.ApiExceptions.ConflictException;
import com.personalfinance.expense.web.ApiExceptions.NotFoundException;
import com.personalfinance.expense.web.ApiExceptions.UnprocessableException;

@Service
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
    private final ApplicationEventPublisher events;

    public CategoryService(CategoryRepository categories, ExpenseRepository expenses,
            ApplicationEventPublisher events) {
        this.categories = categories;
        this.expenses = expenses;
        this.events = events;
    }

    /**
     * Lazily seeds on first read as a fallback for accounts that predate the
     * user.registered event (or if the event was lost).
     */
    @Transactional
    public List<CategoryEntity> list(UUID userId) {
        seedDefaults(userId);
        return categories.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public void seedDefaults(UUID userId) {
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

    @Transactional
    public CategoryEntity create(UUID userId, String name, UUID parentId, boolean mandatory) {
        if (parentId != null) {
            CategoryEntity parent = categories.findByIdAndUserId(parentId, userId)
                    .orElseThrow(() -> new NotFoundException("Parent category not found"));
            if (parent.getParentId() != null) {
                throw new UnprocessableException("Categories can only be nested one level deep");
            }
        }
        if (categories.existsByUserIdAndParentIdAndNameIgnoreCase(userId, parentId, name)) {
            throw new ConflictException("A category with this name already exists here");
        }
        CategoryEntity category = new CategoryEntity(userId, parentId, name, mandatory);
        categories.save(category);
        publishChanged(category);
        return category;
    }

    @Transactional
    public CategoryEntity update(UUID id, UUID userId, String name, boolean mandatory) {
        CategoryEntity category = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (!category.getName().equalsIgnoreCase(name)
                && categories.existsByUserIdAndParentIdAndNameIgnoreCase(userId, category.getParentId(), name)) {
            throw new ConflictException("A category with this name already exists here");
        }
        category.rename(name);
        category.setMandatory(mandatory);
        publishChanged(category);
        return category;
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        CategoryEntity category = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (categories.existsByParentId(id)) {
            throw new ConflictException("Delete or move its subcategories first");
        }
        if (expenses.existsByCategoryId(id)) {
            throw new ConflictException("This category still has expenses");
        }
        categories.delete(category);
        events.publishEvent(new Events.CategoryDeleted(id, userId, Instant.now()));
    }

    /** "Parent > Child" for event snapshots consumed by report/quest projections. */
    @Transactional(readOnly = true)
    public String pathOf(CategoryEntity category) {
        if (category.getParentId() == null) {
            return category.getName();
        }
        return categories.findById(category.getParentId())
                .map(parent -> parent.getName() + " > " + category.getName())
                .orElse(category.getName());
    }

    private void publishChanged(CategoryEntity category) {
        events.publishEvent(new Events.CategoryChanged(
                category.getId(), category.getUserId(), category.getName(),
                category.getParentId(), category.isMandatory(), Instant.now()));
    }
}
