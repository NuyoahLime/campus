package com.campusguinness.school.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.school.application.exception.SchoolRegistrationReviewException;
import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.internal.domain.School;
import com.campusguinness.school.internal.domain.SchoolId;
import com.campusguinness.school.internal.domain.SchoolRegistration;
import com.campusguinness.school.internal.domain.SchoolRegistrationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SchoolRegistrationReviewApplicationService {

    private final SchoolRegistrationRepository registrations;
    private final SchoolRepository schools;
    private final PlatformGovernanceAuthorization authorization;

    public SchoolRegistrationReviewApplicationService(
            SchoolRegistrationRepository registrations,
            SchoolRepository schools,
            PlatformGovernanceAuthorization authorization
    ) {
        this.registrations = registrations;
        this.schools = schools;
        this.authorization = authorization;
    }

    public SchoolRegistrationResult requestSupplement(UUID registrationId, String comment) {
        UUID reviewerId = authorization.requireSuperAdmin();
        SchoolRegistration registration = find(registrationId);
        registration.requestSupplement(reviewerId, comment);
        registrations.save(registration);
        return result(registration);
    }

    public SchoolRegistrationResult approve(UUID registrationId, String comment) {
        UUID reviewerId = authorization.requireSuperAdmin();
        SchoolRegistration registration = find(registrationId);
        ensureUniqueSchoolIdentity(registration);

        SchoolId schoolId = new SchoolId(UUID.randomUUID());
        School school = School.create(new School.Builder()
                .id(schoolId)
                .name(registration.schoolName())
                .unifiedCodeType(registration.unifiedCodeType())
                .unifiedCode(normalize(registration.unifiedCode()))
                .internalCode(schoolId.value().toString().replace("-", ""))
                .schoolType(registration.schoolType())
                .region(registration.region())
                .address(registration.address())
                .contactName(registration.contactName())
                .contactPhone(registration.contactPhone())
                .contactEmail(registration.contactEmail()));

        registration.approve(reviewerId, comment, schoolId.value());
        schools.save(school);
        registrations.save(registration);
        return result(registration);
    }

    public SchoolRegistrationResult reject(UUID registrationId, String reason) {
        UUID reviewerId = authorization.requireSuperAdmin();
        SchoolRegistration registration = find(registrationId);
        registration.reject(reviewerId, reason);
        registrations.save(registration);
        return result(registration);
    }

    private SchoolRegistration find(UUID id) {
        return registrations.findById(new SchoolRegistrationId(id))
                .orElseThrow(() -> new SchoolRegistrationReviewException(
                        "SCHOOL_REGISTRATION_NOT_FOUND",
                        "School registration not found: " + id
                ));
    }

    private void ensureUniqueSchoolIdentity(SchoolRegistration registration) {
        String unifiedCode = normalize(registration.unifiedCode());
        if (unifiedCode != null
                && schools.existsByUnifiedCode(registration.unifiedCodeType(), unifiedCode)) {
            throw new SchoolRegistrationReviewException(
                    "SCHOOL_UNIFIED_CODE_CONFLICT",
                    "A school with the same unified code already exists."
            );
        }
    }

    private SchoolRegistrationResult result(SchoolRegistration registration) {
        return new SchoolRegistrationResult(
                registration.id().value(), registration.schoolName(),
                registration.status().name(), registration.createdSchoolId()
        );
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
