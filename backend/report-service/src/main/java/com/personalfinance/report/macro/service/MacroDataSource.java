package com.personalfinance.report.macro.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.personalfinance.report.macro.entity.MacroReadingKind;

/**
 * A pluggable source for one macro series. {@code Optional.empty()} on any
 * failure (network, parse, unexpected shape) — this interface never throws
 * for an ordinary fetch failure, since a fetch failure is the expected,
 * routine case R4 anticipates, not an exceptional one. {@link MacroService}
 * treats an empty result as "serve the cache," never as reason to block.
 */
public interface MacroDataSource {

    Optional<Reading> fetch(MacroReadingKind kind);

    record Reading(BigDecimal value, LocalDate asOfDate) {
    }
}
