package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.ReviewPageResult;
import com.campusguinness.identity.application.query.StudentIdentityApplicationDetail;
import com.campusguinness.identity.application.query.StudentIdentityApplicationReviewQuery;
import com.campusguinness.identity.application.query.StudentIdentityApplicationSummary;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class StudentIdentityApplicationReviewService {

    private final StudentIdentityReviewAuthorization authorization;
    private final StudentIdentityApplicationReviewQuery query;

    public StudentIdentityApplicationReviewService(
            StudentIdentityReviewAuthorization authorization,
            StudentIdentityApplicationReviewQuery query
    ) {
        this.authorization = authorization;
        this.query = query;
    }

    @Transactional(readOnly = true)
    public ReviewPageResult<StudentIdentityApplicationSummary> list(
            UUID schoolId,
            String status,
            int page,
            int size
    ) {
        authorization.requireSchoolAdmin(schoolId);
        validatePage(page, size);
        return query.findBySchool(schoolId, parseStatus(status), page, size);
    }

    @Transactional(readOnly = true)
    public StudentIdentityApplicationDetail detail(UUID schoolId, UUID applicationId) {
        authorization.requireSchoolAdmin(schoolId);
        if (applicationId == null) throw new IllegalArgumentException("applicationId required");
        return query.findDetail(schoolId, applicationId)
                .orElseThrow(() -> error("STUDENT_APPLICATION_NOT_FOUND", "Student application not found."));
    }

    private StudentIdentityApplicationStatus parseStatus(String raw) {
        String value = raw == null || raw.isBlank() ? "PENDING" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            return StudentIdentityApplicationStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw error("INVALID_STUDENT_APPLICATION_STATUS", "Invalid student application status.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error("INVALID_STUDENT_APPLICATION_PAGE", "Invalid page request.");
        }
    }

    private IdentityApplicationException error(String code, String message) {
        return new IdentityApplicationException(code, message);
    }
}
