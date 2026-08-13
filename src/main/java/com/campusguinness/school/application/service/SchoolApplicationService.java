package com.campusguinness.school.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.application.result.SchoolResult;
import com.campusguinness.school.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class SchoolApplicationService {

    private final SchoolRepository repository;
    private final PlatformGovernanceAuthorization authorization;

    public SchoolApplicationService(
            SchoolRepository repository,
            PlatformGovernanceAuthorization authorization
    ) {
        this.repository = repository;
        this.authorization = authorization;
    }

    public SchoolResult create(String name, String unifiedCodeType, String unifiedCode,
                               String internalCode, String schoolType, String region,
                               String address, String contactName, String contactPhone,
                               String contactEmail) {
        authorization.requireSuperAdmin();
        var school = School.create(new School.Builder()
                .id(new SchoolId(UUID.randomUUID())).name(name)
                .unifiedCodeType(unifiedCodeType).unifiedCode(unifiedCode)
                .internalCode(internalCode).schoolType(schoolType)
                .region(region).address(address)
                .contactName(contactName).contactPhone(contactPhone).contactEmail(contactEmail));
        repository.save(school);
        return new SchoolResult(school.id().value(), name, school.status().name());
    }

    public SchoolResult activate(UUID id) {
        authorization.requireSuperAdmin();
        var s = find(id); s.activate(); repository.save(s);
        return new SchoolResult(id, s.name(), s.status().name());
    }

    public SchoolResult disable(UUID id, String reason) {
        authorization.requireSuperAdmin();
        var s = find(id); s.disable(reason); repository.save(s);
        return new SchoolResult(id, s.name(), s.status().name());
    }

    @Transactional(readOnly = true)
    public School findById(UUID id) {
        authorization.requireSuperAdmin();
        return find(id);
    }

    private School find(UUID id) {
        return repository.findById(new SchoolId(id))
                .orElseThrow(() -> new IllegalArgumentException("School not found: " + id));
    }
}
