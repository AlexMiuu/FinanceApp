package com.personalfinance.user.service;

import com.personalfinance.user.dto.IncomeDto;
import com.personalfinance.user.dto.IncomeRequestDto;
import com.personalfinance.user.entity.IncomeSourceEntity;
import com.personalfinance.user.events.IncomeEventPublisher;
import com.personalfinance.user.mapper.IncomeMapper;
import com.personalfinance.user.repository.IncomeSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeSourceRepository incomes;
    private final IncomeEventPublisher incomeEvents;
    private final IncomeMapper incomeMapper;

    @Transactional(readOnly = true)
    public ResponseEntity<?> listAllIncome(UUID userId) {
        return ResponseEntity.ok(incomeMapper.toDtos(incomes.findByUserIdOrderByCreatedAtAsc(userId)));
    }

    @Transactional
    public ResponseEntity<?> createIncome(UUID userId, IncomeRequestDto request) {

        IncomeSourceEntity entity = IncomeSourceEntity.builder()
                .userId(userId)
                .name(request.getName())
                .amount(request.getAmount())
                .recurrence(request.getRecurrence())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        IncomeDto dto = incomeMapper.toDto(incomes.save(entity));
        incomeEvents.publishFor(userId);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @Transactional
    public ResponseEntity<?> updateIncome(UUID userId, UUID id, IncomeRequestDto request) {

        IncomeSourceEntity income = findOwned(userId, id);
        income.update(request.getName(), request.getAmount(), request.getRecurrence(),
                request.getStartDate(), request.getEndDate());

        IncomeDto dto = incomeMapper.toDto(incomes.save(income));
        incomeEvents.publishFor(userId);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> deleteIncome(UUID userId, UUID id) {
        incomes.delete(findOwned(userId, id));
        incomeEvents.publishFor(userId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /** Scoping the lookup by userId is what keeps one user out of another's rows. */
    private IncomeSourceEntity findOwned(UUID userId, UUID id) {
        return incomes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income source not found"));
    }
}
