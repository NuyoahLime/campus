package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.L3AuthorizationDetailResult;
import com.campusguinness.ranking.application.query.model.L3AuthorizationSummaryResult;
import com.campusguinness.ranking.application.query.model.L3UsableAuthorizationResult;
import com.campusguinness.ranking.application.query.port.L3AuthorizationQueryPort;
import com.campusguinness.ranking.application.query.port.L3UsableAuthorizationQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class L3AuthorizationQueryAdapter implements L3AuthorizationQueryPort, L3UsableAuthorizationQueryPort {
    private final JdbcTemplate jdbc;

    L3AuthorizationQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<L3AuthorizationSummaryResult> listForSchool(
            UUID schoolId, String status, UUID projectId, int page, int size) {
        var filters = new Filters(schoolId, status, projectId);
        return page(filters, page, size);
    }

    @Override
    public Optional<L3AuthorizationDetailResult> findForSchool(UUID id, UUID schoolId) {
        return detail("a.id = ? AND a.school_id = ?", id, schoolId);
    }

    @Override
    public QueryPage<L3AuthorizationSummaryResult> listForReview(
            String status, UUID schoolId, UUID projectId, int page, int size) {
        var filters = new Filters(schoolId, status, projectId);
        return page(filters, page, size);
    }

    @Override
    public Optional<L3AuthorizationDetailResult> findForReview(UUID id) {
        return detail("a.id = ?", id);
    }

    @Override
    public List<L3UsableAuthorizationResult> findUsableAuthorizations(UUID projectId, UUID ruleVersionId) {
        return jdbc.query("""
                SELECT a.id, a.school_id, a.project_id, a.rule_version_id,
                       a.data_scope::text AS data_scope, a.allow_school_name, a.allow_student_name
                FROM l3_authorizations a
                JOIN schools s ON s.id = a.school_id
                WHERE a.authorization_status = 'APPROVED'
                  AND s.school_status = 'NORMAL'
                  AND a.project_id = ?
                  AND a.rule_version_id = ?
                ORDER BY s.name ASC, a.school_id ASC, a.id ASC
                """,
                (rs, row) -> new L3UsableAuthorizationResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("school_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("rule_version_id", UUID.class),
                        rs.getString("data_scope"),
                        rs.getBoolean("allow_school_name"),
                        rs.getBoolean("allow_student_name")),
                projectId, ruleVersionId);
    }

    private QueryPage<L3AuthorizationSummaryResult> page(Filters filters, int page, int size) {
        var where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        filters.append(where, args);
        String from = baseFrom() + where;
        var items = jdbc.query("""
                SELECT a.id, a.school_id, s.name AS school_name, a.project_id, p.name AS project_name,
                       a.rule_version_id, prv.version_number AS rule_version_number,
                       a.authorization_status, a.allow_school_name, a.allow_student_name,
                       a.submitted_at, a.reviewed_at, a.created_at, a.updated_at
                """ + from + """
                 ORDER BY a.updated_at DESC, a.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """,
                ps -> {
                    int i = bind(ps, args);
                    ps.setInt(i++, size);
                    ps.setInt(i, page * size);
                },
                (rs, row) -> new L3AuthorizationSummaryResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("school_id", UUID.class),
                        rs.getString("school_name"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("project_name"),
                        rs.getObject("rule_version_id", UUID.class),
                        (Integer) rs.getObject("rule_version_number"),
                        rs.getString("authorization_status"),
                        rs.getBoolean("allow_school_name"),
                        rs.getBoolean("allow_student_name"),
                        instant(rs, "submitted_at"),
                        instant(rs, "reviewed_at"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at")));
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    private Optional<L3AuthorizationDetailResult> detail(String predicate, Object... args) {
        return jdbc.query("""
                SELECT a.id, a.school_id, s.name AS school_name, a.project_id, p.name AS project_name,
                       a.rule_version_id, prv.version_number AS rule_version_number,
                       a.data_scope::text AS data_scope, a.allow_school_name, a.allow_student_name,
                       a.authorization_status, a.submitted_at, a.reviewed_by, a.reviewed_at,
                       a.review_comment, a.reject_reason, a.paused_at, a.withdrawn_at,
                       a.withdraw_reason, a.created_at, a.updated_at
                """ + baseFrom() + " WHERE " + predicate,
                rs -> rs.next() ? Optional.of(mapDetail(rs)) : Optional.empty(),
                args);
    }

    private L3AuthorizationDetailResult mapDetail(ResultSet rs) throws SQLException {
        return new L3AuthorizationDetailResult(
                rs.getObject("id", UUID.class),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("rule_version_id", UUID.class),
                (Integer) rs.getObject("rule_version_number"),
                rs.getString("data_scope"),
                rs.getBoolean("allow_school_name"),
                rs.getBoolean("allow_student_name"),
                rs.getString("authorization_status"),
                instant(rs, "submitted_at"),
                rs.getObject("reviewed_by", UUID.class),
                instant(rs, "reviewed_at"),
                rs.getString("review_comment"),
                rs.getString("reject_reason"),
                instant(rs, "paused_at"),
                instant(rs, "withdrawn_at"),
                rs.getString("withdraw_reason"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private String baseFrom() {
        return """
                FROM l3_authorizations a
                JOIN schools s ON s.id = a.school_id
                JOIN challenge_projects p ON p.id = a.project_id
                JOIN project_rule_versions prv ON prv.id = a.rule_version_id AND prv.project_id = a.project_id
                """;
    }

    private int bind(java.sql.PreparedStatement ps, List<Object> args) throws SQLException {
        int i = 1;
        for (Object arg : args) {
            ps.setObject(i++, arg);
        }
        return i;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record Filters(UUID schoolId, String status, UUID projectId) {
        void append(StringBuilder where, List<Object> args) {
            if (schoolId != null) {
                where.append(" AND a.school_id = ?");
                args.add(schoolId);
            }
            if (status != null && !status.isBlank()) {
                where.append(" AND a.authorization_status = ?");
                args.add(status.trim());
            }
            if (projectId != null) {
                where.append(" AND a.project_id = ?");
                args.add(projectId);
            }
        }
    }
}
