package com.campusguinness.interfaces.web.activity;

import java.util.UUID;

public record ResponsibleTeacherResponse(UUID activityProjectId, UUID teacherId, UUID membershipId) {}
