package com.campusguinness.score.application.port;

import java.util.UUID;

public interface ScoreAttemptNumberAllocatorPort {
    int allocateNext(
            UUID activityProjectId,
            UUID activityParticipantId,
            UUID studentId);
}
