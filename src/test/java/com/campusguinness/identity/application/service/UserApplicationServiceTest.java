package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.exception.UsernameAlreadyExistsException;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.port.UserSessionRevocationPort;
import com.campusguinness.identity.internal.domain.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {
    @Mock UserRepository repo;
    @Mock UserAccountProvisioningPort provisioning;
    @Mock PasswordHasher hasher;
    @Mock UserSessionRevocationPort sessionRevocation;
    @Mock com.campusguinness.infrastructure.security.LoginNameNormalizer normalizer;
    UserApplicationService svc;

    @BeforeEach void setUp() {
        svc = new UserApplicationService(repo, provisioning, hasher, sessionRevocation, normalizer);
        lenient().when(normalizer.normalize(anyString())).thenAnswer(inv -> ((String) inv.getArgument(0)).trim());
    }

    private User user() { return User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u")); }

    @Nested class Create {
        @Test void success() {
            when(repo.existsByUsername("testuser")).thenReturn(false);
            when(hasher.hash("password123")).thenReturn("$2a$12$hashed");
            var u = user();
            when(provisioning.create(any(User.class), eq("$2a$12$hashed"))).thenReturn(u);

            var result = svc.create("testuser", "password123");
            assertThat(result.status()).isEqualTo("PENDING_ACTIVATION");
            verify(provisioning).create(any(User.class), eq("$2a$12$hashed"));
        }

        @Test void trimsUsername() {
            when(repo.existsByUsername("testuser")).thenReturn(false);
            when(hasher.hash(anyString())).thenReturn("$2a$12$hash");
            var u = user();
            when(provisioning.create(any(), any())).thenReturn(u);

            svc.create("  testuser  ", "password123");
            verify(repo).existsByUsername("testuser");
        }

        @Test void rejectsDuplicate() {
            when(repo.existsByUsername("testuser")).thenReturn(true);
            assertThatThrownBy(() -> svc.create("testuser", "password123"))
                    .isInstanceOf(UsernameAlreadyExistsException.class);
            verify(provisioning, never()).create(any(), any());
        }

        @Test void newUserHasNoPlatformRole() {
            when(repo.existsByUsername("testuser")).thenReturn(false);
            when(hasher.hash(anyString())).thenReturn("$2a$12$hash");
            when(provisioning.create(any(User.class), any())).thenAnswer(inv -> inv.getArgument(0));
            var result = svc.create("testuser", "password123");
            // Created user has no platform role
            verify(provisioning).create(argThat(u -> u.platformRole() == null), any());
        }

        @Test void doesNotCallGenericRepositorySave() {
            when(repo.existsByUsername("testuser")).thenReturn(false);
            when(hasher.hash(anyString())).thenReturn("$2a$12$hash");
            var u = user();
            when(provisioning.create(any(User.class), any())).thenReturn(u);

            svc.create("testuser", "password123");
            verify(repo, never()).save(any());
        }
    }

    @Nested class PasswordValidation {
        @Test void rejectsNull() {
            assertThatThrownBy(() -> svc.create("u", null))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_BLANK");
        }

        @Test void rejectsBlank() {
            assertThatThrownBy(() -> svc.create("u", "   "))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_BLANK");
        }

        @Test void rejectsTooShort() {
            assertThatThrownBy(() -> svc.create("u", "short"))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_TOO_SHORT");
        }

        @Test void accepts8Chars() {
            when(repo.existsByUsername(anyString())).thenReturn(false);
            when(hasher.hash(anyString())).thenReturn("$2a$12$hash");
            var u = user();
            when(provisioning.create(any(), any())).thenReturn(u);
            assertThatCode(() -> svc.create("u", "12345678")).doesNotThrowAnyException();
        }

        @Test void rejectsOver72Utf8Bytes() {
            // 73 ASCII chars = 73 bytes > 72
            var longPw = "a".repeat(73);
            assertThatThrownBy(() -> svc.create("u", longPw))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("PASSWORD_TOO_LONG");
        }

        @Test void doesNotTrimPassword() {
            // " password " with spaces is a valid 10-char password
            when(repo.existsByUsername(anyString())).thenReturn(false);
            when(hasher.hash(" password ")).thenReturn("$2a$12$hash");
            var u = user();
            when(provisioning.create(any(), any())).thenReturn(u);

            assertThatCode(() -> svc.create("u", " password ")).doesNotThrowAnyException();
        }
    }

    @Nested class Activate { @Test void success() { var u=user(); when(repo.findById(any())).thenReturn(Optional.of(u)); assertThat(svc.activate(u.id().value()).status()).isEqualTo("NORMAL"); verify(repo).save(any()); } @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.activate(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); } }
    @Nested class Disable {
        @Test void success() {
            var u=user(); u.activate(); when(repo.findById(any())).thenReturn(Optional.of(u));
            assertThat(svc.disable(u.id().value()).status()).isEqualTo("DISABLED");
            verify(repo).save(any());
            verify(sessionRevocation).revokeAllSessions(u.username());
        }
        @Test void revokesSessionsBeforeReturning() {
            var u=user(); u.activate(); when(repo.findById(any())).thenReturn(Optional.of(u));
            doThrow(new RuntimeException("session error")).when(sessionRevocation).revokeAllSessions(any());
            // Session revocation failure must not silently hide the disable result
            assertThatThrownBy(() -> svc.disable(u.id().value()))
                    .isInstanceOf(RuntimeException.class);
            // The user must still have been saved
            verify(repo).save(any());
        }
    }
    @Nested class ReEnable {
        @Test void success() {
            var u=user(); u.activate(); u.disable(); when(repo.findById(any())).thenReturn(Optional.of(u));
            assertThat(svc.reEnable(u.id().value()).status()).isEqualTo("NORMAL");
            verify(repo).save(any());
            verifyNoInteractions(sessionRevocation);
        }
    }
}
