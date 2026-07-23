package com.campusguinness.interfaces.web.activity;

import java.util.UUID;

public record ResponsibleTeacherResponse(UUID activityProjectId, UUID teacherId) {

    /** Old constructor kept for backward compatibility. */
    @Deprecated
    public ResponsibleTeacherResponse(UUID activityProjectId, UUID teacherId, UUID membershipId) {
        this(activityProjectId, teacherId);
    }
}
