package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityProjectResult;
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

    private List<ActivityProjectResult> loadProjects(UUID activityId) {
        return jdbc.query("""
                SELECT ap.project_id, p.name, p.category, ap.rule_version_id,
                       rv.version_number, rv.rules_text, rv.score_storage_type,
                       rv.score_indicator_type, rv.comparison_direction, rv.score_unit, rv.allow_tie
                FROM activity_projects ap
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN project_rule_versions rv ON rv.id = ap.rule_version_id
                WHERE ap.activity_id = ?
                ORDER BY ap.id
                """, this::mapProject, activityId);
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
        return new ActivityProjectResult(rs.getObject("project_id", UUID.class), rs.getString("name"),
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
