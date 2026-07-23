package com.campusguinness.activity.application.command;

import java.util.UUID;

public record SubmitActivityApplicationCommand(UUID schoolId, String title, String description) {
    public SubmitActivityApplicationCommand {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
    }
}
