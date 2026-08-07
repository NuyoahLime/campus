package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.LoginBusinessStateQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
class LoginBusinessStateResolver {

    private static final Logger log = LoggerFactory.getLogger(LoginBusinessStateResolver.class);

    private final LoginBusinessStateQuery query;
    private final Clock clock;

    LoginBusinessStateResolver(LoginBusinessStateQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    void requireLoginAllowed(AuthenticationAccount account) {
        Instant now = clock.instant();
        String status = account.accountStatus();
        if ("DISABLED".equals(status)) {
            throw denied("ACCOUNT_DISABLED", "The account is disabled.", HttpStatus.FORBIDDEN);
        }
        if ("LOCKED".equals(status) || (account.lockedUntil() != null && account.lockedUntil().isAfter(now))) {
            throw denied("ACCOUNT_LOCKED", "The account is locked.", HttpStatus.UNAUTHORIZED);
        }
        if ("PENDING_ACTIVATION".equals(status)) {
            resolvePendingActivation(account, now);
            return;
        }
        if (!"NORMAL".equals(status)) {
            log.warn("Unknown account_status '{}' for user {}", status, account.userId());
            throw denied("ACCOUNT_ROLE_NOT_READY", "The account role is not ready.", HttpStatus.FORBIDDEN);
        }
    }

    private void resolvePendingActivation(AuthenticationAccount account, Instant now) {
        var application = query.findLatestStudentApplication(account.userId());
        if (application.isPresent()) {
            String applicationStatus = application.get().applicationStatus();
            if ("PENDING".equals(applicationStatus)) {
                throw denied("STUDENT_APPROVAL_PENDING", "Student identity approval is pending.", HttpStatus.FORBIDDEN);
            }
            if ("REJECTED".equals(applicationStatus)) {
                throw denied("STUDENT_APPLICATION_REJECTED", "Student identity application was rejected.", HttpStatus.FORBIDDEN);
            }
            log.warn("Pending user {} has latest student application in unexpected status {}",
                    account.userId(), applicationStatus);
            throw denied("ACCOUNT_ACTIVATION_REQUIRED", "Account activation is required.", HttpStatus.FORBIDDEN);
        }

        var invitation = query.findLatestSchoolAdminInvitation(account.userId());
        if (invitation.isPresent()) {
            var state = invitation.get();
            if ("PENDING".equals(state.invitationStatus()) && state.expiresAt().isAfter(now)) {
                throw denied("SCHOOL_ADMIN_ACTIVATION_PENDING",
                        "School administrator activation is pending.", HttpStatus.FORBIDDEN);
            }
            log.warn("Pending school admin user {} has non-activatable invitation {} with status {}",
                    account.userId(), state.invitationId(), state.invitationStatus());
            throw denied("SCHOOL_ADMIN_ACTIVATION_REQUIRED",
                    "School administrator activation is required.", HttpStatus.FORBIDDEN);
        }

        throw denied("ACCOUNT_ACTIVATION_REQUIRED", "Account activation is required.", HttpStatus.FORBIDDEN);
    }

    private LoginDeniedAuthenticationException denied(String code, String message, HttpStatus status) {
        return new LoginDeniedAuthenticationException(code, message, status);
    }
}
