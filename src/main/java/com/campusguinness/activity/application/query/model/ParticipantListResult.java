package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ParticipantListResult(UUID participantId, UUID activityId, UUID studentMembershipId,
        UUID studentId, String displayName, String grade, String className, String studentNumber,
        long assignedProjectCount, boolean hasScoreAttempt, Instant joinedAt) {}
