package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.ReviewPageResult;
import com.campusguinness.identity.application.query.StudentIdentityApplicationDetail;
import com.campusguinness.identity.application.query.StudentIdentityApplicationReviewQuery;
import com.campusguinness.identity.application.query.StudentIdentityApplicationSummary;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class StudentIdentityApplicationReviewQueryAdapter implements StudentIdentityApplicationReviewQuery {

    private final JdbcTemplate jdbc;

    StudentIdentityApplicationReviewQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResult<StudentIdentityApplicationSummary> findBySchool(
            UUID schoolId,
            StudentIdentityApplicationStatus status,
            int page,
            int size
    ) {
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM student_identity_applications a
                WHERE a.school_id = ? AND a.application_status = ?
                """, Long.class, schoolId, status.name());
        List<StudentIdentityApplicationSummary> items = jdbc.query("""
                SELECT a.id, a.user_id, a.school_id, u.username, a.real_name, a.student_number,
                       a.grade, a.class_name, a.application_status, a.created_at, a.reviewed_at
                FROM student_identity_applications a
                JOIN users u ON u.id = a.user_id
                WHERE a.school_id = ? AND a.application_status = ?
                ORDER BY a.created_at ASC, a.id ASC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> summary(rs),
                schoolId, status.name(), size, page * size);
        return new ReviewPageResult<>(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentIdentityApplicationDetail> findDetail(UUID schoolId, UUID applicationId) {
        List<StudentIdentityApplicationDetail> results = jdbc.query("""
                SELECT a.id, a.user_id, a.school_id, u.username, a.real_name, a.student_number,
                       a.grade, a.class_name, a.evidence_file_key, a.application_status,
                       a.created_at, a.reviewed_by, a.reviewed_at, a.rejection_reason
                FROM student_identity_applications a
                JOIN users u ON u.id = a.user_id
                WHERE a.school_id = ? AND a.id = ?
                """,
                (rs, rowNum) -> detail(rs),
                schoolId, applicationId);
        return results.stream().findFirst();
    }

    private StudentIdentityApplicationSummary summary(ResultSet rs) throws SQLException {
        return new StudentIdentityApplicationSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("school_id", UUID.class),
                rs.getString("username"),
                rs.getString("real_name"),
                rs.getString("student_number"),
                rs.getString("grade"),
                rs.getString("class_name"),
                rs.getString("application_status"),
                instant(rs, "created_at"),
                instant(rs, "reviewed_at")
        );
    }

    private StudentIdentityApplicationDetail detail(ResultSet rs) throws SQLException {
        String evidenceFileKey = rs.getString("evidence_file_key");
        return new StudentIdentityApplicationDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("school_id", UUID.class),
                rs.getString("username"),
                rs.getString("real_name"),
                rs.getString("student_number"),
                rs.getString("grade"),
                rs.getString("class_name"),
                rs.getString("application_status"),
                instant(rs, "created_at"),
                rs.getObject("reviewed_by", UUID.class),
                instant(rs, "reviewed_at"),
                rs.getString("rejection_reason"),
                evidenceFileKey == null || evidenceFileKey.isBlank() ? List.of() : List.of(evidenceFileKey)
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
