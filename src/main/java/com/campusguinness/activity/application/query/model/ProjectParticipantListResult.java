package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectParticipantListResult(UUID activityProjectParticipantId, UUID activityProjectId,
        UUID participantId, UUID studentId, String displayName,
        int attemptCount, boolean hasScoreAttempt, UUID latestAttemptId,
        String latestAttemptStatus, String latestScoreValue, boolean hasApprovedScore,
        Instant assignedAt) {}
