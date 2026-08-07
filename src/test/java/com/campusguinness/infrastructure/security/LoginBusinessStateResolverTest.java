package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.LatestStudentIdentityApplicationState;
import com.campusguinness.identity.application.query.LoginBusinessStateQuery;
import com.campusguinness.identity.application.query.SchoolAdminInvitationLoginState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginBusinessStateResolverTest {

    @Mock LoginBusinessStateQuery query;

    LoginBusinessStateResolver resolver;

    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-06T00:00:00Z");

    @BeforeEach
    void setUp() {
        resolver = new LoginBusinessStateResolver(query, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void disabledIsDeniedBeforeOtherBusinessState() {
        assertDenied(account("DISABLED", null), "ACCOUNT_DISABLED", HttpStatus.FORBIDDEN);
    }

    @Test
    void lockedStatusOrFutureLockedUntilIsDenied() {
        assertDenied(account("LOCKED", null), "ACCOUNT_LOCKED", HttpStatus.UNAUTHORIZED);
        assertDenied(account("NORMAL", now.plusSeconds(60)), "ACCOUNT_LOCKED", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pendingStudentApplicationExplainsPendingApproval() {
        when(query.findLatestStudentApplication(userId)).thenReturn(Optional.of(
                new LatestStudentIdentityApplicationState(UUID.randomUUID(), "PENDING", now)));

        assertDenied(account("PENDING_ACTIVATION", null), "STUDENT_APPROVAL_PENDING", HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectedLatestStudentApplicationExplainsRejection() {
        when(query.findLatestStudentApplication(userId)).thenReturn(Optional.of(
                new LatestStudentIdentityApplicationState(UUID.randomUUID(), "REJECTED", now)));

        assertDenied(account("PENDING_ACTIVATION", null), "STUDENT_APPLICATION_REJECTED", HttpStatus.FORBIDDEN);
    }

    @Test
    void pendingSchoolAdminInvitationExplainsActivationPending() {
        when(query.findLatestStudentApplication(userId)).thenReturn(Optional.empty());
        when(query.findLatestSchoolAdminInvitation(userId)).thenReturn(Optional.of(
                new SchoolAdminInvitationLoginState(
                        UUID.randomUUID(), UUID.randomUUID(), "SCHOOL_ADMIN", "PENDING", now.plusSeconds(60))));

        assertDenied(account("PENDING_ACTIVATION", null), "SCHOOL_ADMIN_ACTIVATION_PENDING", HttpStatus.FORBIDDEN);
    }

    @Test
    void expiredSchoolAdminInvitationRequiresActivation() {
        when(query.findLatestStudentApplication(userId)).thenReturn(Optional.empty());
        when(query.findLatestSchoolAdminInvitation(userId)).thenReturn(Optional.of(
                new SchoolAdminInvitationLoginState(
                        UUID.randomUUID(), UUID.randomUUID(), "SCHOOL_ADMIN", "PENDING", now.minusSeconds(60))));

        assertDenied(account("PENDING_ACTIVATION", null), "SCHOOL_ADMIN_ACTIVATION_REQUIRED", HttpStatus.FORBIDDEN);
    }

    @Test
    void normalAccountIsAllowed() {
        assertThatCode(() -> resolver.requireLoginAllowed(account("NORMAL", null))).doesNotThrowAnyException();
    }

    private void assertDenied(AuthenticationAccount account, String code, HttpStatus status) {
        assertThatThrownBy(() -> resolver.requireLoginAllowed(account))
                .isInstanceOf(LoginDeniedAuthenticationException.class)
                .satisfies(e -> {
                    var denied = (LoginDeniedAuthenticationException) e;
                    org.assertj.core.api.Assertions.assertThat(denied.code()).isEqualTo(code);
                    org.assertj.core.api.Assertions.assertThat(denied.status()).isEqualTo(status);
                });
    }

    private AuthenticationAccount account(String status, Instant lockedUntil) {
        return new AuthenticationAccount(userId, "u", "hash", status, null, lockedUntil);
    }
}
