package com.campusguinness.identity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SchoolMembership child entity")
class SchoolMembershipTest {

    private final Instant startedAt = Instant.parse("2026-08-06T01:00:00Z");

    @Test
    @DisplayName("starts a STUDENT membership as ACTIVE")
    void startsStudentMembership() {
        var membership = SchoolMembership.start(id(), UUID.randomUUID(), SchoolRole.STUDENT, startedAt);

        assertThat(membership.role()).isEqualTo(SchoolRole.STUDENT);
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.endedAt()).isNull();
    }

    @Test
    @DisplayName("starts a SCHOOL_ADMIN membership as ACTIVE")
    void startsSchoolAdminMembership() {
        var membership = SchoolMembership.start(id(), UUID.randomUUID(), SchoolRole.SCHOOL_ADMIN, startedAt);

        assertThat(membership.role()).isEqualTo(SchoolRole.SCHOOL_ADMIN);
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("does not start a new TEACHER membership")
    void rejectsNewTeacherMembership() {
        assertThatThrownBy(() -> SchoolMembership.start(id(), UUID.randomUUID(), SchoolRole.TEACHER, startedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEACHER");
    }

    @Test
    @DisplayName("reconstitutes historical TEACHER membership")
    void reconstitutesTeacherMembership() {
        var endedAt = startedAt.plusSeconds(3600);
        var membership = SchoolMembership.reconstitute(
                id(),
                UUID.randomUUID(),
                SchoolRole.TEACHER,
                MembershipStatus.ENDED,
                startedAt,
                endedAt,
                7
        );

        assertThat(membership.role()).isEqualTo(SchoolRole.TEACHER);
        assertThat(membership.version()).isEqualTo(7);
        assertThat(membership.startedAt()).isEqualTo(startedAt);
        assertThat(membership.endedAt()).isEqualTo(endedAt);
    }

    @Test
    @DisplayName("ACTIVE membership can be ended")
    void endsActiveMembership() {
        var endedAt = startedAt.plusSeconds(60);
        var membership = SchoolMembership.start(id(), UUID.randomUUID(), SchoolRole.STUDENT, startedAt);

        membership.end(endedAt);

        assertThat(membership.status()).isEqualTo(MembershipStatus.ENDED);
        assertThat(membership.endedAt()).isEqualTo(endedAt);
    }

    @Test
    @DisplayName("end time cannot be before start time")
    void rejectsEndBeforeStart() {
        var membership = SchoolMembership.start(id(), UUID.randomUUID(), SchoolRole.STUDENT, startedAt);

        assertThatThrownBy(() -> membership.end(startedAt.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ENDED membership cannot be ended again")
    void rejectsDoubleEnd() {
        var membership = SchoolMembership.start(id(), UUID.randomUUID(), SchoolRole.STUDENT, startedAt);
        membership.end(startedAt.plusSeconds(60));

        assertThatThrownBy(() -> membership.end(startedAt.plusSeconds(120)))
                .isInstanceOf(InvalidSchoolMembershipStateTransitionException.class);
    }

    private SchoolMembershipId id() {
        return new SchoolMembershipId(UUID.randomUUID());
    }
}
