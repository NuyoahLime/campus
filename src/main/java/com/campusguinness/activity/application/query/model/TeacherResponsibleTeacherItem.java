package com.campusguinness.activity.application.query.model;

import java.util.UUID;

public record TeacherResponsibleTeacherItem(
        UUID userId,
        String username,
        String subject,
        String title) {
}
