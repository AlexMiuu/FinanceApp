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

import java.util.UUID;

import static com.personalfinance.user.service.RequestGuards.requireBody;
import static com.personalfinance.user.service.RequestGuards.requireFound;
import static com.personalfinance.user.service.RequestGuards.requireId;
import static com.personalfinance.user.service.RequestGuards.requireUser;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeSourceRepository incomes;
    private final IncomeEventPublisher incomeEvents;
    private final IncomeMapper incomeMapper;

    @Transactional(readOnly = true)
    public ResponseEntity<?> listAllIncome(UUID userId) {
        requireUser(userId);
        return ResponseEntity.ok(incomeMapper.toDtos(incomes.findByUserIdOrderByCreatedAtAsc(userId)));
    }

    @Transactional
    public ResponseEntity<?> createIncome(UUID userId, IncomeRequestDto request) {
        requireUser(userId);
        requireBody(request);

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
        requireUser(userId);
        requireId(id, "Income source");
        requireBody(request);

        IncomeSourceEntity income = findOwned(userId, id);
        income.update(request.getName(), request.getAmount(), request.getRecurrence(),
                request.getStartDate(), request.getEndDate());

        IncomeDto dto = incomeMapper.toDto(incomes.save(income));
        incomeEvents.publishFor(userId);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> deleteIncome(UUID userId, UUID id) {
        requireUser(userId);
        requireId(id, "Income source");

        incomes.delete(findOwned(userId, id));
        incomeEvents.publishFor(userId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /** Scoping the lookup by userId is what keeps one user out of another's rows. */
    private IncomeSourceEntity findOwned(UUID userId, UUID id) {
        return requireFound(incomes.findByIdAndUserId(id, userId), "Income source not found");
    }
}
