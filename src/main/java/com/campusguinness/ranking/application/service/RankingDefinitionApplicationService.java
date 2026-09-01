package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class RankingDefinitionApplicationService {
    private final RankingDefinitionRepository repo;
    private final SchoolResourceAuthorization authorization;

    public RankingDefinitionApplicationService(RankingDefinitionRepository r, SchoolResourceAuthorization authorization) {
        this.repo = r;
        this.authorization = authorization;
    }

    public RankingDefinitionResult create(RankingLayer layer, String name, UUID schoolId, UUID projectId) {
        return create(layer, name, schoolId, projectId, null);
    }

    public RankingDefinitionResult create(RankingLayer layer, String name, UUID schoolId, UUID projectId, UUID activityProjectId) {
        if (layer != RankingLayer.L1) {
            throw new IllegalStateException("Cannot create ranking: Phase1 supports only L1 definitions.");
        }
        if (activityProjectId == null) {
            throw new IllegalStateException("Cannot create ranking: activityProjectId is required.");
        }
        UUID authoritativeSchoolId = authorization.requireUniqueSchoolAdminSchool();
        if (schoolId != null && !schoolId.equals(authoritativeSchoolId)) {
            throw new IdentityApplicationException("SCHOOL_ADMIN_SCOPE_DENIED", "School administration scope denied.");
        }
        UUID actorUserId = authorization.requireSchoolAdmin(authoritativeSchoolId);
        var r = RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID())).layer(layer).name(name)
                .schoolId(authoritativeSchoolId).projectId(projectId)
                .dimensionFilters("{\"activityProjectId\":\"" + activityProjectId + "\"}")
                .createdBy(actorUserId));
        repo.save(r);
        return new RankingDefinitionResult(r.id().value(), r.isEnabled());
    }
    public RankingDefinitionResult disable(UUID id) {
        var r = find(id);
        if (r.schoolId() != null) authorization.requireSchoolAdmin(r.schoolId());
        r.disable();
        repo.save(r);
        return new RankingDefinitionResult(id, r.isEnabled());
    }
    public RankingDefinitionResult enable(UUID id) {
        var r = find(id);
        if (r.schoolId() != null) authorization.requireSchoolAdmin(r.schoolId());
        r.enable();
        repo.save(r);
        return new RankingDefinitionResult(id, r.isEnabled());
    }
    private RankingDefinition find(UUID id) { return repo.findById(new RankingDefinitionId(id)).orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + id)); }
}
