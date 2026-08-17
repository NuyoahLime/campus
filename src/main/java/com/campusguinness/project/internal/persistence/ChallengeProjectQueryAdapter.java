package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.query.model.ChallengeProjectDetailResult;
import com.campusguinness.project.application.query.model.ChallengeProjectGovernanceListResult;
import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ChallengeProjectQueryAdapter implements ChallengeProjectQueryPort {

    private final ChallengeProjectJpaRepository projects;
    private final ProjectRuleVersionJpaRepository ruleVersions;

    ChallengeProjectQueryAdapter(ChallengeProjectJpaRepository projects,
                                 ProjectRuleVersionJpaRepository ruleVersions) {
        this.projects = projects;
        this.ruleVersions = ruleVersions;
    }

    @Override
    public QueryPage<ChallengeProjectListResult> findPublished(int page, int size,
                                                                 String category, String query) {
        var result = projects.findAll(filters("PUBLISHED", category, query), pageable(page, size));
        var items = result.getContent().stream()
                .map(e -> new ChallengeProjectListResult(e.getId(), e.getName(), e.getCategory(),
                        e.getScoreStorageType(), e.getComparisonDirection(), e.getProjectStatus(), e.getCreatedAt()))
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<ChallengeProjectDetailResult> findPublishedById(UUID id) {
        return projects.findByIdAndProjectStatus(id, "PUBLISHED")
                .map(e -> detail(e, ruleVersionNumber(e.getCurrentRuleVersionId())));
    }

    @Override
    public QueryPage<ChallengeProjectGovernanceListResult> findGovernance(
            int page, int size, String status, String category, String query) {
        var result = projects.findAll(filters(status, category, query), pageable(page, size));
        var items = result.getContent().stream()
                .map(e -> new ChallengeProjectGovernanceListResult(e.getId(), e.getName(), e.getCategory(),
                        e.getProjectStatus(), e.getScoreStorageType(), e.getScoreIndicatorType(),
                        e.getComparisonDirection(), e.getScoreUnit(), ruleVersionNumber(e.getCurrentRuleVersionId()),
                        e.getCreatedAt(), e.getUpdatedAt()))
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<ChallengeProjectDetailResult> findGovernanceById(UUID id) {
        return projects.findById(id).map(e -> detail(e, ruleVersionNumber(e.getCurrentRuleVersionId())));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    }

    private Specification<ChallengeProjectEntity> filters(String status, String category, String query) {
        Specification<ChallengeProjectEntity> specification = (root, criteriaQuery, builder) ->
                builder.conjunction();
        if (status != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.<String>get("projectStatus"), status));
        }
        if (category != null) {
            String normalizedCategory = category.toLowerCase(Locale.ROOT);
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(builder.lower(root.<String>get("category")), normalizedCategory));
        }
        if (query != null) {
            String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> builder.or(
                    builder.like(builder.lower(root.<String>get("name")), pattern, '\\'),
                    builder.like(builder.lower(builder.coalesce(
                            root.<String>get("description"), "")), pattern, '\\')
            ));
        }
        return specification;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private ChallengeProjectDetailResult detail(ChallengeProjectEntity e, Integer versionNumber) {
        return new ChallengeProjectDetailResult(e.getId(), e.getName(), e.getCategory(),
                e.getDescription(), e.getVenueRequirements(), e.getEquipmentRequirements(),
                e.getRulesText(), e.getScoreStorageType(), e.getScoreIndicatorType(),
                e.getComparisonDirection(), e.getScoreUnit(), e.getDecimalPlaces(),
                e.getGradeOrder(), e.isAllowTie(), e.getEffectiveScoreRule(),
                e.getProjectStatus(), e.getCurrentRuleVersionId(), versionNumber,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private Integer ruleVersionNumber(UUID id) {
        return id == null ? null : ruleVersions.findById(id).map(ProjectRuleVersionEntity::getVersionNumber).orElse(null);
    }
}
