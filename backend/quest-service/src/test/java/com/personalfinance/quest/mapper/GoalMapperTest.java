package com.personalfinance.quest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.quest.dto.GoalDto;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.entity.GoalEvaluationEntity;
import com.personalfinance.quest.service.GoalStatus;

class GoalMapperTest {

    private final GoalMapper mapper = new GoalMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void toDtoCarriesBothTheStoredGoalAndItsComputedProgress() {
        UUID categoryId = UUID.randomUUID();
        GoalEntity goal = new GoalEntity(userId, "Food budget", categoryId, 50000, "MONTHLY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        GoalStatus status = new GoalStatus(goal, 42000, true,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        GoalDto dto = mapper.toDto(status);

        assertThat(dto.id()).isEqualTo(goal.getId());
        assertThat(dto.name()).isEqualTo("Food budget");
        assertThat(dto.categoryId()).isEqualTo(categoryId);
        assertThat(dto.targetAmount()).isEqualTo(50000);
        assertThat(dto.period()).isEqualTo("MONTHLY");
        assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(dto.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(dto.active()).isTrue();
        assertThat(dto.currentActual()).isEqualTo(42000);
        assertThat(dto.currentMet()).isTrue();
        assertThat(dto.periodStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(dto.periodEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void anOpenEndedGoalMapsItsNullEndDateThrough() {
        GoalEntity goal = new GoalEntity(userId, "Forever", null, 100, "DAILY",
                LocalDate.of(2026, 1, 1), null);

        GoalDto dto = mapper.toDto(new GoalStatus(goal, 0, true,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15)));

        assertThat(dto.endDate()).isNull();
        assertThat(dto.categoryId()).isNull();
    }

    @Test
    void toDtosPreservesOrderAndMapsEmptyToEmpty() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
        assertThat(mapper.toExportDtos(List.of())).isEmpty();
        assertThat(mapper.toEvaluationDtos(List.of())).isEmpty();
    }

    @Test
    void theExportDtoReportsTheStoredRowNotTheComputedProgress() {
        GoalEntity goal = new GoalEntity(userId, "Food budget", null, 50000, "MONTHLY",
                LocalDate.of(2026, 1, 1), null);

        assertThat(mapper.toExportDto(goal)).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(goal.getId());
            assertThat(dto.type()).isEqualTo("SPENDING_LIMIT");
            assertThat(dto.targetAmount()).isEqualTo(50000);
            assertThat(dto.active()).isTrue();
        });
    }

    @Test
    void anEvaluationMapsEveryStoredColumn() {
        UUID goalId = UUID.randomUUID();
        GoalEvaluationEntity evaluation = new GoalEvaluationEntity(goalId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 12345, false);

        assertThat(mapper.toEvaluationDto(evaluation)).satisfies(dto -> {
            assertThat(dto.goalId()).isEqualTo(goalId);
            assertThat(dto.periodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(dto.periodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(dto.actualAmount()).isEqualTo(12345);
            assertThat(dto.met()).isFalse();
            assertThat(dto.evaluatedAt()).isNotNull();
        });
    }
}
