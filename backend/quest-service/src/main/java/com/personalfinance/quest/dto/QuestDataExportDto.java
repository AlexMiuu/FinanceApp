package com.personalfinance.quest.dto;

import java.util.List;

/**
 * Every personal-data class quests_db holds for one user (GDPR Art. 20),
 * per docs/records-of-processing.md. {@code monthlyIncome} is null when no
 * income.updated event has ever been projected for the user.
 */
public record QuestDataExportDto(
        List<CategoryProjectionDto> categoryProjections,
        List<ExpenseProjectionDto> expenseProjections,
        List<GoalExportDto> goals,
        List<GoalEvaluationDto> goalEvaluations,
        List<QuestExportDto> quests,
        UserIncomeDto monthlyIncome) {
}
