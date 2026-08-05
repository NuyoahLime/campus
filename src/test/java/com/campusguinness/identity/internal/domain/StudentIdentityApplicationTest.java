package com.campusguinness.identity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StudentIdentityApplication aggregate")
class StudentIdentityApplicationTest {

    private StudentIdentityApplication.Builder validBuilder() {
        return new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(UUID.randomUUID()))
                .userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .realName("Student Name")
                .studentNumber("S-001")
                .grade("G7")
                .className("Class 1")
                .evidenceFileKey("evidence/student-001.pdf");
    }

    @Nested
    @DisplayName("Creation")
    class Creation {
        @Test
        @DisplayName("creates in PENDING status")
        void createsPending() {
            var application = StudentIdentityApplication.create(validBuilder());
            assertThat(application.status()).isEqualTo(StudentIdentityApplicationStatus.PENDING);
            assertThat(application.reviewedBy()).isNull();
            assertThat(application.reviewedAt()).isNull();
        }

        @Test
        @DisplayName("requires user, school, and student identity fields")
        void rejectsMissingFields() {
            assertThatThrownBy(() -> StudentIdentityApplication.create(validBuilder().userId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudentIdentityApplication.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudentIdentityApplication.create(validBuilder().realName(" ")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudentIdentityApplication.create(validBuilder().studentNumber(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {
        @Test
        @DisplayName("PENDING to APPROVED records reviewer and time")
        void approvesPendingApplication() {
            var reviewerId = UUID.randomUUID();
            var reviewedAt = Instant.parse("2026-08-05T10:15:30Z");
            var application = StudentIdentityApplication.create(validBuilder());

            application.approve(reviewerId, reviewedAt);

            assertThat(application.status()).isEqualTo(StudentIdentityApplicationStatus.APPROVED);
            assertThat(application.reviewedBy()).isEqualTo(reviewerId);
            assertThat(application.reviewedAt()).isEqualTo(reviewedAt);
            assertThat(application.rejectionReason()).isNull();
        }

        @Test
        @DisplayName("PENDING to REJECTED records reason, reviewer, and time")
        void rejectsPendingApplication() {
            var reviewerId = UUID.randomUUID();
            var reviewedAt = Instant.parse("2026-08-05T10:15:30Z");
            var application = StudentIdentityApplication.create(validBuilder());

            application.reject(reviewerId, reviewedAt, "student number does not match");

            assertThat(application.status()).isEqualTo(StudentIdentityApplicationStatus.REJECTED);
            assertThat(application.reviewedBy()).isEqualTo(reviewerId);
            assertThat(application.reviewedAt()).isEqualTo(reviewedAt);
            assertThat(application.rejectionReason()).isEqualTo("student number does not match");
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test
        @DisplayName("APPROVED is terminal")
        void approvedIsTerminal() {
            var application = StudentIdentityApplication.create(validBuilder());
            application.approve(UUID.randomUUID(), Instant.now());

            assertThatThrownBy(() -> application.reject(UUID.randomUUID(), Instant.now(), "late"))
                    .isInstanceOf(InvalidStudentIdentityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("REJECTED is terminal")
        void rejectedIsTerminal() {
            var application = StudentIdentityApplication.create(validBuilder());
            application.reject(UUID.randomUUID(), Instant.now(), "no");

            assertThatThrownBy(() -> application.approve(UUID.randomUUID(), Instant.now()))
                    .isInstanceOf(InvalidStudentIdentityApplicationStateTransitionException.class);
        }
    }
}
