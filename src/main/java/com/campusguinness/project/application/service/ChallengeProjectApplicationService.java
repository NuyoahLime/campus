package com.campusguinness.project.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.command.CreateChallengeProjectCommand;
import com.campusguinness.project.application.command.UpdateChallengeProjectCommand;
import com.campusguinness.project.application.exception.ChallengeProjectNotFoundException;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionSnapshot;
import com.campusguinness.project.application.result.ChallengeProjectResult;
import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import com.campusguinness.project.internal.domain.ProjectCategory;
import com.campusguinness.project.internal.domain.ProjectName;
import com.campusguinness.project.internal.domain.ProjectStatus;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.project.internal.domain.ScoreIndicatorType;
import com.campusguinness.project.internal.domain.ScoreStorageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ChallengeProjectApplicationService {

    private final ChallengeProjectRepository repository;
    private final ProjectRuleVersionRepository ruleVersions;
    private final PlatformGovernanceAuthorization authorization;
    private final AuditRecordCommandPort audit;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChallengeProjectApplicationService(
            ChallengeProjectRepository repository,
            ProjectRuleVersionRepository ruleVersions,
            PlatformGovernanceAuthorization authorization,
            AuditRecordCommandPort audit,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.ruleVersions = ruleVersions;
        this.authorization = authorization;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** Compatibility constructor for domain-focused unit tests. */
    public ChallengeProjectApplicationService(ChallengeProjectRepository repository) {
        this(repository, null, null, null, null);
    }

    public ChallengeProjectResult create(CreateChallengeProjectCommand cmd) {
        requireGovernance();
        ChallengeProjectId id = new ChallengeProjectId(UUID.randomUUID());
        ScoreConfig scoreConfig = scoreConfig(cmd.scoreStorageType(), cmd.scoreIndicatorType(),
                cmd.comparisonDirection(), cmd.effectiveScoreRule(), cmd.allowTie(),
                cmd.scoreUnit(), cmd.decimalPlaces(), cmd.gradeOrder(), cmd.rulesText());
        ChallengeProject project = ChallengeProject.create(id, new ProjectName(cmd.name()),
                new ProjectCategory(cmd.category()), scoreConfig, cmd.description(),
                cmd.venueRequirements(), cmd.equipmentRequirements());
        repository.save(project);
        return new ChallengeProjectResult(id.value(), project.name().value(), project.status().name());
    }

    @Transactional(readOnly = true)
    public ChallengeProject findById(UUID id) {
        return repository.findById(new ChallengeProjectId(id))
                .orElseThrow(() -> new ChallengeProjectNotFoundException("ChallengeProject not found: " + id));
    }

    public ChallengeProjectResult publish(UUID id, String reason) {
        UUID actorId = requireGovernance();
        String normalizedReason = normalizeReason(reason);
        ChallengeProject project = findById(id);
        ProjectStatus oldStatus = project.status();
        project.publish();
        repository.save(project);
        if (ruleVersions != null) {
            if (project.currentRuleVersionId() == null) {
                project.assignCurrentRuleVersion(createRuleVersion(project, actorId, normalizedReason));
                repository.save(project);
            }
            recordAudit(project, actorId,
                    oldStatus == ProjectStatus.ARCHIVED ? "PROJECT_REPUBLISH" : "PROJECT_PUBLISH",
                    oldStatus, ProjectStatus.PUBLISHED, normalizedReason);
        }
        return new ChallengeProjectResult(id, project.name().value(), project.status().name());
    }

    public ChallengeProjectResult archive(UUID id, String reason) {
        UUID actorId = requireGovernance();
        String normalizedReason = normalizeReason(reason);
        ChallengeProject project = findById(id);
        ProjectStatus oldStatus = project.status();
        project.archive();
        repository.save(project);
        recordAudit(project, actorId, "PROJECT_ARCHIVE", oldStatus, ProjectStatus.ARCHIVED, normalizedReason);
        return new ChallengeProjectResult(id, project.name().value(), project.status().name());
    }

    public ChallengeProjectResult update(UUID id, UpdateChallengeProjectCommand cmd) {
        UUID actorId = requireGovernance();
        ChallengeProject project = findById(id);
        ScoreConfig scoreConfig = scoreConfig(cmd.scoreStorageType(), cmd.scoreIndicatorType(),
                cmd.comparisonDirection(), cmd.effectiveScoreRule(), cmd.allowTie(),
                cmd.scoreUnit(), cmd.decimalPlaces(), cmd.gradeOrder(), cmd.rulesText());
        boolean rulesChanged = project.updateDetails(new ProjectName(cmd.name()),
                new ProjectCategory(cmd.category()), scoreConfig, cmd.description(),
                cmd.venueRequirements(), cmd.equipmentRequirements());
        if (rulesChanged && ruleVersions != null
                && (project.status() == ProjectStatus.PUBLISHED || project.status() == ProjectStatus.ARCHIVED)) {
            repository.save(project);
            project.assignCurrentRuleVersion(createRuleVersion(project, actorId, "Project rules updated."));
        }
        repository.save(project);
        if (rulesChanged && ruleVersions != null) {
            recordAudit(project, actorId, "PROJECT_RULE_VERSION_CREATED",
                    project.status(), project.status(), "Project rules updated.");
        }
        return new ChallengeProjectResult(id, project.name().value(), project.status().name());
    }

    private ScoreConfig scoreConfig(String storageType, String indicatorType,
                                    String direction, String effectiveRule, boolean allowTie,
                                    String scoreUnit, Integer decimalPlaces, String gradeOrder,
                                    String rulesText) {
        return new ScoreConfig(ScoreStorageType.valueOf(storageType),
                ScoreIndicatorType.valueOf(indicatorType), ComparisonDirection.valueOf(direction),
                scoreUnit, decimalPlaces, effectiveRule, gradeOrder, rulesText, allowTie);
    }

    private UUID createRuleVersion(ChallengeProject project, UUID actorId, String reason) {
        UUID versionId = UUID.randomUUID();
        ruleVersions.save(new ProjectRuleVersionSnapshot(versionId, project.id().value(),
                ruleVersions.nextVersionNumber(project.id().value()), project.scoreConfig(),
                project.venueRequirements(), project.equipmentRequirements(), reason,
                actorId, Instant.now()));
        return versionId;
    }

    private UUID requireGovernance() {
        return authorization == null ? null : authorization.requireSuperAdmin();
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 2 || normalized.length() > 500) {
            throw new IllegalArgumentException("Project lifecycle reason must contain between 2 and 500 characters.");
        }
        return normalized;
    }

    private void recordAudit(ChallengeProject project, UUID actorId, String action,
                             ProjectStatus oldStatus, ProjectStatus newStatus, String reason) {
        if (audit == null) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", project.id().value());
        detail.put("oldStatus", oldStatus.name());
        detail.put("newStatus", newStatus.name());
        detail.put("reason", reason);
        detail.put("currentRuleVersionId", project.currentRuleVersionId());
        audit.record(new AuditRecordCommand(UUID.randomUUID(), null, actorId, action,
                "CHALLENGE_PROJECT", project.id().value(), writeDetail(detail), Instant.now()));
    }

    private String writeDetail(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Project audit detail could not be serialized.", ex);
        }
    }
}
