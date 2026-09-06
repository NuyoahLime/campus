package com.campusguinness.school.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.ranking.application.service.L3AuthorizationApplicationService;
import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.application.query.port.SchoolAdminGovernanceQueryPort;
import com.campusguinness.school.application.result.SchoolResult;
import com.campusguinness.school.internal.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class SchoolApplicationService {

    private final SchoolRepository repository;
    private final SchoolAdminGovernanceQueryPort governanceQueries;
    private final PlatformGovernanceAuthorization authorization;
    private final L3AuthorizationApplicationService l3Authorizations;
    private final AuditRecordCommandPort audit;
    private final ObjectMapper objectMapper;

    public SchoolApplicationService(
            SchoolRepository repository,
            SchoolAdminGovernanceQueryPort governanceQueries,
            PlatformGovernanceAuthorization authorization,
            L3AuthorizationApplicationService l3Authorizations,
            AuditRecordCommandPort audit,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.governanceQueries = governanceQueries;
        this.authorization = authorization;
        this.l3Authorizations = l3Authorizations;
        this.audit = audit;
        this.objectMapper = objectMapper;
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

    public SchoolResult activate(UUID id, String reason) {
        return transition(id, reason, LifecycleAction.ACTIVATE);
    }

    public SchoolResult disable(UUID id, String reason) {
        return transition(id, reason, LifecycleAction.DISABLE);
    }

    public SchoolResult suspend(UUID id, String reason) {
        return transition(id, reason, LifecycleAction.SUSPEND);
    }

    public SchoolResult restore(UUID id, String reason) {
        return transition(id, reason, LifecycleAction.RESTORE);
    }

    public SchoolResult reEnable(UUID id, String reason) {
        return transition(id, reason, LifecycleAction.REENABLE);
    }

    @Transactional(readOnly = true)
    public School findById(UUID id) {
        authorization.requireSuperAdmin();
        return find(id);
    }

    private School find(UUID id) {
        return repository.findById(new SchoolId(id))
                .orElseThrow(this::schoolNotFound);
    }

    private SchoolResult transition(UUID id, String reason, LifecycleAction action) {
        UUID actorId = authorization.requireSuperAdmin();
        if (id == null) {
            throw new IllegalArgumentException("schoolId is required");
        }
        String normalizedReason = normalizeReason(reason);
        Instant now = Instant.now();

        try {
            var school = repository.findByIdForUpdate(new SchoolId(id))
                    .orElseThrow(this::schoolNotFound);
            SchoolStatus oldStatus = school.status();
            applyTransition(school, action, normalizedReason);

            if (action.requiresTwoActiveAdmins()) {
                long activeAdminCount = governanceQueries.findSchool(id)
                        .orElseThrow(this::schoolNotFound)
                        .normalActiveSchoolAdminCount();
                if (activeAdminCount < 2) {
                    throw error(
                            "SCHOOL_ADMIN_CONFIGURATION_INSUFFICIENT",
                            "School requires at least two active school administrators before activation."
                    );
                }
            }

            repository.save(school);
            coordinateL3AuthorizationLifecycle(school, action, normalizedReason);
            audit.record(auditCommand(
                    school,
                    actorId,
                    action,
                    oldStatus,
                    school.status(),
                    normalizedReason,
                    now
            ));
            return new SchoolResult(id, school.name(), school.status().name());
        } catch (InvalidSchoolStateTransitionException ex) {
            throw error("INVALID_SCHOOL_STATE_TRANSITION", ex.getMessage());
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException ex) {
            throw error("SCHOOL_LIFECYCLE_CONFLICT", "School lifecycle update conflict.");
        }
    }

    private void coordinateL3AuthorizationLifecycle(School school, LifecycleAction action, String reason) {
        switch (action) {
            case SUSPEND -> l3Authorizations.suspendApprovedForSchoolPause(school.id().value());
            case DISABLE -> l3Authorizations.withdrawNonTerminalForSchoolDisable(school.id().value(), reason);
            case RESTORE, ACTIVATE, REENABLE -> {
            }
        }
    }

    private void applyTransition(School school, LifecycleAction action, String reason) {
        switch (action) {
            case ACTIVATE -> school.activate();
            case SUSPEND -> school.suspend(reason);
            case RESTORE -> school.restore();
            case DISABLE -> school.disable(reason);
            case REENABLE -> school.reEnable();
        }
    }

    private String normalizeReason(String reason) {
        String normalized = reason != null ? reason.trim() : "";
        if (normalized.length() < 2 || normalized.length() > 500) {
            throw error(
                    "SCHOOL_LIFECYCLE_REASON_INVALID",
                    "School lifecycle reason must contain between 2 and 500 characters."
            );
        }
        return normalized;
    }

    private AuditRecordCommand auditCommand(
            School school,
            UUID actorId,
            LifecycleAction action,
            SchoolStatus oldStatus,
            SchoolStatus newStatus,
            String reason,
            Instant occurredAt
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetSchoolId", school.id().value());
        detail.put("oldStatus", oldStatus.name());
        detail.put("newStatus", newStatus.name());
        detail.put("reason", reason);
        return new AuditRecordCommand(
                UUID.randomUUID(),
                school.id().value(),
                actorId,
                action.auditAction,
                "SCHOOL",
                school.id().value(),
                writeDetail(detail),
                occurredAt
        );
    }

    private String writeDetail(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("School lifecycle audit detail could not be serialized.", ex);
        }
    }

    private IdentityApplicationException schoolNotFound() {
        return error("SCHOOL_NOT_FOUND", "School not found.");
    }

    private IdentityApplicationException error(String code, String message) {
        return new IdentityApplicationException(code, message);
    }

    private enum LifecycleAction {
        ACTIVATE("SCHOOL_ACTIVATE", true),
        SUSPEND("SCHOOL_SUSPEND", false),
        RESTORE("SCHOOL_RESTORE", true),
        DISABLE("SCHOOL_DISABLE", false),
        REENABLE("SCHOOL_REENABLE", false);

        private final String auditAction;
        private final boolean requiresTwoActiveAdmins;

        LifecycleAction(String auditAction, boolean requiresTwoActiveAdmins) {
            this.auditAction = auditAction;
            this.requiresTwoActiveAdmins = requiresTwoActiveAdmins;
        }

        private boolean requiresTwoActiveAdmins() {
            return requiresTwoActiveAdmins;
        }
    }
}
