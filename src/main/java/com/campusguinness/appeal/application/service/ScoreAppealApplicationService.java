package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScoreAppealApplicationService {
    private final ScoreAppealRepository repo;
    public ScoreAppealApplicationService(ScoreAppealRepository r) { this.repo = r; }

    public ScoreAppealResult submit(UUID schoolId, UUID scoreAttemptId, UUID studentId, String appealType, String appealReason) {
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(schoolId).scoreAttemptId(scoreAttemptId).studentId(studentId)
                .appealType(appealType).appealReason(appealReason));
        repo.save(a);
        return result(a);
    }
    public ScoreAppealResult beginProcessing(UUID id, UUID handlerId) { var a=find(id); a.beginProcessing(handlerId); repo.save(a); return result(a); }
    public ScoreAppealResult reject(UUID id, String resolution) { var a=find(id); a.reject(resolution); repo.save(a); return result(a); }
    public ScoreAppealResult withdraw(UUID id) { var a=find(id); a.withdraw(); repo.save(a); return result(a); }
    public ScoreAppealResult withdraw(UUID id, UUID studentId) { var a=find(id); if (!a.studentId().equals(studentId)) throw new IllegalArgumentException("Appeal not owned by student"); a.withdraw(); repo.save(a); return result(a); }
    public ScoreAppealResult resolve(UUID id, String resolution) { var a=find(id); a.resolve(resolution); repo.save(a); return result(a); }
    private ScoreAppeal find(UUID id) { return repo.findById(new ScoreAppealId(id)).orElseThrow(()->new IllegalArgumentException("ScoreAppeal not found: "+id)); }
    private ScoreAppealResult result(ScoreAppeal a) { return new ScoreAppealResult(a.id().value(), a.status().name()); }

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
