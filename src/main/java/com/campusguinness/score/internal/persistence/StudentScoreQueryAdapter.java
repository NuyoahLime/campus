package com.campusguinness.score.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.StudentScoreDetail;
import com.campusguinness.score.application.query.model.StudentScoreItem;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;

import com.campusguinness.score.application.query.ScoreDisplayFormatter;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Transactional(readOnly = true)
class StudentScoreQueryAdapter implements StudentScoreQueryPort {

    private final ScoreAttemptJpaRepository scoreJpa;

    StudentScoreQueryAdapter(ScoreAttemptJpaRepository scoreJpa) {
        this.scoreJpa = scoreJpa;
    }

    @Override
    public QueryPage<StudentScoreItem> findByStudentId(
            UUID studentId, String status, UUID activityId, UUID projectId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id")));
        var result = scoreJpa.findByStudentIdWithFilters(studentId, status, activityId, projectId, pageable);

        var items = result.getContent().stream().map(row -> {
            Object[] arr = (Object[]) row;
            return new StudentScoreItem(
                    (UUID) arr[0], (UUID) arr[1], (String) arr[2],
                    (UUID) arr[3], (UUID) arr[4], (String) arr[5],
                    ((Number) arr[6]).intValue(), (String) arr[7],
                    ScoreDisplayFormatter.format((String) arr[7], arr[8],
                            arr[9] != null ? ((Number) arr[9]).longValue() : null, (String) arr[10], null),
                    (String) arr[11], (Boolean) arr[12],
                    arr[13] != null ? ((java.sql.Timestamp) arr[13]).toInstant() : null,
                    arr[14] != null ? ((java.sql.Timestamp) arr[14]).toInstant() : null,
                    ((java.sql.Timestamp) arr[15]).toInstant()
            );
        }).toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<StudentScoreDetail> findByIdAndStudentId(UUID attemptId, UUID studentId) {
        return scoreJpa.findById(attemptId)
                .filter(e -> e.getStudentId().equals(studentId))
                .map(e -> {
                    var list = scoreJpa.findDetailById(attemptId);
                    if (list.isEmpty()) return null;
                    Object[] arr = (Object[]) list.getFirst();
                    return new StudentScoreDetail(
                            (UUID) arr[0], (UUID) arr[1], (String) arr[2],
                            (UUID) arr[3], (UUID) arr[4], (String) arr[5],
                            ((Number) arr[6]).intValue(), (String) arr[7],
                            ScoreDisplayFormatter.format((String) arr[7], arr[8],
                                    arr[9] != null ? ((Number) arr[9]).longValue() : null, (String) arr[10], null),
                            (String) arr[11], (Boolean) arr[12],
                            arr[13] != null ? ((java.sql.Timestamp) arr[13]).toInstant() : null,
                            arr[14] != null ? ((java.sql.Timestamp) arr[14]).toInstant() : null,
                            ((java.sql.Timestamp) arr[15]).toInstant(),
                            arr[16] != null ? arr[16].toString() : null,
                            arr[9] != null ? ((Number) arr[9]).longValue() : null,
                            (String) arr[10], (String) arr[17],
                            (String) arr[18], (String) arr[19],
                            (String) arr[20],
                            arr[21] != null ? ((java.sql.Timestamp) arr[21]).toInstant() : null
                    );
                });
    }
}
