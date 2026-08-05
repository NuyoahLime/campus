package com.campusguinness.identity.internal.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationMembershipQueryAdapterTest {

    @Mock SchoolMembershipJpaRepository memberships;
    AuthenticationMembershipQueryAdapter adapter;

    private final UUID userId = UUID.randomUUID();
    private final Instant startedAt = Instant.parse("2026-08-06T01:00:00Z");

    @BeforeEach
    void setUp() {
        adapter = new AuthenticationMembershipQueryAdapter(memberships);
    }

    @Test
    void returnsActiveStudentAndSchoolAdminMemberships() {
        var student = membership(UUID.randomUUID(), "STUDENT");
        var admin = membership(UUID.randomUUID(), "SCHOOL_ADMIN");
        when(memberships.findAllByUserIdAndStatusAndRoleInSchoolInOrderByStartedAtAscIdAsc(
                eq(userId), eq("ACTIVE"), eq(List.of("STUDENT", "SCHOOL_ADMIN"))
        )).thenReturn(List.of(student, admin));

        var results = adapter.findActiveByUserId(userId);

        assertThat(results).extracting("membershipId").containsExactly(student.getId(), admin.getId());
        assertThat(results).extracting("schoolId").containsExactly(student.getSchoolId(), admin.getSchoolId());
        assertThat(results).extracting("roleInSchool").containsExactly("STUDENT", "SCHOOL_ADMIN");
    }

    @Test
    void delegatesFilteringOfEndedTeacherAndOtherUsersToRepositoryQuery() {
        adapter.findActiveByUserId(userId);

        verify(memberships).findAllByUserIdAndStatusAndRoleInSchoolInOrderByStartedAtAscIdAsc(
                userId,
                "ACTIVE",
                List.of("STUDENT", "SCHOOL_ADMIN")
        );
    }

    private SchoolMembershipEntity membership(UUID schoolId, String role) {
        var e = new SchoolMembershipEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setSchoolId(schoolId);
        e.setRoleInSchool(role);
        e.setStatus("ACTIVE");
        e.setStartedAt(startedAt);
        e.setCreatedAt(startedAt);
        e.setVersion(1);
        return e;
    }
}
