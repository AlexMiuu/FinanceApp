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
import com.personalfinance.report.entity.UserIncomeEntity;
import com.personalfinance.report.entity.WeatherBand;
import com.personalfinance.report.entity.WeatherStateEntity;
import com.personalfinance.report.events.Events;
import com.personalfinance.report.mapper.ProjectionExportMapper;
import com.personalfinance.report.mapper.ReportMapper;
import com.personalfinance.report.mapper.UserIncomeMapper;
import com.personalfinance.report.mapper.WeatherMapper;
import com.personalfinance.report.repository.CategoryProjectionRepository;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.ReportRepository;
import com.personalfinance.report.repository.UserIncomeRepository;
import com.personalfinance.report.repository.WeatherStateRepository;

class PrivacyServiceTest {

    private final UUID userId = UUID.randomUUID();

    private ExpenseProjectionRepository expenseProjections;
    private CategoryProjectionRepository categoryProjections;
    private ReportRepository reports;
    private UserIncomeRepository incomes;
    private WeatherStateRepository weatherStates;
    private List<Object> published;
    private PrivacyService service;

    @BeforeEach
    void setUp() {
        expenseProjections = mock(ExpenseProjectionRepository.class);
        categoryProjections = mock(CategoryProjectionRepository.class);
        reports = mock(ReportRepository.class);
        incomes = mock(UserIncomeRepository.class);
        weatherStates = mock(WeatherStateRepository.class);
        published = new ArrayList<>();

        service = new PrivacyService(expenseProjections, categoryProjections, reports, incomes, weatherStates,
                new ProjectionExportMapper(), new ReportMapper(), new UserIncomeMapper(), new WeatherMapper(),
                published::add);
    }

    @Test
    void eraseUserDataDeletesFromAllFiveTablesAndPublishesCompletion() {
        UUID erasureRequestId = UUID.randomUUID();

        service.eraseUserData(erasureRequestId, userId);

        verify(expenseProjections).deleteByUserId(userId);
        verify(categoryProjections).deleteByUserId(userId);
        verify(reports).deleteByUserId(userId);
        verify(incomes).deleteByUserId(userId);
        verify(weatherStates).deleteByUserId(userId);

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ErasureCompleted.class, event -> {
            assertThat(event.erasureRequestId()).isEqualTo(erasureRequestId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.service()).isEqualTo("report");
            assertThat(event.routingKey()).isEqualTo("user.erasure.completed");
        });
    }

    @Test
    void exportUserDataIncludesIncomeAndWeatherWhenPresent() {
        when(incomes.findById(userId)).thenReturn(java.util.Optional.of(new UserIncomeEntity(userId, 500000)));
        WeatherStateEntity weather = new WeatherStateEntity(userId);
        weather.commit(WeatherBand.GATHERING, java.time.Instant.parse("2026-08-01T00:00:00Z"));
        when(weatherStates.findById(userId)).thenReturn(java.util.Optional.of(weather));

        ReportDataExportDto export = service.exportUserData(userId);

        assertThat(export.userIncome().monthlyIncome()).isEqualTo(500000);
        assertThat(export.weatherState().currentBand()).isEqualTo("gathering");
    }

    @Test
    void exportUserDataNullsIncomeAndWeatherWhenAbsent() {
        when(incomes.findById(userId)).thenReturn(java.util.Optional.empty());
        when(weatherStates.findById(userId)).thenReturn(java.util.Optional.empty());

        ReportDataExportDto export = service.exportUserData(userId);

        assertThat(export.userIncome()).isNull();
        assertThat(export.weatherState()).isNull();
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
