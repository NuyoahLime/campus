package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingGenerationCalculatorTest {
    private final RankingGenerationCalculator calculator = new RankingGenerationCalculator();

    @Test
    void higherBetterUsesSharedRanksAndDeterministicOrder() {
        UUID studentA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID studentB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID studentC = UUID.fromString("00000000-0000-0000-0000-000000000003");
        var snapshot = calculator.calculate(context("INTEGER", "HIGHER_BETTER", null),
                List.of(
                        source("a1", studentA, "Alice", new BigDecimal("98")),
                        source("b1", studentB, "Bob", new BigDecimal("98")),
                        source("c1", studentC, "Cathy", new BigDecimal("95"))
                ));
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::rankPosition)
                .containsExactly(1, 1, 3);
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::studentId)
                .containsExactly(studentA, studentB, studentC);
    }

    @Test
    void lowerBetterSortsAscending() {
        UUID studentA = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID studentB = UUID.fromString("00000000-0000-0000-0000-000000000012");
        var snapshot = calculator.calculate(context("DURATION", "LOWER_BETTER", null),
                List.of(
                        source("a1", studentA, "Alice", null, 1200L, null),
                        source("b1", studentB, "Bob", null, 900L, null)
                ));
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::studentId)
                .containsExactly(studentB, studentA);
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::rankPosition)
                .containsExactly(1, 2);
    }

    @Test
    void decimalOrderingUsesNumericComparison() {
        UUID studentA = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID studentB = UUID.fromString("00000000-0000-0000-0000-000000000032");
        UUID studentC = UUID.fromString("00000000-0000-0000-0000-000000000033");
        var snapshot = calculator.calculate(context("DECIMAL", "HIGHER_BETTER", null),
                List.of(
                        source("a1", studentA, "Alice", new BigDecimal("10.20")),
                        source("b1", studentB, "Bob", new BigDecimal("10.11")),
                        source("c1", studentC, "Cathy", new BigDecimal("9.95"))
                ));
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::studentId)
                .containsExactly(studentA, studentB, studentC);
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::rankPosition)
                .containsExactly(1, 2, 3);
    }

    @Test
    void gradeOrderUsesHistoricalOrdering() {
        UUID studentA = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID studentB = UUID.fromString("00000000-0000-0000-0000-000000000022");
        var snapshot = calculator.calculate(context("GRADE", "GRADE_ORDER", "[\"GOLD\",\"SILVER\",\"BRONZE\"]"),
                List.of(
                        source("a1", studentA, "Alice", null, null, "GOLD"),
                        source("b1", studentB, "Bob", null, null, "BRONZE")
                ));
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::studentDisplayName)
                .containsExactly("Alice", "Bob");
        assertThat(snapshot.entries()).extracting(GeneratedRankingEntry::rankPosition)
                .containsExactly(1, 2);
    }

    @Test
    void rejectsDuplicateStudents() {
        UUID student = UUID.fromString("00000000-0000-0000-0000-000000000031");
        assertThatThrownBy(() -> calculator.calculate(context("INTEGER", "HIGHER_BETTER", null),
                List.of(
                        source("a1", student, "Alice", new BigDecimal("1")),
                        source("a2", student, "Alice", new BigDecimal("2"))
                ))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidGradeOrderValue() {
        UUID student = UUID.fromString("00000000-0000-0000-0000-000000000041");
        assertThatThrownBy(() -> calculator.calculate(context("GRADE", "GRADE_ORDER", "[\"GOLD\",\"SILVER\"]"),
                List.of(source("a1", student, "Alice", null, null, "BRONZE"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private RankingGenerationContext context(String storageType, String direction, String gradeOrder) {
        return new RankingGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Activity",
                UUID.randomUUID(),
                "Campus School",
                UUID.randomUUID(),
                "Math Project",
                UUID.randomUUID(),
                1,
                storageType,
                direction,
                2,
                gradeOrder);
    }

    private RankingGenerationSourceRow source(String attemptIdSuffix, UUID studentId, String displayName, BigDecimal numericValue) {
        return source(attemptIdSuffix, studentId, displayName, numericValue, null, null);
    }

    private RankingGenerationSourceRow source(String attemptIdSuffix, UUID studentId, String displayName,
                                              BigDecimal numericValue, Long durationMs, String grade) {
        return new RankingGenerationSourceRow(
                UUID.nameUUIDFromBytes(attemptIdSuffix.getBytes()),
                studentId,
                displayName,
                numericValue,
                durationMs,
                grade);
    }
}
