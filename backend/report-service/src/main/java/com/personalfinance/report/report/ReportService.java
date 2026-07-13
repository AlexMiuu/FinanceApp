package com.personalfinance.report.report;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.report.domain.ExpenseProjectionEntity;
import com.personalfinance.report.domain.ExpenseProjectionRepository;
import com.personalfinance.report.domain.ReportEntity;
import com.personalfinance.report.domain.ReportRepository;
import com.personalfinance.report.web.ApiExceptions.NotFoundException;

@Service
public class ReportService {

    public record Filters(LocalDate from, LocalDate to, List<UUID> categoryIds) {
    }

    private final ReportRepository reports;
    private final ExpenseProjectionRepository projections;
    private final ObjectMapper objectMapper;

    public ReportService(ReportRepository reports, ExpenseProjectionRepository projections,
            ObjectMapper objectMapper) {
        this.reports = reports;
        this.projections = projections;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ReportEntity> list(UUID userId) {
        return reports.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public ReportEntity get(UUID id, UUID userId) {
        return reports.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
    }

    @Transactional
    public ReportEntity create(UUID userId, String name, Map<String, Object> filters) {
        return reports.save(new ReportEntity(userId, name, filters));
    }

    @Transactional
    public ReportEntity update(UUID id, UUID userId, String name, Map<String, Object> filters) {
        ReportEntity report = get(id, userId);
        report.rename(name);
        report.setFilters(filters);
        return report;
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        reports.delete(get(id, userId));
    }

    /** Re-evaluates the saved filters against the projection and caches the result. */
    @Transactional
    public Map<String, Object> run(UUID id, UUID userId) {
        ReportEntity report = get(id, userId);
        List<ExpenseProjectionEntity> rows = matching(userId, parseFilters(report.getFilters()));

        Map<String, Long> byCategory = rows.stream().collect(Collectors.groupingBy(
                row -> row.getCategoryPath().split(" > ")[0],
                LinkedHashMap::new,
                Collectors.summingLong(ExpenseProjectionEntity::getAmount)));
        Map<String, Long> byMonth = rows.stream().collect(Collectors.groupingBy(
                row -> row.getExpenseDate().toString().substring(0, 7),
                Collectors.summingLong(ExpenseProjectionEntity::getAmount)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSpent", rows.stream().mapToLong(ExpenseProjectionEntity::getAmount).sum());
        result.put("expenseCount", rows.size());
        result.put("byCategory", byCategory);
        result.put("byMonth", new java.util.TreeMap<>(byMonth));
        report.recordRun(result);
        return result;
    }

    /** Rows backing the report, for CSV export (FR-4). */
    @Transactional(readOnly = true)
    public List<ExpenseProjectionEntity> rowsFor(UUID id, UUID userId) {
        ReportEntity report = get(id, userId);
        return matching(userId, parseFilters(report.getFilters()));
    }

    private Filters parseFilters(Map<String, Object> raw) {
        return objectMapper.convertValue(raw, Filters.class);
    }

    private List<ExpenseProjectionEntity> matching(UUID userId, Filters filters) {
        Specification<ExpenseProjectionEntity> spec =
                (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (filters.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), filters.from()));
        }
        if (filters.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), filters.to()));
        }
        if (filters.categoryIds() != null && !filters.categoryIds().isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("categoryId").in(filters.categoryIds()));
        }
        return projections.findAll(spec,
                Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.asc("categoryPath")));
    }
}
