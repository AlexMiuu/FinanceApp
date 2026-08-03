package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireBody;
import static com.personalfinance.user.service.RequestGuards.requireFound;
import static com.personalfinance.user.service.RequestGuards.requireId;
import static com.personalfinance.user.service.RequestGuards.requireUser;

import com.personalfinance.user.dto.SavingsDto;
import com.personalfinance.user.dto.SavingsRequestDto;
import com.personalfinance.user.entity.SavingsAccountEntity;
import com.personalfinance.user.mapper.SavingsMapper;
import com.personalfinance.user.repository.SavingsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavingsService {

    private final SavingsAccountRepository savings;
    private final SavingsMapper savingsMapper;

    @Transactional(readOnly = true)
    public ResponseEntity<?> listAllSavings(UUID userId) {
        requireUser(userId);
        return ResponseEntity.ok(savingsMapper.toDtos(savings.findByUserIdOrderByNameAsc(userId)));
    }

    @Transactional
    public ResponseEntity<?> createSavings(UUID userId, SavingsRequestDto request) {
        requireUser(userId);
        requireBody(request);

        SavingsAccountEntity entity = new SavingsAccountEntity(userId, request.getName(), request.getBalance());

        SavingsDto dto = savingsMapper.toDto(savings.save(entity));
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @Transactional
    public ResponseEntity<?> updateSavings(UUID userId, UUID id, SavingsRequestDto request) {
        requireUser(userId);
        requireId(id, "Savings account");
        requireBody(request);

        SavingsAccountEntity account = findOwned(userId, id);
        account.update(request.getName(), request.getBalance());

        SavingsDto dto = savingsMapper.toDto(savings.save(account));
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> deleteSavings(UUID userId, UUID id) {
        requireUser(userId);
        requireId(id, "Savings account");

        savings.delete(findOwned(userId, id));
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /** Scoping the lookup by userId is what keeps one user out of another's rows. */
    private SavingsAccountEntity findOwned(UUID userId, UUID id) {
        return requireFound(savings.findByIdAndUserId(id, userId), "Savings account not found");
    }
}
