package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.internal.domain.*;
import java.time.Instant;

final class SchoolPersistenceMapper {
    private SchoolPersistenceMapper() {}

    static SchoolEntity toEntity(School domain) {
        SchoolEntity e = new SchoolEntity();
        e.setId(domain.id().value());
        updateEntity(e, domain);
        e.setCreatedAt(Instant.now());
        return e;
    }

    static void updateEntity(SchoolEntity e, School domain) {
        e.setName(domain.name());
        e.setUnifiedCodeType(domain.unifiedCodeType());
        e.setUnifiedCode(domain.unifiedCode());
        e.setInternalCode(domain.internalCode());
        e.setSchoolType(domain.schoolType());
        e.setRegion(domain.region());
        e.setAddress(domain.address());
        e.setContactName(domain.contactName());
        e.setContactPhone(domain.contactPhone());
        e.setContactEmail(domain.contactEmail());
        e.setSchoolStatus(domain.status().name());
        e.setUpdatedAt(Instant.now());
    }

    static School toDomain(SchoolEntity e) {
        return School.reconstitute(new School.Builder()
                .id(new SchoolId(e.getId())).name(e.getName())
                .unifiedCodeType(e.getUnifiedCodeType()).unifiedCode(e.getUnifiedCode())
                .internalCode(e.getInternalCode()).schoolType(e.getSchoolType())
                .region(e.getRegion()).address(e.getAddress())
                .contactName(e.getContactName()).contactPhone(e.getContactPhone())
                .contactEmail(e.getContactEmail())
                .status(SchoolStatus.valueOf(e.getSchoolStatus())));
    }
}
