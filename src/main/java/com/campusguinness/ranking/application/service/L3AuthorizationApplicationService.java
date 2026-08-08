package com.campusguinness.ranking.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.port.L3AuthorizationRepository;
import com.campusguinness.ranking.application.result.L3AuthorizationResult;
import com.campusguinness.ranking.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class L3AuthorizationApplicationService {
    private final L3AuthorizationRepository repo;
    private final CurrentActor currentActor;
    private final SchoolResourceAuthorization authorization;

    public L3AuthorizationApplicationService(L3AuthorizationRepository r, CurrentActor currentActor,
            SchoolResourceAuthorization authorization) {
        this.repo = r;
        this.currentActor = currentActor;
        this.authorization = authorization;
    }

    public L3AuthorizationResult submit(UUID schoolId, UUID projectId, UUID ruleVersionId) {
        authorization.requireSchoolAdmin(schoolId);
        var a = L3Authorization.create(new L3Authorization.Builder()
                .id(new L3AuthorizationId(UUID.randomUUID())).schoolId(schoolId)
                .projectId(projectId).ruleVersionId(ruleVersionId));
        a.submit(); repo.save(a);
        return new L3AuthorizationResult(a.id().value(), a.status().name());
    }
    public L3AuthorizationResult approve(UUID id, String comment) {
        UUID actorUserId = currentActor.requireUserId();
        var a = find(id); a.approve(actorUserId, comment); repo.save(a);
        return new L3AuthorizationResult(id, a.status().name());
    }
    public L3AuthorizationResult withdraw(UUID id, String reason) {
        var a = find(id);
        authorization.requireSchoolAdmin(a.schoolId());
        a.withdraw(reason);
        repo.save(a);
        return new L3AuthorizationResult(id, a.status().name());
    }
    private L3Authorization find(UUID id) { return repo.findById(new L3AuthorizationId(id)).orElseThrow(() -> new IllegalArgumentException("L3Authorization not found: " + id)); }
}
