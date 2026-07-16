package com.campusguinness.activity.application.command;
import java.util.UUID;
public record SubmitActivityApplicationCommand(UUID schoolId, UUID applicantId, String title, String description) {
    public SubmitActivityApplicationCommand {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (applicantId == null) throw new IllegalArgumentException("applicantId required");
    }
}
