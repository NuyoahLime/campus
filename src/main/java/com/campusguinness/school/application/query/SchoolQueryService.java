package com.campusguinness.school.application.query;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolListResult;
import com.campusguinness.school.application.query.port.SchoolQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SchoolQueryService {
    private final SchoolQueryPort queryPort;
    public SchoolQueryService(SchoolQueryPort p) { this.queryPort = p; }

    public QueryPage<SchoolListResult> listNormal(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findNormal(page, size);
    }
}
