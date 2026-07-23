package com.campusguinness.score.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreAttemptJpaRepository extends JpaRepository<ScoreAttemptEntity, UUID> {
    List<ScoreAttemptEntity> findByStudentIdAndActivityProjectId(UUID studentId, UUID activityProjectId);
    List<ScoreAttemptEntity> findByStudentId(UUID studentId);
    List<ScoreAttemptEntity> findByStudentIdAndScoreStatus(UUID studentId, String status);
    Optional<ScoreAttemptEntity> findByIdAndStudentIdAndScoreStatus(UUID id, UUID studentId, String status);
    Optional<ScoreAttemptEntity> findByIdAndStudentId(UUID id, UUID studentId);
    List<ScoreAttemptEntity> findByActivityProjectIdAndScoreStatus(UUID activityProjectId, String status);
    boolean existsByActivityProjectIdAndStudentId(UUID activityProjectId, UUID studentId);
    List<ScoreAttemptEntity> findByActivityProjectIdAndStudentIdIn(UUID activityProjectId, List<UUID> studentIds);
}
