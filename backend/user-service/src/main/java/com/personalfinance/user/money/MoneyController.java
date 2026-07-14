package com.personalfinance.user.money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Income sources, savings, and net worth (FR-2, FR-8). */
@RestController
@RequestMapping("/api/v1/me")
public class MoneyController {

    public record IncomeDto(UUID id, String name, long amount, String recurrence,
            LocalDate startDate, LocalDate endDate) {

        static IncomeDto of(IncomeSourceEntity e) {
            return new IncomeDto(e.getId(), e.getName(), e.getAmount(), e.getRecurrence(),
                    e.getStartDate(), e.getEndDate());
        }
    }

    public record IncomeRequest(
            @NotBlank @Size(max = 100) String name,
            @Positive long amount,
            @NotNull @Pattern(regexp = "MONTHLY|YEARLY|ONE_OFF") String recurrence,
            @NotNull LocalDate startDate,
            LocalDate endDate) {
    }

    public record SavingsDto(UUID id, String name, long balance) {

        static SavingsDto of(SavingsAccountEntity e) {
            return new SavingsDto(e.getId(), e.getName(), e.getBalance());
        }
    }

    public record SavingsRequest(
            @NotBlank @Size(max = 100) String name,
            @PositiveOrZero long balance) {
    }

    public record NetWorth(long total, int accounts, long monthlyIncome) {
    }

    private final IncomeSourceRepository incomes;
    private final SavingsAccountRepository savings;

    public MoneyController(IncomeSourceRepository incomes, SavingsAccountRepository savings) {
        this.incomes = incomes;
        this.savings = savings;
    }

    // ---- income sources ----

    @GetMapping("/income-sources")
    public List<IncomeDto> listIncome(@AuthenticationPrincipal UUID userId) {
        return incomes.findByUserIdOrderByCreatedAtAsc(userId).stream().map(IncomeDto::of).toList();
    }

    @PostMapping("/income-sources")
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeDto createIncome(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody IncomeRequest request) {
        return IncomeDto.of(incomes.save(new IncomeSourceEntity(userId, request.name(), request.amount(),
                request.recurrence(), request.startDate(), request.endDate())));
    }

    @PutMapping("/income-sources/{id}")
    public IncomeDto updateIncome(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody IncomeRequest request) {
        IncomeSourceEntity income = incomes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income source not found"));
        income.update(request.name(), request.amount(), request.recurrence(),
                request.startDate(), request.endDate());
        return IncomeDto.of(incomes.save(income));
    }

    @DeleteMapping("/income-sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIncome(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        IncomeSourceEntity income = incomes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income source not found"));
        incomes.delete(income);
    }

    // ---- savings ----

    @GetMapping("/savings")
    public List<SavingsDto> listSavings(@AuthenticationPrincipal UUID userId) {
        return savings.findByUserIdOrderByNameAsc(userId).stream().map(SavingsDto::of).toList();
    }

    @PostMapping("/savings")
    @ResponseStatus(HttpStatus.CREATED)
    public SavingsDto createSavings(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody SavingsRequest request) {
        return SavingsDto.of(savings.save(new SavingsAccountEntity(userId, request.name(), request.balance())));
    }

    @PutMapping("/savings/{id}")
    public SavingsDto updateSavings(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody SavingsRequest request) {
        SavingsAccountEntity account = savings.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found"));
        account.update(request.name(), request.balance());
        return SavingsDto.of(savings.save(account));
    }

    @DeleteMapping("/savings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavings(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        SavingsAccountEntity account = savings.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found"));
        savings.delete(account);
    }

    // ---- net worth (FR-8) ----

    @GetMapping("/net-worth")
    public NetWorth netWorth(@AuthenticationPrincipal UUID userId) {
        List<SavingsAccountEntity> accounts = savings.findByUserIdOrderByNameAsc(userId);
        long total = accounts.stream().mapToLong(SavingsAccountEntity::getBalance).sum();

        // Normalized monthly income for the quest/goal features and the UI.
        LocalDate today = LocalDate.now();
        long monthlyIncome = incomes.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(i -> !i.getStartDate().isAfter(today))
                .filter(i -> i.getEndDate() == null || !i.getEndDate().isBefore(today))
                .mapToLong(i -> switch (i.getRecurrence()) {
                    case "MONTHLY" -> i.getAmount();
                    case "YEARLY" -> Math.round(i.getAmount() / 12.0);
                    default -> 0;   // ONE_OFF doesn't contribute to recurring income
                })
                .sum();

        return new NetWorth(total, accounts.size(), monthlyIncome);
    }
}
