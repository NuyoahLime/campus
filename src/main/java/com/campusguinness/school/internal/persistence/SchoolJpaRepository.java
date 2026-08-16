package com.campusguinness.school.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SchoolJpaRepository extends JpaRepository<SchoolEntity, UUID> {
    Optional<SchoolEntity> findByInternalCode(String internalCode);
    org.springframework.data.domain.Page<SchoolEntity> findBySchoolStatus(String status, org.springframework.data.domain.Pageable pageable);
    boolean existsByIdAndSchoolStatus(UUID id, String schoolStatus);
    boolean existsByIdAndSchoolStatusIn(UUID id, Collection<String> schoolStatuses);
    boolean existsByUnifiedCodeTypeAndUnifiedCode(String unifiedCodeType, String unifiedCode);
}
