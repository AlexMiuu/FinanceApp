package com.personalfinance.expense.category;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.expense.domain.CategoryEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    public record CategoryDto(UUID id, String name, UUID parentId, boolean isMandatory) {

        static CategoryDto of(CategoryEntity c) {
            return new CategoryDto(c.getId(), c.getName(), c.getParentId(), c.isMandatory());
        }
    }

    public record CreateRequest(@NotBlank @Size(max = 60) String name, UUID parentId, boolean isMandatory) {
    }

    public record UpdateRequest(@NotBlank @Size(max = 60) String name, boolean isMandatory) {
    }

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt)).stream().map(CategoryDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return CategoryDto.of(service.create(userId(jwt), request.name(), request.parentId(), request.isMandatory()));
    }

    @PutMapping("/{id}")
    public CategoryDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateRequest request) {
        return CategoryDto.of(service.update(id, userId(jwt), request.name(), request.isMandatory()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, userId(jwt));
    }

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
