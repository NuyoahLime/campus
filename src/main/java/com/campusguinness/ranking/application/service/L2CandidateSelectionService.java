package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class L2CandidateSelectionService {
    static final String POLICY = "BEST_SCORE";
    private static final ObjectMapper JSON = new ObjectMapper();

    List<RankingGenerationSourceRow> selectBestScores(
            RankingGenerationContext context,
            List<RankingGenerationSourceRow> sources) {
        requireContext(context);
        Map<UUID, SelectedCandidate> selected = new HashMap<>();
        for (RankingGenerationSourceRow source : sources) {
            if (source.ruleVersionId() == null || !source.ruleVersionId().equals(context.ruleVersionId())) {
                throw conflict("L2 candidate RuleVersion does not match generation context.");
            }
            SelectedCandidate candidate = new SelectedCandidate(source, score(context, source));
            selected.merge(source.studentId(), candidate, (left, right) -> better(context, left, right));
        }
        return selected.values().stream()
                .map(SelectedCandidate::source)
                .sorted(Comparator.comparing(RankingGenerationSourceRow::studentId)
                        .thenComparing(RankingGenerationSourceRow::scoreAttemptId))
                .toList();
    }

    private SelectedCandidate better(
            RankingGenerationContext context,
            SelectedCandidate left,
            SelectedCandidate right) {
        int comparison = left.businessValue().compareTo(right.businessValue());
        if ("HIGHER_BETTER".equals(context.comparisonDirection()) || "GRADE_ORDER".equals(context.comparisonDirection())) {
            comparison = -comparison;
        }
        if (comparison < 0) {
            return left;
        }
        if (comparison > 0) {
            return right;
        }
        return technicalOrder(left.source(), right.source()) <= 0 ? left : right;
    }

    private int technicalOrder(RankingGenerationSourceRow left, RankingGenerationSourceRow right) {
        int byActivityProject = compareNullable(left.activityProjectId(), right.activityProjectId());
        if (byActivityProject != 0) {
            return byActivityProject;
        }
        return left.scoreAttemptId().compareTo(right.scoreAttemptId());
    }

    private int compareNullable(UUID left, UUID right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private void requireContext(RankingGenerationContext context) {
        if (context == null || context.ruleVersionId() == null || context.scoreStorageType() == null
                || context.comparisonDirection() == null) {
            throw conflict("L2 historical rule context is unavailable.");
        }
        switch (context.scoreStorageType()) {
            case "INTEGER", "DECIMAL", "DURATION" -> {
                if (!"HIGHER_BETTER".equals(context.comparisonDirection())
                        && !"LOWER_BETTER".equals(context.comparisonDirection())) {
                    throw conflict("L2 numeric candidate selection requires comparison direction.");
                }
            }
            case "GRADE" -> {
                if (!"GRADE_ORDER".equals(context.comparisonDirection())) {
                    throw conflict("L2 grade candidate selection requires grade order.");
                }
                parseGradeOrder(context.gradeOrder());
            }
            default -> throw conflict("L2 score storage type is invalid.");
        }
    }

    private BigDecimal score(RankingGenerationContext context, RankingGenerationSourceRow source) {
        return switch (context.scoreStorageType()) {
            case "INTEGER", "DECIMAL" -> {
                if (source.numericValue() == null) throw conflict("L2 numeric score is invalid.");
                yield source.numericValue();
            }
            case "DURATION" -> {
                if (source.durationMs() == null) throw conflict("L2 duration score is invalid.");
                yield BigDecimal.valueOf(source.durationMs());
            }
            case "GRADE" -> BigDecimal.valueOf(gradeRank(parseGradeOrder(context.gradeOrder()), source.grade()));
            default -> throw conflict("L2 score storage type is invalid.");
        };
    }

    private int gradeRank(List<String> grades, String grade) {
        if (grade == null || grade.isBlank()) throw conflict("L2 grade score is invalid.");
        int index = grades.indexOf(grade);
        if (index < 0) throw conflict("L2 grade is absent from the historical grade order.");
        return grades.size() - index;
    }

    private List<String> parseGradeOrder(String raw) {
        if (raw == null || raw.isBlank()) throw conflict("L2 historical grade order is required.");
        String normalized = raw.trim();
        try {
            JsonNode node = JSON.readTree(normalized);
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : node) {
                    if (!item.isTextual() || item.textValue().isBlank()) {
                        throw conflict("L2 historical grade order contains an invalid grade.");
                    }
                    result.add(item.textValue().trim());
                }
                if (result.isEmpty()) throw conflict("L2 historical grade order is required.");
                return requireDistinctGrades(result);
            }
            throw conflict("L2 historical grade order JSON must be an array.");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ignored) {
        }
        if (normalized.startsWith("[") || normalized.startsWith("{") || normalized.startsWith("\"")) {
            throw conflict("L2 historical grade order JSON is malformed.");
        }
        List<String> result = java.util.Arrays.stream(normalized.split("[,;\\r\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (result.isEmpty()) throw conflict("L2 historical grade order is required.");
        return requireDistinctGrades(result);
    }

    private List<String> requireDistinctGrades(List<String> grades) {
        Set<String> seen = new HashSet<>();
        for (String grade : grades) {
            if (!seen.add(grade)) {
                throw conflict("L2 historical grade order contains duplicate grades.");
            }
        }
        return grades;
    }

    private IllegalStateException conflict(String message) {
        return new IllegalStateException("Cannot generate ranking: " + message);
    }

    private record SelectedCandidate(RankingGenerationSourceRow source, BigDecimal businessValue) {}
}
