package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
import com.campusguinness.score.application.port.ScoreAttemptRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScoreAppealApplicationService {
    private final ScoreAppealRepository repo;
    private final ScoreAttemptRepository scoreAttemptRepo;

    public ScoreAppealApplicationService(ScoreAppealRepository r, ScoreAttemptRepository scoreAttemptRepo) {
        this.repo = r;
        this.scoreAttemptRepo = scoreAttemptRepo;
    }

    public ScoreAppealResult submit(UUID scoreAttemptId, UUID currentStudentId, String appealType, String appealReason) {
        var attempt = scoreAttemptRepo.findByIdAndStudentId(scoreAttemptId, currentStudentId)
                .orElseThrow(() -> new IllegalArgumentException("ScoreAttempt not found"));
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(attempt.schoolId()).scoreAttemptId(scoreAttemptId).studentId(currentStudentId)
                .appealType(appealType).appealReason(appealReason));
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult beginProcessing(UUID id, ActorContext actor) {
        var a = findManageable(id, actor);
        a.beginProcessing(actor.userId());
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult reject(UUID id, ActorContext actor, String resolution) {
        var a = findManageable(id, actor);
        a.reject(resolution);
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult resolve(UUID id, ActorContext actor, String resolution) {
        var a = findManageable(id, actor);
        a.resolve(resolution);
        repo.save(a);
        return result(a);
    }

    public ScoreAppealResult withdraw(UUID id, UUID currentStudentId) {
        var a = repo.findByIdAndStudentId(id, currentStudentId)
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found"));
        a.withdraw(); repo.save(a); return result(a);
    }

    private ScoreAppeal findManageable(UUID id, ActorContext actor) {
        if (actor.isSuperAdmin()) return repo.findById(new ScoreAppealId(id))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found"));
        if (!actor.isSchoolAdmin()) throw new AccessDeniedException("School administrator role required");
        if (actor.primarySchoolId() == null) throw new AccessDeniedException("School context required");
        return repo.findByIdAndSchoolId(id, actor.primarySchoolId())
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found"));
    }

    private ScoreAppealResult result(ScoreAppeal a) {
        return new ScoreAppealResult(a.id().value(), a.status().name());
    }

    @Transactional(readOnly = true)
    public List<ScoreAppealResult> listMine(UUID studentId) {
        return repo.findByStudentId(studentId).stream()
                .map(a -> new ScoreAppealResult(a.id().value(), a.status().name())).toList();
    }

    @Transactional(readOnly = true)
    public ScoreAppealResult getMine(UUID id, UUID studentId) {
        return repo.findByIdAndStudentId(id, studentId)
                .map(a -> new ScoreAppealResult(a.id().value(), a.status().name()))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found: " + id));
    }
}
