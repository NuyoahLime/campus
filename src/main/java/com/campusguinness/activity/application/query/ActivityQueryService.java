package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
