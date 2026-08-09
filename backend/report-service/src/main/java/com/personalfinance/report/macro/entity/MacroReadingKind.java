package com.personalfinance.report.macro.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The macro series F1 ingests, each with the provenance and the age past which a
 * cached value stops being trustworthy.
 *
 * <p>The staleness budgets follow each publisher's real cadence plus its lag. INS
 * publishes the CPI monthly, roughly two months behind the reference period, so
 * 100 days is the point at which a value is genuinely overdue rather than merely
 * old. ANRE changes tariffs irregularly and announces them per order, so a wide
 * budget is the honest one - a tariff unchanged for six months is normal.
 */
public enum MacroReadingKind {

    CPI("cpi", "INS", 100),
    ENERGY_TARIFF("energy_tariff", "ANRE", 400);

    private final String wireName;
    private final String source;
    private final int stalenessBudgetDays;

    MacroReadingKind(String wireName, String source, int stalenessBudgetDays) {
        this.wireName = wireName;
        this.source = source;
        this.stalenessBudgetDays = stalenessBudgetDays;
    }

    public boolean isStaleAt(LocalDate asOfDate, LocalDate today) {
        return ChronoUnit.DAYS.between(asOfDate, today) > stalenessBudgetDays;
    }

    public String wireName() {
        return wireName;
    }

    public String source() {
        return source;
    }

    public int stalenessBudgetDays() {
        return stalenessBudgetDays;
    }
}
