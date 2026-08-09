package com.personalfinance.report.macro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.personalfinance.report.events.Events;
import com.personalfinance.report.macro.entity.MacroReadingEntity;
import com.personalfinance.report.macro.entity.MacroReadingKind;
import com.personalfinance.report.macro.entity.MacroSeasonStateEntity;
import com.personalfinance.report.macro.entity.PastoralSeason;
import com.personalfinance.report.macro.repository.MacroReadingRepository;
import com.personalfinance.report.macro.repository.MacroSeasonStateRepository;

/**
 * Given-When-Then coverage of one refresh cycle (F1). The repository mocks
 * stand in for the singleton season row and the per-kind reading cache,
 * mutated across the single {@code refresh} call under test, mirroring how
 * the real repositories behave for the daily {@code MacroJobs} sweep.
 */
class MacroServiceTest {

    private final LocalDate today = LocalDate.of(2026, 8, 9); // pastoral MUNTE window

    private MacroDataSource dataSource;
    private MacroReadingRepository readings;
    private MacroSeasonStateRepository seasonStates;
    private List<Object> published;
    private MacroService service;

    private Map<MacroReadingKind, MacroReadingEntity> readingStore;
    private MacroSeasonStateEntity seasonStore;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        dataSource = mock(MacroDataSource.class);
        readings = mock(MacroReadingRepository.class);
        seasonStates = mock(MacroSeasonStateRepository.class);
        published = new ArrayList<>();
        readingStore = new EnumMap<>(MacroReadingKind.class);
        seasonStore = null;

        when(dataSource.fetch(any())).thenReturn(Optional.empty());

        when(readings.findById(any(MacroReadingKind.class)))
                .thenAnswer(inv -> Optional.ofNullable(readingStore.get(inv.getArgument(0))));
        when(readings.save(any(MacroReadingEntity.class))).thenAnswer(inv -> {
            MacroReadingEntity entity = inv.getArgument(0);
            readingStore.put(entity.getKind(), entity);
            return entity;
        });

        when(seasonStates.findById(MacroSeasonStateEntity.SINGLETON_ID))
                .thenAnswer(inv -> Optional.ofNullable(seasonStore));
        when(seasonStates.save(any(MacroSeasonStateEntity.class))).thenAnswer(inv -> {
            seasonStore = inv.getArgument(0);
            return seasonStore;
        });

        service = new MacroService(dataSource, readings, seasonStates, published::add);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(MacroService.class)).addAppender(logAppender);
    }

    private void seedSeason(PastoralSeason season, LocalDate asOfDate) {
        seasonStore = new MacroSeasonStateEntity(season.wireName(), asOfDate);
    }

    private void seedFreshReading(MacroReadingKind kind, BigDecimal value, LocalDate asOfDate) {
        readingStore.put(kind, new MacroReadingEntity(kind, value, asOfDate));
    }

    private boolean errorLogged() {
        return logAppender.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void refreshThrowsOnNullToday() {
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seasonUnchangedDoesNotSaveOrPublish() {
        seedSeason(PastoralSeason.forDate(today), today.minusDays(10));
        seedFreshReading(MacroReadingKind.CPI, new BigDecimal("120.5"), today.minusDays(5));
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));

        service.refresh(today);

        verify(seasonStates, never()).save(any());
        assertThat(published).noneMatch(Events.MacroSeasonChanged.class::isInstance);
    }

    @Test
    void seasonChangedSavesAndPublishes() {
        seedSeason(PastoralSeason.URCATUL, today.minusDays(60));
        seedFreshReading(MacroReadingKind.CPI, new BigDecimal("120.5"), today.minusDays(5));
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));

        service.refresh(today);

        assertThat(seasonStore.getSeason()).isEqualTo(PastoralSeason.forDate(today).wireName());
        assertThat(published).filteredOn(Events.MacroSeasonChanged.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(Events.MacroSeasonChanged.class, event -> {
                    assertThat(event.season()).isEqualTo(PastoralSeason.forDate(today).wireName());
                    assertThat(event.source()).isEqualTo("pastoral-calendar");
                    assertThat(event.asOfDate()).isEqualTo(today);
                });
    }

    @Test
    void cpiUnchangedDoesNotPublish() {
        seedSeason(PastoralSeason.forDate(today), today);
        BigDecimal cpiValue = new BigDecimal("120.5");
        seedFreshReading(MacroReadingKind.CPI, cpiValue, today.minusDays(10));
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));
        when(dataSource.fetch(MacroReadingKind.CPI))
                .thenReturn(Optional.of(new MacroDataSource.Reading(cpiValue, today)));

        service.refresh(today);

        assertThat(published).noneMatch(Events.MacroCpiUpdated.class::isInstance);
        assertThat(readingStore.get(MacroReadingKind.CPI).getAsOfDate()).isEqualTo(today);
    }

    @Test
    void cpiChangedPublishes() {
        seedSeason(PastoralSeason.forDate(today), today);
        seedFreshReading(MacroReadingKind.CPI, new BigDecimal("120.5"), today.minusDays(35));
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));
        BigDecimal newValue = new BigDecimal("121.3");
        when(dataSource.fetch(MacroReadingKind.CPI))
                .thenReturn(Optional.of(new MacroDataSource.Reading(newValue, today)));

        service.refresh(today);

        assertThat(published).filteredOn(Events.MacroCpiUpdated.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(Events.MacroCpiUpdated.class, event -> {
                    assertThat(event.value()).isEqualTo(newValue.toPlainString());
                    assertThat(event.source()).isEqualTo(MacroReadingKind.CPI.source());
                    assertThat(event.asOfDate()).isEqualTo(today);
                });
    }

    @Test
    void fetchFailureWithFreshCacheLogsNoError() {
        seedSeason(PastoralSeason.forDate(today), today);
        seedFreshReading(MacroReadingKind.CPI, new BigDecimal("120.5"), today.minusDays(5));
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));

        service.refresh(today);

        assertThat(errorLogged()).isFalse();
        verify(readings, never()).save(any());
    }

    @Test
    void fetchFailureWithStaleCacheLogsError() {
        seedSeason(PastoralSeason.forDate(today), today);
        seedFreshReading(MacroReadingKind.CPI, new BigDecimal("120.5"), today.minusDays(150)); // > 100-day budget
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));

        service.refresh(today);

        assertThat(errorLogged()).isTrue();
        verify(readings, never()).save(any());
    }

    @Test
    void fetchFailureWithNoCacheAtAllLogsError() {
        seedSeason(PastoralSeason.forDate(today), today);
        seedFreshReading(MacroReadingKind.ENERGY_TARIFF, new BigDecimal("0.95"), today.minusDays(5));
        // No CPI reading seeded at all.

        service.refresh(today);

        assertThat(errorLogged()).isTrue();
        verify(readings, never()).save(any());
    }
}
