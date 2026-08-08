package com.campusguinness.ranking.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
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
    private final CurrentActor currentActor;

    public RankingDefinitionApplicationService(RankingDefinitionRepository r, CurrentActor currentActor) {
        this.repo = r;
        this.currentActor = currentActor;
    }

    public RankingDefinitionResult create(RankingLayer layer, String name, UUID schoolId, UUID projectId) {
        UUID actorUserId = currentActor.requireUserId();
        var r = RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID())).layer(layer).name(name)
                .schoolId(schoolId).projectId(projectId).createdBy(actorUserId));
        repo.save(r);
        return new RankingDefinitionResult(r.id().value(), r.isEnabled());
    }
    public RankingDefinitionResult disable(UUID id) { var r = find(id); r.disable(); repo.save(r); return new RankingDefinitionResult(id, r.isEnabled()); }
    public RankingDefinitionResult enable(UUID id) { var r = find(id); r.enable(); repo.save(r); return new RankingDefinitionResult(id, r.isEnabled()); }
    private RankingDefinition find(UUID id) { return repo.findById(new RankingDefinitionId(id)).orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + id)); }
}
