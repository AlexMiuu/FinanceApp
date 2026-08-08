package com.personalfinance.report.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.OptionalDouble;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.report.dto.WeatherDto;
import com.personalfinance.report.entity.UserIncomeEntity;
import com.personalfinance.report.entity.WeatherBand;
import com.personalfinance.report.entity.WeatherStateEntity;
import com.personalfinance.report.events.Events;
import com.personalfinance.report.mapper.WeatherMapper;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.UserIncomeRepository;
import com.personalfinance.report.repository.WeatherStateRepository;

/**
 * F4 composite: trailing-30-day burn rate over monthly income, banded with
 * hysteresis (D4). A candidate band only ever moves one step from the
 * currently-committed band per recompute, and only commits after dwelling
 * continuously for {@link #DWELL} — oscillating back to the committed band
 * cancels the pending clock rather than merely pausing it, which is what
 * makes flicker across a boundary cost zero commits.
 */
@Service
public class WeatherService {

    static final Duration DWELL = Duration.ofHours(6);

    private final ExpenseProjectionRepository expenses;
    private final UserIncomeRepository incomes;
    private final WeatherStateRepository states;
    private final WeatherMapper mapper;
    private final ApplicationEventPublisher events;

    public WeatherService(ExpenseProjectionRepository expenses, UserIncomeRepository incomes,
            WeatherStateRepository states, WeatherMapper mapper, ApplicationEventPublisher events) {
        this.expenses = expenses;
        this.incomes = incomes;
        this.states = states;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public WeatherDto currentFor(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return states.findById(userId).map(mapper::toDto).orElseGet(WeatherDto::clear);
    }

    /** Burn rate / income. Empty when income is unknown or non-positive — never gates on missing data. */
    OptionalDouble compositeFor(UUID userId, LocalDate today) {
        long monthlyIncome = incomes.findById(userId).map(UserIncomeEntity::getMonthlyIncome).orElse(0L);
        if (monthlyIncome <= 0) {
            return OptionalDouble.empty();
        }
        long burnRate = expenses.sumForRange(userId, today.minusDays(29), today);
        return OptionalDouble.of((double) burnRate / monthlyIncome);
    }

    @Transactional
    public void recompute(UUID userId, Instant now) {
        if (userId == null || now == null) {
            throw new IllegalArgumentException("userId and now must not be null");
        }
        WeatherStateEntity state = states.findById(userId).orElseGet(() -> new WeatherStateEntity(userId));

        OptionalDouble composite = compositeFor(userId, LocalDate.ofInstant(now, ZoneOffset.UTC));
        if (composite.isEmpty()) {
            // Degraded computation: hold the committed band. Don't force CLEAR (that
            // would itself be a spurious transition) and don't let a transient gap
            // start or silently complete a pending one.
            cancelPendingIfPresent(state, now);
            return;
        }

        WeatherBand candidate = candidateBand(state.getCurrentBand(), composite.getAsDouble());
        if (candidate == state.getCurrentBand()) {
            cancelPendingIfPresent(state, now);
            return;
        }

        boolean sameCandidatePending = state.getPendingBand().filter(b -> b == candidate).isPresent();
        if (!sameCandidatePending) {
            state.startPending(candidate, now);
            states.save(state);
            return;
        }

        Instant pendingSince = state.getPendingSince().orElseThrow();
        if (Duration.between(pendingSince, now).compareTo(DWELL) >= 0) {
            state.commit(candidate, now);
            states.save(state);
            events.publishEvent(new Events.WeatherUpdated(userId, candidate.wireName(), now));
        }
    }

    private void cancelPendingIfPresent(WeatherStateEntity state, Instant now) {
        if (state.getPendingBand().isPresent()) {
            state.clearPending(now);
            states.save(state);
        }
    }

    /**
     * Boundaries are shifted against the direction of travel (D4's ±0.05
     * hysteresis): leaving CLEAR needs ratio ≥0.85, falling back from GATHERING
     * needs ≤0.75, climbing from GATHERING needs >1.05, falling back from STORM
     * needs ≤0.95. A candidate is always at most one band away from current.
     */
    private static WeatherBand candidateBand(WeatherBand current, double ratio) {
        return switch (current) {
            case CLEAR -> ratio >= 0.85 ? WeatherBand.GATHERING : WeatherBand.CLEAR;
            case GATHERING -> {
                if (ratio <= 0.75) {
                    yield WeatherBand.CLEAR;
                }
                yield ratio > 1.05 ? WeatherBand.STORM : WeatherBand.GATHERING;
            }
            case STORM -> ratio <= 0.95 ? WeatherBand.GATHERING : WeatherBand.STORM;
        };
    }
}
