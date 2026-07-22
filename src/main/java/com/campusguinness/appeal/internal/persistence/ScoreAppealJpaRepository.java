package com.campusguinness.appeal.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ScoreAppealJpaRepository extends JpaRepository<ScoreAppealEntity, UUID> {     List<ScoreAppealEntity> findByStudentId(UUID studentId);
    Optional<ScoreAppealEntity> findByIdAndStudentId(UUID id, UUID studentId);
}
