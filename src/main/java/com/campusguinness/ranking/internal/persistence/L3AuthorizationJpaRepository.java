package com.campusguinness.ranking.internal.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface L3AuthorizationJpaRepository extends JpaRepository<L3AuthorizationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from L3AuthorizationEntity a where a.id = :id")
    Optional<L3AuthorizationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from L3AuthorizationEntity a
            where a.schoolId = :schoolId and a.authorizationStatus in :statuses
            order by a.createdAt asc, a.id asc
            """)
    List<L3AuthorizationEntity> findBySchoolIdAndAuthorizationStatusInForUpdate(
            @Param("schoolId") UUID schoolId,
            @Param("statuses") Collection<String> statuses);

}
