package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadSummaryResult;
import com.campusguinness.ranking.application.query.port.RankingReadQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RankingReadQueryService {
    private final RankingReadQueryPort queryPort;
    private final CurrentActor currentActor;
    private final StudentSchoolScopeAuthorization studentScope;
    private final SchoolResourceAuthorization schoolAuthorization;

    public RankingReadQueryService(
            RankingReadQueryPort queryPort,
            CurrentActor currentActor,
            StudentSchoolScopeAuthorization studentScope,
            SchoolResourceAuthorization schoolAuthorization) {
        this.queryPort = queryPort;
        this.currentActor = currentActor;
        this.studentScope = studentScope;
        this.schoolAuthorization = schoolAuthorization;
    }

    public QueryPage<RankingReadSummaryResult> listPublic(int page, int size) {
        validatePage(page, size);
        return queryPort.list(null, false, page, size);
    }

    public RankingReadResult publicDetail(UUID id) {
        requireId(id);
        return queryPort.detail(id, null, false)
                .orElseThrow(() -> new IllegalArgumentException("Ranking not found: " + id));
    }

    public QueryPage<RankingReadSummaryResult> listStudent(int page, int size) {
        validatePage(page, size);
        return queryPort.list(studentScope.requireUniqueActiveStudent().schoolId(), true, page, size);
    }

    public RankingReadResult studentDetail(UUID id) {
        requireId(id);
        return queryPort.detail(id, studentScope.requireUniqueActiveStudent().schoolId(), true)
                .orElseThrow(() -> new IllegalArgumentException("Ranking not found: " + id));
    }

    public QueryPage<RankingReadSummaryResult> listSchoolAdmin(int page, int size) {
        validatePage(page, size);
        return queryPort.list(schoolAuthorization.requireUniqueSchoolAdminSchool(), false, page, size);
    }

    public RankingReadResult schoolAdminDetail(UUID id) {
        requireId(id);
        return queryPort.detail(id, schoolAuthorization.requireUniqueSchoolAdminSchool(), false)
                .orElseThrow(() -> new IllegalArgumentException("Ranking not found: " + id));
    }

    private void requireId(UUID id) {
        if (id == null) throw new IllegalArgumentException("rankingId required");
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
