package com.campusguinness.school.application.service;

import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class SchoolRegistrationApplicationService {

    private final SchoolRegistrationRepository repository;
    private final SchoolMembershipResolver membershipResolver;

    public SchoolRegistrationApplicationService(SchoolRegistrationRepository repository,
                                                 SchoolMembershipResolver membershipResolver) {
        this.repository = repository;
        this.membershipResolver = membershipResolver;
    }

    public SchoolRegistrationResult submit(SubmitSchoolRegistrationCommand cmd) {
        var reg = SchoolRegistration.create(buildBuilder(cmd));
        reg.submit();
        repository.save(reg);
        return new SchoolRegistrationResult(reg.id().value(), cmd.schoolName(), reg.status().name(), null);
    }

    public SchoolRegistrationResult approve(UUID registrationId, UUID reviewerId, String comment, UUID schoolId) {
        var reg = find(registrationId);
        UUID realSchoolId = reg.createdSchoolId();
        AuthorizationPolicy.requireSchoolAdmin(membershipResolver, reviewerId, realSchoolId);
        reg.approve(reviewerId, comment, schoolId);
        repository.save(reg);
        return new SchoolRegistrationResult(reg.id().value(), reg.schoolName(), reg.status().name(), schoolId);
    }

    public SchoolRegistrationResult reject(UUID registrationId, UUID reviewerId, String reason) {
        var reg = find(registrationId);
        UUID realSchoolId = reg.createdSchoolId();
        AuthorizationPolicy.requireSchoolAdmin(membershipResolver, reviewerId, realSchoolId);
        reg.reject(reviewerId, reason);
        repository.save(reg);
        return new SchoolRegistrationResult(reg.id().value(), reg.schoolName(), reg.status().name(), null);
    }

    public SchoolRegistrationResult withdraw(UUID registrationId) {
        var reg = find(registrationId);
        reg.withdraw();
        repository.save(reg);
        return new SchoolRegistrationResult(reg.id().value(), reg.schoolName(), reg.status().name(), null);
    }

    private SchoolRegistration find(UUID id) {
        return repository.findById(new SchoolRegistrationId(id))
                .orElseThrow(() -> new IllegalArgumentException("SchoolRegistration not found: " + id));
    }

    private SchoolRegistration.Builder buildBuilder(SubmitSchoolRegistrationCommand cmd) {
        return new SchoolRegistration.Builder()
                .id(new SchoolRegistrationId(UUID.randomUUID())).schoolName(cmd.schoolName())
                .unifiedCodeType(cmd.unifiedCodeType()).unifiedCode(cmd.unifiedCode())
                .schoolType(cmd.schoolType()).region(cmd.region()).address(cmd.address())
                .contactName(cmd.contactName()).contactPhone(cmd.contactPhone())
                .contactEmail(cmd.contactEmail()).description(cmd.description())
                .evidenceFileKey(cmd.evidenceFileKey());
    }
}
