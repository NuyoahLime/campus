package com.campusguinness.result.application.format;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ResultFormatPresentation(List<Paragraph> paragraphs, List<EmphasisRange> emphasisRanges) {
    public record Paragraph(int start, int end, String style) {}
    public record EmphasisRange(int start, int end, String style) {}
}
