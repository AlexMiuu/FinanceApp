package com.personalfinance.report.macro.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Arrays;

/**
 * The Romanian pastoral year, which F1 uses as the ledger's calendar. The two
 * outer boundaries are the traditional ones: Sângeorz (23 April) opens the
 * pastoral year and sends the flocks up, Sânmedru (26 October) closes it with
 * {@code coborâtul oilor}, the descent, when shepherds are paid and the village
 * lays in its winter stores.
 *
 * <p>The four windows tile the year exactly — every date resolves to exactly one
 * season, and {@link #IERNAT} is the one that wraps the year boundary.
 *
 * <p>{@code costFactor} is a <em>documented starting assumption</em>, not a
 * measured figure: winter carries heating, the descent carries stocking up, the
 * mountain months are the cheapest. It is deliberately conservative and should be
 * recalibrated against real per-season history once enough of it exists.
 */
public enum PastoralSeason {

    URCATUL("urcatul", "Urcatul — the spring ascent",
            MonthDay.of(4, 23), MonthDay.of(6, 20), "0.98"),

    MUNTE("munte", "Muntele — the high pasture",
            MonthDay.of(6, 21), MonthDay.of(9, 14), "0.95"),

    COBORATUL("coboratul", "Coborâtul oilor — the autumn descent",
            MonthDay.of(9, 15), MonthDay.of(10, 26), "1.05"),

    IERNAT("iernat", "Iernatul — wintering in the village",
            MonthDay.of(10, 27), MonthDay.of(4, 22), "1.12");

    private final String wireName;
    private final String label;
    private final MonthDay start;
    private final MonthDay end;
    private final BigDecimal costFactor;

    PastoralSeason(String wireName, String label, MonthDay start, MonthDay end, String costFactor) {
        this.wireName = wireName;
        this.label = label;
        this.start = start;
        this.end = end;
        this.costFactor = new BigDecimal(costFactor);
    }

    public static PastoralSeason forDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        MonthDay probe = MonthDay.from(date);
        return Arrays.stream(values())
                .filter(season -> season.contains(probe))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no pastoral season covers " + date));
    }

    public static PastoralSeason fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(season -> season.wireName.equals(wireName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown season: " + wireName));
    }

    private boolean contains(MonthDay probe) {
        boolean wrapsYearEnd = start.isAfter(end);
        if (wrapsYearEnd) {
            return !probe.isBefore(start) || !probe.isAfter(end);
        }
        return !probe.isBefore(start) && !probe.isAfter(end);
    }

    public String wireName() {
        return wireName;
    }

    public String label() {
        return label;
    }

    public BigDecimal costFactor() {
        return costFactor;
    }
}
