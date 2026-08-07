package com.personalfinance.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.personalfinance.report.entity.ExpenseProjectionEntity;
import com.personalfinance.report.entity.ReportEntity;
import com.personalfinance.report.exception.NotFoundException;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.ReportRepository;

class ReportServiceTest {

    private final UUID userId = UUID.randomUUID();
    private ReportRepository reports;
    private ExpenseProjectionRepository projections;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        projections = mock(ExpenseProjectionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ReportService(reports, projections, objectMapper);
    }

    @Test
    void getThrowsNotFoundWhenNoReportMatchesIdAndUser() {
        UUID id = UUID.randomUUID();
        when(reports.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id, userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createSavesANewReportForTheUser() {
        Map<String, Object> filters = Map.of();
        ReportEntity saved = new ReportEntity(userId, "Groceries", filters);
        when(reports.save(any())).thenReturn(saved);

        ReportEntity result = service.create(userId, "Groceries", filters);

        assertThat(result.getName()).isEqualTo("Groceries");
        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void updateRenamesAndReplacesFiltersOnTheExistingReport() {
        UUID id = UUID.randomUUID();
        ReportEntity existing = new ReportEntity(userId, "Old name", Map.of());
        when(reports.findByIdAndUserId(id, userId)).thenReturn(Optional.of(existing));

        Map<String, Object> newFilters = Map.of("from", "2026-07-01");
        ReportEntity updated = service.update(id, userId, "New name", newFilters);

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getFilters()).isEqualTo(newFilters);
    }

    @Test
    void deleteRemovesTheOwnedReport() {
        UUID id = UUID.randomUUID();
        ReportEntity existing = new ReportEntity(userId, "Report", Map.of());
        when(reports.findByIdAndUserId(id, userId)).thenReturn(Optional.of(existing));

        service.delete(id, userId);

        verify(reports).delete(existing);
    }

    @Test
    void runWithNoMatchingRowsProducesZeroTotalsNotAnException() {
        UUID id = UUID.randomUUID();
        ReportEntity report = new ReportEntity(userId, "Empty", Map.of());
        when(reports.findByIdAndUserId(id, userId)).thenReturn(Optional.of(report));
        when(projections.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Sort.class))).thenReturn(List.of());

        Map<String, Object> result = service.run(id, userId);

        assertThat(result.get("totalSpent")).isEqualTo(0L);
        assertThat(result.get("expenseCount")).isEqualTo(0);
        assertThat(report.getCachedResult()).isEqualTo(result);
    }

    @Test
    void runAggregatesMatchingRowsByCategoryAndMonth() {
        UUID id = UUID.randomUUID();
        ReportEntity report = new ReportEntity(userId, "Report", Map.of());
        when(reports.findByIdAndUserId(id, userId)).thenReturn(Optional.of(report));
        ExpenseProjectionEntity row = new ExpenseProjectionEntity(UUID.randomUUID(), userId, UUID.randomUUID(),
                "Food > Groceries", false, 5000, "RON", null, LocalDate.of(2026, 7, 10));
        when(projections.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(row));

        Map<String, Object> result = service.run(id, userId);

        assertThat(result.get("totalSpent")).isEqualTo(5000L);
        assertThat(result.get("expenseCount")).isEqualTo(1);
    }

    @Test
    void getWithANullIdIsTreatedAsNotFoundRatherThanCrashing() {
        // No repository stubbing for a null id: the repository legitimately has no row
        // keyed by null, so this must resolve through the same NotFoundException path
        // as any other missing report — not leak a NullPointerException to the caller.
        assertThatThrownBy(() -> service.get(null, userId)).isInstanceOf(NotFoundException.class);
    }
}
