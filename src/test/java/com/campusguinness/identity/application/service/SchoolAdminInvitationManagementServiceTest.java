package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.InvitationCodeGenerator;
import com.campusguinness.identity.application.port.InvitationCodeHasher;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.PlaceholderCredentialGenerator;
import com.campusguinness.identity.application.port.SchoolAdminInvitationRepository;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationStatus;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.school.application.query.port.SchoolQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAdminInvitationManagementServiceTest {

    @Mock UserRepository users;
    @Mock UserAccountProvisioningPort provisioning;
    @Mock SchoolAdminInvitationRepository invitations;
    @Mock SchoolQueryPort schools;
    @Mock CurrentActor currentActor;
    @Mock PlaceholderCredentialGenerator placeholderCredentials;
    @Mock PasswordHasher passwordHasher;
    @Mock InvitationCodeGenerator invitationCodes;
    @Mock InvitationCodeHasher invitationCodeHasher;

    SchoolAdminInvitationManagementService service;

    @BeforeEach
    void setUp() {
        service = new SchoolAdminInvitationManagementService(users, provisioning, invitations, schools, currentActor,
                placeholderCredentials, passwordHasher, invitationCodes, invitationCodeHasher);
    }

    @Test
    void createPreCreatesPendingUserAndReturnsRawInvitationCodeOnce() {
        UUID schoolId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(schools.isEligibleForMembership(schoolId)).thenReturn(true);
        when(placeholderCredentials.generate()).thenReturn("placeholder-raw");
        when(passwordHasher.hash("placeholder-raw")).thenReturn("placeholder-hash");
        when(invitationCodes.generate()).thenReturn("raw-invite");
        when(invitationCodeHasher.hash("raw-invite")).thenReturn("invite-hash");
        when(provisioning.create(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(" teacher-admin ", schoolId, Instant.now().plusSeconds(3600));

        assertThat(result.username()).isEqualTo("teacher-admin");
        assertThat(result.invitationCode()).isEqualTo("raw-invite");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(provisioning).create(userCaptor.capture(), org.mockito.ArgumentMatchers.eq("placeholder-hash"));
        assertThat(userCaptor.getValue().status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(userCaptor.getValue().platformRole()).isNull();
        ArgumentCaptor<SchoolAdminInvitation> invitationCaptor = ArgumentCaptor.forClass(SchoolAdminInvitation.class);
        verify(invitations).save(invitationCaptor.capture());
        assertThat(invitationCaptor.getValue().invitationCodeHash()).isEqualTo("invite-hash");
        assertThat(invitationCaptor.getValue().createdBy()).isEqualTo(actorId);
        assertThat(invitationCaptor.getValue().status()).isEqualTo(SchoolAdminInvitationStatus.PENDING);
    }

    @Test
    void duplicateUsernameFailsWithStableCode() {
        when(users.existsByUsername("taken")).thenReturn(true);
        when(currentActor.requireUserId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.create("taken", UUID.randomUUID(), null))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void regenerateRevokesOldInvitationAndCreatesReplacement() {
        UUID userId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var user = User.create(new User.Builder().id(new UserId(userId)).username("admin"));
        var invitation = SchoolAdminInvitation.create(new SchoolAdminInvitation.Builder()
                .id(new SchoolAdminInvitationId(invitationId))
                .userId(userId)
                .schoolId(schoolId)
                .invitationCodeHash("old-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdBy(actorId));
        when(invitations.findById(new SchoolAdminInvitationId(invitationId))).thenReturn(Optional.of(invitation));
        when(users.findByIdForUpdate(new UserId(userId))).thenReturn(Optional.of(user));
        when(invitations.findByIdForUpdate(new SchoolAdminInvitationId(invitationId))).thenReturn(Optional.of(invitation));
        when(invitationCodes.generate()).thenReturn("new-code");
        when(invitationCodeHasher.hash("new-code")).thenReturn("new-hash");
        when(currentActor.requireUserId()).thenReturn(actorId);

        var result = service.regenerate(invitationId);

        assertThat(result.invitationCode()).isEqualTo("new-code");
        verify(invitations).saveAndFlush(org.mockito.ArgumentMatchers.argThat(old ->
                old.id().value().equals(invitationId) && old.status() == SchoolAdminInvitationStatus.REVOKED));
        verify(invitations).save(org.mockito.ArgumentMatchers.argThat(next ->
                next.userId().equals(userId) && next.status() == SchoolAdminInvitationStatus.PENDING
                        && next.invitationCodeHash().equals("new-hash")));
    }
}
