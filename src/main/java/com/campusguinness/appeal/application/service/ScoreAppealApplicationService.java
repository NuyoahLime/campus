package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.application.query.port.ScoreAppealQueryPort;
import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ScoreAppealApplicationService {
    private final ScoreAppealRepository repo;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final StudentResourceAuthorization studentAuthorization;
    private final StudentSchoolScopeAuthorization studentScopeAuthorization;
    private final StudentScoreQueryPort studentScoreQueryPort;

    public ScoreAppealApplicationService(ScoreAppealRepository r,
            SchoolResourceAuthorization schoolAuthorization, StudentResourceAuthorization studentAuthorization,
            StudentSchoolScopeAuthorization studentScopeAuthorization,
            StudentScoreQueryPort studentScoreQueryPort) {
        this.repo = r;
        this.schoolAuthorization = schoolAuthorization;
        this.studentAuthorization = studentAuthorization;
        this.studentScopeAuthorization = studentScopeAuthorization;
        this.studentScoreQueryPort = studentScoreQueryPort;
    }

    public ScoreAppealResult submit(UUID schoolId, UUID scoreAttemptId, String appealType, String appealReason) {
        return submitForCurrentStudent(scoreAttemptId, appealType, appealReason);
    }

    public ScoreAppealResult submitForCurrentStudent(UUID scoreAttemptId, String appealType, String appealReason) {
        var scope = studentScopeAuthorization.requireUniqueActiveStudent();
        studentScoreQueryPort.findVisibleById(scoreAttemptId, scope.studentId(), scope.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("Score attempt not found: " + scoreAttemptId));
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(scope.schoolId()).scoreAttemptId(scoreAttemptId).studentId(scope.studentId())
                .appealType(appealType).appealReason(appealReason));
        repo.save(a);
        return result(a);
    }
    public ScoreAppealResult beginProcessing(UUID id, UUID handlerId) {
        return beginProcessing(id);
    }
    public ScoreAppealResult beginProcessing(UUID id) {
        var a = find(id);
        UUID actorUserId = schoolAuthorization.requireSchoolAdmin(a.schoolId());
        a.beginProcessing(actorUserId);
        repo.save(a);
        return result(a);
    }
    public ScoreAppealResult reject(UUID id, String resolution) {
        var a = find(id);
        schoolAuthorization.requireSchoolAdmin(a.schoolId());
        a.reject(resolution);
        repo.save(a);
        return result(a);
    }
    public ScoreAppealResult withdraw(UUID id) {
        var a = find(id);
        studentAuthorization.requireSelf(a.studentId());
        a.withdraw();
        repo.save(a);
        return result(a);
    }
    public ScoreAppealResult resolve(UUID id, String resolution) { var a=find(id); a.resolve(resolution); repo.save(a); return result(a); }
    private ScoreAppeal find(UUID id) { return repo.findById(new ScoreAppealId(id)).orElseThrow(()->new IllegalArgumentException("ScoreAppeal not found: "+id)); }
    private ScoreAppealResult result(ScoreAppeal a) { return new ScoreAppealResult(a.id().value(), a.status().name()); }
}
