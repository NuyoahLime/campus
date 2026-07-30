package com.campusguinness.ranking.application.port;

import com.campusguinness.ranking.application.query.model.CalculatedRankingEntry;
import com.campusguinness.ranking.application.query.model.RankingVersionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RankingPublicationPort {

    int nextVersionNumber(UUID definitionId);

    UUID createPublishedVersion(
            UUID definitionId,
            int versionNumber,
            UUID previousVersionId,
            UUID publishedBy,
            Map<String, Object> calculationParams,
            Map<String, Object> dataScopeSnapshot);

    void saveEntries(UUID versionId, List<CalculatedRankingEntry> entries);

    void markReplaced(UUID versionId);

    Optional<LockedVersion> lockVersion(UUID versionId);

    void withdrawVersion(UUID versionId, UUID withdrawnBy, String reason);

    record LockedVersion(
            UUID versionId,
            RankingVersionStatus status,
            Instant withdrawnAt) {
    }
}
