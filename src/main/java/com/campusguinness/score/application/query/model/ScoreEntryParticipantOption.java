package com.campusguinness.score.application.query.model;

import java.util.UUID;

public record ScoreEntryParticipantOption(
        UUID studentId,
        String displayName,
        String studentNumber,
        String grade,
        String className,
        long attemptCount,
        Integer latestAttemptNumber,
        String latestAttemptStatus,
        String latestScoreValue) {
}
