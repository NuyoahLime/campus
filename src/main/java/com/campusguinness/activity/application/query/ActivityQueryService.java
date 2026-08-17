package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
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
    public ActivityQueryService(ActivityQueryPort p) { this.queryPort = p; }

    public QueryPage<ActivityListResult> listPublic(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findPublic(page, size, PUBLIC_EXECUTION_STATUSES);
    }

    public ActivityDetailResult publicDetail(UUID id) {
        return queryPort.findPublicById(id, PUBLIC_EXECUTION_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }
}
