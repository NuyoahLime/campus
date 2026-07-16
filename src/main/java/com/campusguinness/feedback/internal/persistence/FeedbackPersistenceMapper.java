package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.feedback.internal.domain.*;
import java.time.Instant;

final class FeedbackPersistenceMapper {
    private FeedbackPersistenceMapper() {}

    static FeedbackEntity toEntity(Feedback domain) {
        var e = new FeedbackEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setSubmitterId(domain.submitterId()); e.setFeedbackType(domain.feedbackType());
        e.setContent(domain.content()); e.setFeedbackStatus(domain.status().name());
        e.setHandlerId(domain.handlerId()); e.setHandlerLevel(domain.handlerLevel());
        e.setReply(domain.reply()); e.setCloseReason(domain.closeReason());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static Feedback toDomain(FeedbackEntity e) {
        return Feedback.reconstitute(new Feedback.Builder()
                .id(new FeedbackId(e.getId())).schoolId(e.getSchoolId())
                .submitterId(e.getSubmitterId()).feedbackType(e.getFeedbackType())
                .content(e.getContent()),
                FeedbackStatus.valueOf(e.getFeedbackStatus()),
                e.getHandlerId(), e.getHandlerLevel(), e.getReply(), e.getCloseReason());
    }
}
