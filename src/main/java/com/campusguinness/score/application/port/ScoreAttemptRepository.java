package com.campusguinness.score.application.port;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import java.util.Optional;
public interface ScoreAttemptRepository {
    void save(ScoreAttempt scoreAttempt);
    Optional<ScoreAttempt> findById(ScoreAttemptId id);
}
