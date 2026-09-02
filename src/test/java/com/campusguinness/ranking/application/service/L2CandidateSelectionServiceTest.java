package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class L2CandidateSelectionServiceTest {
    private final L2CandidateSelectionService selector = new L2CandidateSelectionService();

    @Test
    void integerHigherBetterSelectsMaximumScore() {
        UUID student = UUID.randomUUID();
        UUID selected = UUID.nameUUIDFromBytes("selected".getBytes());
        var rows = selector.selectBestScores(context("INTEGER", "HIGHER_BETTER", null),
                List.of(
                        source("low", student, new BigDecimal("90"), null, null),
                        source(selected, student, new BigDecimal("95"), null, null),
                        source("mid", student, new BigDecimal("92"), null, null)
                ));

        assertThat(rows).extracting(RankingGenerationSourceRow::scoreAttemptId).containsExactly(selected);
    }

    @Test
    void decimalHigherBetterUsesNumericComparison() {
        UUID student = UUID.randomUUID();
        UUID selected = UUID.nameUUIDFromBytes("ten-twenty".getBytes());
        var rows = selector.selectBestScores(context("DECIMAL", "HIGHER_BETTER", null),
                List.of(
                        source("ten-eleven", student, new BigDecimal("10.11"), null, null),
                        source(selected, student, new BigDecimal("10.20"), null, null)
                ));

        assertThat(rows).extracting(RankingGenerationSourceRow::scoreAttemptId).containsExactly(selected);
    }

    @Test
    void durationLowerBetterSelectsMinimumDuration() {
        UUID student = UUID.randomUUID();
        UUID selected = UUID.nameUUIDFromBytes("fast".getBytes());
        var rows = selector.selectBestScores(context("DURATION", "LOWER_BETTER", null),
                List.of(
                        source("slow", student, null, 12_800L, null),
                        source(selected, student, null, 10_200L, null),
                        source("middle", student, null, 11_100L, null)
                ));

        assertThat(rows).extracting(RankingGenerationSourceRow::scoreAttemptId).containsExactly(selected);
    }

    @Test
    void gradeUsesFrozenGradeOrder() {
        UUID student = UUID.randomUUID();
        UUID selected = UUID.nameUUIDFromBytes("gold".getBytes());
        var rows = selector.selectBestScores(context("GRADE", "GRADE_ORDER", "[\"GOLD\",\"SILVER\",\"BRONZE\"]"),
                List.of(
                        source("bronze", student, null, null, "BRONZE"),
                        source(selected, student, null, null, "GOLD")
                ));

        assertThat(rows).extracting(RankingGenerationSourceRow::scoreAttemptId).containsExactly(selected);
    }

    private RankingGenerationContext context(String storageType, String direction, String gradeOrder) {
        return new RankingGenerationContext(
                null,
                null,
                null,
                UUID.randomUUID(),
                "Campus School",
                UUID.randomUUID(),
                "Project",
                ruleVersionId(),
                1,
                storageType,
                direction,
                2,
                gradeOrder);
    }

    private RankingGenerationSourceRow source(String attemptIdSeed, UUID studentId, BigDecimal value, Long duration, String grade) {
        return source(UUID.nameUUIDFromBytes(attemptIdSeed.getBytes()), studentId, value, duration, grade);
    }

    private RankingGenerationSourceRow source(UUID attemptId, UUID studentId, BigDecimal value, Long duration, String grade) {
        return new RankingGenerationSourceRow(
                attemptId,
                studentId,
                "Student",
                value,
                duration,
                grade,
                UUID.randomUUID(),
                ruleVersionId());
    }

    private UUID ruleVersionId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000099");
    }
}
