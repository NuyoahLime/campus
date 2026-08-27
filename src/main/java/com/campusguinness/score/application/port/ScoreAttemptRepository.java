package com.campusguinness.score.application.port;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import java.util.Optional;
import java.util.List;
public interface ScoreAttemptRepository {
    void save(ScoreAttempt scoreAttempt);
    Optional<ScoreAttempt> findById(ScoreAttemptId id);
    List<ScoreAttempt> findByStudentAndActivityProject(java.util.UUID studentId, java.util.UUID activityProjectId);
}
