package com.campusguinness.score.application.service;

import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
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
    private final CurrentActor currentActor;
    private final SchoolMembershipResolver membershipResolver;

    public ScoreAttemptApplicationService(ScoreAttemptRepository repository,
                                           CurrentActor currentActor,
                                           SchoolMembershipResolver membershipResolver) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
    }

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

    public ScoreAttemptResult approve(UUID id) {
        var s = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), s.schoolId());
        }
        s.approve();
        repository.save(s);
        return new ScoreAttemptResult(id, s.status().name(), s.scoreStorageType().name());
    }

    public ScoreAttemptResult reject(UUID id, String reason) {
        var s = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), s.schoolId());
        }
        s.reject(reason);
        repository.save(s);
        return new ScoreAttemptResult(id, s.status().name(), s.scoreStorageType().name());
    }

    private ScoreAttempt find(UUID id) {
        return repository.findById(new ScoreAttemptId(id))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAttempt not found: " + id));
    }
}
