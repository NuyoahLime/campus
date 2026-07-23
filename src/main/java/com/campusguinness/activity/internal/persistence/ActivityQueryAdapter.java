package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
class ActivityQueryAdapter implements ActivityQueryPort {

    private final ActivityJpaRepository jpa;
    ActivityQueryAdapter(ActivityJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public QueryPage<ActivityListResult> findPublic(int page, int size, List<String> statuses) {
        var pageable = PageRequest.of(page, size, Sort.by("startTime").descending().and(Sort.by("id").descending()));
        var result = jpa.findByExecutionStatusIn(statuses, pageable);
        return toPage(result);
    }

    @Override
    public QueryPage<ActivityListResult> findPublicPublished(int page, int size, List<String> executionStatuses) {
        var pageable = PageRequest.of(page, size, Sort.by("startTime").descending().and(Sort.by("id").descending()));
        var result = jpa.findByExecutionStatusInAndPublicStatus(executionStatuses, "PUBLIC", pageable);
        return toPage(result);
    }

    private QueryPage<ActivityListResult> toPage(org.springframework.data.domain.Page<ActivityEntity> result) {
        var items = result.getContent().stream()
                .map(e -> new ActivityListResult(e.getId(), e.getSchoolId(), e.getTitle(),
                        e.getStartTime(), e.getEndTime(), e.getLocation(), e.getExecutionStatus()))
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
