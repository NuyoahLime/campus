package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.port.*;
import com.campusguinness.identity.internal.domain.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapServiceTest {

    @Mock UserBootstrapStateQuery stateQuery;
    @Mock BootstrapLock lock;
    @Mock PasswordHasher hasher;
    @Mock UserAccountProvisioningPort provisioning;
    SuperAdminBootstrapService svc;

    @BeforeEach void setUp() { svc = new SuperAdminBootstrapService(stateQuery, lock, hasher, provisioning); }

    @Nested class Bootstrap {
        @Test void success() {
            when(stateQuery.countUsers()).thenReturn(0L);
            when(hasher.hash("adminPass123")).thenReturn("$2a$12$hash");
            var saved = User.create(new User.Builder().id(new UserId(java.util.UUID.randomUUID())).username("admin").platformRole("SUPER_ADMIN"));
            saved.activate();
            when(provisioning.create(any(User.class), eq("$2a$12$hash"))).thenReturn(saved);

            var result = svc.bootstrap("admin", "adminPass123");
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.status()).isEqualTo("NORMAL");
            assertThat(result.platformRole()).isEqualTo("SUPER_ADMIN");
            verify(lock).acquireFirstSuperAdminLock();
        }

        @Test void trimsUsername() {
            when(stateQuery.countUsers()).thenReturn(0L);
            when(hasher.hash(anyString())).thenReturn("$2a$12$hash");
            var saved = User.create(new User.Builder().id(new UserId(java.util.UUID.randomUUID())).username("admin").platformRole("SUPER_ADMIN"));
            saved.activate();
            when(provisioning.create(any(User.class), any())).thenReturn(saved);

            svc.bootstrap("  admin  ", "adminPass123");
            verify(provisioning).create(argThat(u -> u.username().equals("admin")), any());
        }

        @Test void refusesDatabaseNotEmpty() {
            when(stateQuery.countUsers()).thenReturn(1L);
            assertThatThrownBy(() -> svc.bootstrap("admin", "adminPass123"))
                    .isInstanceOf(BootstrapRefusedException.class);
            verify(provisioning, never()).create(any(), any());
        }

        @Test void refusesEmptyUsername() {
            assertThatThrownBy(() -> svc.bootstrap("  ", "adminPass123"))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(lock, provisioning);
        }

        @Test void refusesNullPassword() {
            assertThatThrownBy(() -> svc.bootstrap("admin", null))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_BLANK");
            verifyNoInteractions(lock, provisioning);
        }

        @Test void refusesShortPassword() {
            assertThatThrownBy(() -> svc.bootstrap("admin", "short"))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_TOO_SHORT");
            verifyNoInteractions(lock, provisioning);
        }

        @Test void refusesLongPassword() {
            var longPw = "a".repeat(73);
            assertThatThrownBy(() -> svc.bootstrap("admin", longPw))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_TOO_LONG");
            verifyNoInteractions(lock, provisioning);
        }

        @Test void doesNotTrimPassword() {
            when(stateQuery.countUsers()).thenReturn(0L);
            when(hasher.hash(" passwordWithSpaces ")).thenReturn("$2a$12$hash");
            var saved = User.create(new User.Builder().id(new UserId(java.util.UUID.randomUUID())).username("admin").platformRole("SUPER_ADMIN"));
            saved.activate();
            when(provisioning.create(any(User.class), any())).thenReturn(saved);

            assertThatCode(() -> svc.bootstrap("admin", " passwordWithSpaces ")).doesNotThrowAnyException();
        }
    }
}
