package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampusGuinnessUserDetailsServiceTest {

    @Mock AuthenticationAccountQuery accountQuery;
    CampusGuinnessUserDetailsService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CampusGuinnessUserDetailsService(accountQuery);
    }

    private AuthenticationAccount account(String status, String platformRole) {
        return new AuthenticationAccount(USER_ID, "testuser", "$2a$12$hash", status, platformRole, java.util.List.of());
    }

    @Nested class LoadUser {
        @Test void loadsByUsername() {
            when(accountQuery.findByLoginName("testuser")).thenReturn(Optional.of(account("NORMAL", null)));
            var ud = service.loadUserByUsername("testuser");
            assertThat(ud.getUsername()).isEqualTo("testuser");
            assertThat(((CampusGuinnessUserDetails) ud).getUserId()).isEqualTo(USER_ID);
        }
        @Test void trimsWhitespace() {
            when(accountQuery.findByLoginName("testuser")).thenReturn(Optional.of(account("NORMAL", null)));
            var ud = service.loadUserByUsername("  testuser  ");
            assertThat(ud.getUsername()).isEqualTo("testuser");
        }
        @Test void notFoundThrowsUsernameNotFoundException() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    @Nested class StatusMapping {
        @Test void normalEnabled() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("NORMAL", null)));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.isEnabled()).isTrue();
            assertThat(ud.isAccountNonLocked()).isTrue();
        }
        @Test void lockedNotLocked() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("LOCKED", null)));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.isEnabled()).isTrue();  // LOCKED is still enabled
            assertThat(ud.isAccountNonLocked()).isFalse();
        }
        @Test void disabledNotEnabled() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("DISABLED", null)));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.isEnabled()).isFalse();
        }
        @Test void pendingActivationNotEnabled() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("PENDING_ACTIVATION", null)));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.isEnabled()).isFalse();
        }
    }

    @Nested class PlatformRoleMapping {
        @Test void superAdminGetsRole() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("NORMAL", "SUPER_ADMIN")));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.getAuthorities()).extracting("authority").contains("ROLE_SUPER_ADMIN");
        }
        @Test void nullPlatformRoleGetsNoPlatformAuthority() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("NORMAL", null)));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.getAuthorities()).extracting("authority").doesNotContain("ROLE_SUPER_ADMIN");
        }
        @Test void unknownPlatformRoleDenied() {
            when(accountQuery.findByLoginName(anyString())).thenReturn(Optional.of(account("NORMAL", "EVIL_ADMIN")));
            var ud = service.loadUserByUsername("u");
            assertThat(ud.getAuthorities()).extracting("authority").doesNotContain("ROLE_EVIL_ADMIN");
            assertThat(ud.getAuthorities()).extracting("authority").doesNotContain("ROLE_SUPER_ADMIN");
        }
    }
}
