package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityProjectResult;
import com.campusguinness.activity.application.query.model.ActivityManagementListResult;
import com.campusguinness.activity.application.query.model.ActivityManagementDetailResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
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
class ActivityQueryAdapter implements ActivityQueryPort {

    private final JdbcTemplate jdbc;

    ActivityQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<ActivityListResult> findPublic(int page, int size, List<String> statuses) {
        String statusPlaceholders = String.join(", ", statuses.stream().map(value -> "?").toList());
        String visibility = " a.public_status = 'PUBLIC' AND a.execution_status IN (" + statusPlaceholders + ")"
                + " AND s.school_status = 'NORMAL'";
        String sql = "SELECT a.id, a.school_id, s.name, s.region, a.title, a.description,"
                + " a.start_time, a.end_time, a.location, a.execution_status"
                + " FROM activities a JOIN schools s ON s.id = a.school_id"
                + " WHERE" + visibility
                + " ORDER BY a.start_time DESC NULLS LAST, a.id DESC LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(statuses);
        args.add(size);
        args.add(page * size);
        List<ActivityListResult> items = jdbc.query(sql, this::mapList, args.toArray());

        String countSql = "SELECT COUNT(*) FROM activities a JOIN schools s ON s.id = a.school_id WHERE" + visibility;
        long total = jdbc.queryForObject(countSql, Long.class, statuses.toArray());
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<ActivityDetailResult> findPublicById(UUID id, List<String> statuses) {
        String statusPlaceholders = String.join(", ", statuses.stream().map(value -> "?").toList());
        String sql = "SELECT a.id, a.school_id, s.name, s.region, a.title, a.description,"
                + " a.start_time, a.end_time, a.location, a.execution_status"
                + " FROM activities a JOIN schools s ON s.id = a.school_id"
                + " WHERE a.id = ? AND a.public_status = 'PUBLIC'"
                + " AND a.execution_status IN (" + statusPlaceholders + ")"
                + " AND s.school_status = 'NORMAL'";
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.addAll(statuses);
        List<ActivityDetailResult> activities = jdbc.query(sql, this::mapDetailWithoutProjects, args.toArray());
        if (activities.isEmpty()) return Optional.empty();
        ActivityDetailResult activity = activities.get(0);
        return Optional.of(new ActivityDetailResult(activity.id(), activity.schoolId(), activity.schoolName(),
                activity.schoolRegion(), activity.title(), activity.description(), activity.startTime(),
                activity.endTime(), activity.location(), activity.executionStatus(), loadProjects(activity.id())));
    }

    @Override
    public QueryPage<ActivityManagementListResult> findManagement(
            UUID schoolId, int page, int size, String status, String query, UUID projectId) {
        StringBuilder where = new StringBuilder(" WHERE a.school_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        if (status != null) {
            where.append(" AND (a.execution_status = ? OR a.public_status = ?)");
            args.add(status); args.add(status);
        }
        if (query != null) {
            where.append(" AND (LOWER(a.title) LIKE ? OR EXISTS (SELECT 1 FROM activity_projects aqp "
                    + "JOIN challenge_projects aq ON aq.id = aqp.project_id WHERE aqp.activity_id = a.id "
                    + "AND LOWER(aq.name) LIKE ?))");
            String pattern = "%" + query.toLowerCase() + "%";
            args.add(pattern); args.add(pattern);
        }
        if (projectId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM activity_projects apf WHERE apf.activity_id = a.id AND apf.project_id = ?)");
            args.add(projectId);
        }
        String select = "SELECT a.id, a.title, a.execution_status, a.public_status, a.start_time, a.end_time, a.updated_at, "
                + "(SELECT p.name FROM activity_projects ap JOIN challenge_projects p ON p.id = ap.project_id "
                + "WHERE ap.activity_id = a.id ORDER BY ap.id LIMIT 1) AS project_name, "
                + "(SELECT rv.version_number FROM activity_projects ap JOIN project_rule_versions rv ON rv.id = ap.rule_version_id "
                + "WHERE ap.activity_id = a.id ORDER BY ap.id LIMIT 1) AS version_number "
                + "FROM activities a" + where + " ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(page * size);
        List<ActivityManagementListResult> items = jdbc.query(select, this::mapManagementList, pageArgs.toArray());
        String countSql = "SELECT COUNT(*) FROM activities a" + where;
        long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<ActivityManagementDetailResult> findManagementById(UUID id, UUID schoolId) {
        String sql = "SELECT a.id, a.school_id, s.name, a.title, a.description, a.start_time, a.end_time, "
                + "a.location, a.execution_status, a.public_status, a.created_at, a.updated_at "
                + "FROM activities a JOIN schools s ON s.id = a.school_id WHERE a.id = ? AND a.school_id = ?";
        List<ActivityManagementDetailResult> rows = jdbc.query(sql, this::mapManagementDetail, id, schoolId);
        if (rows.isEmpty()) return Optional.empty();
        ActivityManagementDetailResult base = rows.get(0);
        return Optional.of(new ActivityManagementDetailResult(base.id(), base.schoolId(), base.schoolName(),
                base.title(), base.description(), base.startTime(), base.endTime(), base.location(),
                base.executionStatus(), base.publicStatus(), base.createdAt(), base.updatedAt(),
                loadProjects(base.id())));
    }

    private List<ActivityProjectResult> loadProjects(UUID activityId) {
        return jdbc.query("""
                SELECT ap.id, ap.project_id, p.name, p.category, ap.rule_version_id,
                       rv.version_number, rv.rules_text, rv.score_storage_type,
                       rv.score_indicator_type, rv.comparison_direction, rv.score_unit, rv.allow_tie
                FROM activity_projects ap
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN project_rule_versions rv ON rv.id = ap.rule_version_id
                WHERE ap.activity_id = ?
                ORDER BY ap.id
                """, this::mapProject, activityId);
    }

    private ActivityManagementListResult mapManagementList(ResultSet rs, int row) throws SQLException {
        return new ActivityManagementListResult(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("project_name"), rs.getObject("version_number", Integer.class),
                rs.getString("execution_status"), rs.getString("public_status"),
                instant(rs, "start_time"), instant(rs, "end_time"), instant(rs, "updated_at"));
    }

    private ActivityManagementDetailResult mapManagementDetail(ResultSet rs, int row) throws SQLException {
        return new ActivityManagementDetailResult(rs.getObject("id", UUID.class),
                rs.getObject("school_id", UUID.class), rs.getString("name"), rs.getString("title"),
                rs.getString("description"), instant(rs, "start_time"), instant(rs, "end_time"),
                rs.getString("location"), rs.getString("execution_status"), rs.getString("public_status"),
                instant(rs, "created_at"), instant(rs, "updated_at"), List.of());
    }

    private ActivityListResult mapList(ResultSet rs, int row) throws SQLException {
        return new ActivityListResult(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
                rs.getString("title"), instant(rs, "start_time"), instant(rs, "end_time"),
                rs.getString("location"), rs.getString("execution_status"), rs.getString("name"),
                rs.getString("region"), rs.getString("description"));
    }

    private ActivityDetailResult mapDetailWithoutProjects(ResultSet rs, int row) throws SQLException {
        return new ActivityDetailResult(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
                rs.getString("name"), rs.getString("region"), rs.getString("title"),
                rs.getString("description"), instant(rs, "start_time"), instant(rs, "end_time"),
                rs.getString("location"), rs.getString("execution_status"), List.of());
    }

    private ActivityProjectResult mapProject(ResultSet rs, int row) throws SQLException {
        return new ActivityProjectResult(rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getString("name"),
                rs.getString("category"), rs.getObject("rule_version_id", UUID.class),
                rs.getInt("version_number"), rs.getString("rules_text"),
                rs.getString("score_storage_type"), rs.getString("score_indicator_type"),
                rs.getString("comparison_direction"), rs.getString("score_unit"), rs.getBoolean("allow_tie"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
