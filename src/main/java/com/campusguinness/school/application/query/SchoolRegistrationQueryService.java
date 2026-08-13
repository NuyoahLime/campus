package com.campusguinness.school.application.query;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.exception.SchoolRegistrationNotFoundException;
import com.campusguinness.school.application.query.model.SchoolRegistrationDetailResult;
import com.campusguinness.school.application.query.model.SchoolRegistrationListResult;
import com.campusguinness.school.application.query.port.SchoolRegistrationQueryPort;
import com.campusguinness.school.internal.domain.RegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SchoolRegistrationQueryService {

    private final SchoolRegistrationQueryPort queryPort;
    private final PlatformGovernanceAuthorization authorization;

    public SchoolRegistrationQueryService(
            SchoolRegistrationQueryPort queryPort,
            PlatformGovernanceAuthorization authorization
    ) {
        this.queryPort = queryPort;
        this.authorization = authorization;
    }

    public QueryPage<SchoolRegistrationListResult> list(int page, int size, String status) {
        authorization.requireSuperAdmin();
        validatePage(page, size);
        return queryPort.findAll(normalizeStatus(status), page, size);
    }

    public SchoolRegistrationDetailResult detail(UUID registrationId) {
        authorization.requireSuperAdmin();
        if (registrationId == null) {
            throw new IllegalArgumentException("registrationId is required");
        }
        return queryPort.findById(registrationId)
                .orElseThrow(() -> new SchoolRegistrationNotFoundException(registrationId));
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            return RegistrationStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(RegistrationStatus.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("status must be one of: " + allowed);
        }
    }
}
