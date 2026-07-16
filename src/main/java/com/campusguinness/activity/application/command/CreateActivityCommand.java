package com.campusguinness.activity.application.command;
import java.time.Instant;
import java.util.UUID;
public record CreateActivityCommand(UUID schoolId, UUID createdBy, String title, String description,
        Instant startTime, Instant endTime, String location) {
    public CreateActivityCommand { if (title == null || title.isBlank()) throw new IllegalArgumentException("title required"); }
}
