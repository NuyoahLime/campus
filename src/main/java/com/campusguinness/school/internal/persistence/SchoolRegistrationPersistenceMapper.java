package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.internal.domain.*;
import java.time.Instant;

final class SchoolRegistrationPersistenceMapper {
    private SchoolRegistrationPersistenceMapper() {}

    static SchoolRegistrationEntity toEntity(SchoolRegistration domain) {
        SchoolRegistrationEntity e = new SchoolRegistrationEntity();
        e.setId(domain.id().value());
        e.setSchoolName(domain.schoolName());
        e.setUnifiedCodeType(domain.unifiedCodeType());
        e.setUnifiedCode(domain.unifiedCode());
        e.setSchoolType(domain.schoolType());
        e.setRegion(domain.region());
        e.setAddress(domain.address());
        e.setContactName(domain.contactName());
        e.setContactPhone(domain.contactPhone());
        e.setContactEmail(domain.contactEmail());
        e.setDescription(domain.description());
        e.setEvidenceFileKey(domain.evidenceFileKey());
        e.setRegistrationStatus(domain.status().name());
        e.setCreatedSchoolId(domain.createdSchoolId());
        e.setReviewedBy(domain.reviewedBy());
        e.setReviewComment(domain.reviewComment());
        e.setRejectReason(domain.rejectReason());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    static SchoolRegistration toDomain(SchoolRegistrationEntity e) {
        return SchoolRegistration.reconstitute(new SchoolRegistration.Builder()
                .id(new SchoolRegistrationId(e.getId()))
                .schoolName(e.getSchoolName()).unifiedCodeType(e.getUnifiedCodeType())
                .unifiedCode(e.getUnifiedCode()).schoolType(e.getSchoolType())
                .region(e.getRegion()).address(e.getAddress())
                .contactName(e.getContactName()).contactPhone(e.getContactPhone())
                .contactEmail(e.getContactEmail())
                .description(e.getDescription()).evidenceFileKey(e.getEvidenceFileKey())
                .status(RegistrationStatus.valueOf(e.getRegistrationStatus()))
                .createdSchoolId(e.getCreatedSchoolId())
                .reviewedBy(e.getReviewedBy())
                .reviewComment(e.getReviewComment())
                .rejectReason(e.getRejectReason()));
    }
}
