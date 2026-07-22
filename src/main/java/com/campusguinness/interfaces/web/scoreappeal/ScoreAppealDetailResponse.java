package com.campusguinness.interfaces.web.scoreappeal;

import java.util.UUID;

public record ScoreAppealDetailResponse(UUID id, UUID schoolId, UUID studentId,
        String appealType, String appealReason, String status, UUID handlerId) {}
