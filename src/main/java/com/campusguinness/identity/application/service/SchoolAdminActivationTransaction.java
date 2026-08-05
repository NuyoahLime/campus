package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.InvitationCodeHasher;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.SchoolAdminInvitationRepository;
import com.campusguinness.identity.application.port.UserCredentialCommandPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
class SchoolAdminActivationTransaction {

    private final UserRepository users;
    private final SchoolAdminInvitationRepository invitations;
    private final InvitationCodeHasher invitationCodeHasher;
    private final PasswordHasher passwordHasher;
    private final UserCredentialCommandPort credentials;

    SchoolAdminActivationTransaction(
            UserRepository users,
            SchoolAdminInvitationRepository invitations,
            InvitationCodeHasher invitationCodeHasher,
            PasswordHasher passwordHasher,
            UserCredentialCommandPort credentials
    ) {
        this.users = users;
        this.invitations = invitations;
        this.invitationCodeHasher = invitationCodeHasher;
        this.passwordHasher = passwordHasher;
        this.credentials = credentials;
    }

    @Transactional
    public ActivationOutcome tryActivate(ActivateSchoolAdminCommand command) {
        String username = command.username() != null ? command.username().trim() : "";
        var probe = users.findByUsername(username);
        if (probe.isEmpty()) {
            return ActivationOutcome.INVALID_CREDENTIAL;
        }

        var user = users.findByIdForUpdate(probe.get().id())
                .orElse(null);
        if (user == null) {
            return ActivationOutcome.INVALID_CREDENTIAL;
        }

        var invitation = invitations.findPendingByUserIdForUpdate(user.id().value())
                .orElse(null);
        if (invitation == null) {
            return ActivationOutcome.INVALID_CREDENTIAL;
        }

        Instant now = Instant.now();
        boolean matches = invitationCodeHasher.matches(command.invitationCode(), invitation.invitationCodeHash());
        if (!matches) {
            if (invitation.isExpiredAt(now)) {
                invitation.expire();
            } else {
                invitation.recordFailedAttempt(now);
            }
            invitations.save(invitation);
            return ActivationOutcome.INVALID_CREDENTIAL;
        }
        if (invitation.isExpiredAt(now)) {
            invitation.expire();
            invitations.save(invitation);
            return ActivationOutcome.EXPIRED;
        }
        if (user.status() != AccountStatus.PENDING_ACTIVATION) {
            return ActivationOutcome.ACCOUNT_NOT_ACTIVATABLE;
        }
        if (user.activeMembershipFor(invitation.schoolId()).isPresent()) {
            return ActivationOutcome.MEMBERSHIP_CONFLICT;
        }

        user.activate();
        user.grantSchoolAdminMembership(new SchoolMembershipId(UUID.randomUUID()), invitation.schoolId(), now);
        users.save(user);
        credentials.replacePasswordHash(user.id().value(), passwordHasher.hash(command.newPassword()));
        invitation.accept(now);
        invitations.save(invitation);
        return ActivationOutcome.SUCCESS;
    }
}
