package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchoolAdminInvitationRepositoryAdapter")
class SchoolAdminInvitationRepositoryAdapterTest {

    @Mock private SchoolAdminInvitationJpaRepository jpaRepository;
    @InjectMocks private SchoolAdminInvitationRepositoryAdapter adapter;

    @Nested
    @DisplayName("save")
    class Save {
        @Test
        @DisplayName("saves domain via JpaRepository")
        void saves() {
            adapter.save(invitation());

            verify(jpaRepository).save(any(SchoolAdminInvitationEntity.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("returns empty when not found")
        void returnsEmpty() {
            when(jpaRepository.findById(any())).thenReturn(Optional.empty());

            assertThat(adapter.findById(new SchoolAdminInvitationId(UUID.randomUUID()))).isEmpty();
        }

        @Test
        @DisplayName("restores domain when found")
        void restoresDomain() {
            var id = UUID.randomUUID();
            var entity = entity(id, "REVOKED");
            when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));

            var restored = adapter.findById(new SchoolAdminInvitationId(id));

            assertThat(restored).isPresent();
            assertThat(restored.get().status()).isEqualTo(SchoolAdminInvitationStatus.REVOKED);
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

    private SchoolAdminInvitationEntity entity(UUID id, String status) {
        var entity = new SchoolAdminInvitationEntity();
        entity.setId(id);
        entity.setUserId(UUID.randomUUID());
        entity.setSchoolId(UUID.randomUUID());
        entity.setRoleInSchool("SCHOOL_ADMIN");
        entity.setInvitationCodeHash("$2a$10$hash");
        entity.setInvitationStatus(status);
        entity.setExpiresAt(Instant.parse("2026-08-06T00:00:00Z"));
        entity.setCreatedBy(UUID.randomUUID());
        entity.setRevokedAt(Instant.now());
        entity.setFailedAttempts(0);
        entity.setMaxAttempts(5);
        return entity;
    }
}
