package com.campusguinness.project.application.query;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.ChallengeProjectDetailResult;
import com.campusguinness.project.application.query.model.ChallengeProjectGovernanceListResult;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;
import com.campusguinness.project.application.port.ProjectRuleVersionRepository;
import com.campusguinness.project.application.query.model.ProjectRuleVersionResult;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChallengeProjectQueryService {
    private final ChallengeProjectQueryPort queryPort;
    private final PlatformGovernanceAuthorization authorization;
    private final ProjectRuleVersionRepository ruleVersions;

    @Autowired
    public ChallengeProjectQueryService(ChallengeProjectQueryPort p,
                                        PlatformGovernanceAuthorization authorization,
                                        ProjectRuleVersionRepository ruleVersions) {
        this.queryPort = p;
        this.authorization = authorization;
        this.ruleVersions = ruleVersions;
    }

    public ChallengeProjectQueryService(ChallengeProjectQueryPort p,
                                        PlatformGovernanceAuthorization authorization) {
        this(p, authorization, null);
    }

    public ChallengeProjectQueryService(ChallengeProjectQueryPort p) {
        this(p, null);
    }

    public QueryPage<ChallengeProjectListResult> listPublic(int page, int size) {
        return listPublic(page, size, null, null);
    }

    public QueryPage<ChallengeProjectListResult> listPublic(int page, int size, String category, String query) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        String normalizedCategory = normalize(category);
        String normalizedQuery = normalize(query);
        return normalizedCategory == null && normalizedQuery == null
                ? queryPort.findPublished(page, size)
                : queryPort.findPublished(page, size, normalizedCategory, normalizedQuery);
    }

    public ChallengeProjectDetailResult publicDetail(UUID id) {
        return queryPort.findPublishedById(id)
                .orElseThrow(() -> new IllegalArgumentException("ChallengeProject not found: " + id));
    }

    public QueryPage<ChallengeProjectGovernanceListResult> listGovernance(
            int page, int size, String status, String category, String query) {
        authorization.requireSuperAdmin();
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findGovernance(page, size, normalize(status), normalize(category), normalize(query));
    }

    public ChallengeProjectDetailResult governanceDetail(UUID id) {
        authorization.requireSuperAdmin();
        return queryPort.findGovernanceById(id)
                .orElseThrow(() -> new IllegalArgumentException("ChallengeProject not found: " + id));
    }

    public java.util.List<ProjectRuleVersionResult> ruleVersions(UUID id) {
        authorization.requireSuperAdmin();
        if (ruleVersions == null) return java.util.List.of();
        return ruleVersions.findAllByProjectId(id).stream()
                .map(value -> new ProjectRuleVersionResult(value.id(), value.versionNumber(),
                        value.scoreConfig().storageType().name(), value.scoreConfig().indicatorType().name(),
                        value.scoreConfig().comparisonDirection().name(), value.scoreConfig().scoreUnit(),
                        value.scoreConfig().decimalPlaces(), value.scoreConfig().gradeOrder(),
                        value.scoreConfig().allowTie(), value.scoreConfig().effectiveScoreRule(),
                        value.scoreConfig().rulesText(), value.venueRequirements(),
                        value.equipmentRequirements(), value.changeReason(), value.createdBy(),
                        value.createdAt()))
                .toList();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
