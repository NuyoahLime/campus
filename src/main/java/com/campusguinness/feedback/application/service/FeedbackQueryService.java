package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.query.model.FeedbackDetailResult;
import com.campusguinness.feedback.application.query.model.FeedbackListResult;
import com.campusguinness.feedback.application.query.port.FeedbackQueryPort;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FeedbackQueryService {
    private final FeedbackQueryPort queryPort;
    private final StudentSchoolScopeAuthorization studentScopeAuthorization;
    private final SchoolResourceAuthorization schoolAuthorization;

    public FeedbackQueryService(FeedbackQueryPort queryPort,
                                StudentSchoolScopeAuthorization studentScopeAuthorization,
                                SchoolResourceAuthorization schoolAuthorization) {
        this.queryPort = queryPort;
        this.studentScopeAuthorization = studentScopeAuthorization;
        this.schoolAuthorization = schoolAuthorization;
    }

    public QueryPage<FeedbackListResult> listForCurrentStudent(int page, int size) {
        validatePage(page, size);
        var scope = studentScopeAuthorization.requireUniqueActiveStudent();
        return queryPort.findByStudent(scope.studentId(), scope.schoolId(), page, size);
    }

    public FeedbackDetailResult detailForCurrentStudent(UUID feedbackId) {
        if (feedbackId == null) throw new IllegalArgumentException("feedbackId required");
        var scope = studentScopeAuthorization.requireUniqueActiveStudent();
        return queryPort.findByIdAndStudent(feedbackId, scope.studentId(), scope.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + feedbackId));
    }

    public QueryPage<FeedbackListResult> listForCurrentSchoolAdmin(int page, int size) {
        validatePage(page, size);
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return queryPort.findBySchool(schoolId, page, size);
    }

    public FeedbackDetailResult detailForCurrentSchoolAdmin(UUID feedbackId) {
        if (feedbackId == null) throw new IllegalArgumentException("feedbackId required");
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return queryPort.findByIdAndSchool(feedbackId, schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + feedbackId));
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
