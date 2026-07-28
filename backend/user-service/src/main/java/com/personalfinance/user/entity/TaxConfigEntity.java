package com.personalfinance.user.entity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "tax_config")
public class TaxConfigEntity {

    @Id
    private UUID id;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> rules;
}
