package com.personalfinance.user.service;

import java.time.LocalDate;
import java.util.Map;

import com.personalfinance.user.entity.TaxConfigEntity;
import com.personalfinance.user.repository.TaxConfigRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Romanian net ↔ gross salary calculator (FR-10). Rates come from the
 * tax_config table (versioned by valid_from) so a law change is a data change.
 * All amounts in bani.
 */
@Service
@AllArgsConstructor
public class SalaryService {

    public record TaxRules(double casRate, double cassRate, double incomeTaxRate, long personalDeduction,
            LocalDate validFrom) {
    }

    public record Breakdown(long gross, long cas, long cass, long taxable, long incomeTax, long net,
            LocalDate rulesValidFrom) {
    }

    private final TaxConfigRepository taxConfigs;

    @Transactional(readOnly = true)
    public TaxRules rulesFor(LocalDate date) {
        TaxConfigEntity config = taxConfigs.findFirstByValidFromLessThanEqualOrderByValidFromDesc(date)
                .orElseThrow(() -> new IllegalStateException("No tax configuration for " + date));
        Map<String, Object> rules = config.getRules();
        return new TaxRules(
                ((Number) rules.get("casRate")).doubleValue(),
                ((Number) rules.get("cassRate")).doubleValue(),
                ((Number) rules.get("incomeTaxRate")).doubleValue(),
                ((Number) rules.getOrDefault("personalDeduction", 0)).longValue(),
                config.getValidFrom());
    }

    public Breakdown netFromGross(long gross, TaxRules rules) {
        long cas = Math.round(gross * rules.casRate());
        long cass = Math.round(gross * rules.cassRate());
        long taxable = Math.max(0, gross - cas - cass - rules.personalDeduction());
        long incomeTax = Math.round(taxable * rules.incomeTaxRate());
        long net = gross - cas - cass - incomeTax;
        return new Breakdown(gross, cas, cass, taxable, incomeTax, net, rules.validFrom());
    }

    /**
     * Inverts netFromGross. Rounding makes a closed form inexact, so estimate
     * and search the neighborhood for the gross whose net matches best.
     */
    public Breakdown grossFromNet(long net, TaxRules rules) {
        double keepRatio = (1 - rules.casRate() - rules.cassRate()) * (1 - rules.incomeTaxRate());
        long estimate = Math.round(net / keepRatio);

        Breakdown best = netFromGross(estimate, rules);
        for (long gross = Math.max(0, estimate - 300); gross <= estimate + 300; gross++) {
            Breakdown candidate = netFromGross(gross, rules);
            if (Math.abs(candidate.net() - net) < Math.abs(best.net() - net)
                    || (Math.abs(candidate.net() - net) == Math.abs(best.net() - net)
                            && candidate.gross() < best.gross())) {
                best = candidate;
            }
            if (candidate.net() == net) {
                return candidate;
            }
        }
        return best;
    }
}
