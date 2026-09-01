package com.campusguinness.ranking.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public record RankingGenerationScope(UUID activityProjectId) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static RankingGenerationScope fromDimensionFilters(String raw) {
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
}
