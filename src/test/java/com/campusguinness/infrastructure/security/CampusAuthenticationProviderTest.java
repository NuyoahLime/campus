package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.LoginCredentialCommandPort;
import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampusAuthenticationProviderTest {

    @Mock AuthenticationAccountQuery accounts;
    @Mock AuthenticationMembershipQuery memberships;
    @Mock LoginCredentialCommandPort credentials;
    @Mock LoginBusinessStateResolver businessStates;
    @Mock CampusPrincipalFactory principals;
    @Mock PasswordEncoder passwordEncoder;

    CampusAuthenticationProvider provider;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        provider = new CampusAuthenticationProvider(
                accounts, memberships, credentials, businessStates, principals, passwordEncoder);
    }

    @Test
    void unknownUserRunsDummyPasswordMatchAndReturnsBadCredentials() {
        when(accounts.findByLoginName("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(token(" missing ", "pw")))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder).matches("pw", "dummy-hash");
        verifyNoInteractions(credentials, businessStates, memberships, principals);
    }

    @Test
    void wrongPasswordDoesNotRevealBusinessState() {
        var account = account("PENDING_ACTIVATION", null);
        when(accounts.findByLoginName("student")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", account.passwordHash())).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(token("student", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(credentials).recordPasswordFailure(userId);
        verify(businessStates, never()).requireLoginAllowed(account);
        verifyNoInteractions(memberships, principals);
    }

    @Test
    void correctPasswordChecksBusinessStateBeforeLoadingRoles() {
        var account = account("PENDING_ACTIVATION", null);
        when(accounts.findByLoginName("student")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pw", account.passwordHash())).thenReturn(true);
        var denial = new LoginDeniedAuthenticationException(
                "STUDENT_APPROVAL_PENDING", "pending", org.springframework.http.HttpStatus.FORBIDDEN);
        org.mockito.Mockito.doThrow(denial).when(businessStates).requireLoginAllowed(account);

        assertThatThrownBy(() -> provider.authenticate(token("student", "pw")))
                .isSameAs(denial);

        verify(credentials).resetPasswordFailures(userId);
        verifyNoInteractions(memberships, principals);
    }

    @Test
    void successfulLoginReturnsPrincipalWithCredentialsCleared() {
        var account = account("NORMAL", "SUPER_ADMIN");
        var principal = new CampusGuinnessUserDetails(userId, "admin", "hash", "NORMAL", Set.of(), List.of());
        when(accounts.findByLoginName("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pw", account.passwordHash())).thenReturn(true);
        when(memberships.findActiveByUserId(userId)).thenReturn(List.of());
        when(principals.create(account, List.of())).thenReturn(principal);

        var auth = provider.authenticate(token("admin", "pw"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isSameAs(principal);
        assertThat(auth.getCredentials()).isNull();
        verify(credentials).resetPasswordFailures(userId);
    }

    private UsernamePasswordAuthenticationToken token(String username, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(username, password);
    }

    private AuthenticationAccount account(String status, String platformRole) {
        return new AuthenticationAccount(userId, "student", "password-hash", status, platformRole, null);
    }
}
