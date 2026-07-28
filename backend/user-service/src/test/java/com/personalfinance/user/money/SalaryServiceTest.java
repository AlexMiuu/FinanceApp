package com.personalfinance.user.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.personalfinance.user.repository.TaxConfigRepository;
import com.personalfinance.user.service.SalaryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.personalfinance.user.service.SalaryService.Breakdown;
import com.personalfinance.user.service.SalaryService.TaxRules;

class SalaryServiceTest {

    private final SalaryService service = new SalaryService(Mockito.mock(TaxConfigRepository.class));
    private final TaxRules rules2026 = new TaxRules(0.25, 0.10, 0.10, 0, LocalDate.of(2026, 1, 1));

    @Test
    void grossToNet_romanianRates() {
        // 10,000 RON gross: CAS 2,500 + CASS 1,000 + income tax 650 -> net 5,850
        Breakdown b = service.netFromGross(1_000_000, rules2026);

        assertThat(b.cas()).isEqualTo(250_000);
        assertThat(b.cass()).isEqualTo(100_000);
        assertThat(b.taxable()).isEqualTo(650_000);
        assertThat(b.incomeTax()).isEqualTo(65_000);
        assertThat(b.net()).isEqualTo(585_000);
    }

    @Test
    void netToGross_invertsExactly() {
        Breakdown b = service.grossFromNet(585_000, rules2026);

        assertThat(b.net()).isEqualTo(585_000);
        assertThat(b.gross()).isEqualTo(1_000_000);
    }

    @Test
    void netToGross_handlesRoundingResidue() {
        // An arbitrary non-round net still resolves to a gross whose recomputed
        // net matches (or is the closest achievable value).
        long net = 317_777;
        Breakdown b = service.grossFromNet(net, rules2026);

        assertThat(Math.abs(b.net() - net)).isLessThanOrEqualTo(1);
        assertThat(service.netFromGross(b.gross(), rules2026).net()).isEqualTo(b.net());
    }

    @Test
    void personalDeductionReducesTax() {
        TaxRules withDeduction = new TaxRules(0.25, 0.10, 0.10, 50_000, LocalDate.of(2026, 1, 1));

        Breakdown without = service.netFromGross(400_000, rules2026);
        Breakdown with = service.netFromGross(400_000, withDeduction);

        assertThat(with.taxable()).isEqualTo(without.taxable() - 50_000);
        assertThat(with.net()).isEqualTo(without.net() + 5_000);
    }
}
