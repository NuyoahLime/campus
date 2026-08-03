package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.score.application.port.ScoreAttemptRepository;

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

    public ScoreAppealResult beginProcessing(UUID id, UUID handlerId) {
        var a = find(id); a.beginProcessing(handlerId); repo.save(a); return result(a);
    }

    public ScoreAppealResult reject(UUID id, String resolution) {
        var a = find(id); a.reject(resolution); repo.save(a); return result(a);
    }

    public ScoreAppealResult withdraw(UUID id, UUID currentStudentId) {
        var a = repo.findByIdAndStudentId(id, currentStudentId)
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found"));
        a.withdraw(); repo.save(a); return result(a);
    }

    public ScoreAppealResult resolve(UUID id, String resolution) {
        var a = find(id); a.resolve(resolution); repo.save(a); return result(a);
    }

    private ScoreAppeal find(UUID id) {
        return repo.findById(new ScoreAppealId(id))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found: " + id));
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
