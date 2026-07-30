package com.campusguinness.score.internal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreAttemptJpaRepository extends JpaRepository<ScoreAttemptEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScoreAttemptEntity s where s.id = :id")
    Optional<ScoreAttemptEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from ScoreAttemptEntity s
        where s.activityProjectId = :activityProjectId
          and s.studentId = :studentId
          and s.currentEffective = true
        """)
    Optional<ScoreAttemptEntity> findCurrentEffectiveForUpdate(
            @Param("activityProjectId") UUID activityProjectId,
            @Param("studentId") UUID studentId);

    List<ScoreAttemptEntity> findByStudentIdAndActivityProjectId(UUID studentId, UUID activityProjectId);
    List<ScoreAttemptEntity> findByStudentId(UUID studentId);
    List<ScoreAttemptEntity> findByStudentIdAndScoreStatus(UUID studentId, String status);
    Optional<ScoreAttemptEntity> findByIdAndStudentIdAndScoreStatus(UUID id, UUID studentId, String status);
    Optional<ScoreAttemptEntity> findByIdAndStudentId(UUID id, UUID studentId);
    List<ScoreAttemptEntity> findByActivityProjectIdAndScoreStatus(UUID activityProjectId, String status);
    boolean existsByActivityProjectIdAndStudentId(UUID activityProjectId, UUID studentId);
    List<ScoreAttemptEntity> findByActivityProjectIdAndStudentIdIn(UUID activityProjectId, List<UUID> studentIds);

    @Query(value = """
        SELECT sa.id, a.id, a.title, ap.id, ap.project_id, cp.name,
               sa.attempt_number, sa.score_storage_type, sa.score_value, sa.score_duration_ms, sa.score_grade,
               sa.score_status, sa.is_current_effective,
               sa.score_business_time, sa.submitted_at, sa.created_at
        FROM score_attempts sa
        JOIN activity_projects ap ON sa.activity_project_id = ap.id
        JOIN activities a ON ap.activity_id = a.id
        JOIN challenge_projects cp ON ap.project_id = cp.id
        WHERE sa.student_id = :studentId
          AND (:status IS NULL OR sa.score_status = :status)
          AND (:activityId IS NULL OR a.id = :activityId)
          AND (:projectId IS NULL OR ap.project_id = :projectId)
        ORDER BY COALESCE(sa.submitted_at, sa.created_at) DESC, sa.id DESC
        """, nativeQuery = true)
    Page<Object[]> findByStudentIdWithFilters(
            @Param("studentId") UUID studentId,
            @Param("status") String status,
            @Param("activityId") UUID activityId,
            @Param("projectId") UUID projectId,
            Pageable pageable);

    @Query(value = """
        SELECT sa.id, a.id, a.title, ap.id, ap.project_id, cp.name,
               sa.attempt_number, sa.score_storage_type, sa.score_value, sa.score_duration_ms, sa.score_grade,
               sa.score_status, sa.is_current_effective,
               sa.score_business_time, sa.submitted_at, sa.created_at,
               sa.time_source, u.username,
               srr.review_comment, srr.reject_reason, srr.reviewed_at
        FROM score_attempts sa
        JOIN activity_projects ap ON sa.activity_project_id = ap.id
        JOIN activities a ON ap.activity_id = a.id
        JOIN challenge_projects cp ON ap.project_id = cp.id
        LEFT JOIN users u ON sa.entered_by = u.id
        LEFT JOIN score_review_records srr ON srr.score_attempt_id = sa.id
        WHERE sa.id = :id
        """, nativeQuery = true)
    List<Object[]> findDetailById(@Param("id") UUID id);
}
