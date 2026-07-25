package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.project.application.query.model.QueryPage;
import java.util.UUID;

public interface TeacherApplicationQueryPort {
    QueryPage<ActivityApplicationResult> findMine(UUID applicantId, String status,
            UUID schoolId, String keyword, int page, int size);
}
