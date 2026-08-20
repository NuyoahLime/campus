package com.campusguinness.feedback.application.query.port;

import com.campusguinness.feedback.application.query.model.FeedbackDetailResult;
import com.campusguinness.feedback.application.query.model.FeedbackListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface FeedbackQueryPort {
    QueryPage<FeedbackListResult> findByStudent(UUID submitterId, UUID schoolId, int page, int size);

    Optional<FeedbackDetailResult> findByIdAndStudent(UUID feedbackId, UUID submitterId, UUID schoolId);

    QueryPage<FeedbackListResult> findBySchool(UUID schoolId, int page, int size);

    Optional<FeedbackDetailResult> findByIdAndSchool(UUID feedbackId, UUID schoolId);
}
