package com.campusguinness.school.application.query;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolAdminAccountResult;
import com.campusguinness.school.application.query.model.SchoolAdminInvitationQueryResult;
import com.campusguinness.school.application.query.model.SchoolGovernanceDetailResult;
import com.campusguinness.school.application.query.model.SchoolGovernanceListResult;
import com.campusguinness.school.application.query.port.SchoolAdminGovernanceQueryPort;
import com.campusguinness.school.internal.domain.SchoolStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SchoolAdminGovernanceQueryService {

    private static final int MAX_SEARCH_LENGTH = 200;
    private static final Set<String> INVITATION_STATUSES = Set.of(
            "PENDING", "ACCEPTED", "REVOKED", "EXPIRED"
    );

    private final SchoolAdminGovernanceQueryPort queryPort;
    private final PlatformGovernanceAuthorization authorization;

    public SchoolAdminGovernanceQueryService(
            SchoolAdminGovernanceQueryPort queryPort,
            PlatformGovernanceAuthorization authorization
    ) {
        this.queryPort = queryPort;
        this.authorization = authorization;
    }

    public QueryPage<SchoolGovernanceListResult> listSchools(
            int page,
            int size,
            String status,
            String search
    ) {
        authorization.requireSuperAdmin();
        validatePage(page, size);
        return queryPort.findSchools(normalizeSchoolStatus(status), normalizeSearch(search), page, size);
    }

    public SchoolGovernanceDetailResult schoolDetail(UUID schoolId) {
        authorization.requireSuperAdmin();
        UUID requiredSchoolId = requireId(schoolId, "schoolId");
        return queryPort.findSchool(requiredSchoolId)
                .orElseThrow(this::schoolNotFound);
    }

    public List<SchoolAdminAccountResult> listSchoolAdmins(UUID schoolId) {
        authorization.requireSuperAdmin();
        UUID requiredSchoolId = requireId(schoolId, "schoolId");
        requireSchool(requiredSchoolId);
        return queryPort.findSchoolAdmins(requiredSchoolId);
    }

    public QueryPage<SchoolAdminInvitationQueryResult> listInvitations(
            UUID schoolId,
            int page,
            int size,
            String status
    ) {
        authorization.requireSuperAdmin();
        UUID requiredSchoolId = requireId(schoolId, "schoolId");
        validatePage(page, size);
        requireSchool(requiredSchoolId);
        return queryPort.findInvitations(requiredSchoolId, normalizeInvitationStatus(status), page, size);
    }

    public SchoolAdminInvitationQueryResult invitationDetail(UUID schoolId, UUID invitationId) {
        authorization.requireSuperAdmin();
        UUID requiredSchoolId = requireId(schoolId, "schoolId");
        UUID requiredInvitationId = requireId(invitationId, "invitationId");
        return queryPort.findInvitation(requiredSchoolId, requiredInvitationId)
                .orElseThrow(() -> new IdentityApplicationException(
                        "INVITATION_NOT_FOUND",
                        "Invitation not found."
                ));
    }

    private void requireSchool(UUID schoolId) {
        if (queryPort.findSchool(schoolId).isEmpty()) {
            throw schoolNotFound();
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private String normalizeSchoolStatus(String status) {
        return normalizeEnum(status, SchoolStatus.class, "school status");
    }

    private String normalizeInvitationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!INVITATION_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("invitation status is invalid");
        }
        return normalized;
    }

    private <E extends Enum<E>> String normalizeEnum(String value, Class<E> type, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String normalized = search.trim();
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("q max 200 chars");
        }
        return normalized;
    }

    private UUID requireId(UUID id, String label) {
        if (id == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return id;
    }

    private IdentityApplicationException schoolNotFound() {
        return new IdentityApplicationException("SCHOOL_NOT_FOUND", "School not found.");
    }
}
