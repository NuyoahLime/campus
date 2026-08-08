package com.campusguinness.interfaces.web.security;

import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaleMembershipAuthorizationIT extends ResourceAuthorizationTestSupport {

    @Autowired private FeedbackApplicationService feedbacks;

    @Test
    void staleSessionMembershipDoesNotAuthorizeAfterDatabaseMembershipEnded() {
        UUID schoolId = insertSchool("stale");
        UUID adminId = insertUser("admin");
        UUID studentId = insertUser("student");
        UUID membershipId = insertMembership(adminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        UUID feedbackId = insertFeedback(schoolId, studentId, "SUBMITTED");
        authenticate(adminId, "SCHOOL_ADMIN",
                List.of(snapshotMembership(membershipId, schoolId, "SCHOOL_ADMIN")));

        jdbc.update("UPDATE school_memberships SET status = 'ENDED', ended_at = now() WHERE id = ?", membershipId);

        assertThatThrownBy(() -> feedbacks.beginProcessing(feedbackId, UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class)
                .hasMessageContaining("School administration scope denied");
    }
}
