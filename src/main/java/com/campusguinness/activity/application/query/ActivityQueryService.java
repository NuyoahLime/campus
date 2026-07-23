package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ActivityQueryService {
    private final ActivityQueryPort queryPort;
    public ActivityQueryService(ActivityQueryPort p) { this.queryPort = p; }

    /** Internal use: find by execution status (no publicStatus check). */
    public QueryPage<ActivityListResult> listPublic(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findPublic(page, size, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));
    }

    /** Public anonymous discovery: requires publicStatus = PUBLIC. */
    public QueryPage<ActivityListResult> listPublicPublished(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findPublicPublished(page, size, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));
    }

    /** School-scoped list with filters. */
    public QueryPage<ActivityListResult> listBySchool(UUID schoolId, String executionStatus,
            String publicStatus, String keyword, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        return queryPort.findBySchool(schoolId, executionStatus, publicStatus, keyword, page, size);
    }

    /** Public review queue. */
    public QueryPage<ActivityListResult> listPublicReview(String schoolId, String publicStatus,
            int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findPublicReview(schoolId, publicStatus, page, size);
    }
}
