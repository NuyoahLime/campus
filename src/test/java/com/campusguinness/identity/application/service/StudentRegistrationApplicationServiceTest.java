package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.school.application.query.model.StudentRegistrationSchool;
import com.campusguinness.school.application.query.port.StudentRegistrationSchoolQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationApplicationServiceTest {

    @Mock UserRepository users;
    @Mock UserAccountProvisioningPort provisioning;
    @Mock StudentIdentityApplicationRepository applications;
    @Mock StudentRegistrationSchoolQueryPort schools;
    @Mock PasswordHasher passwordHasher;

    StudentRegistrationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StudentRegistrationApplicationService(users, provisioning, applications, schools, passwordHasher);
    }

    @Test
    void registerCreatesPendingUserAndPendingApplication() {
        UUID schoolId = UUID.randomUUID();
        when(schools.findForStudentRegistration(schoolId))
                .thenReturn(new StudentRegistrationSchool(schoolId, true, true));
        when(passwordHasher.hash("SecurePassword123!")).thenReturn("hashed-password");
        when(provisioning.create(any(), org.mockito.ArgumentMatchers.eq("hashed-password")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.register(command(" student_001 ", schoolId));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(provisioning).create(userCaptor.capture(), org.mockito.ArgumentMatchers.eq("hashed-password"));
        assertThat(userCaptor.getValue().username()).isEqualTo("student_001");
        assertThat(userCaptor.getValue().status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(userCaptor.getValue().platformRole()).isNull();

        ArgumentCaptor<StudentIdentityApplication> applicationCaptor =
                ArgumentCaptor.forClass(StudentIdentityApplication.class);
        verify(applications).save(applicationCaptor.capture());
        var application = applicationCaptor.getValue();
        assertThat(application.userId()).isEqualTo(userCaptor.getValue().id().value());
        assertThat(application.schoolId()).isEqualTo(schoolId);
        assertThat(application.realName()).isEqualTo("Zhang San");
        assertThat(application.studentNumber()).isEqualTo("20260001");
        assertThat(application.grade()).isEqualTo("Grade 10");
        assertThat(application.className()).isEqualTo("Class 1");
        assertThat(application.evidenceFileKey()).isNull();
        assertThat(application.status()).isEqualTo(StudentIdentityApplicationStatus.PENDING);
        assertThat(application.reviewedBy()).isNull();
        assertThat(application.reviewedAt()).isNull();
        assertThat(application.rejectionReason()).isNull();

        assertThat(result.userId()).isEqualTo(userCaptor.getValue().id().value());
        assertThat(result.applicationId()).isEqualTo(application.id().value());
        assertThat(result.accountStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(result.applicationStatus()).isEqualTo(StudentIdentityApplicationStatus.PENDING);
        assertThat(result.submittedAt()).isNotNull();
    }

    @Test
    void passwordConfirmationMismatchDoesNotTouchRepositoriesOrHasher() {
        assertThatThrownBy(() -> service.register(new RegisterStudentCommand(
                "student", "SecurePassword123!", "DifferentPassword123!",
                "Name", UUID.randomUUID(), "S1", "G1", "C1", List.of())))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("PASSWORD_CONFIRMATION_MISMATCH");

        verifyNoInteractions(users, provisioning, applications, schools, passwordHasher);
    }

    @Test
    void passwordPolicyViolationDoesNotHashOrPersist() {
        assertThatThrownBy(() -> service.register(new RegisterStudentCommand(
                "student", "short", "short",
                "Name", UUID.randomUUID(), "S1", "G1", "C1", List.of())))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("PASSWORD_POLICY_VIOLATION");

        verifyNoInteractions(users, provisioning, applications, schools, passwordHasher);
    }

    @Test
    void closedSchoolDoesNotCreateUser() {
        UUID schoolId = UUID.randomUUID();
        when(schools.findForStudentRegistration(schoolId))
                .thenReturn(new StudentRegistrationSchool(schoolId, true, false));

        assertThatThrownBy(() -> service.register(command("student", schoolId)))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("SCHOOL_NOT_OPEN_FOR_REGISTRATION");

        verify(users, never()).existsByUsername(any());
        verifyNoInteractions(provisioning, applications, passwordHasher);
    }

    @Test
    void missingSchoolDoesNotCreateUser() {
        UUID schoolId = UUID.randomUUID();
        when(schools.findForStudentRegistration(schoolId))
                .thenReturn(new StudentRegistrationSchool(schoolId, false, false));

        assertThatThrownBy(() -> service.register(command("student", schoolId)))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("SCHOOL_NOT_FOUND");

        verifyNoInteractions(provisioning, applications, passwordHasher);
    }

    @Test
    void existingUsernameFailsBeforeHashing() {
        UUID schoolId = UUID.randomUUID();
        when(schools.findForStudentRegistration(schoolId))
                .thenReturn(new StudentRegistrationSchool(schoolId, true, true));
        when(users.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.register(command("taken", schoolId)))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("USERNAME_ALREADY_EXISTS");

        verifyNoInteractions(provisioning, applications, passwordHasher);
    }

    @Test
    void proofFileKeysAreDefensivelyCopiedAndRejectedWhenNonEmpty() {
        var keys = new ArrayList<String>();
        keys.add("proof/key");
        var command = new RegisterStudentCommand(
                "student", "SecurePassword123!", "SecurePassword123!",
                "Name", UUID.randomUUID(), "S1", "G1", "C1", keys);
        keys.clear();

        assertThat(command.proofFileKeys()).containsExactly("proof/key");
        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("PROOF_ATTACHMENT_NOT_SUPPORTED");

        verifyNoInteractions(users, provisioning, applications, schools, passwordHasher);
    }

    private RegisterStudentCommand command(String username, UUID schoolId) {
        return new RegisterStudentCommand(
                username,
                "SecurePassword123!",
                "SecurePassword123!",
                " Zhang San ",
                schoolId,
                " 20260001 ",
                " Grade 10 ",
                " Class 1 ",
                List.of());
    }
}
