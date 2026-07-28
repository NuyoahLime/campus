package com.campusguinness.identity.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;

import java.util.UUID;

public interface SchoolTeacherDirectoryQueryPort {

    record SchoolTeacherItem(UUID userId, UUID membershipId, String username, String subject, String title) {}

    QueryPage<SchoolTeacherItem> findActiveTeachers(UUID schoolId, String keyword, int page, int size);
}
