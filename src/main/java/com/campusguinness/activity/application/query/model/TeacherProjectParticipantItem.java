package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record TeacherProjectParticipantItem(
        UUID studentId,
        String displayName,
        String studentNumber,
        String grade,
        String className,
        long attemptCount,
        UUID latestAttemptId,
        Integer latestAttemptNumber,
        String latestAttemptStatus,
        String latestScoreValue,
        boolean hasApprovedScore,
        Instant assignedAt) {
}
