package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.MembershipStatus;
import com.campusguinness.identity.internal.domain.SchoolMembership;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.SchoolRole;

import java.time.Instant;
import java.util.UUID;

final class SchoolMembershipPersistenceMapper {
    private SchoolMembershipPersistenceMapper() {}

    static SchoolMembershipEntity toNewEntity(UUID userId, SchoolMembership domain, Instant createdAt) {
        var e = new SchoolMembershipEntity();
        e.setId(domain.id().value());
        e.setUserId(userId);
        e.setSchoolId(domain.schoolId());
        e.setRoleInSchool(domain.role().name());
        e.setStatus(domain.status().name());
        e.setStartedAt(domain.startedAt());
        e.setEndedAt(domain.endedAt());
        e.setCreatedAt(createdAt);
        e.setVersion(domain.version());
        return e;
    }

    static void updateEntity(SchoolMembershipEntity existing, SchoolMembership domain) {
        existing.setStatus(domain.status().name());
        existing.setEndedAt(domain.endedAt());
    }

    static SchoolMembership toDomain(SchoolMembershipEntity entity) {
        return SchoolMembership.reconstitute(
                new SchoolMembershipId(entity.getId()),
                entity.getSchoolId(),
                SchoolRole.valueOf(entity.getRoleInSchool()),
                MembershipStatus.valueOf(entity.getStatus()),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getVersion()
        );
    }
}
