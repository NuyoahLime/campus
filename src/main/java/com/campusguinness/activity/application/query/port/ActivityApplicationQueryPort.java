package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface ActivityApplicationQueryPort {
    QueryPage<ActivityApplicationResult> findAll(String status, UUID schoolId, int page, int size);

    Optional<ActivityApplicationResult> findById(UUID id);
}
