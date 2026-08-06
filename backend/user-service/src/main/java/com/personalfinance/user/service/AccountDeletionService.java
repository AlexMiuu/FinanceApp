package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireFound;
import static com.personalfinance.user.service.RequestGuards.requireUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.personalfinance.user.entity.ErasureRequestEntity;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.events.UserErasureRequestedEvent;
import com.personalfinance.user.repository.ErasureRequestRepository;
import com.personalfinance.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account erasure (GDPR Art. 17). This service deletes its own data inline and
 * fans the request out to the other services holding a copy, then tracks their
 * acknowledgements until the request reaches a terminal COMPLETED state.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** This service's own name in the expected/completed service lists. */
    private static final String OWN_SERVICE = "user";

    private final UserRepository users;
    private final ErasureRequestRepository erasureRequests;
    private final ApplicationEventPublisher eventPublisher;
    private final List<String> expectedServices;

    public AccountDeletionService(UserRepository users,
            ErasureRequestRepository erasureRequests,
            ApplicationEventPublisher eventPublisher,
            // Fallback mirrors application.yml's default: includes report,quest,
            // notification even before M9 adds their handlers, so a request stays
            // honestly PENDING instead of completing while their data survives.
            @Value("${privacy.erasure.expected-services:user,expense,report,quest,notification}") List<String> expectedServices) {
        this.users = users;
        this.erasureRequests = erasureRequests;
        this.eventPublisher = eventPublisher;
        this.expectedServices = List.copyOf(expectedServices);
    }

    /**
     * Deletes this service's copy of the account and asks every other service to
     * do the same. The audit row is written before the user row goes away, and the
     * fanout only leaves the process if this transaction commits.
     */
    @Transactional
    public UUID initiateErasure(UUID userId) {
        requireUser(userId);

        UserEntity user = requireFound(users.findById(userId), "Account not found");

        ErasureRequestEntity request =
                new ErasureRequestEntity(userId, expectedServices, Set.of(OWN_SERVICE));
        erasureRequests.save(request);

        // auth_identities, refresh_tokens, income_sources, savings_accounts and
        // consent_records all cascade from users (id); erasure_requests does not.
        users.delete(user);

        eventPublisher.publishEvent(
                new UserErasureRequestedEvent(request.getId(), userId, Instant.now()));
        return request.getId();
    }

    /**
     * Records one service's confirmation that it deleted its copy. Idempotent, and
     * deliberately forgiving: this runs on a message listener, where throwing on
     * unexpected input costs redelivery loops rather than correctness.
     */
    @Transactional
    public void recordServiceAck(UUID erasureRequestId, String service) {
        if (erasureRequestId == null || service == null || service.isBlank()) {
            log.warn("Ignoring erasure ack with missing request id or service name");
            return;
        }
        Optional<ErasureRequestEntity> found = erasureRequests.findById(erasureRequestId);
        if (found.isEmpty()) {
            log.warn("Erasure ack from {} for unknown request {}", service, erasureRequestId);
            return;
        }
        ErasureRequestEntity request = found.get();
        request.addCompletedService(service);
        erasureRequests.save(request);

        if (request.isComplete()) {
            log.info("Erasure request {} completed by all of {}",
                    request.getId(), request.expectedServiceNames());
        }
    }
}
