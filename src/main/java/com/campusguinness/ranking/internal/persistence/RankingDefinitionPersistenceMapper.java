package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.internal.domain.*;
import java.time.Instant;

final class RankingDefinitionPersistenceMapper {
    private RankingDefinitionPersistenceMapper() {}

    static RankingDefinitionEntity toEntity(RankingDefinition domain) {
        var e = new RankingDefinitionEntity();
        e.setId(domain.id().value()); e.setLayer(domain.layer().name());
        e.setName(domain.name()); e.setSchoolId(domain.schoolId());
        e.setProjectId(domain.projectId()); e.setDimensionFilters(domain.dimensionFilters());
        e.setTieBreakRule(domain.tieBreakRule()); e.setEnabled(domain.isEnabled());
        e.setCurrentVersionId(domain.currentVersionId()); e.setCreatedBy(domain.createdBy());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static RankingDefinition toDomain(RankingDefinitionEntity e) {
        return RankingDefinition.reconstitute(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(e.getId())).layer(RankingLayer.valueOf(e.getLayer()))
                .name(e.getName()).schoolId(e.getSchoolId()).projectId(e.getProjectId())
                .dimensionFilters(e.getDimensionFilters()).tieBreakRule(e.getTieBreakRule())
                .createdBy(e.getCreatedBy()),
                e.isEnabled(), e.getCurrentVersionId());
    }
}
