package com.campusguinness.school.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolRegistrationListResult(
        UUID id,
        String schoolName,
        String schoolType,
        String region,
        String contactName,
        String status,
        Instant createdAt
) {}
