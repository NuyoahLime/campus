package com.campusguinness.ranking.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

public record RankingGenerationScope(
        UUID activityProjectId,
        String selectionPolicy,
        String grade,
        String className,
        Instant activityPeriodStart,
        Instant activityPeriodEnd) {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String L2_SELECTION_POLICY = "BEST_SCORE";

    public RankingGenerationScope(UUID activityProjectId) {
        this(activityProjectId, null, null, null, null, null);
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
            return new RankingGenerationScope(null, L2_SELECTION_POLICY, null, null, null, null);
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
            return new RankingGenerationScope(
                    null,
                    L2_SELECTION_POLICY,
                    text(node, "grade"),
                    text(node, "className"),
                    instant(node, "activityPeriodStart"),
                    instant(node, "activityPeriodEnd"));
        } catch (IllegalStateException ex) {
            throw ex;
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
