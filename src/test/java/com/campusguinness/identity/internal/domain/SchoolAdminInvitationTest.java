package com.campusguinness.identity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SchoolAdminInvitation aggregate")
class SchoolAdminInvitationTest {

    private SchoolAdminInvitation.Builder validBuilder() {
        return new SchoolAdminInvitation.Builder()
                .id(new SchoolAdminInvitationId(UUID.randomUUID()))
                .userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .invitationCodeHash("$2a$10$hash")
                .expiresAt(Instant.parse("2026-08-06T00:00:00Z"))
                .createdBy(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Creation")
    class Creation {
        @Test
        @DisplayName("creates a pending SCHOOL_ADMIN invitation")
        void createsPendingInvitation() {
            var invitation = SchoolAdminInvitation.create(validBuilder());

            assertThat(invitation.status()).isEqualTo(SchoolAdminInvitationStatus.PENDING);
            assertThat(invitation.roleInSchool()).isEqualTo("SCHOOL_ADMIN");
            assertThat(invitation.failedAttempts()).isZero();
            assertThat(invitation.maxAttempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("requires hashed code, user, school, creator, and expiry")
        void rejectsMissingFields() {
            assertThatThrownBy(() -> SchoolAdminInvitation.create(validBuilder().invitationCodeHash(" ")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SchoolAdminInvitation.create(validBuilder().userId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SchoolAdminInvitation.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SchoolAdminInvitation.create(validBuilder().createdBy(null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SchoolAdminInvitation.create(validBuilder().expiresAt(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {
        @Test
        @DisplayName("PENDING to ACCEPTED records accepted time")
        void acceptsPendingInvitation() {
            var acceptedAt = Instant.parse("2026-08-05T10:15:30Z");
            var invitation = SchoolAdminInvitation.create(validBuilder());

            invitation.accept(acceptedAt);

            assertThat(invitation.status()).isEqualTo(SchoolAdminInvitationStatus.ACCEPTED);
            assertThat(invitation.acceptedAt()).isEqualTo(acceptedAt);
            assertThat(invitation.revokedAt()).isNull();
        }

        @Test
        @DisplayName("PENDING to REVOKED records revoked time")
        void revokesPendingInvitation() {
            var revokedAt = Instant.parse("2026-08-05T10:15:30Z");
            var invitation = SchoolAdminInvitation.create(validBuilder());

            invitation.revoke(revokedAt);

            assertThat(invitation.status()).isEqualTo(SchoolAdminInvitationStatus.REVOKED);
            assertThat(invitation.revokedAt()).isEqualTo(revokedAt);
        }

        @Test
        @DisplayName("PENDING to EXPIRED")
        void expiresPendingInvitation() {
            var invitation = SchoolAdminInvitation.create(validBuilder());

            invitation.expire();

            assertThat(invitation.status()).isEqualTo(SchoolAdminInvitationStatus.EXPIRED);
        }

        @Test
        @DisplayName("failed attempts revoke invitation at maxAttempts")
        void failedAttemptsRevokeAtLimit() {
            var now = Instant.parse("2026-08-05T10:15:30Z");
            var invitation = SchoolAdminInvitation.create(validBuilder().maxAttempts(2));

            invitation.recordFailedAttempt(now);
            assertThat(invitation.status()).isEqualTo(SchoolAdminInvitationStatus.PENDING);
            invitation.recordFailedAttempt(now.plusSeconds(1));

            assertThat(invitation.failedAttempts()).isEqualTo(2);
            assertThat(invitation.status()).isEqualTo(SchoolAdminInvitationStatus.REVOKED);
            assertThat(invitation.revokedAt()).isEqualTo(now.plusSeconds(1));
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test
        @DisplayName("ACCEPTED is terminal")
        void acceptedIsTerminal() {
            var invitation = SchoolAdminInvitation.create(validBuilder());
            invitation.accept(Instant.now());

            assertThatThrownBy(() -> invitation.revoke(Instant.now()))
                    .isInstanceOf(InvalidSchoolAdminInvitationStateTransitionException.class);
        }

        @Test
        @DisplayName("REVOKED is terminal")
        void revokedIsTerminal() {
            var invitation = SchoolAdminInvitation.create(validBuilder());
            invitation.revoke(Instant.now());

            assertThatThrownBy(() -> invitation.accept(Instant.now()))
                    .isInstanceOf(InvalidSchoolAdminInvitationStateTransitionException.class);
        }
    }
}
