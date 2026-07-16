package com.campusguinness.ranking.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface RankingDefinitionJpaRepository extends JpaRepository<RankingDefinitionEntity, UUID> { }
