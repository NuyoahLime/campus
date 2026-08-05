package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchoolAdminInvitationPersistenceMapper")
class SchoolAdminInvitationPersistenceMapperTest {

    @Nested
    @DisplayName("Domain to Entity")
    class ToEntity {
        @Test
        @DisplayName("maps bound user, school, role, hashed code, and status")
        void mapsDomain() {
            var invitation = invitation();

            var entity = SchoolAdminInvitationPersistenceMapper.toEntity(invitation);

            assertThat(entity.getId()).isEqualTo(invitation.id().value());
            assertThat(entity.getRoleInSchool()).isEqualTo("SCHOOL_ADMIN");
            assertThat(entity.getInvitationCodeHash()).isEqualTo("$2a$10$hash");
            assertThat(entity.getInvitationStatus()).isEqualTo("PENDING");
            assertThat(entity.getMaxAttempts()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Entity to Domain")
    class ToDomain {
        @Test
        @DisplayName("restores ACCEPTED with accepted time")
        void restoresAccepted() {
            var acceptedAt = Instant.parse("2026-08-05T10:15:30Z");
            var entity = entity("ACCEPTED");
            entity.setAcceptedAt(acceptedAt);

            var domain = SchoolAdminInvitationPersistenceMapper.toDomain(entity);

            assertThat(domain.status()).isEqualTo(SchoolAdminInvitationStatus.ACCEPTED);
            assertThat(domain.acceptedAt()).isEqualTo(acceptedAt);
            assertThat(domain.roleInSchool()).isEqualTo("SCHOOL_ADMIN");
        }
    }

    private SchoolAdminInvitation invitation() {
        return SchoolAdminInvitation.create(new SchoolAdminInvitation.Builder()
                .id(new SchoolAdminInvitationId(UUID.randomUUID()))
                .userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .invitationCodeHash("$2a$10$hash")
                .expiresAt(Instant.parse("2026-08-06T00:00:00Z"))
                .createdBy(UUID.randomUUID()));
    }

    private SchoolAdminInvitationEntity entity(String status) {
        var entity = new SchoolAdminInvitationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());
        entity.setSchoolId(UUID.randomUUID());
        entity.setRoleInSchool("SCHOOL_ADMIN");
        entity.setInvitationCodeHash("$2a$10$hash");
        entity.setInvitationStatus(status);
        entity.setExpiresAt(Instant.parse("2026-08-06T00:00:00Z"));
        entity.setCreatedBy(UUID.randomUUID());
        entity.setFailedAttempts(0);
        entity.setMaxAttempts(5);
        return entity;
    }
}
