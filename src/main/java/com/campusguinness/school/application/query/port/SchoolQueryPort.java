package com.campusguinness.school.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolListResult;

import java.util.UUID;

public interface SchoolQueryPort {
    QueryPage<SchoolListResult> findNormal(int page, int size);
    boolean isEligibleForMembership(UUID schoolId);
}
