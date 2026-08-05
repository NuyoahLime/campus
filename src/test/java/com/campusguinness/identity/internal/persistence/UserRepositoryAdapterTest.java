package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.MembershipStatus;
import com.campusguinness.identity.internal.domain.SchoolMembership;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.SchoolRole;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock UserJpaRepository jpa;
    @Mock SchoolMembershipJpaRepository membershipJpa;
    UserRepositoryAdapter adapter;

    private final Instant startedAt = Instant.parse("2026-08-06T01:00:00Z");

    @BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(jpa, membershipJpa);
    }

    @Test
    void saveUpdatesWhenFound() {
        var u = user();
        var existing = entity(u.id().value(), "oldname", "NORMAL");
        existing.setPasswordHash("$2a$12$preservedHash");
        existing.setLoginFailures(5);
        when(jpa.findById(u.id().value())).thenReturn(Optional.of(existing));
        when(membershipJpa.findAllByUserIdOrderByStartedAtAsc(u.id().value())).thenReturn(List.of());

        adapter.save(u);

        verify(jpa).save(existing);
        verify(membershipJpa).flush();
        verify(jpa).flush();
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$12$preservedHash");
        assertThat(existing.getLoginFailures()).isEqualTo(5);
    }

    @Test
    void saveExistingUpdatesDomainFieldsOnly() {
        var u = User.reconstitute(
                new User.Builder().id(new UserId(UUID.randomUUID())).username("newname").platformRole("SUPER_ADMIN"),
                AccountStatus.DISABLED, List.of());
        var existing = entity(u.id().value(), "oldname", "NORMAL");
        existing.setPasswordHash("$2a$12$hash");
        existing.setLoginFailures(3);
        when(jpa.findById(u.id().value())).thenReturn(Optional.of(existing));
        when(membershipJpa.findAllByUserIdOrderByStartedAtAsc(u.id().value())).thenReturn(List.of());

        adapter.save(u);

        assertThat(existing.getUsername()).isEqualTo("newname");
        assertThat(existing.getAccountStatus()).isEqualTo("DISABLED");
        assertThat(existing.getPlatformRole()).isEqualTo("SUPER_ADMIN");
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(existing.getLoginFailures()).isEqualTo(3);
    }

    @Test
    void saveRejectsNewEntity() {
        var u = user();
        when(jpa.findById(u.id().value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.save(u))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UserAccountProvisioningPort");
        verify(jpa, never()).save(any());
        verifyNoInteractions(membershipJpa);
    }

    @Test
    void findByIdEmpty() {
        when(jpa.findById(any())).thenReturn(Optional.empty());

        assertThat(adapter.findById(new UserId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void findByIdRestoresMemberships() {
        var e = ent();
        var membership = membership(e.getId(), UUID.randomUUID(), "STUDENT", "ACTIVE", null);
        when(jpa.findById(e.getId())).thenReturn(Optional.of(e));
        when(membershipJpa.findAllByUserIdOrderByStartedAtAsc(e.getId())).thenReturn(List.of(membership));

        var restored = adapter.findById(new UserId(e.getId())).orElseThrow();

        assertThat(restored.domainEvents()).isEmpty();
        assertThat(restored.memberships()).hasSize(1);
        assertThat(restored.memberships().getFirst().id().value()).isEqualTo(membership.getId());
    }

    @Test
    void findByIdForUpdateLocksUserAndRestoresMemberships() {
        var e = ent();
        when(jpa.findByIdForUpdate(e.getId())).thenReturn(Optional.of(e));
        when(membershipJpa.findAllByUserIdOrderByStartedAtAsc(e.getId())).thenReturn(List.of());

        assertThat(adapter.findByIdForUpdate(new UserId(e.getId()))).isPresent();

        verify(jpa).findByIdForUpdate(e.getId());
    }

    @Test
    void saveInsertsNewMembership() {
        var u = normalUser();
        var existing = entity(u.id().value(), "u", "NORMAL");
        var schoolId = UUID.randomUUID();
        var granted = u.grantStudentMembership(new SchoolMembershipId(UUID.randomUUID()), schoolId, startedAt);
        when(jpa.findById(u.id().value())).thenReturn(Optional.of(existing));
        when(membershipJpa.findAllByUserIdOrderByStartedAtAsc(u.id().value())).thenReturn(List.of());

        adapter.save(u);

        ArgumentCaptor<SchoolMembershipEntity> captor = ArgumentCaptor.forClass(SchoolMembershipEntity.class);
        verify(membershipJpa).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(granted.id().value());
        assertThat(captor.getValue().getUserId()).isEqualTo(u.id().value());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void saveUpdatesEndedMembershipWithoutDeletingHistory() {
        var u = normalUser();
        var existingUser = entity(u.id().value(), "u", "NORMAL");
        var schoolId = UUID.randomUUID();
        var granted = u.grantStudentMembership(new SchoolMembershipId(UUID.randomUUID()), schoolId, startedAt);
        var existingMembership = membership(u.id().value(), schoolId, "STUDENT", "ACTIVE", null);
        existingMembership.setId(granted.id().value());
        u.endMembership(schoolId, startedAt.plusSeconds(60));
        when(jpa.findById(u.id().value())).thenReturn(Optional.of(existingUser));
        when(membershipJpa.findAllByUserIdOrderByStartedAtAsc(u.id().value()))
                .thenReturn(List.of(existingMembership));

        adapter.save(u);

        verify(membershipJpa).save(existingMembership);
        verify(membershipJpa, never()).delete(any());
        assertThat(existingMembership.getStatus()).isEqualTo("ENDED");
        assertThat(existingMembership.getEndedAt()).isEqualTo(startedAt.plusSeconds(60));
    }

    @Test
    void existsByUsername() {
        adapter.existsByUsername("u");

        verify(jpa).existsByUsername("u");
    }

    private User user() {
        return User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u"));
    }

    private User normalUser() {
        var user = user();
        user.activate();
        return user;
    }

    private UserEntity entity(UUID id, String username, String status) {
        var e = new UserEntity();
        e.setId(id);
        e.setUsername(username);
        e.setAccountStatus(status);
        e.setPasswordHash("h");
        return e;
    }

    private UserEntity ent() {
        var e = new UserEntity();
        e.setId(UUID.randomUUID());
        e.setUsername("u");
        e.setAccountStatus("PENDING_ACTIVATION");
        e.setPasswordHash("h");
        return e;
    }

    private SchoolMembershipEntity membership(
            UUID userId,
            UUID schoolId,
            String role,
            String status,
            Instant endedAt
    ) {
        var e = new SchoolMembershipEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setSchoolId(schoolId);
        e.setRoleInSchool(role);
        e.setStatus(status);
        e.setStartedAt(startedAt);
        e.setEndedAt(endedAt);
        e.setCreatedAt(startedAt);
        e.setVersion(1);
        return e;
    }
}
