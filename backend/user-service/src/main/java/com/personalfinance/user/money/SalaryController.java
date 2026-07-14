package com.personalfinance.user.money;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/salary-calculator")
public class SalaryController {

    public record CalcRequest(
            @NotNull @Pattern(regexp = "GROSS_TO_NET|NET_TO_GROSS") String mode,
            @Positive long amount) {
    }

    private final SalaryService service;

    public SalaryController(SalaryService service) {
        this.service = service;
    }

    @PostMapping
    public SalaryService.Breakdown calculate(@Valid @RequestBody CalcRequest request) {
        SalaryService.TaxRules rules = service.rulesFor(LocalDate.now());
        return "GROSS_TO_NET".equals(request.mode())
                ? service.netFromGross(request.amount(), rules)
                : service.grossFromNet(request.amount(), rules);
    }
}
