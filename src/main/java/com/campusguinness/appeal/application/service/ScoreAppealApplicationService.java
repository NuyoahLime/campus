package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScoreAppealApplicationService {
    private final ScoreAppealRepository repo;
    private final JdbcTemplate jdbc;
    private final CurrentActor currentActor;
    private final SchoolMembershipResolver membershipResolver;

    public ScoreAppealApplicationService(ScoreAppealRepository r, JdbcTemplate j,
                                          CurrentActor currentActor,
                                          SchoolMembershipResolver membershipResolver) {
        this.repo = r;
        this.jdbc = j;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
    }

    /** Submits an appeal after verifying the actor owns the score attempt. */
    public ScoreAppealResult submitAuthorized(UUID actorId, UUID schoolId, UUID scoreAttemptId,
                                               UUID studentId, String appealType, String appealReason) {
        AuthorizationPolicy.requireResourceOwner(actorId, resolveScoreOwner(scoreAttemptId));
        return submit(schoolId, scoreAttemptId, studentId, appealType, appealReason);
    }

    public ScoreAppealResult submit(UUID schoolId, UUID scoreAttemptId, UUID studentId, String appealType, String appealReason) {
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(schoolId).scoreAttemptId(scoreAttemptId).studentId(studentId)
                .appealType(appealType).appealReason(appealReason));
        repo.save(a);
        return result(a);
    }

    private UUID resolveScoreOwner(UUID scoreAttemptId) {
        var rows = jdbc.queryForList(
                "SELECT student_id FROM score_attempts WHERE id = ?", UUID.class, scoreAttemptId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("ScoreAttempt not found: " + scoreAttemptId);
        }
        return rows.getFirst();
    }

    public ScoreAppealResult beginProcessing(UUID id, UUID handlerId) {
        var a = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), a.schoolId());
        }
        a.beginProcessing(handlerId);
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult reject(UUID id, String resolution) {
        var a = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), a.schoolId());
        }
        a.reject(resolution);
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult withdraw(UUID id) {
        var a = find(id);
        UUID actorId = currentActor.requireUserId();
        if (!a.studentId().equals(actorId)) {
            throw new AccessDeniedException(
                    "Actor " + actorId + " cannot withdraw appeal owned by student " + a.studentId());
        }
        a.withdraw();
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult resolve(UUID id, String resolution) {
        var a = find(id);
        a.resolve(resolution);
        repo.save(a);
        return result(a);
    }

    private ScoreAppeal find(UUID id) {
        return repo.findById(new ScoreAppealId(id))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found: " + id));
    }

    @Transactional(readOnly = true)
    public ScoreAppeal findDetailById(UUID id) {
        var a = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), a.schoolId());
        }
        return a;
    }

    @Transactional(readOnly = true)
    public List<ScoreAppealResult> findPendingBySchool(UUID schoolId) {
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), schoolId);
        }
        return repo.findBySchoolIdAndStatusIn(schoolId,
                        List.of(AppealStatus.SUBMITTED, AppealStatus.PROCESSING)).stream()
                .map(a -> new ScoreAppealResult(a.id().value(), a.status().name()))
                .toList();
    }

    private ScoreAppealResult result(ScoreAppeal a) {
        return new ScoreAppealResult(a.id().value(), a.status().name());
    }
}
