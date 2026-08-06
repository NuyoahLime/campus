package com.campusguinness.identity.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.result.StudentIdentityApplicationReviewResult;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RejectStudentIdentityApplicationServiceTest {

    @Mock StudentIdentityReviewAuthorization authorization;
    @Mock StudentIdentityApplicationRepository applications;
    @Mock AuditRecordCommandPort audit;

    private RejectStudentIdentityApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RejectStudentIdentityApplicationService(authorization, applications, audit);
    }

    @Test
    void rejectTrimsReasonAndWritesAudit() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var application = pendingApplication(applicationId, userId, schoolId);

        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.of(application));

        StudentIdentityApplicationReviewResult result = service.reject(schoolId, applicationId, " student number mismatch ");

        assertThat(result.applicationId()).isEqualTo(applicationId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.schoolId()).isEqualTo(schoolId);
        assertThat(result.applicationStatus()).isEqualTo("REJECTED");
        assertThat(result.accountStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(result.reason()).isEqualTo("student number mismatch");
        assertThat(result.reviewedAt()).isNotNull();

        assertThat(application.status()).isEqualTo(StudentIdentityApplicationStatus.REJECTED);
        assertThat(application.reviewedBy()).isEqualTo(actorId);
        assertThat(application.rejectionReason()).isEqualTo("student number mismatch");

        verify(authorization).requireSchoolAdmin(schoolId);
        verify(applications).save(application);
        verify(audit).record(any());
    }

    @Test
    void rejectRejectsBlankReason() {
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.reject(schoolId, applicationId, "   "))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("REJECTION_REASON_REQUIRED");

        verifyNoInteractions(applications, audit);
    }

    @Test
    void rejectRejectsWhenApplicationAlreadyProcessed() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var application = StudentIdentityApplication.reconstitute(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(applicationId))
                .userId(UUID.randomUUID())
                .schoolId(schoolId)
                .realName("Student")
                .studentNumber("SN-001")
                .grade("Grade 10")
                .className("Class 1")
                .status(StudentIdentityApplicationStatus.APPROVED)
                .reviewedBy(actorId)
                .reviewedAt(java.time.Instant.parse("2026-08-06T00:00:00Z")));

        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.reject(schoolId, applicationId, "no"))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("STUDENT_APPLICATION_NOT_PENDING");

        verifyNoInteractions(audit);
    }

    @Test
    void rejectPropagatesAuditFailureAfterSavingApplication() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var application = pendingApplication(applicationId, userId, schoolId);

        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.of(application));
        doThrow(new RuntimeException("audit failed")).when(audit).record(any());

        assertThatThrownBy(() -> service.reject(schoolId, applicationId, "ok"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit failed");

        verify(applications).save(application);
    }

    private StudentIdentityApplication pendingApplication(UUID applicationId, UUID userId, UUID schoolId) {
        return StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(applicationId))
                .userId(userId)
                .schoolId(schoolId)
                .realName("Student")
                .studentNumber("SN-001")
                .grade("Grade 10")
                .className("Class 1"));
    }
}
