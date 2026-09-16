package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.*;

final class ActivityResultPersistenceMapper {
    private ActivityResultPersistenceMapper() {}

    static ActivityResultEntity toEntity(ActivityResult domain) {
        var e = new ActivityResultEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setActivityId(domain.activityId());
        e.setResultInternalStatus(domain.internalStatus().name());
        e.setResultPublicStatus(domain.publicStatus().name());
        e.setCurrentCandidateVersionId(domain.currentCandidateVersionId());
        e.setCurrentInternalVersionId(domain.currentInternalVersionId());
        e.setCurrentPublicVersionId(domain.currentPublicVersionId());
        e.setPublicVisibilityBlocked(domain.publicVisibilityBlocked());
        e.setCreatedAt(domain.createdAt()); e.setUpdatedAt(domain.updatedAt());
        e.setVersion(domain.persistenceVersion());
        return e;
    }

    static ActivityResult toDomain(ActivityResultEntity e) {
        return ActivityResult.reconstitute(new ActivityResult.Builder()
                .id(new ActivityResultId(e.getId())).schoolId(e.getSchoolId())
                .activityId(e.getActivityId()),
                ResultInternalStatus.valueOf(e.getResultInternalStatus()),
                ResultPublicStatus.valueOf(e.getResultPublicStatus()),
                e.getCurrentCandidateVersionId(), e.getCurrentInternalVersionId(),
                e.getCurrentPublicVersionId(), e.isPublicVisibilityBlocked(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}
