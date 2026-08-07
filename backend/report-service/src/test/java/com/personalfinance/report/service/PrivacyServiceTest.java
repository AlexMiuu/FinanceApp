package com.personalfinance.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.report.dto.ReportDataExportDto;
import com.personalfinance.report.entity.CategoryProjectionEntity;
import com.personalfinance.report.entity.ExpenseProjectionEntity;
import com.personalfinance.report.entity.ReportEntity;
import com.personalfinance.report.events.Events;
import com.personalfinance.report.mapper.ProjectionExportMapper;
import com.personalfinance.report.mapper.ReportMapper;
import com.personalfinance.report.repository.CategoryProjectionRepository;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.ReportRepository;

class PrivacyServiceTest {

    private final UUID userId = UUID.randomUUID();

    private ExpenseProjectionRepository expenseProjections;
    private CategoryProjectionRepository categoryProjections;
    private ReportRepository reports;
    private List<Object> published;
    private PrivacyService service;

    @BeforeEach
    void setUp() {
        expenseProjections = mock(ExpenseProjectionRepository.class);
        categoryProjections = mock(CategoryProjectionRepository.class);
        reports = mock(ReportRepository.class);
        published = new ArrayList<>();

        service = new PrivacyService(expenseProjections, categoryProjections, reports,
                new ProjectionExportMapper(), new ReportMapper(), published::add);
    }

    @Test
    void eraseUserDataDeletesFromAllThreeTablesAndPublishesCompletion() {
        UUID erasureRequestId = UUID.randomUUID();

        service.eraseUserData(erasureRequestId, userId);

        verify(expenseProjections).deleteByUserId(userId);
        verify(categoryProjections).deleteByUserId(userId);
        verify(reports).deleteByUserId(userId);

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ErasureCompleted.class, event -> {
            assertThat(event.erasureRequestId()).isEqualTo(erasureRequestId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.service()).isEqualTo("report");
            assertThat(event.routingKey()).isEqualTo("user.erasure.completed");
        });
    }

    @Test
    void exportUserDataAggregatesAllThreeCollections() {
        CategoryProjectionEntity category = new CategoryProjectionEntity(
                UUID.randomUUID(), userId, "Housing", null, true);
        ExpenseProjectionEntity expense = new ExpenseProjectionEntity(
                UUID.randomUUID(), userId, category.getCategoryId(), "Housing", true, 5000, "RON", null,
                LocalDate.of(2026, 7, 1));
        ReportEntity report = new ReportEntity(userId, "Monthly", java.util.Map.of());

        when(categoryProjections.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));
        when(expenseProjections.findByUserIdOrderByExpenseDateDesc(userId)).thenReturn(List.of(expense));
        when(reports.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(report));

        ReportDataExportDto export = service.exportUserData(userId);

        assertThat(export.categoryProjections()).singleElement()
                .satisfies(dto -> assertThat(dto.categoryId()).isEqualTo(category.getCategoryId()));
        assertThat(export.expenseProjections()).singleElement()
                .satisfies(dto -> assertThat(dto.expenseId()).isEqualTo(expense.getExpenseId()));
        assertThat(export.reports()).singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(report.getId()));
    }

    @Test
    void exportUserDataReturnsEmptyListsForAUserWithNoData() {
        when(categoryProjections.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseProjections.findByUserIdOrderByExpenseDateDesc(userId)).thenReturn(List.of());
        when(reports.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        ReportDataExportDto export = service.exportUserData(userId);

        assertThat(export.categoryProjections()).isEmpty();
        assertThat(export.expenseProjections()).isEmpty();
        assertThat(export.reports()).isEmpty();
    }
}
