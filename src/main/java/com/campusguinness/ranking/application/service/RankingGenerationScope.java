package com.campusguinness.ranking.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.campusguinness.ranking.internal.domain.RankingLayer;

import java.time.Instant;
import java.util.UUID;

public record RankingGenerationScope(
        UUID activityProjectId,
        RankingLayer layer,
        String selectionPolicy,
        String grade,
        String className,
        Instant activityPeriodStart,
        Instant activityPeriodEnd,
        UUID ruleVersionId) {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String L2_SELECTION_POLICY = "BEST_SCORE";

    public RankingGenerationScope(UUID activityProjectId) {
        this(activityProjectId, RankingLayer.L1, null, null, null, null, null, null);
    }

    public static RankingGenerationScope l1FromDimensionFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters.activityProjectId is required.");
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject()) {
                throw new IllegalStateException("Cannot generate ranking: dimensionFilters must be a JSON object.");
            }
            JsonNode value = node.get("activityProjectId");
            if (value == null || !value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalStateException("Cannot generate ranking: dimensionFilters.activityProjectId is required.");
            }
            return new RankingGenerationScope(UUID.fromString(value.textValue().trim()));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters.activityProjectId is invalid.");
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters JSON is malformed.");
        }
    }

    public static RankingGenerationScope fromDimensionFilters(String raw) {
        return l1FromDimensionFilters(raw);
    }

    public static RankingGenerationScope l2FromDimensionFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            return new RankingGenerationScope(null, RankingLayer.L2, L2_SELECTION_POLICY, null, null, null, null, null);
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject()) {
                throw new IllegalStateException("Cannot generate ranking: dimensionFilters must be a JSON object.");
            }
            if (node.has("activityProjectId")) {
                throw new IllegalStateException("Cannot generate ranking: L2 definitions must not use activityProjectId.");
            }
            String selectionPolicy = text(node, "selectionPolicy");
            if (selectionPolicy != null && !L2_SELECTION_POLICY.equals(selectionPolicy)) {
                throw new IllegalStateException("Cannot generate ranking: L2 supports only BEST_SCORE selectionPolicy.");
            }
            Instant activityPeriodStart = instant(node, "activityPeriodStart");
            Instant activityPeriodEnd = instant(node, "activityPeriodEnd");
            if (activityPeriodStart != null && activityPeriodEnd != null
                    && activityPeriodStart.isAfter(activityPeriodEnd)) {
                throw new IllegalStateException(
                        "Cannot generate ranking: activityPeriodStart must not be after activityPeriodEnd.");
            }
            return new RankingGenerationScope(
                    null,
                    RankingLayer.L2,
                    L2_SELECTION_POLICY,
                    text(node, "grade"),
                    text(node, "className"),
                    activityPeriodStart,
                    activityPeriodEnd,
                    null);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters JSON is malformed.");
        }
    }

    public static RankingGenerationScope l3FromDimensionFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters.ruleVersionId is required.");
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject()) {
                throw new IllegalStateException("Cannot generate ranking: dimensionFilters must be a JSON object.");
            }
            if (node.size() == 0) {
                throw new IllegalStateException("Cannot generate ranking: dimensionFilters.ruleVersionId is required.");
            }
            for (var fields = node.fieldNames(); fields.hasNext();) {
                String field = fields.next();
                if (!"ruleVersionId".equals(field)) {
                    throw new IllegalStateException("Cannot generate ranking: unsupported dataScope field: " + field + ".");
                }
            }
            String ruleVersionId = text(node, "ruleVersionId");
            if (ruleVersionId == null) {
                throw new IllegalStateException("Cannot generate ranking: dimensionFilters.ruleVersionId is required.");
            }
            UUID parsedRuleVersionId = UUID.fromString(ruleVersionId);
            return new RankingGenerationScope(
                    null,
                    RankingLayer.L3,
                    L2_SELECTION_POLICY,
                    null,
                    null,
                    null,
                    null,
                    parsedRuleVersionId);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters.ruleVersionId is invalid.");
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters JSON is malformed.");
        }
    }

    public static String normalizeL2DimensionFilters(String raw) {
        RankingGenerationScope scope = l2FromDimensionFilters(raw);
        ObjectNode node = JSON.createObjectNode();
        node.put("selectionPolicy", L2_SELECTION_POLICY);
        put(node, "grade", scope.grade());
        put(node, "className", scope.className());
        put(node, "activityPeriodStart", scope.activityPeriodStart());
        put(node, "activityPeriodEnd", scope.activityPeriodEnd());
        return node.toString();
    }

    public static String normalizeL3DimensionFilters(String raw) {
        RankingGenerationScope scope = l3FromDimensionFilters(raw);
        ObjectNode node = JSON.createObjectNode();
        node.put("ruleVersionId", scope.ruleVersionId().toString());
        return node.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters." + field + " must be text.");
        }
        String text = value.textValue().trim();
        return text.isBlank() ? null : text;
    }

    private static Instant instant(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: dimensionFilters." + field + " must be an ISO-8601 instant.");
        }
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private static void put(ObjectNode node, String field, Instant value) {
        if (value != null) {
            node.put(field, value.toString());
        }
    }
}
