package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.InvitationCodeGenerator;
import com.campusguinness.identity.application.port.InvitationCodeHasher;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.PlaceholderCredentialGenerator;
import com.campusguinness.identity.application.port.SchoolAdminInvitationRepository;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.result.SchoolAdminInvitationResult;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationStatus;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.school.application.query.port.SchoolQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SchoolAdminInvitationManagementService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(72);
    private static final Duration MIN_TTL = Duration.ofMinutes(15);
    private static final Duration MAX_TTL = Duration.ofDays(30);

    private final UserRepository users;
    private final UserAccountProvisioningPort provisioning;
    private final SchoolAdminInvitationRepository invitations;
    private final SchoolQueryPort schools;
    private final CurrentActor currentActor;
    private final PlaceholderCredentialGenerator placeholderCredentials;
    private final PasswordHasher passwordHasher;
    private final InvitationCodeGenerator invitationCodes;
    private final InvitationCodeHasher invitationCodeHasher;

    public SchoolAdminInvitationManagementService(
            UserRepository users,
            UserAccountProvisioningPort provisioning,
            SchoolAdminInvitationRepository invitations,
            SchoolQueryPort schools,
            CurrentActor currentActor,
            PlaceholderCredentialGenerator placeholderCredentials,
            PasswordHasher passwordHasher,
            InvitationCodeGenerator invitationCodes,
            InvitationCodeHasher invitationCodeHasher
    ) {
        this.users = users;
        this.provisioning = provisioning;
        this.invitations = invitations;
        this.schools = schools;
        this.currentActor = currentActor;
        this.placeholderCredentials = placeholderCredentials;
        this.passwordHasher = passwordHasher;
        this.invitationCodes = invitationCodes;
        this.invitationCodeHasher = invitationCodeHasher;
    }

    public SchoolAdminInvitationResult create(String username, UUID schoolId, Instant requestedExpiresAt) {
        String normalized = normalizeUsername(username);
        Instant now = Instant.now();
        Instant expiresAt = resolveExpiresAt(requestedExpiresAt, now);
        UUID actorId = currentActor.requireUserId();

        if (users.existsByUsername(normalized)) {
            throw error("USERNAME_ALREADY_EXISTS", "Username already exists.");
        }
        if (!schools.isEligibleForMembership(schoolId)) {
            throw error("SCHOOL_NOT_ELIGIBLE", "School is not eligible for membership.");
        }

        var user = User.create(new User.Builder()
                .id(new UserId(UUID.randomUUID()))
                .username(normalized));
        var savedUser = provisioning.create(user, passwordHasher.hash(placeholderCredentials.generate()));

        String rawCode = invitationCodes.generate();
        var invitation = SchoolAdminInvitation.create(new SchoolAdminInvitation.Builder()
                .id(new SchoolAdminInvitationId(UUID.randomUUID()))
                .userId(savedUser.id().value())
                .schoolId(schoolId)
                .invitationCodeHash(invitationCodeHasher.hash(rawCode))
                .expiresAt(expiresAt)
                .createdBy(actorId));

        invitations.save(invitation);
        return SchoolAdminInvitationResult.withRawCode(normalized, invitation, rawCode);
    }

    public void revoke(UUID invitationId) {
        var probe = invitations.findById(new SchoolAdminInvitationId(requireInvitationId(invitationId)))
                .orElseThrow(() -> error("INVITATION_NOT_FOUND", "Invitation not found."));
        users.findByIdForUpdate(new UserId(probe.userId()))
                .orElseThrow(() -> error("INVITATION_NOT_FOUND", "Invitation not found."));
        var invitation = invitations.findByIdForUpdate(probe.id())
                .orElseThrow(() -> error("INVITATION_NOT_FOUND", "Invitation not found."));
        if (invitation.status() != SchoolAdminInvitationStatus.PENDING) {
            throw error("INVITATION_NOT_PENDING", "Invitation is not pending.");
        }
        invitation.revoke(Instant.now());
        invitations.save(invitation);
    }

    public SchoolAdminInvitationResult regenerate(UUID invitationId) {
        var probe = invitations.findById(new SchoolAdminInvitationId(requireInvitationId(invitationId)))
                .orElseThrow(() -> error("INVITATION_NOT_FOUND", "Invitation not found."));
        var user = users.findByIdForUpdate(new UserId(probe.userId()))
                .orElseThrow(() -> error("INVITATION_NOT_FOUND", "Invitation not found."));
        var invitation = invitations.findByIdForUpdate(probe.id())
                .orElseThrow(() -> error("INVITATION_NOT_FOUND", "Invitation not found."));

        if (invitation.status() != SchoolAdminInvitationStatus.PENDING) {
            throw error("INVITATION_NOT_PENDING", "Invitation is not pending.");
        }
        if (user.status() != AccountStatus.PENDING_ACTIVATION) {
            throw error("ACCOUNT_ALREADY_ACTIVATED", "Account is already activated.");
        }

        invitation.revoke(Instant.now());
        invitations.saveAndFlush(invitation);

        String rawCode = invitationCodes.generate();
        var replacement = SchoolAdminInvitation.create(new SchoolAdminInvitation.Builder()
                .id(new SchoolAdminInvitationId(UUID.randomUUID()))
                .userId(invitation.userId())
                .schoolId(invitation.schoolId())
                .invitationCodeHash(invitationCodeHasher.hash(rawCode))
                .expiresAt(resolveExpiresAt(null, Instant.now()))
                .createdBy(currentActor.requireUserId()));
        invitations.save(replacement);

        return SchoolAdminInvitationResult.withRawCode(user.username(), replacement, rawCode);
    }

    private Instant resolveExpiresAt(Instant requestedExpiresAt, Instant now) {
        Instant expiresAt = requestedExpiresAt != null ? requestedExpiresAt : now.plus(DEFAULT_TTL);
        if (!expiresAt.isAfter(now.plus(MIN_TTL.minusSeconds(1))) || expiresAt.isAfter(now.plus(MAX_TTL))) {
            throw error("INVALID_INVITATION_EXPIRY", "Invitation expiry is outside allowed range.");
        }
        return expiresAt;
    }

    private String normalizeUsername(String username) {
        String normalized = username != null ? username.trim() : "";
        if (normalized.isBlank()) throw new IllegalArgumentException("username must not be blank");
        if (normalized.length() > 100) throw new IllegalArgumentException("username max 100 chars");
        return normalized;
    }

    private UUID requireInvitationId(UUID invitationId) {
        if (invitationId == null) throw new IllegalArgumentException("invitationId required");
        return invitationId;
    }

    private IdentityApplicationException error(String code, String message) {
        return new IdentityApplicationException(code, message);
    }
}
