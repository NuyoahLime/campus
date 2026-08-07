package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampusGuinnessUserDetailsServiceTest {

    @Mock AuthenticationAccountQuery accountQuery;
    @Mock AuthenticationMembershipQuery membershipQuery;
    CampusGuinnessUserDetailsService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CampusGuinnessUserDetailsService(
                accountQuery,
                membershipQuery,
                new CampusPrincipalFactory());
    }

    @Test void loadsByUsernameAndTrimsWhitespace() {
        when(accountQuery.findByLoginName("testuser")).thenReturn(Optional.of(account("NORMAL", "SUPER_ADMIN")));
        when(membershipQuery.findActiveByUserId(USER_ID)).thenReturn(List.of());

        var ud = (CampusGuinnessUserDetails) service.loadUserByUsername("  testuser  ");

        assertThat(ud.getUsername()).isEqualTo("testuser");
        assertThat(ud.getUserId()).isEqualTo(USER_ID);
        assertThat(ud.getAuthorities()).extracting("authority").containsExactly("ROLE_SUPER_ADMIN");
        assertThat(ud.activeSchoolMemberships()).isEmpty();
    }

    @Test void notFoundThrowsUsernameNotFoundException() {
        when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test void loadsActiveSchoolMembershipAuthoritiesAndScopes() {
        UUID membershipId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("NORMAL", null)));
        when(membershipQuery.findActiveByUserId(USER_ID))
                .thenReturn(List.of(new AuthenticationMembership(membershipId, schoolId, "STUDENT")));

        var ud = (CampusGuinnessUserDetails) service.loadUserByUsername("u");

        assertThat(ud.getAuthorities()).extracting("authority").containsExactly("ROLE_STUDENT");
        assertThat(ud.activeSchoolMemberships()).containsExactly(
                new AuthenticatedSchoolMembership(membershipId, schoolId, "STUDENT"));
    }

    @Test void unknownRolesDoNotMapDynamicallyAndFailClosed() {
        when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("NORMAL", "EVIL_ADMIN")));
        when(membershipQuery.findActiveByUserId(USER_ID))
                .thenReturn(List.of(new AuthenticationMembership(UUID.randomUUID(), UUID.randomUUID(), "TEACHER")));

        assertThatThrownBy(() -> service.loadUserByUsername("u"))
                .isInstanceOf(LoginDeniedAuthenticationException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_ROLE_NOT_READY");
    }

    private AuthenticationAccount account(String status, String platformRole) {
        return new AuthenticationAccount(USER_ID, "testuser", "$2a$12$hash", status, platformRole, null);
    }
}
