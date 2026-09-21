package com.campusguinness.result.application.format;

import com.campusguinness.result.application.query.ActivityResultReadConsistencyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class ResultFormatPresentationValidator {
    private static final Set<String> PRESENTATION_KEYS = Set.of("paragraphs", "emphasisRanges");
    private static final Set<String> RANGE_KEYS = Set.of("start", "end", "style");
    private static final Set<String> EMPHASIS_STYLES = Set.of("BOLD", "ITALIC");
    private static final int MAX_ITEMS = 200;

    private final ObjectMapper mapper;

    public ResultFormatPresentationValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ResultFormatPresentation validateAndCanonicalize(JsonNode input, String summary) {
        if (input == null || !input.isObject()) invalid("presentation must be an object");
        requireKeys(input, PRESENTATION_KEYS);
        JsonNode paragraphs = normalizeCollection(input.get("paragraphs"));
        JsonNode emphasis = normalizeCollection(input.get("emphasisRanges"));
        if (!paragraphs.isArray() || !emphasis.isArray()
                || paragraphs.size() > MAX_ITEMS || emphasis.size() > MAX_ITEMS) {
            invalid("invalid presentation collections");
        }

        int length = summary == null ? -1 : summary.codePointCount(0, summary.length());
        int previousEnd = 0;
        List<ResultFormatPresentation.Paragraph> canonicalParagraphs = new ArrayList<>();
        for (JsonNode paragraph : paragraphs) {
            requireKeys(paragraph, RANGE_KEYS);
            requireIntegerRange(paragraph);
            int start = paragraph.path("start").intValue();
            int end = paragraph.path("end").intValue();
            String style = paragraph.path("style").isTextual() ? paragraph.path("style").textValue() : "";
            if (start != previousEnd || end <= start || end > length || !"NORMAL".equals(style)) {
                invalid("invalid paragraph range");
            }
            canonicalParagraphs.add(new ResultFormatPresentation.Paragraph(start, end, style));
            previousEnd = end;
        }
        if (summary != null && previousEnd != length) invalid("paragraphs must cover summaryText");

        List<ResultFormatPresentation.EmphasisRange> canonicalEmphasis = new ArrayList<>();
        for (JsonNode range : emphasis) {
            requireKeys(range, RANGE_KEYS);
            requireIntegerRange(range);
            int start = range.path("start").intValue();
            int end = range.path("end").intValue();
            String style = range.path("style").isTextual() ? range.path("style").textValue() : "";
            if (start < 0 || end <= start || end > length || !EMPHASIS_STYLES.contains(style)) {
                invalid("invalid emphasis range");
            }
            boolean contained = canonicalParagraphs.stream()
                    .anyMatch(paragraph -> start >= paragraph.start() && end <= paragraph.end());
            if (!contained) invalid("emphasis crosses paragraph");
            for (ResultFormatPresentation.EmphasisRange existing : canonicalEmphasis) {
                if ((start == existing.start() && end == existing.end())
                        || (existing.style().equals(style) && start < existing.end() && existing.start() < end)) {
                    invalid("overlapping emphasis");
                }
            }
            canonicalEmphasis.add(new ResultFormatPresentation.EmphasisRange(start, end, style));
        }
        canonicalEmphasis.sort(Comparator.comparingInt(ResultFormatPresentation.EmphasisRange::start)
                .thenComparingInt(ResultFormatPresentation.EmphasisRange::end)
                .thenComparing(ResultFormatPresentation.EmphasisRange::style));
        return new ResultFormatPresentation(List.copyOf(canonicalParagraphs), List.copyOf(canonicalEmphasis));
    }

    public ResultFormatPresentation restoreAndValidate(String persistedJson, String summary) {
        try {
            return validateAndCanonicalize(mapper.readTree(persistedJson), summary);
        } catch (Exception ex) {
            throw new ActivityResultReadConsistencyException("Stored format overlay is invalid");
        }
    }

    public String serialize(ResultFormatPresentation presentation) {
        try {
            return mapper.writeValueAsString(presentation);
        } catch (Exception ex) {
            throw new ResultFormatEditException(
                    "ACTIVITY_RESULT_FORMAT_INVALID", "presentation cannot be serialized");
        }
    }

    private JsonNode normalizeCollection(JsonNode value) {
        return value == null || value.isNull() ? mapper.createArrayNode() : value;
    }

    private void requireKeys(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) invalid("invalid presentation item");
        node.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) invalid("unknown presentation item key");
        });
    }

    private void requireIntegerRange(JsonNode node) {
        if (!node.path("start").isIntegralNumber() || !node.path("end").isIntegralNumber()) {
            invalid("range bounds must be integers");
        }
    }

    private void invalid(String message) {
        throw new ResultFormatEditException("ACTIVITY_RESULT_FORMAT_INVALID", message);
    }
}
