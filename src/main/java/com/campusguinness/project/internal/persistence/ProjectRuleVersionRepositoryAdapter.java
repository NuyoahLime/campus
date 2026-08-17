package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.port.ProjectRuleVersionRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionSnapshot;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.project.internal.domain.ScoreIndicatorType;
import com.campusguinness.project.internal.domain.ScoreStorageType;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
class ProjectRuleVersionRepositoryAdapter implements ProjectRuleVersionRepository {

    private final ProjectRuleVersionJpaRepository jpa;

    ProjectRuleVersionRepositoryAdapter(ProjectRuleVersionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public ProjectRuleVersionSnapshot save(ProjectRuleVersionSnapshot snapshot) {
        var entity = new ProjectRuleVersionEntity();
        entity.setId(snapshot.id());
        entity.setProjectId(snapshot.projectId());
        entity.setVersionNumber(snapshot.versionNumber());
        entity.setScoreStorageType(snapshot.scoreConfig().storageType().name());
        entity.setScoreIndicatorType(snapshot.scoreConfig().indicatorType().name());
        entity.setComparisonDirection(snapshot.scoreConfig().comparisonDirection().name());
        entity.setScoreUnit(snapshot.scoreConfig().scoreUnit());
        entity.setDecimalPlaces(snapshot.scoreConfig().decimalPlaces());
        entity.setGradeOrder(snapshot.scoreConfig().gradeOrder());
        entity.setRulesText(snapshot.scoreConfig().rulesText());
        entity.setVenueRequirements(snapshot.venueRequirements());
        entity.setEquipmentRequirements(snapshot.equipmentRequirements());
        entity.setAllowTie(snapshot.scoreConfig().allowTie());
        entity.setEffectiveScoreRule(snapshot.scoreConfig().effectiveScoreRule());
        entity.setChangeReason(snapshot.changeReason());
        entity.setCreatedBy(snapshot.createdBy());
        entity.setCreatedAt(snapshot.createdAt());
        jpa.save(entity);
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public int nextVersionNumber(UUID projectId) {
        return jpa.findTopByProjectIdOrderByVersionNumberDesc(projectId)
                .map(e -> e.getVersionNumber() + 1)
                .orElse(1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectRuleVersionSnapshot> findAllByProjectId(UUID projectId) {
        return jpa.findAllByProjectIdOrderByVersionNumberAsc(projectId).stream()
                .map(ProjectRuleVersionRepositoryAdapter::toSnapshot)
                .toList();
    }

    private static ProjectRuleVersionSnapshot toSnapshot(ProjectRuleVersionEntity entity) {
        var scoreConfig = new ScoreConfig(
                ScoreStorageType.valueOf(entity.getScoreStorageType()),
                ScoreIndicatorType.valueOf(entity.getScoreIndicatorType()),
                ComparisonDirection.valueOf(entity.getComparisonDirection()),
                entity.getScoreUnit(), entity.getDecimalPlaces(),
                entity.getEffectiveScoreRule(), entity.getGradeOrder(),
                entity.getRulesText(), entity.isAllowTie());
        return new ProjectRuleVersionSnapshot(entity.getId(), entity.getProjectId(),
                entity.getVersionNumber(), scoreConfig, entity.getVenueRequirements(),
                entity.getEquipmentRequirements(), entity.getChangeReason(),
                entity.getCreatedBy(), entity.getCreatedAt());
    }
}
