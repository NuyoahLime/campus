package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.project.application.query.model.QueryPage;
import java.util.Optional;
import java.util.UUID;

public interface TeacherApplicationQueryPort {
    QueryPage<ActivityApplicationResult> findMine(UUID applicantId, String status,
            UUID schoolId, String keyword, int page, int size);

    Optional<ActivityApplicationResult> findMineById(UUID applicantId, UUID applicationId);

    record ApplicationStats(int total, int draft, int submitted, int approved, int rejected, int withdrawn) {}

    ApplicationStats getStats(UUID applicantId);

    record TeacherSchoolItem(UUID schoolId, String schoolName) {}

    java.util.List<TeacherSchoolItem> findTeacherSchools(UUID userId);
}
