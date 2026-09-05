package com.campusguinness.ranking.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record L3AuthorizationScope(
        List<UUID> activityIds,
        Instant activityPeriodStart,
        Instant activityPeriodEnd,
        List<String> grades,
        List<String> classNames) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static L3AuthorizationScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        try {
            return parse(JSON.readTree(raw));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot save L3 authorization: dataScope JSON is malformed.");
        }
    }

    public static L3AuthorizationScope parse(JsonNode node) {
        if (node == null || node.isNull()) {
            return empty();
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Cannot save L3 authorization: dataScope must be a JSON object.");
        }
        Instant start = instant(node, "activityPeriodStart");
        Instant end = instant(node, "activityPeriodEnd");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalStateException(
                    "Cannot save L3 authorization: activityPeriodStart must not be after activityPeriodEnd.");
        }
        return new L3AuthorizationScope(
                uuids(node, "activityIds"),
                start,
                end,
                strings(node, "grades"),
                strings(node, "classNames"));
    }

    public String normalizedJson() {
        ObjectNode node = JSON.createObjectNode();
        put(node, "activityIds", activityIds);
        put(node, "activityPeriodStart", activityPeriodStart);
        put(node, "activityPeriodEnd", activityPeriodEnd);
        put(node, "grades", grades);
        put(node, "classNames", classNames);
        return node.toString();
    }

    private static L3AuthorizationScope empty() {
        return new L3AuthorizationScope(List.of(), null, null, List.of(), List.of());
    }

    private static List<UUID> uuids(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalStateException("Cannot save L3 authorization: dataScope." + field + " must be an array.");
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw new IllegalStateException(
                        "Cannot save L3 authorization: dataScope." + field + " must contain UUID text values.");
            }
            try {
                result.add(UUID.fromString(item.textValue().trim()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "Cannot save L3 authorization: dataScope." + field + " contains an invalid UUID.");
            }
        }
        return result.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalStateException("Cannot save L3 authorization: dataScope." + field + " must be an array.");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new IllegalStateException(
                        "Cannot save L3 authorization: dataScope." + field + " must contain text values.");
            }
            String text = item.textValue().trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result.stream().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
    }

    private static Instant instant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("Cannot save L3 authorization: dataScope." + field + " must be ISO-8601 text.");
        }
        try {
            return Instant.parse(value.textValue().trim());
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot save L3 authorization: dataScope." + field + " must be an ISO-8601 instant.");
        }
    }

    private static void put(ObjectNode node, String field, List<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ArrayNode array = node.putArray(field);
        for (Object value : values) {
            array.add(value.toString());
        }
    }

    private static void put(ObjectNode node, String field, Instant value) {
        if (value != null) {
            node.put(field, value.toString());
        }
    }
}
