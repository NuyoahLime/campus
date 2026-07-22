package com.campusguinness.ranking.application.service;

import com.campusguinness.project.internal.domain.ComparisonDirection;
import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RankingCalculatorTest {

    private ScoreAttempt approvedInt(UUID studentId, long value) {
        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(UUID.randomUUID())
                .activityProjectId(UUID.randomUUID()).studentId(studentId)
                .attemptNumber(1).scoreStorageType(ScoreStorageType.INTEGER)
                .scoreValue(new ScoreValue.IntegerScore(value))
                .scoreBusinessTime(Instant.now()).timeSource("t").enteredBy(UUID.randomUUID()));
        s.submit();
        s.approve();
        return s;
    }

    @Test void shouldRankHigherValuesFirst() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var results = RankingCalculator.rank(
                List.of(approvedInt(a, 100), approvedInt(b, 90), approvedInt(c, 80)),
                ComparisonDirection.HIGHER_BETTER);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(0).studentId()).isEqualTo(a);
        assertThat(results.get(2).rank()).isEqualTo(3);
    }

    @Test void shouldRankLowerValuesFirst() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var results = RankingCalculator.rank(
                List.of(approvedInt(a, 10), approvedInt(b, 8), approvedInt(c, 12)),
                ComparisonDirection.LOWER_BETTER);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).studentId()).isEqualTo(b);
    }

    @Test void shouldUseBestAttemptPerStudent() {
        UUID s = UUID.randomUUID();
        var a1 = approvedInt(s, 80);
        var a2 = approvedInt(s, 95);
        var a3 = approvedInt(s, 90);
        var results = RankingCalculator.rank(List.of(a1, a2, a3), ComparisonDirection.HIGHER_BETTER);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().scoreAttemptId()).isEqualTo(a2.id().value());
    }

    @Test void shouldAssignSameRankForTies() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var results = RankingCalculator.rank(
                List.of(approvedInt(a, 100), approvedInt(b, 100)),
                ComparisonDirection.HIGHER_BETTER);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(1).rank()).isEqualTo(1);
    }

    @Test void shouldSkipRankAfterTie() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var results = RankingCalculator.rank(
                List.of(approvedInt(a, 100), approvedInt(b, 100), approvedInt(c, 80)),
                ComparisonDirection.HIGHER_BETTER);
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(1).rank()).isEqualTo(1);
        assertThat(results.get(2).rank()).isEqualTo(3); // competition ranking
    }

    @Test void shouldReturnEmptyForNoScores() {
        assertThat(RankingCalculator.rank(List.of(), ComparisonDirection.HIGHER_BETTER)).isEmpty();
    }
}
