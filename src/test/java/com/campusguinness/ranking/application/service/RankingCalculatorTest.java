package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.exception.RankingConfigurationException;
import com.campusguinness.ranking.application.exception.RankingConflictException;
import com.campusguinness.ranking.application.exception.RankingDataConflictException;
import com.campusguinness.ranking.application.query.model.RankingScoreSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingCalculatorTest {

    private static final UUID RULE_ID =
            UUID.fromString("10000000-0000-0000-8000-000000000001");
    private static final Instant BASE_TIME =
            Instant.parse("2026-07-30T08:00:00Z");

    @Test
    void higherBetterRanksLargerIntegerFirst() {
        var low = numeric("INTEGER", "10", 1);
        var high = numeric("INTEGER", "20", 2);

        var result = rank(List.of(low, high), "INTEGER", "HIGHER_BETTER", true);

        assertThat(result).extracting(entry -> entry.scoreAttemptId())
                .containsExactly(high.scoreAttemptId(), low.scoreAttemptId());
    }

    @Test
    void lowerBetterRanksSmallerDurationFirst() {
        var slow = duration(65_000, 1);
        var fast = duration(50_000, 2);

        var result = rank(List.of(slow, fast), "DURATION", "LOWER_BETTER", true);

        assertThat(result.getFirst().scoreAttemptId()).isEqualTo(fast.scoreAttemptId());
    }

    @Test
    void decimalRankingUsesNumericComparison() {
        var ten = numeric("DECIMAL", "10.10", 1);
        var nine = numeric("DECIMAL", "9.90", 2);

        var result = RankingCalculator.rank(
                List.of(nine, ten), "DECIMAL", "HIGHER_BETTER", null, true, 2);

        assertThat(result.getFirst().scoreAttemptId()).isEqualTo(ten.scoreAttemptId());
    }

    @Test
    void gradeRankingUsesGradeOrder() {
        var gold = grade("金", 1);
        var silver = grade("银", 2);

        var result = RankingCalculator.rank(
                List.of(silver, gold), "GRADE", "GRADE_ORDER",
                "金,银,铜", true, null);

        assertThat(result.getFirst().scoreAttemptId()).isEqualTo(gold.scoreAttemptId());
    }

    @Test
    void gradeRankingDoesNotUseLexicographicOrder() {
        var a = grade("A", 1);
        var s = grade("S", 2);

        var result = RankingCalculator.rank(
                List.of(a, s), "GRADE", "GRADE_ORDER", "S,A,B", true, null);

        assertThat(result.getFirst().scoreAttemptId()).isEqualTo(s.scoreAttemptId());
    }

    @Test
    void invalidGradeOrderFails() {
        assertThatThrownBy(() -> RankingCalculator.rank(
                List.of(grade("A", 1)), "GRADE", "GRADE_ORDER", "A,,B", true, null))
                .isInstanceOf(RankingConfigurationException.class);
    }

    @Test
    void scoreGradeMissingFromOrderFails() {
        assertThatThrownBy(() -> RankingCalculator.rank(
                List.of(grade("C", 1)), "GRADE", "GRADE_ORDER", "A,B", true, null))
                .isInstanceOf(RankingConfigurationException.class);
    }

    @Test
    void noRankingFails() {
        assertThatThrownBy(() -> RankingCalculator.rank(
                List.of(numeric("INTEGER", "1", 1)),
                "INTEGER", "NO_RANKING", null, true, 0))
                .isInstanceOf(RankingConflictException.class)
                .extracting("errorCode")
                .isEqualTo("RANKING_DISABLED_FOR_PROJECT");
    }

    @Test
    void allowTieUsesCompetitionRanking() {
        var result = rank(
                List.of(
                        numeric("INTEGER", "100", 1),
                        numeric("INTEGER", "100", 2)),
                "INTEGER",
                "HIGHER_BETTER",
                true);

        assertThat(result).extracting(entry -> entry.rankPosition())
                .containsExactly(1, 1);
    }

    @Test
    void competitionRanksAreOneTwoTwoFour() {
        var result = rank(
                List.of(
                        numeric("INTEGER", "100", 1),
                        numeric("INTEGER", "90", 2),
                        numeric("INTEGER", "90", 3),
                        numeric("INTEGER", "80", 4)),
                "INTEGER",
                "HIGHER_BETTER",
                true);

        assertThat(result).extracting(entry -> entry.rankPosition())
                .containsExactly(1, 2, 2, 4);
    }

    @Test
    void disallowTieUsesBusinessTime() {
        var later = numeric("INTEGER", "100", 3);
        var earlier = numeric("INTEGER", "100", 1);

        var result = rank(
                List.of(later, earlier), "INTEGER", "HIGHER_BETTER", false);

        assertThat(result).extracting(entry -> entry.scoreAttemptId())
                .containsExactly(earlier.scoreAttemptId(), later.scoreAttemptId());
        assertThat(result).extracting(entry -> entry.rankPosition())
                .containsExactly(1, 2);
    }

    @Test
    void sameBusinessTimeUsesAttemptId() {
        UUID lowAttempt = UUID.fromString("00000000-0000-0000-8000-000000000001");
        UUID highAttempt = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        var first = source(lowAttempt, UUID.randomUUID(), "INTEGER",
                new BigDecimal("10"), null, null, BASE_TIME);
        var second = source(highAttempt, UUID.randomUUID(), "INTEGER",
                new BigDecimal("10"), null, null, BASE_TIME);

        var result = rank(
                List.of(second, first), "INTEGER", "HIGHER_BETTER", false);

        assertThat(result.getFirst().scoreAttemptId()).isEqualTo(lowAttempt);
    }

    @Test
    void duplicateStudentSourceFails() {
        UUID student = UUID.randomUUID();
        var first = source(UUID.randomUUID(), student, "INTEGER",
                BigDecimal.ONE, null, null, BASE_TIME);
        var second = source(UUID.randomUUID(), student, "INTEGER",
                BigDecimal.TEN, null, null, BASE_TIME.plusSeconds(1));

        assertThatThrownBy(() -> rank(
                List.of(first, second), "INTEGER", "HIGHER_BETTER", true))
                .isInstanceOf(RankingDataConflictException.class);
    }

    @Test
    void mixedStorageTypesFail() {
        assertThatThrownBy(() -> rank(
                List.of(numeric("INTEGER", "1", 1), duration(1_000, 2)),
                "INTEGER", "HIGHER_BETTER", true))
                .isInstanceOf(RankingDataConflictException.class);
    }

    @Test
    void emptySourcesReturnEmptyPreview() {
        assertThat(rank(List.of(), "INTEGER", "HIGHER_BETTER", true)).isEmpty();
    }

    @Test
    void scoreDisplayUsesSharedFormatter() {
        var result = RankingCalculator.rank(
                List.of(duration(62_000, 1)),
                "DURATION", "LOWER_BETTER", null, true, null);

        assertThat(result.getFirst().scoreDisplayValue()).isEqualTo("1分2秒");
    }

    @Test
    void calculationIsStableAcrossInputOrder() {
        List<RankingScoreSource> sources = new ArrayList<>(List.of(
                numeric("INTEGER", "80", 1),
                numeric("INTEGER", "90", 2),
                numeric("INTEGER", "90", 3)));
        var first = rank(sources, "INTEGER", "HIGHER_BETTER", false);
        Collections.reverse(sources);
        var second = rank(sources, "INTEGER", "HIGHER_BETTER", false);

        assertThat(second).isEqualTo(first);
    }

    private static List<com.campusguinness.ranking.application.query.model.CalculatedRankingEntry> rank(
            List<RankingScoreSource> sources,
            String storageType,
            String direction,
            boolean allowTie) {
        return RankingCalculator.rank(
                sources, storageType, direction, null, allowTie, 0);
    }

    private static RankingScoreSource numeric(
            String storageType, String value, int order) {
        return source(
                UUID.nameUUIDFromBytes(("attempt-" + order).getBytes()),
                UUID.nameUUIDFromBytes(("student-" + order).getBytes()),
                storageType,
                new BigDecimal(value),
                null,
                null,
                BASE_TIME.plusSeconds(order));
    }

    private static RankingScoreSource duration(long durationMs, int order) {
        return source(
                UUID.nameUUIDFromBytes(("duration-attempt-" + order).getBytes()),
                UUID.nameUUIDFromBytes(("duration-student-" + order).getBytes()),
                "DURATION",
                null,
                durationMs,
                null,
                BASE_TIME.plusSeconds(order));
    }

    private static RankingScoreSource grade(String grade, int order) {
        return source(
                UUID.nameUUIDFromBytes(("grade-attempt-" + order).getBytes()),
                UUID.nameUUIDFromBytes(("grade-student-" + order).getBytes()),
                "GRADE",
                null,
                null,
                grade,
                BASE_TIME.plusSeconds(order));
    }

    private static RankingScoreSource source(
            UUID attemptId,
            UUID studentId,
            String storageType,
            BigDecimal value,
            Long durationMs,
            String grade,
            Instant businessTime) {
        return new RankingScoreSource(
                attemptId,
                studentId,
                "Student " + studentId,
                "School",
                storageType,
                value,
                durationMs,
                grade,
                businessTime,
                RULE_ID,
                "DECIMAL".equals(storageType) ? 2 : 0);
    }
}
