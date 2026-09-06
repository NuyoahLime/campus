package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.port.L3AuthorizationRepository;
import com.campusguinness.ranking.application.port.L3AuthorizationValidationPort;
import com.campusguinness.ranking.application.result.L3AuthorizationResult;
import com.campusguinness.ranking.internal.domain.AuthorizationStatus;
import com.campusguinness.ranking.internal.domain.L3Authorization;
import com.campusguinness.ranking.internal.domain.L3AuthorizationId;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
@Transactional
public class L3AuthorizationApplicationService {
    private final L3AuthorizationRepository repo;
    private final CurrentActor currentActor;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final PlatformGovernanceAuthorization platformAuthorization;
    private final L3AuthorizationValidationPort validation;

    public L3AuthorizationApplicationService(
            L3AuthorizationRepository repo,
            CurrentActor currentActor,
            SchoolResourceAuthorization schoolAuthorization,
            PlatformGovernanceAuthorization platformAuthorization,
            L3AuthorizationValidationPort validation) {
        this.repo = repo;
        this.currentActor = currentActor;
        this.schoolAuthorization = schoolAuthorization;
        this.platformAuthorization = platformAuthorization;
        this.validation = validation;
    }

    public L3AuthorizationResult create(UUID projectId, UUID ruleVersionId, JsonNode dataScope,
                                        boolean allowSchoolName, boolean allowStudentName) {
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        L3AuthorizationScope scope = normalizeAndValidate(schoolId, projectId, ruleVersionId, dataScope);
        failIfDuplicateActive(schoolId, projectId, ruleVersionId, scope.normalizedJson());
        var authorization = L3Authorization.create(new L3Authorization.Builder()
                .id(new L3AuthorizationId(UUID.randomUUID()))
                .schoolId(schoolId)
                .projectId(projectId)
                .ruleVersionId(ruleVersionId)
                .dataScope(scope.normalizedJson())
                .allowSchoolName(allowSchoolName)
                .allowStudentName(allowStudentName));
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult editDraft(UUID id, JsonNode dataScope, boolean allowSchoolName, boolean allowStudentName) {
        var authorization = findForSchoolUpdate(id);
        L3AuthorizationScope scope = normalizeAndValidate(
                authorization.schoolId(), authorization.projectId(), authorization.ruleVersionId(), dataScope);
        authorization.editDraft(scope.normalizedJson(), allowSchoolName, allowStudentName);
        failIfDuplicateActive(authorization.schoolId(), authorization.projectId(), authorization.ruleVersionId(),
                authorization.dataScope(), authorization.id().value());
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult submit(UUID id) {
        var authorization = findForSchoolUpdate(id);
        authorization.submit();
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult returnToDraft(UUID id) {
        var authorization = findForSchoolUpdate(id);
        authorization.returnToDraft();
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult withdraw(UUID id, String reason) {
        var authorization = findForSchoolUpdate(id);
        authorization.withdraw(reason);
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult approve(UUID id, String comment) {
        UUID reviewerId = platformAuthorization.requireSuperAdmin();
        var authorization = findForUpdate(id);
        validateExistingForApproval(authorization);
        authorization.approve(reviewerId, comment);
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult reject(UUID id, String reason) {
        UUID reviewerId = platformAuthorization.requireSuperAdmin();
        var authorization = findForUpdate(id);
        authorization.reject(reviewerId, reason);
        repo.save(authorization);
        return result(authorization);
    }

    public L3AuthorizationResult resume(UUID id) {
        platformAuthorization.requireSuperAdmin();
        var authorization = findForReviewUpdate(id);
        validateExistingForApproval(authorization);
        authorization.resume();
        repo.save(authorization);
        return result(authorization);
    }

    public void suspendApprovedForSchoolPause(UUID schoolId) {
        var approvals = repo.findBySchoolIdAndStatusesForUpdate(schoolId, EnumSet.of(AuthorizationStatus.APPROVED));
        approvals.forEach(authorization -> {
            authorization.suspend();
            repo.save(authorization);
        });
    }

    public void withdrawNonTerminalForSchoolDisable(UUID schoolId, String reason) {
        var active = repo.findBySchoolIdAndStatusesForUpdate(schoolId, EnumSet.of(
                AuthorizationStatus.DRAFT,
                AuthorizationStatus.PENDING_REVIEW,
                AuthorizationStatus.APPROVED,
                AuthorizationStatus.REJECTED,
                AuthorizationStatus.SUSPENDED));
        active.forEach(authorization -> {
            authorization.withdrawForSchoolDisable(reason);
            repo.save(authorization);
        });
    }

    private L3Authorization findForSchoolUpdate(UUID id) {
        var authorization = findForUpdate(id);
        schoolAuthorization.requireSchoolAdmin(authorization.schoolId());
        return authorization;
    }

    private L3Authorization findForReviewUpdate(UUID id) {
        platformAuthorization.requireSuperAdmin();
        return findForUpdate(id);
    }

    private L3Authorization findForUpdate(UUID id) {
        return repo.findByIdForUpdate(new L3AuthorizationId(id))
                .orElseThrow(() -> new IllegalArgumentException("L3Authorization not found: " + id));
    }

    private L3AuthorizationScope normalizeAndValidate(UUID schoolId, UUID projectId, UUID ruleVersionId, JsonNode dataScope) {
        if (projectId == null || ruleVersionId == null) {
            throw new IllegalArgumentException("projectId and ruleVersionId are required");
        }
        var scope = L3AuthorizationScope.parse(dataScope);
        validation.validateProjectRuleVersion(projectId, ruleVersionId);
        validation.validateSchoolScope(schoolId, projectId, ruleVersionId, scope);
        return scope;
    }

    private void validateExistingForApproval(L3Authorization authorization) {
        L3AuthorizationScope scope = L3AuthorizationScope.parse(authorization.dataScope());
        validation.validateSchoolNormal(authorization.schoolId());
        validation.validateProjectRuleVersion(authorization.projectId(), authorization.ruleVersionId());
        validation.validateSchoolScope(authorization.schoolId(), authorization.projectId(), authorization.ruleVersionId(), scope);
    }

    private void failIfDuplicateActive(UUID schoolId, UUID projectId, UUID ruleVersionId, String dataScope) {
        failIfDuplicateActive(schoolId, projectId, ruleVersionId, dataScope, null);
    }

    private void failIfDuplicateActive(UUID schoolId, UUID projectId, UUID ruleVersionId, String dataScope, UUID currentId) {
        if (currentId != null) {
            if (!repo.existsNonWithdrawnByBusinessKeyExcluding(schoolId, projectId, ruleVersionId, dataScope, currentId)) {
                return;
            }
        } else if (!repo.existsNonWithdrawnByBusinessKey(schoolId, projectId, ruleVersionId, dataScope)) {
            return;
        }
        throw new IllegalStateException("Cannot save L3 authorization: active authorization already exists for this scope.");
    }

    private L3AuthorizationResult result(L3Authorization authorization) {
        return new L3AuthorizationResult(authorization.id().value(), authorization.status().name());
    }
}
