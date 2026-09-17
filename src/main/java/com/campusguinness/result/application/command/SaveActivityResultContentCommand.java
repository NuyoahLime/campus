package com.campusguinness.result.application.command;

import java.util.List;
import java.util.UUID;

public record SaveActivityResultContentCommand(
        String title,
        String summaryText,
        List<String> scoreHighlights,
        List<UUID> mediaRefs) {
}
