package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.port.TeacherApplicationQueryPort;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;

@Component
@Transactional(readOnly = true)
class TeacherApplicationQueryAdapter implements TeacherApplicationQueryPort {

    private final JdbcTemplate jdbc;

    TeacherApplicationQueryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public QueryPage<ActivityApplicationResult> findMine(UUID applicantId, String status,
            UUID schoolId, String keyword, int page, int size) {

        StringBuilder where = new StringBuilder("WHERE aa.applicant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(applicantId);

        if (status != null && !status.isBlank()) {
            where.append(" AND aa.application_status = ?");
            params.add(status);
        }
        if (schoolId != null) {
            where.append(" AND aa.school_id = ?");
            params.add(schoolId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND aa.title ILIKE ?");
            params.add("%" + keyword + "%");
        }

        String countSql = "SELECT count(*) FROM activity_applications aa " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        if (total == null || total == 0)
            return new QueryPage<>(List.of(), page, size, 0);

        String sql = """
            SELECT aa.id, aa.school_id, s.name AS school_name, aa.title, aa.description,
                   aa.application_status, aa.application_version, aa.created_activity_id,
                   aa.reviewed_at, aa.review_comment, aa.reject_reason, aa.created_at, aa.updated_at
            FROM activity_applications aa
            LEFT JOIN schools s ON aa.school_id = s.id
            """ + where + """
            ORDER BY aa.updated_at DESC, aa.id DESC
            LIMIT ? OFFSET ?
            """;
        List<Object> sqlParams = new ArrayList<>(params);
        sqlParams.add(size);
        sqlParams.add(page * size);

        var items = jdbc.queryForList(sql, sqlParams.toArray()).stream().map(row ->
            new ActivityApplicationResult(
                    (UUID) row.get("id"), (UUID) row.get("school_id"),
                    (String) row.get("school_name"), (String) row.get("title"),
                    (String) row.get("description"), (String) row.get("application_status"),
                    (UUID) row.get("created_activity_id"),
                    row.get("reviewed_at") != null ? ((Timestamp) row.get("reviewed_at")).toInstant() : null,
                    (String) row.get("review_comment"), (String) row.get("reject_reason"),
                    ((Number) row.get("application_version")).intValue(),
                    row.get("created_at") != null ? ((Timestamp) row.get("created_at")).toInstant() : null,
                    row.get("updated_at") != null ? ((Timestamp) row.get("updated_at")).toInstant() : null)
        ).toList();
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<ActivityApplicationResult> findMineById(UUID applicantId, UUID applicationId) {
        String sql = """
            SELECT aa.id, aa.school_id, s.name AS school_name, aa.title, aa.description,
                   aa.application_status, aa.application_version, aa.created_activity_id,
                   aa.reviewed_at, aa.review_comment, aa.reject_reason, aa.created_at, aa.updated_at
            FROM activity_applications aa
            LEFT JOIN schools s ON aa.school_id = s.id
            WHERE aa.id = ? AND aa.applicant_id = ?
            """;
        var rows = jdbc.queryForList(sql, applicationId, applicantId);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        return Optional.of(mapRow(row));
    }

    @Override
    public ApplicationStats getStats(UUID applicantId) {
        String sql = """
            SELECT
              count(*) AS total,
              count(*) FILTER (WHERE aa.application_status = 'DRAFT') AS draft,
              count(*) FILTER (WHERE aa.application_status = 'SUBMITTED') AS submitted,
              count(*) FILTER (WHERE aa.application_status = 'APPROVED') AS approved,
              count(*) FILTER (WHERE aa.application_status = 'REJECTED') AS rejected,
              count(*) FILTER (WHERE aa.application_status = 'WITHDRAWN') AS withdrawn
            FROM activity_applications aa
            WHERE aa.applicant_id = ?
            """;
        var row = jdbc.queryForMap(sql, applicantId);
        return new ApplicationStats(
                ((Number) row.get("total")).intValue(), ((Number) row.get("draft")).intValue(),
                ((Number) row.get("submitted")).intValue(), ((Number) row.get("approved")).intValue(),
                ((Number) row.get("rejected")).intValue(), ((Number) row.get("withdrawn")).intValue());
    }

    @Override
    public java.util.List<TeacherSchoolItem> findTeacherSchools(UUID userId) {
        String sql = """
            SELECT sm.school_id, s.name AS school_name
            FROM school_memberships sm
            JOIN schools s ON sm.school_id = s.id
            WHERE sm.user_id = ? AND sm.role_in_school = 'TEACHER' AND sm.status = 'ACTIVE'
            """;
        return jdbc.queryForList(sql, userId).stream()
                .map(row -> new TeacherSchoolItem((UUID) row.get("school_id"), (String) row.get("school_name")))
                .toList();
    }

    private ActivityApplicationResult mapRow(Map<String, Object> row) {
        return new ActivityApplicationResult(
                (UUID) row.get("id"), (UUID) row.get("school_id"), (String) row.get("school_name"),
                (String) row.get("title"), (String) row.get("description"),
                (String) row.get("application_status"), (UUID) row.get("created_activity_id"),
                row.get("reviewed_at") != null ? ((java.sql.Timestamp) row.get("reviewed_at")).toInstant() : null,
                (String) row.get("review_comment"), (String) row.get("reject_reason"),
                ((Number) row.get("application_version")).intValue(),
                row.get("created_at") != null ? ((java.sql.Timestamp) row.get("created_at")).toInstant() : null,
                row.get("updated_at") != null ? ((java.sql.Timestamp) row.get("updated_at")).toInstant() : null);
    }
}
