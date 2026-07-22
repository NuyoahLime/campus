package com.campusguinness.score.application.port;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ScoreAttemptRepository {
    void save(ScoreAttempt scoreAttempt);
    Optional<ScoreAttempt> findById(ScoreAttemptId id);
    List<ScoreAttempt> findByStudentId(UUID studentId);
    List<ScoreAttempt> findApprovedByStudentId(UUID studentId);
    Optional<ScoreAttempt> findApprovedByIdAndStudentId(UUID id, UUID studentId);
    List<ScoreAttempt> findApprovedByActivityProjectId(UUID activityProjectId);
}
