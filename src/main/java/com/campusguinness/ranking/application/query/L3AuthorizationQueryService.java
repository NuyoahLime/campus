package com.campusguinness.ranking.application.query;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.L3AuthorizationDetailResult;
import com.campusguinness.ranking.application.query.model.L3AuthorizationSummaryResult;
import com.campusguinness.ranking.application.query.port.L3AuthorizationQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class L3AuthorizationQueryService {
    private final L3AuthorizationQueryPort query;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final PlatformGovernanceAuthorization platformAuthorization;

    public L3AuthorizationQueryService(
            L3AuthorizationQueryPort query,
            SchoolResourceAuthorization schoolAuthorization,
            PlatformGovernanceAuthorization platformAuthorization) {
        this.query = query;
        this.schoolAuthorization = schoolAuthorization;
        this.platformAuthorization = platformAuthorization;
    }

    public QueryPage<L3AuthorizationSummaryResult> listSchool(String status, UUID projectId, int page, int size) {
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return query.listForSchool(schoolId, status, projectId, page, size);
    }

    public L3AuthorizationDetailResult schoolDetail(UUID id) {
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return query.findForSchool(id, schoolId)
                .orElseThrow(() -> new IllegalArgumentException("L3Authorization not found: " + id));
    }

    public QueryPage<L3AuthorizationSummaryResult> listReview(String status, UUID schoolId, UUID projectId, int page, int size) {
        platformAuthorization.requireSuperAdmin();
        return query.listForReview(status, schoolId, projectId, page, size);
    }

    public L3AuthorizationDetailResult reviewDetail(UUID id) {
        platformAuthorization.requireSuperAdmin();
        return query.findForReview(id)
                .orElseThrow(() -> new IllegalArgumentException("L3Authorization not found: " + id));
    }
}
