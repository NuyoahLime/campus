package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.exception.RankingConfigurationException;
import com.campusguinness.ranking.application.exception.RankingConflictException;
import com.campusguinness.ranking.application.exception.RankingDataConflictException;
import com.campusguinness.ranking.application.query.model.CalculatedRankingEntry;
import com.campusguinness.ranking.application.query.model.RankingScoreSource;
import com.campusguinness.score.application.query.ScoreDisplayFormatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic calculator for already-selected current effective score sources.
 */
public final class RankingCalculator {

    private static final Set<String> NUMERIC_STORAGE_TYPES =
            Set.of("INTEGER", "DECIMAL", "DURATION");

    private RankingCalculator() {
    }

    public static List<CalculatedRankingEntry> rank(
            List<RankingScoreSource> sources,
            String scoreStorageType,
            String comparisonDirection,
            String gradeOrder,
            boolean allowTie,
            Integer decimalPlaces) {
        validateConfiguration(scoreStorageType, comparisonDirection, gradeOrder);
        if (sources.isEmpty()) {
            return List.of();
        }

        ensureOneSourcePerStudent(sources);
        for (RankingScoreSource source : sources) {
            if (!scoreStorageType.equals(source.scoreStorageType())) {
                throw new RankingDataConflictException(
                        "Ranking sources contain mixed or unexpected score storage types");
            }
            requireScoreValue(source);
        }

        Map<String, Integer> gradeRanks = "GRADE_ORDER".equals(comparisonDirection)
                ? parseGradeOrder(gradeOrder)
                : Map.of();
        if ("GRADE_ORDER".equals(comparisonDirection)) {
            for (RankingScoreSource source : sources) {
                if (!gradeRanks.containsKey(source.scoreGrade())) {
                    throw new RankingConfigurationException(
                            "Current effective score grade is absent from gradeOrder");
                }
            }
        }
        Comparator<RankingScoreSource> scoreComparator =
                scoreComparator(scoreStorageType, comparisonDirection, gradeRanks);
        Comparator<RankingScoreSource> stableTieBreaker = Comparator
                .comparing(RankingScoreSource::scoreBusinessTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(source -> source.scoreAttemptId().toString());

        List<RankingScoreSource> sorted = new ArrayList<>(sources);
        sorted.sort(scoreComparator.thenComparing(stableTieBreaker));

        List<CalculatedRankingEntry> result = new ArrayList<>(sorted.size());
        int rank = 1;
        for (int index = 0; index < sorted.size(); index++) {
            RankingScoreSource source = sorted.get(index);
            if (allowTie) {
                if (index > 0 && scoreComparator.compare(sorted.get(index - 1), source) != 0) {
                    rank = index + 1;
                }
            } else {
                rank = index + 1;
            }
            result.add(new CalculatedRankingEntry(
                    rank,
                    source.studentId(),
                    source.studentDisplayName(),
                    source.schoolName(),
                    source.scoreAttemptId(),
                    ScoreDisplayFormatter.format(
                            source.scoreStorageType(),
                            source.scoreValue(),
                            source.scoreDurationMs(),
                            source.scoreGrade(),
                            decimalPlaces),
                    source.scoreBusinessTime(),
                    source.currentRuleVersionId()));
        }
        return List.copyOf(result);
    }

    private static void validateConfiguration(
            String storageType, String direction, String gradeOrder) {
        if ("NO_RANKING".equals(direction)) {
            throw new RankingConflictException(
                    "RANKING_DISABLED_FOR_PROJECT", "Ranking is disabled for this project");
        }
        if ("GRADE".equals(storageType)) {
            if (!"GRADE_ORDER".equals(direction)) {
                throw new RankingConfigurationException(
                        "GRADE scores require GRADE_ORDER comparison");
            }
            parseGradeOrder(gradeOrder);
            return;
        }
        if (!NUMERIC_STORAGE_TYPES.contains(storageType)) {
            throw new RankingConfigurationException("Unsupported score storage type");
        }
        if (!"HIGHER_BETTER".equals(direction) && !"LOWER_BETTER".equals(direction)) {
            throw new RankingConfigurationException(
                    "Numeric and duration scores require HIGHER_BETTER or LOWER_BETTER");
        }
    }

    private static Map<String, Integer> parseGradeOrder(String gradeOrder) {
        if (gradeOrder == null || gradeOrder.isBlank()) {
            throw new RankingConfigurationException("gradeOrder is required for grade ranking");
        }
        String[] values = gradeOrder.split(",", -1);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index++) {
            String grade = values[index].trim();
            if (grade.isEmpty()) {
                throw new RankingConfigurationException(
                        "gradeOrder cannot contain blank grades");
            }
            if (result.putIfAbsent(grade, index) != null) {
                throw new RankingConfigurationException(
                        "gradeOrder cannot contain duplicate grades");
            }
        }
        return Map.copyOf(result);
    }

    private static Comparator<RankingScoreSource> scoreComparator(
            String storageType,
            String direction,
            Map<String, Integer> gradeRanks) {
        if ("GRADE".equals(storageType)) {
            return Comparator.comparingInt(source -> {
                Integer rank = gradeRanks.get(source.scoreGrade());
                if (rank == null) {
                    throw new RankingConfigurationException(
                            "Current effective score grade is absent from gradeOrder");
                }
                return rank;
            });
        }

        Comparator<RankingScoreSource> comparator = switch (storageType) {
            case "INTEGER", "DECIMAL" -> Comparator.comparing(
                    RankingScoreSource::scoreValue, BigDecimal::compareTo);
            case "DURATION" -> Comparator.comparingLong(
                    RankingScoreSource::scoreDurationMs);
            default -> throw new RankingConfigurationException(
                    "Unsupported score storage type");
        };
        return "HIGHER_BETTER".equals(direction) ? comparator.reversed() : comparator;
    }

    private static void ensureOneSourcePerStudent(List<RankingScoreSource> sources) {
        Map<UUID, UUID> attemptsByStudent = new HashMap<>();
        for (RankingScoreSource source : sources) {
            UUID previous = attemptsByStudent.putIfAbsent(
                    source.studentId(), source.scoreAttemptId());
            if (previous != null) {
                throw new RankingDataConflictException(
                        "Multiple current effective scores exist for the same student");
            }
        }
    }

    private static void requireScoreValue(RankingScoreSource source) {
        boolean present = switch (source.scoreStorageType()) {
            case "INTEGER", "DECIMAL" -> source.scoreValue() != null;
            case "DURATION" -> source.scoreDurationMs() != null;
            case "GRADE" -> source.scoreGrade() != null && !source.scoreGrade().isBlank();
            default -> false;
        };
        if (!present) {
            throw new RankingDataConflictException(
                    "Ranking source does not contain a value for its storage type");
        }
    }
}
