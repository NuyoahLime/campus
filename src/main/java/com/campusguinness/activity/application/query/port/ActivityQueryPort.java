package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityQueryPort {
    QueryPage<ActivityListResult> findPublic(int page, int size, List<String> statuses);

    Optional<ActivityDetailResult> findPublicById(UUID id, List<String> statuses);
}
