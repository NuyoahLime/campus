package com.campusguinness.school.internal.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SchoolJpaRepository extends JpaRepository<SchoolEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SchoolEntity s where s.id = :id")
    Optional<SchoolEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<SchoolEntity> findByInternalCode(String internalCode);
    org.springframework.data.domain.Page<SchoolEntity> findBySchoolStatus(String status, org.springframework.data.domain.Pageable pageable);
    boolean existsByIdAndSchoolStatus(UUID id, String schoolStatus);
    boolean existsByIdAndSchoolStatusIn(UUID id, Collection<String> schoolStatuses);
    boolean existsByUnifiedCodeTypeAndUnifiedCode(String unifiedCodeType, String unifiedCode);
}
