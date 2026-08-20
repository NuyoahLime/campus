package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.query.model.ScoreAppealDetailResult;
import com.campusguinness.appeal.application.query.model.ScoreAppealListResult;
import com.campusguinness.appeal.application.query.port.ScoreAppealQueryPort;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScoreAppealQueryService {
    private final ScoreAppealQueryPort queryPort;
    private final StudentSchoolScopeAuthorization studentScopeAuthorization;
    private final SchoolResourceAuthorization schoolAuthorization;

    public ScoreAppealQueryService(ScoreAppealQueryPort queryPort,
                                   StudentSchoolScopeAuthorization studentScopeAuthorization,
                                   SchoolResourceAuthorization schoolAuthorization) {
        this.queryPort = queryPort;
        this.studentScopeAuthorization = studentScopeAuthorization;
        this.schoolAuthorization = schoolAuthorization;
    }

    public QueryPage<ScoreAppealListResult> listForCurrentStudent(int page, int size) {
        validatePage(page, size);
        var scope = studentScopeAuthorization.requireUniqueActiveStudent();
        return queryPort.findByStudent(scope.studentId(), scope.schoolId(), page, size);
    }

    public ScoreAppealDetailResult detailForCurrentStudent(UUID appealId) {
        if (appealId == null) throw new IllegalArgumentException("appealId required");
        var scope = studentScopeAuthorization.requireUniqueActiveStudent();
        return queryPort.findByIdAndStudent(appealId, scope.studentId(), scope.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found: " + appealId));
    }

    public QueryPage<ScoreAppealListResult> listForCurrentSchoolAdmin(int page, int size) {
        validatePage(page, size);
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return queryPort.findBySchool(schoolId, page, size);
    }

    public ScoreAppealDetailResult detailForCurrentSchoolAdmin(UUID appealId) {
        if (appealId == null) throw new IllegalArgumentException("appealId required");
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return queryPort.findByIdAndSchool(appealId, schoolId)
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found: " + appealId));
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
