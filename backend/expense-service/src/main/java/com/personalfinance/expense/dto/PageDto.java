package com.personalfinance.expense.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Trimmed page envelope: Spring's own Page serialization is unstable across
 * versions and exposes more than the client needs.
 */
public record PageDto<T>(List<T> items, int page, int size, long totalElements) {

    public static <E, D> PageDto<D> of(Page<E> page, List<D> items) {
        return new PageDto<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
