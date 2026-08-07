package com.personalfinance.report.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.report.dto.ReportDataExportDto;
import com.personalfinance.report.events.Events;
import com.personalfinance.report.mapper.ProjectionExportMapper;
import com.personalfinance.report.mapper.ReportMapper;
import com.personalfinance.report.repository.CategoryProjectionRepository;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.ReportRepository;

@Service
public class PrivacyService {

    private final ExpenseProjectionRepository expenseProjections;
    private final CategoryProjectionRepository categoryProjections;
    private final ReportRepository reports;
    private final ProjectionExportMapper projectionExportMapper;
    private final ReportMapper reportMapper;
    private final ApplicationEventPublisher events;

    public PrivacyService(ExpenseProjectionRepository expenseProjections,
            CategoryProjectionRepository categoryProjections, ReportRepository reports,
            ProjectionExportMapper projectionExportMapper, ReportMapper reportMapper,
            ApplicationEventPublisher events) {
        this.expenseProjections = expenseProjections;
        this.categoryProjections = categoryProjections;
        this.reports = reports;
        this.projectionExportMapper = projectionExportMapper;
        this.reportMapper = reportMapper;
        this.events = events;
    }

    /**
     * None of report-service's three tables carry a foreign key (they're an
     * event-fed local mirror, per V2__reports_schema.sql) so, unlike
     * expense-service, delete order here is not FK-constrained — it's kept in
     * this order only because expense/category projections are derived data
     * and reports are the user's own saved content.
     */
    @Transactional
    public void eraseUserData(UUID erasureRequestId, UUID userId) {
        expenseProjections.deleteByUserId(userId);
        categoryProjections.deleteByUserId(userId);
        reports.deleteByUserId(userId);
        events.publishEvent(new Events.ErasureCompleted(erasureRequestId, userId, "report", Instant.now()));
    }

    @Transactional(readOnly = true)
    public ReportDataExportDto exportUserData(UUID userId) {
        return new ReportDataExportDto(
                projectionExportMapper.toCategoryDtos(categoryProjections.findByUserIdOrderByNameAsc(userId)),
                projectionExportMapper.toExpenseDtos(
                        expenseProjections.findByUserIdOrderByExpenseDateDesc(userId)),
                reportMapper.toDtos(reports.findByUserIdOrderByCreatedAtDesc(userId)));
    }
}
