package com.campusguinness.school.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
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
    private final PlatformGovernanceAuthorization authorization;

    public SchoolRegistrationApplicationService(
            SchoolRegistrationRepository repository,
            PlatformGovernanceAuthorization authorization
    ) {
        this.repository = repository;
        this.authorization = authorization;
    }

    public SchoolRegistrationResult submit(SubmitSchoolRegistrationCommand cmd) {
        var reg = SchoolRegistration.create(buildBuilder(cmd));
        reg.submit();
        repository.save(reg);
        return new SchoolRegistrationResult(reg.id().value(), cmd.schoolName(), reg.status().name(), null);
    }

    public SchoolRegistrationResult approve(UUID registrationId, String comment, UUID schoolId) {
        UUID actorUserId = authorization.requireSuperAdmin();
        var reg = find(registrationId);
        reg.approve(actorUserId, comment, schoolId);
        repository.save(reg);
        return new SchoolRegistrationResult(reg.id().value(), reg.schoolName(), reg.status().name(), schoolId);
    }

    public SchoolRegistrationResult reject(UUID registrationId, String reason) {
        UUID actorUserId = authorization.requireSuperAdmin();
        var reg = find(registrationId);
        reg.reject(actorUserId, reason);
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
