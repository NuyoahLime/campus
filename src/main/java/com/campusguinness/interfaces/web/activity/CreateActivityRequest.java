package com.campusguinness.interfaces.web.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** schoolId and createdBy are sourced from CurrentActor and Membership — never from request body. */
public record CreateActivityRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        Instant startTime,
        Instant endTime,
        String location) {}
