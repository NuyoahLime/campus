package com.campusguinness.interfaces.web.schoolregistration;

import java.time.Instant;
import java.util.UUID;

/** Lightweight summary for registration listings. Excludes sensitive applicant details. */
public record SchoolRegistrationSummary(
        UUID id,
        String schoolName,
        String schoolType,
        String region,
        String status,
        Instant submittedAt
) {}
