package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireUser;

import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.mapper.UserMapper;
import com.personalfinance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository users;
    private final UserMapper userMapper;

    /**
     * A token whose subject no longer exists (deleted account, rotated database)
     * is an authentication problem, not a missing resource — hence 401, not 404.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<?> currentUser(UUID userId) {
        requireUser(userId);

        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown account"));
        return ResponseEntity.ok(userMapper.toDto(user));
    }
}
