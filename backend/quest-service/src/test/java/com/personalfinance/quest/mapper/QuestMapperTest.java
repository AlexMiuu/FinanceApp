package com.personalfinance.quest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.quest.dto.QuestDto;
import com.personalfinance.quest.entity.QuestEntity;
import com.personalfinance.quest.entity.UserIncomeEntity;
import com.personalfinance.quest.service.QuestView;

class QuestMapperTest {

    private final QuestMapper mapper = new QuestMapper();
    private final UUID userId = UUID.randomUUID();

    private QuestEntity quest() {
        return new QuestEntity(userId, "WEEKLY_CAP", "Keep it under 100 RON",
                Map.of("cap", 10000L), LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12));
    }

    @Test
    void toDtoRendersPeriodDatesAsIsoStringsAsTheApiAlwaysHas() {
        QuestDto dto = mapper.toDto(new QuestView(quest(), 10000, 4200, "CAP"));

        assertThat(dto.periodStart()).isEqualTo("2026-07-06");
        assertThat(dto.periodEnd()).isEqualTo("2026-07-12");
    }

    @Test
    void toDtoTakesTargetAndProgressFromTheFreshEvaluation() {
        QuestEntity quest = quest();

        QuestDto dto = mapper.toDto(new QuestView(quest, 10000, 4200, "CAP"));

        assertThat(dto.id()).isEqualTo(quest.getId());
        assertThat(dto.templateCode()).isEqualTo("WEEKLY_CAP");
        assertThat(dto.title()).isEqualTo("Keep it under 100 RON");
        assertThat(dto.status()).isEqualTo("SUGGESTED");
        assertThat(dto.kind()).isEqualTo("CAP");
        assertThat(dto.target()).isEqualTo(10000);
        assertThat(dto.progress()).isEqualTo(4200);
        assertThat(dto.params()).containsEntry("cap", 10000L);
    }

    @Test
    void toDtosMapsEmptyToEmpty() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
        assertThat(mapper.toExportDtos(List.of())).isEmpty();
    }

    @Test
    void theExportDtoReportsStoredProgressRatherThanARecomputedTarget() {
        QuestEntity quest = quest();
        quest.setStatus("ACTIVE");
        quest.setProgressAmount(4200);

        assertThat(mapper.toExportDto(quest)).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(quest.getId());
            assertThat(dto.status()).isEqualTo("ACTIVE");
            assertThat(dto.progressAmount()).isEqualTo(4200);
            assertThat(dto.periodStart()).isEqualTo(LocalDate.of(2026, 7, 6));
            assertThat(dto.params()).containsEntry("cap", 10000L);
        });
    }

    @Test
    void theIncomeProjectionIsExportedWithoutItsUserId() {
        // The export is already scoped to one user — repeating the id adds nothing
        UserIncomeEntity income = new UserIncomeEntity(userId, 600000);

        assertThat(mapper.toIncomeDto(income)).satisfies(dto -> {
            assertThat(dto.monthlyIncome()).isEqualTo(600000);
            assertThat(dto.updatedAt()).isNotNull();
        });
    }
}
