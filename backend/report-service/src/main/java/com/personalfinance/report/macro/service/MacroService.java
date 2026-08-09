package com.personalfinance.report.macro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.report.events.Events;
import com.personalfinance.report.macro.entity.MacroReadingEntity;
import com.personalfinance.report.macro.entity.MacroReadingKind;
import com.personalfinance.report.macro.entity.MacroSeasonStateEntity;
import com.personalfinance.report.macro.entity.PastoralSeason;
import com.personalfinance.report.macro.repository.MacroReadingRepository;
import com.personalfinance.report.macro.repository.MacroSeasonStateRepository;

/**
 * F1 orchestration: one refresh cycle advances the pastoral calendar and
 * re-pulls each macro series, always degrading to the cache on failure
 * rather than blocking (see {@link MacroDataSource}).
 */
@Service
public class MacroService {

    private static final Logger log = LoggerFactory.getLogger(MacroService.class);

    private final MacroDataSource dataSource;
    private final MacroReadingRepository readings;
    private final MacroSeasonStateRepository seasonState;
    private final ApplicationEventPublisher events;

    public MacroService(MacroDataSource dataSource, MacroReadingRepository readings,
            MacroSeasonStateRepository seasonState, ApplicationEventPublisher events) {
        this.dataSource = dataSource;
        this.readings = readings;
        this.seasonState = seasonState;
        this.events = events;
    }

    @Transactional
    public void refresh(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today must not be null");
        }
        refreshSeason(today);
        refreshReading(MacroReadingKind.CPI, today);
        refreshReading(MacroReadingKind.ENERGY_TARIFF, today);
    }

    private void refreshSeason(LocalDate today) {
        PastoralSeason season = PastoralSeason.forDate(today);
        Optional<MacroSeasonStateEntity> current = seasonState.findById(MacroSeasonStateEntity.SINGLETON_ID);
        if (current.map(MacroSeasonStateEntity::getSeason).filter(season.wireName()::equals).isPresent()) {
            return;
        }
        seasonState.save(new MacroSeasonStateEntity(season.wireName(), today));
        events.publishEvent(new Events.MacroSeasonChanged(season.wireName(), "pastoral-calendar", today, Instant.now()));
    }

    private void refreshReading(MacroReadingKind kind, LocalDate today) {
        Optional<MacroReadingEntity> cached = readings.findById(kind);
        Optional<MacroDataSource.Reading> fetched = dataSource.fetch(kind);

        if (fetched.isEmpty()) {
            logIfStale(kind, today, cached);
            return;
        }

        MacroDataSource.Reading reading = fetched.get();
        boolean changed = cached.map(MacroReadingEntity::getValue)
                .map(previous -> previous.compareTo(reading.value()) != 0)
                .orElse(true);

        if (cached.isPresent()) {
            cached.get().update(reading.value(), reading.asOfDate());
            readings.save(cached.get());
        } else {
            readings.save(new MacroReadingEntity(kind, reading.value(), reading.asOfDate()));
        }

        if (kind == MacroReadingKind.CPI && changed) {
            events.publishEvent(new Events.MacroCpiUpdated(
                    reading.value().toPlainString(), kind.source(), reading.asOfDate(), Instant.now()));
        }
    }

    private void logIfStale(MacroReadingKind kind, LocalDate today, Optional<MacroReadingEntity> cached) {
        if (cached.isEmpty()) {
            log.error("Fetch failed for {} and no cached reading exists", kind);
            return;
        }
        LocalDate cachedAsOfDate = cached.get().getAsOfDate();
        if (kind.isStaleAt(cachedAsOfDate, today)) {
            log.error("Fetch failed for {} and cached reading from {} is now stale", kind, cachedAsOfDate);
        }
    }
}
