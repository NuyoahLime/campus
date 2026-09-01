package com.campusguinness.ranking.application.port;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import java.util.Optional;
public interface RankingDefinitionRepository {
    void save(RankingDefinition r);
    Optional<RankingDefinition> findById(RankingDefinitionId id);
    Optional<RankingDefinition> findByIdForUpdate(RankingDefinitionId id);
}
