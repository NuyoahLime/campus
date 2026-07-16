package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.*;
import java.time.Instant;

final class ActivityResultPersistenceMapper {
    private ActivityResultPersistenceMapper() {}

    static ActivityResultEntity toEntity(ActivityResult domain) {
        var e = new ActivityResultEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setActivityId(domain.activityId());
        e.setResultInternalStatus(domain.internalStatus().name());
        e.setResultPublicStatus(domain.publicStatus().name());
        e.setCurrentInternalVersionId(domain.currentInternalVersionId());
        e.setCurrentPublicVersionId(domain.currentPublicVersionId());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static ActivityResult toDomain(ActivityResultEntity e) {
        return ActivityResult.reconstitute(new ActivityResult.Builder()
                .id(new ActivityResultId(e.getId())).schoolId(e.getSchoolId())
                .activityId(e.getActivityId()),
                ResultInternalStatus.valueOf(e.getResultInternalStatus()),
                ResultPublicStatus.valueOf(e.getResultPublicStatus()),
                e.getCurrentInternalVersionId(), e.getCurrentPublicVersionId());
    }
}
