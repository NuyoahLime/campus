package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolMembershipApplicationServiceTest {

    @Mock UserRepository users;
    SchoolMembershipApplicationService service;

    private final Instant startedAt = Instant.parse("2026-08-06T01:00:00Z");

    @BeforeEach
    void setUp() {
        service = new SchoolMembershipApplicationService(users);
    }

    @Test
    void grantStudentUsesFindByIdForUpdateAndSavesUser() {
        var user = normalUser();
        var schoolId = UUID.randomUUID();
        when(users.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));

        var result = service.grantStudent(user.id().value(), schoolId, startedAt);

        assertThat(result.userId()).isEqualTo(user.id().value());
        assertThat(result.schoolId()).isEqualTo(schoolId);
        assertThat(result.roleInSchool()).isEqualTo("STUDENT");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(users).findByIdForUpdate(user.id());
        verify(users).save(user);
    }

    @Test
    void grantSchoolAdminUsesFindByIdForUpdateAndSavesUser() {
        var user = normalUser();
        var schoolId = UUID.randomUUID();
        when(users.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));

        var result = service.grantSchoolAdmin(user.id().value(), schoolId, startedAt);

        assertThat(result.roleInSchool()).isEqualTo("SCHOOL_ADMIN");
        verify(users).findByIdForUpdate(user.id());
        verify(users).save(user);
    }

    @Test
    void endUsesFindByIdForUpdateAndSavesUser() {
        var user = normalUser();
        var schoolId = UUID.randomUUID();
        user.grantStudentMembership(new com.campusguinness.identity.internal.domain.SchoolMembershipId(UUID.randomUUID()),
                schoolId, startedAt);
        when(users.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));

        var result = service.end(user.id().value(), schoolId, startedAt.plusSeconds(60));

        assertThat(result.status()).isEqualTo("ENDED");
        assertThat(result.endedAt()).isEqualTo(startedAt.plusSeconds(60));
        verify(users).save(user);
    }

    @Test
    void userNotFoundFails() {
        when(users.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantStudent(UUID.randomUUID(), UUID.randomUUID(), startedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        verify(users, never()).save(any());
    }

    @Test
    void duplicateActiveMembershipFailsAndDoesNotSaveAgain() {
        var user = normalUser();
        var schoolId = UUID.randomUUID();
        user.grantStudentMembership(new com.campusguinness.identity.internal.domain.SchoolMembershipId(UUID.randomUUID()),
                schoolId, startedAt);
        when(users.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.grantStudent(user.id().value(), schoolId, startedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void nonNormalUserGrantFails() {
        var user = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u"));
        when(users.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.grantStudent(user.id().value(), UUID.randomUUID(), startedAt))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    private User normalUser() {
        var user = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u"));
        user.activate();
        return user;
    }
}
