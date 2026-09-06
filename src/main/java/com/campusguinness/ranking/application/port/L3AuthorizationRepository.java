package com.campusguinness.ranking.application.port;

import com.campusguinness.ranking.internal.domain.AuthorizationStatus;
import com.campusguinness.ranking.internal.domain.L3Authorization;
import com.campusguinness.ranking.internal.domain.L3AuthorizationId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface L3AuthorizationRepository {
    void save(L3Authorization a);

    Optional<L3Authorization> findById(L3AuthorizationId id);

    Optional<L3Authorization> findByIdForUpdate(L3AuthorizationId id);

    List<L3Authorization> findBySchoolIdAndStatusesForUpdate(UUID schoolId, Collection<AuthorizationStatus> statuses);

    boolean existsNonWithdrawnByBusinessKey(UUID schoolId, UUID projectId, UUID ruleVersionId, String dataScope);

    boolean existsNonWithdrawnByBusinessKeyExcluding(
            UUID schoolId, UUID projectId, UUID ruleVersionId, String dataScope, UUID excludedId);
}
