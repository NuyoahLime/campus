package com.campusguinness.interfaces.web.activity;

import java.util.UUID;

public record ResponsibleTeacherResponse(UUID id, UUID activityProjectId, UUID teacherMembershipId, UUID userId,
                                         String username, String subject, String title,
                                         String membershipStatus, String accountStatus) {}
