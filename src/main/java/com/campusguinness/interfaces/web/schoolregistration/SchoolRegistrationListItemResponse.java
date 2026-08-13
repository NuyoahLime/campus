package com.campusguinness.interfaces.web.schoolregistration;

import java.time.Instant;
import java.util.UUID;

public record SchoolRegistrationListItemResponse(
        UUID id,
        String schoolName,
        String schoolType,
        String region,
        String contactName,
        String status,
        Instant createdAt
) {}
