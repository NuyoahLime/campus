package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.port.AdminApplicationQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
@Transactional(readOnly = true)
public class AdminApplicationQueryAdapter implements AdminApplicationQueryPort {

    private final JdbcTemplate jdbc;
    public AdminApplicationQueryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public QueryPage<AdminApplicationItem> findApplications(String status, UUID schoolId, String keyword,
            Instant submittedFrom, Instant submittedTo, String sort, int page, int size) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) { where.append(" AND aa.application_status = ?"); params.add(status); }
        if (schoolId != null) { where.append(" AND aa.school_id = ?"); params.add(schoolId); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (aa.title ILIKE ? OR s.name ILIKE ? OR u.username ILIKE ?)");
            params.add("%" + keyword + "%"); params.add("%" + keyword + "%"); params.add("%" + keyword + "%");
        }
        if (submittedFrom != null) { where.append(" AND aa.created_at >= ?"); params.add(Timestamp.from(submittedFrom)); }
        if (submittedTo != null) { where.append(" AND aa.created_at <= ?"); params.add(Timestamp.from(submittedTo)); }

        String orderBy = switch (sort != null ? sort : "updated_desc") {
            case "created_asc" -> "aa.created_at ASC, aa.id DESC";
            case "created_desc" -> "aa.created_at DESC, aa.id DESC";
            case "updated_asc" -> "aa.updated_at ASC, aa.id DESC";
            default -> "aa.updated_at DESC, aa.id DESC";
        };

        String countSql = "SELECT count(*) FROM activity_applications aa LEFT JOIN schools s ON aa.school_id = s.id LEFT JOIN users u ON aa.applicant_id = u.id " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        if (total == null || total == 0) return new QueryPage<>(List.of(), page, size, 0);

        String sql = """
            SELECT aa.id, aa.school_id, s.name AS school_name, aa.applicant_id, u.username AS applicant_name,
                   aa.title, CASE WHEN length(aa.description)>100 THEN left(aa.description,100)||'...' ELSE aa.description END AS desc_summary,
                   aa.application_status, aa.application_version, aa.created_activity_id,
                   aa.reviewed_at, aa.created_at, aa.updated_at
            FROM activity_applications aa LEFT JOIN schools s ON aa.school_id = s.id LEFT JOIN users u ON aa.applicant_id = u.id
            """ + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
        List<Object> sqlParams = new ArrayList<>(params);
        sqlParams.add(size); sqlParams.add(page * size);

        var items = jdbc.queryForList(sql, sqlParams.toArray()).stream().map(row -> new AdminApplicationItem(
                (UUID) row.get("id"), (UUID) row.get("school_id"), (String) row.get("school_name"),
                (UUID) row.get("applicant_id"), (String) row.get("applicant_name"),
                (String) row.get("title"), (String) row.get("desc_summary"),
                (String) row.get("application_status"), ((Number) row.get("application_version")).intValue(),
                (UUID) row.get("created_activity_id"),
                row.get("reviewed_at") != null ? ((Timestamp) row.get("reviewed_at")).toInstant() : null,
                ((Timestamp) row.get("created_at")).toInstant(), ((Timestamp) row.get("updated_at")).toInstant())
        ).toList();
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<AdminApplicationDetail> findById(UUID applicationId) {
        String sql = """
            SELECT aa.id, aa.school_id, s.name AS school_name, aa.applicant_id, u.username AS applicant_name,
                   aa.title, aa.description, aa.application_status, aa.application_version, aa.created_activity_id,
                   aa.reviewed_at, aa.review_comment, aa.reject_reason, aa.created_at, aa.updated_at
            FROM activity_applications aa LEFT JOIN schools s ON aa.school_id = s.id LEFT JOIN users u ON aa.applicant_id = u.id
            WHERE aa.id = ?
            """;
        var rows = jdbc.queryForList(sql, applicationId);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        return Optional.of(new AdminApplicationDetail(
                (UUID) row.get("id"), (UUID) row.get("school_id"), (String) row.get("school_name"),
                (UUID) row.get("applicant_id"), (String) row.get("applicant_name"),
                (String) row.get("title"), (String) row.get("description"),
                (String) row.get("application_status"), ((Number) row.get("application_version")).intValue(),
                (UUID) row.get("created_activity_id"),
                row.get("reviewed_at") != null ? ((Timestamp) row.get("reviewed_at")).toInstant() : null,
                (String) row.get("review_comment"), (String) row.get("reject_reason"),
                ((Timestamp) row.get("created_at")).toInstant(), ((Timestamp) row.get("updated_at")).toInstant()));
    }

    @Override
    public ApplicationStats getStats() {
        var row = jdbc.queryForMap("SELECT count(*) FILTER (WHERE application_status='DRAFT') AS draft, count(*) FILTER (WHERE application_status='SUBMITTED') AS submitted, count(*) FILTER (WHERE application_status='APPROVED') AS approved, count(*) FILTER (WHERE application_status='REJECTED') AS rejected, count(*) FILTER (WHERE application_status='WITHDRAWN') AS withdrawn, count(*) FILTER (WHERE created_at::date = CURRENT_DATE) AS created_today, count(*) AS total FROM activity_applications");
        return new ApplicationStats(((Number)row.get("total")).intValue(), ((Number)row.get("draft")).intValue(), ((Number)row.get("submitted")).intValue(), ((Number)row.get("approved")).intValue(), ((Number)row.get("rejected")).intValue(), ((Number)row.get("withdrawn")).intValue(), ((Number)row.get("created_today")).intValue());
    }

    @Override
    public List<SchoolOption> getApplicationSchools() {
        return jdbc.queryForList("SELECT DISTINCT aa.school_id, s.name AS school_name FROM activity_applications aa JOIN schools s ON aa.school_id = s.id ORDER BY s.name, aa.school_id").stream()
                .map(row -> new SchoolOption((UUID) row.get("school_id"), (String) row.get("school_name"))).toList();
    }
}
