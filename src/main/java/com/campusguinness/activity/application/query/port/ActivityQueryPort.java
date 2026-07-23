package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.List;

public interface ActivityQueryPort {
    QueryPage<ActivityListResult> findPublic(int page, int size, List<String> executionStatuses);
    QueryPage<ActivityListResult> findPublicPublished(int page, int size, List<String> executionStatuses);
}
