package com.campusguinness.school.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolListResult;

public interface SchoolQueryPort {
    QueryPage<SchoolListResult> findNormal(int page, int size);
}
