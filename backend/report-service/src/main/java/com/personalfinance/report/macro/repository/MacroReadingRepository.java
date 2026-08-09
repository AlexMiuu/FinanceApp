package com.personalfinance.report.macro.repository;

import com.personalfinance.report.macro.entity.MacroReadingEntity;
import com.personalfinance.report.macro.entity.MacroReadingKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MacroReadingRepository extends JpaRepository<MacroReadingEntity, MacroReadingKind> {
}
