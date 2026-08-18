package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityManagementListResult;
import com.campusguinness.activity.application.query.model.ActivityManagementDetailResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ActivityQueryService {
    private static final List<String> PUBLIC_EXECUTION_STATUSES = List.of("PUBLISHED", "IN_PROGRESS", "ENDED");
    private final ActivityQueryPort queryPort;
    private final SchoolResourceAuthorization authorization;
    public ActivityQueryService(ActivityQueryPort p) { this(p, null); }
    public ActivityQueryService(ActivityQueryPort p, SchoolResourceAuthorization authorization) {
        this.queryPort = p; this.authorization = authorization;
    }

    public QueryPage<ActivityListResult> listPublic(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findPublic(page, size, PUBLIC_EXECUTION_STATUSES);
    }

    public ActivityDetailResult publicDetail(UUID id) {
        return queryPort.findPublicById(id, PUBLIC_EXECUTION_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }

    public QueryPage<ActivityManagementListResult> listManagement(
            int page, int size, String status, String query, UUID projectId) {
        validatePage(page, size);
        return queryPort.findManagement(requireSchool(), page, size, normalize(status), normalize(query), projectId);
    }

    public ActivityManagementDetailResult managementDetail(UUID id) {
        return queryPort.findManagementById(id, requireSchool())
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }

    private UUID requireSchool() {
        if (authorization == null) throw new IllegalStateException("Management authorization is unavailable");
        return authorization.requireUniqueSchoolAdminSchool();
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
