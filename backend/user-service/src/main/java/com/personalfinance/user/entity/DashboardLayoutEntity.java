package com.personalfinance.user.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One user's dashboard widget arrangement, stored as a JSON document keyed by
 * the user id. The layout is replaced whole on every save, so there is no
 * per-widget row to keep in sync.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "dashboard_layouts")
public class DashboardLayoutEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Already-serialized JSON; the service owns the shape and validates it before it gets here. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String layout;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DashboardLayoutEntity(UUID userId, String layout) {
        this.userId = userId;
        this.layout = layout;
        this.updatedAt = Instant.now();
    }

    /**
     * Stamped here rather than in a {@code @PreUpdate} callback: the callback runs
     * at flush, so a caller reading the timestamp back straight after saving would
     * see the previous value.
     */
    public void replaceLayout(String layout) {
        this.layout = layout;
        this.updatedAt = Instant.now();
    }
}
