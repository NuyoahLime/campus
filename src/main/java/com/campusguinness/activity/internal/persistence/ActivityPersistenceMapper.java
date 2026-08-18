package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
import java.time.Instant;

final class ActivityPersistenceMapper {
    private ActivityPersistenceMapper() {}

    static ActivityEntity toEntity(Activity domain) {
        var e = new ActivityEntity();
        updateEntity(domain, e);
        e.setCreatedAt(Instant.now());
        return e;
    }

    static void updateEntity(Activity domain, ActivityEntity e) {
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setTitle(domain.title()); e.setDescription(domain.description());
        e.setStartTime(domain.startTime()); e.setEndTime(domain.endTime());
        e.setLocation(domain.location());
        e.setExecutionStatus(domain.executionStatus().name());
        e.setPublicStatus(domain.publicStatus().name());
        e.setCreatedBy(domain.createdBy());
        e.setUpdatedAt(Instant.now());
    }

    static Activity toDomain(ActivityEntity e) {
        return Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(e.getId())).schoolId(e.getSchoolId())
                .title(e.getTitle()).description(e.getDescription())
                .startTime(e.getStartTime()).endTime(e.getEndTime())
                .location(e.getLocation()).createdBy(e.getCreatedBy())
                .executionStatus(ExecutionStatus.valueOf(e.getExecutionStatus()))
                .publicStatus(PublicStatus.valueOf(e.getPublicStatus())));
    }
}
