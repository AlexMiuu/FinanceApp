package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireUser;

import com.personalfinance.user.dto.NetWorthDto;
import com.personalfinance.user.entity.SavingsAccountEntity;
import com.personalfinance.user.repository.IncomeSourceRepository;
import com.personalfinance.user.repository.SavingsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Savings total plus normalized monthly income (FR-8). */
@Service
@RequiredArgsConstructor
public class NetWorthService {

    private final SavingsAccountRepository savings;
    private final IncomeSourceRepository incomes;

    @Transactional(readOnly = true)
    public ResponseEntity<?> netWorth(UUID userId) {
        requireUser(userId);

        List<SavingsAccountEntity> accounts = savings.findByUserIdOrderByNameAsc(userId);
        long total = accounts.stream().mapToLong(SavingsAccountEntity::getBalance).sum();
        long monthlyIncome = IncomeCalculator.monthlyIncome(
                incomes.findByUserIdOrderByCreatedAtAsc(userId), LocalDate.now());

        return ResponseEntity.ok(NetWorthDto.builder()
                .total(total)
                .accounts(accounts.size())
                .monthlyIncome(monthlyIncome)
                .build());
    }
}
