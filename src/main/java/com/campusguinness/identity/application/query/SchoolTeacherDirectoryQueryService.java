package com.campusguinness.identity.application.query;

import com.campusguinness.identity.application.query.port.SchoolTeacherDirectoryQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SchoolTeacherDirectoryQueryService {

    private final SchoolTeacherDirectoryQueryPort port;

    public SchoolTeacherDirectoryQueryService(SchoolTeacherDirectoryQueryPort port) { this.port = port; }

    public QueryPage<SchoolTeacherDirectoryQueryPort.SchoolTeacherItem> listTeachers(
            UUID schoolId, String keyword, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (keyword != null && keyword.length() > 100) throw new IllegalArgumentException("keyword too long");
        return port.findActiveTeachers(schoolId, keyword, page, size);
    }
}
