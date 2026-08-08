package com.campusguinness.interfaces.web.security;

import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentSelfScopeAuthorizationIT extends ResourceAuthorizationTestSupport {

    @Autowired private FeedbackApplicationService feedbacks;

    @Test
    void studentCanCloseOwnFeedback() {
        UUID schoolId = insertSchool("self");
        UUID studentId = insertUser("student");
        UUID membershipId = insertMembership(studentId, schoolId, "STUDENT", "ACTIVE");
        UUID feedbackId = insertFeedback(schoolId, studentId, "SUBMITTED");
        authenticate(studentId, "STUDENT",
                List.of(snapshotMembership(membershipId, schoolId, "STUDENT")));

        assertThat(feedbacks.close(feedbackId, "done").status()).isEqualTo("CLOSED");
    }

    @Test
    void studentCannotCloseAnotherStudentsFeedback() {
        UUID schoolId = insertSchool("horizontal");
        UUID studentA = insertUser("student-a");
        UUID studentB = insertUser("student-b");
        UUID membershipId = insertMembership(studentA, schoolId, "STUDENT", "ACTIVE");
        UUID feedbackId = insertFeedback(schoolId, studentB, "SUBMITTED");
        authenticate(studentA, "STUDENT",
                List.of(snapshotMembership(membershipId, schoolId, "STUDENT")));

        assertThatThrownBy(() -> feedbacks.close(feedbackId, "done"))
                .isInstanceOf(IdentityApplicationException.class)
                .hasMessageContaining("Student resource scope denied");
    }
}
