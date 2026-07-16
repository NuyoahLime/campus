package com.campusguinness.interfaces.web.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateActivityRequest(
        @NotNull UUID schoolId,
        @NotNull UUID createdBy,
        @NotBlank @Size(max = 200) String title,
        String description,
        Instant startTime,
        Instant endTime,
        String location) {}
