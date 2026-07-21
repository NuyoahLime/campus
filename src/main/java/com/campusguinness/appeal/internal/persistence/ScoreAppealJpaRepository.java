package com.campusguinness.appeal.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ScoreAppealJpaRepository extends JpaRepository<ScoreAppealEntity, UUID> {
    List<ScoreAppealEntity> findBySchoolIdAndAppealStatusIn(UUID schoolId, List<String> statuses);
}
