package com.personalfinance.report.mapper;

import org.springframework.stereotype.Component;

import com.personalfinance.report.dto.UserIncomeExportDto;
import com.personalfinance.report.entity.UserIncomeEntity;

@Component
public class UserIncomeMapper {

    public UserIncomeExportDto toExportDto(UserIncomeEntity entity) {
        return new UserIncomeExportDto(entity.getMonthlyIncome(), entity.getUpdatedAt());
    }
}
