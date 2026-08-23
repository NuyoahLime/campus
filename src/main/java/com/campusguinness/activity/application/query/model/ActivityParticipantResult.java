package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityParticipantResult(
        UUID studentId,
        String displayName,
        String studentNumber,
        String grade,
        String className,
        Instant assignedAt
) {}
