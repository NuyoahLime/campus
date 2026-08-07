package com.campusguinness.identity.application.query;

import java.time.Instant;
import java.util.UUID;

public record LatestStudentIdentityApplicationState(
        UUID applicationId,
        String applicationStatus,
        Instant submittedAt
) {}
