package com.campusguinness.identity.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.CreateStudentProfileCommand;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.port.StudentProfileCommandPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.result.StudentIdentityApplicationReviewResult;
import com.campusguinness.identity.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApproveStudentIdentityApplicationServiceTest {

    @Mock StudentIdentityReviewAuthorization authorization;
    @Mock StudentIdentityApplicationRepository applications;
    @Mock UserRepository users;
    @Mock StudentProfileCommandPort profiles;
    @Mock AuditRecordCommandPort audit;

    private ApproveStudentIdentityApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApproveStudentIdentityApplicationService(authorization, applications, users, profiles, audit);
    }

    @Test
    void approveCreatesStudentMembershipProfileAndAudit() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var user = pendingUser(userId);
        var application = pendingApplication(applicationId, userId, schoolId);

        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.of(application));
        when(users.findByIdForUpdate(new UserId(userId))).thenReturn(Optional.of(user));
        when(profiles.existsByUserId(userId)).thenReturn(false);

        StudentIdentityApplicationReviewResult result = service.approve(schoolId, applicationId);

        assertThat(result.applicationId()).isEqualTo(applicationId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.schoolId()).isEqualTo(schoolId);
        assertThat(result.applicationStatus()).isEqualTo("APPROVED");
        assertThat(result.accountStatus()).isEqualTo("NORMAL");
        assertThat(result.membershipRole()).isEqualTo("STUDENT");
        assertThat(result.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(result.reason()).isNull();
        assertThat(result.reviewedAt()).isNotNull();

        assertThat(application.status()).isEqualTo(StudentIdentityApplicationStatus.APPROVED);
        assertThat(application.reviewedBy()).isEqualTo(actorId);
        assertThat(application.reviewedAt()).isNotNull();
        assertThat(user.status()).isEqualTo(AccountStatus.NORMAL);
        assertThat(user.activeMembershipFor(schoolId)).isPresent();

        verify(authorization).requireSchoolAdmin(schoolId);
        verify(users).findByIdForUpdate(new UserId(userId));
        verify(users).save(user);
        verify(applications).save(application);

        ArgumentCaptor<CreateStudentProfileCommand> profileCaptor = ArgumentCaptor.forClass(CreateStudentProfileCommand.class);
        verify(profiles).create(profileCaptor.capture());
        assertThat(profileCaptor.getValue().membershipId()).isEqualTo(user.activeMembershipFor(schoolId).orElseThrow().id().value());
        assertThat(profileCaptor.getValue().grade()).isEqualTo(application.grade());
        assertThat(profileCaptor.getValue().className()).isEqualTo(application.className());
        assertThat(profileCaptor.getValue().studentNumber()).isEqualTo(application.studentNumber());

        ArgumentCaptor<com.campusguinness.audit.application.port.AuditRecordCommand> auditCaptor =
                ArgumentCaptor.forClass(com.campusguinness.audit.application.port.AuditRecordCommand.class);
        verify(audit).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("STUDENT_APPLICATION_APPROVED");
        assertThat(auditCaptor.getValue().schoolId()).isEqualTo(schoolId);
        assertThat(auditCaptor.getValue().actorId()).isEqualTo(actorId);
        assertThat(auditCaptor.getValue().targetId()).isEqualTo(applicationId);
    }

    @Test
    void approveRejectsWhenApplicationIsMissing() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(schoolId, applicationId))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("STUDENT_APPLICATION_NOT_FOUND");

        verify(users, never()).save(any());
        verify(profiles, never()).create(any());
        verify(audit, never()).record(any());
    }

    @Test
    void approveRejectsWhenApplicationAlreadyProcessed() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var user = pendingUser(userId);
        var application = approvedApplication(applicationId, userId, schoolId, actorId);

        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.approve(schoolId, applicationId))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("STUDENT_APPLICATION_NOT_PENDING");

        verify(users, never()).findByIdForUpdate(any());
        verifyNoInteractions(profiles, audit);
    }

    @Test
    void approvePropagatesProfileCreationFailureBeforeAudit() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var user = pendingUser(userId);
        var application = pendingApplication(applicationId, userId, schoolId);

        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        when(applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))).thenReturn(Optional.of(application));
        when(users.findByIdForUpdate(new UserId(userId))).thenReturn(Optional.of(user));
        when(profiles.existsByUserId(userId)).thenReturn(false);
        doThrow(new RuntimeException("profile failed")).when(profiles).create(any());

        assertThatThrownBy(() -> service.approve(schoolId, applicationId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("profile failed");

        verify(audit, never()).record(any());
    }

    private User pendingUser(UUID userId) {
        return User.create(new User.Builder().id(new UserId(userId)).username("student"));
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

    private StudentIdentityApplication approvedApplication(UUID applicationId, UUID userId, UUID schoolId, UUID reviewerId) {
        return StudentIdentityApplication.reconstitute(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(applicationId))
                .userId(userId)
                .schoolId(schoolId)
                .realName("Student")
                .studentNumber("SN-001")
                .grade("Grade 10")
                .className("Class 1")
                .status(StudentIdentityApplicationStatus.APPROVED)
                .reviewedBy(reviewerId)
                .reviewedAt(java.time.Instant.parse("2026-08-06T00:00:00Z")));
    }
}
