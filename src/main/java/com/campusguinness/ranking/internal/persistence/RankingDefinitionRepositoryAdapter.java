package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class RankingDefinitionRepositoryAdapter implements RankingDefinitionRepository {
    private final RankingDefinitionJpaRepository jpa;
    RankingDefinitionRepositoryAdapter(RankingDefinitionJpaRepository r) { this.jpa = r; }
    @Override @Transactional public void save(RankingDefinition r) { jpa.save(RankingDefinitionPersistenceMapper.toEntity(r)); }
    @Override @Transactional(readOnly = true) public Optional<RankingDefinition> findById(RankingDefinitionId id) {
        return jpa.findById(id.value()).map(RankingDefinitionPersistenceMapper::toDomain);
    }
}
