package com.campusguinness.activity.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ActivityJpaRepository extends JpaRepository<ActivityEntity, UUID> {
    org.springframework.data.domain.Page<ActivityEntity> findByExecutionStatusIn(List<String> statuses, org.springframework.data.domain.Pageable pageable);
}
