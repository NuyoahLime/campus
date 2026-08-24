package com.campusguinness.score.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreWriteContextPort {
    record Context(UUID activityId, UUID activityProjectId, UUID schoolId, UUID ruleVersionId,
                   String activityTitle, String activityStatus, String projectName,
                   String scoreStorageType, Integer decimalPlaces, String gradeOrder) {}
    record Activity(UUID activityId, UUID schoolId, String title, String activityStatus) {}
    record Student(UUID userId, UUID membershipId) {}
    record CandidateRow(UUID studentId, String displayName, String studentNumber,
                        UUID activityProjectId, String projectName, String scoreStorageType,
                        UUID latestAttemptId, Integer latestAttemptNumber, String latestStatus) {}
    record ScoreRow(UUID scoreAttemptId, UUID activityId, String activityTitle,
                    UUID activityProjectId, String projectName, UUID studentId,
                    String studentDisplay, String studentNumber, int attemptNumber,
                    String status, String scoreStorageType, java.math.BigDecimal numericValue,
                    Long durationMs, String grade, java.time.Instant scoreBusinessTime) {}

    Optional<Activity> findActivity(UUID activityId);
    Optional<Context> findContext(UUID activityProjectId);
    Optional<Student> findActiveStudent(UUID studentId, UUID schoolId);
    boolean isParticipant(UUID activityId, UUID membershipId);
    int nextAttemptNumber(UUID activityProjectId, UUID studentId);
    List<CandidateRow> findCandidates(UUID activityId, UUID schoolId);
    List<ScoreRow> findScores(UUID activityId, UUID schoolId, UUID activityProjectId, String status);
    Optional<ScoreRow> findScore(UUID scoreAttemptId);
}
