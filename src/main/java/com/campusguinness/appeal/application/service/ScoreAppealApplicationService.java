package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ScoreAppealApplicationService {
    private final ScoreAppealRepository repo;
    private final CurrentActor currentActor;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final StudentResourceAuthorization studentAuthorization;

    public ScoreAppealApplicationService(ScoreAppealRepository r, CurrentActor currentActor,
            SchoolResourceAuthorization schoolAuthorization, StudentResourceAuthorization studentAuthorization) {
        this.repo = r;
        this.currentActor = currentActor;
        this.schoolAuthorization = schoolAuthorization;
        this.studentAuthorization = studentAuthorization;
    }

    public ScoreAppealResult submit(UUID schoolId, UUID scoreAttemptId, String appealType, String appealReason) {
        UUID actorUserId = currentActor.requireUserId();
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(schoolId).scoreAttemptId(scoreAttemptId).studentId(actorUserId)
                .appealType(appealType).appealReason(appealReason));
        repo.save(a);
        return result(a);
    }
    public ScoreAppealResult beginProcessing(UUID id, UUID handlerId) {
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
