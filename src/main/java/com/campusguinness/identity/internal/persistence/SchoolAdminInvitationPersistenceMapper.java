package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationStatus;

import java.time.Instant;

final class SchoolAdminInvitationPersistenceMapper {
    private SchoolAdminInvitationPersistenceMapper() {}

    static SchoolAdminInvitationEntity toEntity(SchoolAdminInvitation domain) {
        var e = new SchoolAdminInvitationEntity();
        e.setId(domain.id().value());
        e.setUserId(domain.userId());
        e.setSchoolId(domain.schoolId());
        e.setRoleInSchool(domain.roleInSchool());
        e.setInvitationCodeHash(domain.invitationCodeHash());
        e.setInvitationStatus(domain.status().name());
        e.setExpiresAt(domain.expiresAt());
        e.setAcceptedAt(domain.acceptedAt());
        e.setRevokedAt(domain.revokedAt());
        e.setCreatedBy(domain.createdBy());
        e.setFailedAttempts(domain.failedAttempts());
        e.setMaxAttempts(domain.maxAttempts());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    static SchoolAdminInvitation toDomain(SchoolAdminInvitationEntity e) {
        return SchoolAdminInvitation.reconstitute(new SchoolAdminInvitation.Builder()
                .id(new SchoolAdminInvitationId(e.getId()))
                .userId(e.getUserId())
                .schoolId(e.getSchoolId())
                .invitationCodeHash(e.getInvitationCodeHash())
                .expiresAt(e.getExpiresAt())
                .createdBy(e.getCreatedBy())
                .status(SchoolAdminInvitationStatus.valueOf(e.getInvitationStatus()))
                .acceptedAt(e.getAcceptedAt())
                .revokedAt(e.getRevokedAt())
                .failedAttempts(e.getFailedAttempts())
                .maxAttempts(e.getMaxAttempts()));
    }
}
