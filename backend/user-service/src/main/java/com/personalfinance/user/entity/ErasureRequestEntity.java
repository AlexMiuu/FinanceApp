package com.personalfinance.user.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Audit trail for one account erasure, tracking which services have confirmed
 * they deleted their copy of the user's data. Survives the user row it refers to
 * (no FK), because the proof an erasure completed must outlive the erasure.
 *
 * <p>The two service lists are stored as comma-separated text rather than an
 * array column: the sets are tiny and closed, and plain text keeps this table
 * free of Hibernate array/JSON type mapping.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "erasure_requests")
public class ErasureRequestEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "expected_services", nullable = false)
    private String expectedServices;

    @Column(name = "completed_services", nullable = false)
    private String completedServices;

    @Column(nullable = false)
    private String status;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ErasureRequestEntity(UUID userId, Collection<String> expectedServices,
            Collection<String> completedServices) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.expectedServices = join(expectedServices);
        this.completedServices = join(completedServices);
        this.status = STATUS_PENDING;
        refreshCompletion();
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    public Set<String> expectedServiceNames() {
        return split(expectedServices);
    }

    public Set<String> completedServiceNames() {
        return split(completedServices);
    }

    public boolean isComplete() {
        return completedServiceNames().containsAll(expectedServiceNames());
    }

    /**
     * Records one service's acknowledgement. Idempotent: a service that acks
     * twice (a redelivered message) leaves the request exactly as it was.
     */
    public void addCompletedService(String service) {
        if (service == null || service.isBlank()) {
            return;
        }
        Set<String> completed = completedServiceNames();
        if (!completed.add(service.trim())) {
            return;
        }
        completedServices = join(completed);
        refreshCompletion();
    }

    private void refreshCompletion() {
        if (isComplete() && !STATUS_COMPLETED.equals(status)) {
            status = STATUS_COMPLETED;
            completedAt = Instant.now();
        }
    }

    private static String join(Collection<String> services) {
        if (services == null) {
            return "";
        }
        return String.join(",", normalize(services));
    }

    private static Set<String> split(String services) {
        if (services == null || services.isBlank()) {
            return new LinkedHashSet<>();
        }
        return normalize(Arrays.asList(services.split(",")));
    }

    private static Set<String> normalize(Collection<String> services) {
        return services.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
