package com.campusguinness.ranking.internal.persistence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface RankingDefinitionJpaRepository extends JpaRepository<RankingDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from RankingDefinitionEntity d where d.id = :id")
    Optional<RankingDefinitionEntity> findByIdForUpdate(@Param("id") UUID id);
}
