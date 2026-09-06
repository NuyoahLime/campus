package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class RankingGenerationCalculator {
    static final String TIE_POLICY = "COMPETITION_SHARED_RANK";
    private static final ObjectMapper JSON = new ObjectMapper();

    GeneratedRankingSnapshot calculate(RankingGenerationContext context, List<RankingGenerationSourceRow> sources) {
        requireContext(context);
        requireUniqueStudents(sources);
        List<ScoredRow> scored = sources.stream()
                .map(row -> score(context, row))
                .sorted(rowComparator(context))
                .toList();
        List<GeneratedRankingEntry> entries = new ArrayList<>();
        ComparableScore previous = null;
        int currentRank = 0;
        for (int i = 0; i < scored.size(); i++) {
            ScoredRow row = scored.get(i);
            if (previous == null || row.score().compareBusinessValue(previous) != 0) {
                currentRank = i + 1;
            }
            entries.add(new GeneratedRankingEntry(row.source().studentId(), row.source().scoreAttemptId(),
                    currentRank, row.source().studentDisplayName(),
                    row.source().schoolName() != null ? row.source().schoolName() : context.schoolName(),
                    scoreDisplayValue(context, row.source()), context.ruleVersionId()));
            previous = row.score();
        }
        return new GeneratedRankingSnapshot(TIE_POLICY, entries);
    }

    private void requireContext(RankingGenerationContext context) {
        if (context == null || context.ruleVersionId() == null || context.scoreStorageType() == null
                || context.comparisonDirection() == null) {
            throw conflict("Historical activity-project rule version is unavailable.");
        }
        switch (context.scoreStorageType()) {
            case "INTEGER", "DECIMAL", "DURATION" -> {
                if (!"HIGHER_BETTER".equals(context.comparisonDirection())
                        && !"LOWER_BETTER".equals(context.comparisonDirection())) {
                    throw conflict("Numeric ranking requires a historical comparison direction.");
                }
            }
            case "GRADE" -> {
                if (!"GRADE_ORDER".equals(context.comparisonDirection())) {
                    throw conflict("GRADE ranking requires a historical grade order.");
                }
                parseGradeOrder(context.gradeOrder());
            }
            default -> throw conflict("Historical score storage type is invalid.");
        }
    }

    private void requireUniqueStudents(List<RankingGenerationSourceRow> sources) {
        Set<java.util.UUID> seen = new HashSet<>();
        for (RankingGenerationSourceRow source : sources) {
            if (!seen.add(source.studentId())) {
                throw conflict("More than one current effective score exists for a ranking student.");
            }
        }
    }

    private Comparator<ScoredRow> rowComparator(RankingGenerationContext context) {
        Comparator<ScoredRow> comparator = Comparator.comparing(ScoredRow::score);
        if ("HIGHER_BETTER".equals(context.comparisonDirection())
                || "GRADE_ORDER".equals(context.comparisonDirection())) {
            comparator = comparator.reversed();
        }
        return comparator
                .thenComparing(row -> row.source().studentId())
                .thenComparing(row -> row.source().scoreAttemptId());
    }

    private ScoredRow score(RankingGenerationContext context, RankingGenerationSourceRow source) {
        BigDecimal businessValue = switch (context.scoreStorageType()) {
            case "INTEGER", "DECIMAL" -> {
                if (source.numericValue() == null) throw conflict("Stored ranking score value is invalid.");
                yield source.numericValue();
            }
            case "DURATION" -> {
                if (source.durationMs() == null) throw conflict("Stored ranking duration value is invalid.");
                yield BigDecimal.valueOf(source.durationMs());
            }
            case "GRADE" -> BigDecimal.valueOf(gradeRank(parseGradeOrder(context.gradeOrder()), source.grade()));
            default -> throw conflict("Historical score storage type is invalid.");
        };
        return new ScoredRow(source, new ComparableScore(businessValue));
    }

    private String scoreDisplayValue(RankingGenerationContext context, RankingGenerationSourceRow source) {
        return switch (context.scoreStorageType()) {
            case "DURATION" -> {
                if (source.durationMs() == null) throw conflict("Stored ranking duration value is invalid.");
                yield String.valueOf(source.durationMs());
            }
            case "GRADE" -> {
                if (source.grade() == null || source.grade().isBlank()) {
                    throw conflict("Stored ranking grade value is invalid.");
                }
                yield source.grade();
            }
            case "INTEGER", "DECIMAL" -> {
                if (source.numericValue() == null) throw conflict("Stored ranking score value is invalid.");
                yield source.numericValue().stripTrailingZeros().toPlainString();
            }
            default -> throw conflict("Historical score storage type is invalid.");
        };
    }

    private int gradeRank(List<String> grades, String grade) {
        if (grade == null || grade.isBlank()) throw conflict("Stored ranking grade value is invalid.");
        int index = grades.indexOf(grade);
        if (index < 0) throw conflict("Approved grade is absent from the historical grade order.");
        return grades.size() - index;
    }

    private List<String> parseGradeOrder(String raw) {
        if (raw == null || raw.isBlank()) throw conflict("Historical grade order is required.");
        String normalized = raw.trim();
        try {
            JsonNode node = JSON.readTree(normalized);
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : node) {
                    if (!item.isTextual() || item.textValue().isBlank()) {
                        throw conflict("Historical grade order contains an invalid grade.");
                    }
                    result.add(item.textValue().trim());
                }
                if (result.isEmpty()) throw conflict("Historical grade order is required.");
                return requireDistinctGrades(result);
            }
            throw conflict("Historical grade order JSON must be an array.");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ignored) {
            // Existing project forms also persist comma-, semicolon-, or newline-delimited orders.
        }
        if (normalized.startsWith("[") || normalized.startsWith("{") || normalized.startsWith("\"")) {
            throw conflict("Historical grade order JSON is malformed.");
        }
        List<String> result = java.util.Arrays.stream(normalized.split("[,;\\r\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (result.isEmpty()) throw conflict("Historical grade order is required.");
        return requireDistinctGrades(result);
    }

    private List<String> requireDistinctGrades(List<String> grades) {
        Set<String> seen = new HashSet<>();
        for (String grade : grades) {
            if (!seen.add(grade)) {
                throw conflict("Historical grade order contains duplicate grades.");
            }
        }
        return grades;
    }

    private IllegalStateException conflict(String message) {
        return new IllegalStateException("Cannot generate ranking: " + message);
    }

    private record ComparableScore(BigDecimal businessValue) implements Comparable<ComparableScore> {
        @Override
        public int compareTo(ComparableScore other) {
            return businessValue.compareTo(other.businessValue);
        }

        int compareBusinessValue(ComparableScore other) {
            return businessValue.compareTo(other.businessValue);
        }
    }

    private record ScoredRow(RankingGenerationSourceRow source, ComparableScore score) {}
}
