package com.campusguinness.ranking.application.service;

import com.campusguinness.project.internal.domain.ComparisonDirection;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure domain ranking calculator — no framework, database, or JPA dependencies.
 */
public class RankingCalculator {

    public record RankingEntry(int rank, UUID studentId, UUID scoreAttemptId, String scoreDisplay) {}

    private RankingCalculator() {}

    /**
     * Compute competition ranking (1, 2, 2, 4) for the given APPROVED score attempts.
     */
    public static List<RankingEntry> rank(List<ScoreAttempt> attempts, ComparisonDirection direction) {
        if (attempts.isEmpty()) return List.of();

        // Validate all same storage type
        ScoreStorageType type = attempts.getFirst().scoreStorageType();
        for (var a : attempts) {
            if (a.scoreStorageType() != type) {
                throw new IllegalArgumentException("Mixed score storage types not supported for ranking");
            }
        }

        // Pick best attempt per student
        Map<UUID, ScoreAttempt> bestPerStudent = pickBestPerStudent(attempts, direction);

        // Sort by comparison value
        List<ScoreAttempt> sorted = new ArrayList<>(bestPerStudent.values());
        sorted.sort((a, b) -> compare(b, a, direction)); // descending for rank

        // Assign competition ranks (1, 2, 2, 4)
        List<RankingEntry> entries = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && compare(sorted.get(i - 1), sorted.get(i), direction) != 0) {
                rank = i + 1;
            }
            entries.add(new RankingEntry(rank, sorted.get(i).studentId(),
                    sorted.get(i).id().value(), displayScore(sorted.get(i))));
        }
        return entries;
    }

    private static Map<UUID, ScoreAttempt> pickBestPerStudent(List<ScoreAttempt> attempts, ComparisonDirection dir) {
        return attempts.stream().collect(Collectors.toMap(
                ScoreAttempt::studentId, a -> a,
                (a, b) -> compare(a, b, dir) >= 0 ? a : b,
                LinkedHashMap::new));
    }

    /** Positive if a is "better" than b according to the direction. */
    private static int compare(ScoreAttempt a, ScoreAttempt b, ComparisonDirection dir) {
        int cmp = compareValues(a.scoreValue(), b.scoreValue());
        return dir == ComparisonDirection.LOWER_BETTER ? -cmp : cmp;
    }

    private static int compareValues(ScoreValue a, ScoreValue b) {
        if (a instanceof ScoreValue.IntegerScore ai && b instanceof ScoreValue.IntegerScore bi)
            return Long.compare(ai.value(), bi.value());
        if (a instanceof ScoreValue.DecimalScore ad && b instanceof ScoreValue.DecimalScore bd)
            return ad.value().compareTo(bd.value());
        if (a instanceof ScoreValue.DurationScore ad && b instanceof ScoreValue.DurationScore bd)
            return Long.compare(ad.durationMs(), bd.durationMs());
        if (a instanceof ScoreValue.GradeScore ag && b instanceof ScoreValue.GradeScore bg)
            return ag.grade().compareTo(bg.grade());
        return 0;
    }

    private static String displayScore(ScoreAttempt a) {
        return switch (a.scoreValue()) {
            case ScoreValue.IntegerScore s -> String.valueOf(s.value());
            case ScoreValue.DecimalScore s -> s.value().toPlainString();
            case ScoreValue.DurationScore s -> s.durationMs() + "ms";
            case ScoreValue.GradeScore s -> s.grade();
        };
    }
}
