package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.command.SaveActivityResultContentCommand;

import java.util.List;
import java.util.UUID;

public record ActivityResultSaveRequest(
        String title,
        String summaryText,
        List<String> scoreHighlights,
        List<UUID> mediaRefs) {

    SaveActivityResultContentCommand toCommand() {
        return new SaveActivityResultContentCommand(title, summaryText, scoreHighlights, mediaRefs);
    }
}
