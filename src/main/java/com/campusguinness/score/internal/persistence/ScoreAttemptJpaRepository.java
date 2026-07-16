package com.campusguinness.score.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ScoreAttemptJpaRepository extends JpaRepository<ScoreAttemptEntity, UUID> {
    List<ScoreAttemptEntity> findByStudentIdAndActivityProjectId(UUID studentId, UUID activityProjectId);
}
