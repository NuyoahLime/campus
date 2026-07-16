package com.campusguinness.ranking.application.service;

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
    public L3AuthorizationApplicationService(L3AuthorizationRepository r) { this.repo = r; }

    public L3AuthorizationResult submit(UUID schoolId, UUID projectId, UUID ruleVersionId) {
        var a = L3Authorization.create(new L3Authorization.Builder()
                .id(new L3AuthorizationId(UUID.randomUUID())).schoolId(schoolId)
                .projectId(projectId).ruleVersionId(ruleVersionId));
        a.submit(); repo.save(a);
        return new L3AuthorizationResult(a.id().value(), a.status().name());
    }
    public L3AuthorizationResult approve(UUID id, UUID reviewerId, String comment) {
        var a = find(id); a.approve(reviewerId, comment); repo.save(a);
        return new L3AuthorizationResult(id, a.status().name());
    }
    public L3AuthorizationResult withdraw(UUID id, String reason) {
        var a = find(id); a.withdraw(reason); repo.save(a);
        return new L3AuthorizationResult(id, a.status().name());
    }
    private L3Authorization find(UUID id) { return repo.findById(new L3AuthorizationId(id)).orElseThrow(() -> new IllegalArgumentException("L3Authorization not found: " + id)); }
}
