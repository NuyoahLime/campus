package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.MembershipStatus;
import com.campusguinness.identity.internal.domain.SchoolMembership;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.SchoolRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchoolMembershipPersistenceMapper")
class SchoolMembershipPersistenceMapperTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID schoolId = UUID.randomUUID();
    private final SchoolMembershipId membershipId = new SchoolMembershipId(UUID.randomUUID());
    private final Instant startedAt = Instant.parse("2026-08-06T01:00:00Z");
    private final Instant createdAt = Instant.parse("2026-08-06T01:00:05Z");

    @Test
    @DisplayName("maps domain to new entity completely")
    void mapsDomainToNewEntity() {
        var domain = SchoolMembership.start(membershipId, schoolId, SchoolRole.STUDENT, startedAt);

        var entity = SchoolMembershipPersistenceMapper.toNewEntity(userId, domain, createdAt);

        assertThat(entity.getId()).isEqualTo(membershipId.value());
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getSchoolId()).isEqualTo(schoolId);
        assertThat(entity.getRoleInSchool()).isEqualTo("STUDENT");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getStartedAt()).isEqualTo(startedAt);
        assertThat(entity.getEndedAt()).isNull();
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getVersion()).isZero();
    }

    @Test
    @DisplayName("maps entity to domain completely")
    void mapsEntityToDomain() {
        var endedAt = startedAt.plusSeconds(60);
        var entity = entity("TEACHER", "ENDED", endedAt, 9);

        var domain = SchoolMembershipPersistenceMapper.toDomain(entity);

        assertThat(domain.id()).isEqualTo(membershipId);
        assertThat(domain.schoolId()).isEqualTo(schoolId);
        assertThat(domain.role()).isEqualTo(SchoolRole.TEACHER);
        assertThat(domain.status()).isEqualTo(MembershipStatus.ENDED);
        assertThat(domain.startedAt()).isEqualTo(startedAt);
        assertThat(domain.endedAt()).isEqualTo(endedAt);
        assertThat(domain.version()).isEqualTo(9);
    }

    @Test
    @DisplayName("updates existing entity status and endedAt")
    void updatesExistingEntity() {
        var entity = entity("STUDENT", "ACTIVE", null, 3);
        var domain = SchoolMembership.start(membershipId, schoolId, SchoolRole.STUDENT, startedAt);
        var endedAt = startedAt.plusSeconds(60);
        domain.end(endedAt);

        SchoolMembershipPersistenceMapper.updateEntity(entity, domain);

        assertThat(entity.getStatus()).isEqualTo("ENDED");
        assertThat(entity.getEndedAt()).isEqualTo(endedAt);
        assertThat(entity.getRoleInSchool()).isEqualTo("STUDENT");
        assertThat(entity.getVersion()).isEqualTo(3);
    }

    private SchoolMembershipEntity entity(String role, String status, Instant endedAt, int version) {
        var e = new SchoolMembershipEntity();
        e.setId(membershipId.value());
        e.setUserId(userId);
        e.setSchoolId(schoolId);
        e.setRoleInSchool(role);
        e.setStatus(status);
        e.setStartedAt(startedAt);
        e.setEndedAt(endedAt);
        e.setCreatedAt(createdAt);
        e.setVersion(version);
        return e;
    }
}
