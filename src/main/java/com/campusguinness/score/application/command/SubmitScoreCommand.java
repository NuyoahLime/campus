package com.campusguinness.score.application.command;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;
import java.time.Instant;
import java.util.UUID;
public record SubmitScoreCommand(UUID schoolId, UUID activityProjectId, UUID studentId,
        int attemptNumber, ScoreStorageType scoreStorageType, ScoreValue scoreValue,
        Instant scoreBusinessTime, String timeSource, UUID enteredBy) {
    public SubmitScoreCommand { if (schoolId == null) throw new IllegalArgumentException("schoolId required"); }
}
