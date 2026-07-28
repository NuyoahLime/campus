package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
import java.time.Instant;

final class ActivityPersistenceMapper {
    private ActivityPersistenceMapper() {}

    static ActivityEntity toEntity(Activity domain) {
        var e = new ActivityEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setTitle(domain.title()); e.setDescription(domain.description());
        e.setStartTime(domain.startTime()); e.setEndTime(domain.endTime());
        e.setLocation(domain.location());
        e.setExecutionStatus(domain.executionStatus().name());
        e.setPublicStatus(domain.publicStatus().name());
        e.setCreatedBy(domain.createdBy());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
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

    /** Update mutable fields on managed entity — preserves id, schoolId, createdBy, createdAt, version. */
    static void updateEntity(ActivityEntity entity, Activity domain) {
        entity.setTitle(domain.title());
        entity.setDescription(domain.description());
        entity.setStartTime(domain.startTime());
        entity.setEndTime(domain.endTime());
        entity.setLocation(domain.location());
        entity.setExecutionStatus(domain.executionStatus().name());
        entity.setPublicStatus(domain.publicStatus().name());
        entity.setUpdatedAt(Instant.now());
    }
}
