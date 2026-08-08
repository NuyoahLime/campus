package com.campusguinness.interfaces.web.security;

import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolScopeAuthorizationIT extends ResourceAuthorizationTestSupport {

    @Autowired private FeedbackApplicationService feedbacks;

    @Test
    void activeSchoolAdminCanProcessSameSchoolResource() {
        UUID schoolId = insertSchool("same");
        UUID adminId = insertUser("admin");
        UUID studentId = insertUser("student");
        UUID membershipId = insertMembership(adminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        UUID feedbackId = insertFeedback(schoolId, studentId, "SUBMITTED");
        authenticate(adminId, "SCHOOL_ADMIN",
                List.of(snapshotMembership(membershipId, schoolId, "SCHOOL_ADMIN")));

        assertThat(feedbacks.beginProcessing(feedbackId, UUID.randomUUID()).status()).isEqualTo("PROCESSING");
    }

    @Test
    void activeSchoolAdminCannotProcessOtherSchoolResource() {
        UUID schoolA = insertSchool("a");
        UUID schoolB = insertSchool("b");
        UUID adminId = insertUser("admin");
        UUID studentId = insertUser("student");
        UUID membershipId = insertMembership(adminId, schoolA, "SCHOOL_ADMIN", "ACTIVE");
        UUID feedbackId = insertFeedback(schoolB, studentId, "SUBMITTED");
        authenticate(adminId, "SCHOOL_ADMIN",
                List.of(snapshotMembership(membershipId, schoolA, "SCHOOL_ADMIN")));

        assertThatThrownBy(() -> feedbacks.beginProcessing(feedbackId, UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class)
                .hasMessageContaining("School administration scope denied");
    }

    @Test
    void endedSchoolAdminMembershipCannotProcessSameSchoolResource() {
        UUID schoolId = insertSchool("ended");
        UUID adminId = insertUser("admin");
        UUID studentId = insertUser("student");
        UUID membershipId = insertMembership(adminId, schoolId, "SCHOOL_ADMIN", "ENDED");
        UUID feedbackId = insertFeedback(schoolId, studentId, "SUBMITTED");
        authenticate(adminId, "SCHOOL_ADMIN",
                List.of(snapshotMembership(membershipId, schoolId, "SCHOOL_ADMIN")));

        assertThatThrownBy(() -> feedbacks.beginProcessing(feedbackId, UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class)
                .hasMessageContaining("School administration scope denied");
    }

    @Test
    void studentMembershipCannotProcessSchoolAdminResource() {
        UUID schoolId = insertSchool("student-role");
        UUID actorId = insertUser("student-actor");
        UUID submitterId = insertUser("student-submit");
        UUID membershipId = insertMembership(actorId, schoolId, "STUDENT", "ACTIVE");
        UUID feedbackId = insertFeedback(schoolId, submitterId, "SUBMITTED");
        authenticate(actorId, "STUDENT",
                List.of(snapshotMembership(membershipId, schoolId, "STUDENT")));

        assertThatThrownBy(() -> feedbacks.beginProcessing(feedbackId, UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class)
                .hasMessageContaining("School administration scope denied");
    }
}
