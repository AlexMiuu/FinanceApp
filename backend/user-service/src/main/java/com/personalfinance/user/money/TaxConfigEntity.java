package com.personalfinance.user.money;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tax_config")
public class TaxConfigEntity {

    @Id
    private UUID id;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> rules;

    protected TaxConfigEntity() {
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public Map<String, Object> getRules() {
        return rules;
    }
}
