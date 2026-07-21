package com.campusguinness.score.application.service;

import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScoreAttemptApplicationService {
    private final ScoreAttemptRepository repository;
    public ScoreAttemptApplicationService(ScoreAttemptRepository r) { this.repository = r; }

    public ScoreAttemptResult submit(SubmitScoreCommand cmd) {
        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .activityProjectId(cmd.activityProjectId()).studentId(cmd.studentId())
                .attemptNumber(cmd.attemptNumber()).scoreStorageType(cmd.scoreStorageType())
                .scoreValue(cmd.scoreValue()).scoreBusinessTime(cmd.scoreBusinessTime())
                .timeSource(cmd.timeSource()).enteredBy(cmd.enteredBy()));
        s.submit();
        repository.save(s);
        return new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name());
    }

    @Transactional(readOnly = true)
    public List<ScoreAttemptResult> findBySchool(UUID schoolId) {
        return repository.findBySchoolId(schoolId).stream()
                .map(s -> new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name()))
                .toList();
    }
}
