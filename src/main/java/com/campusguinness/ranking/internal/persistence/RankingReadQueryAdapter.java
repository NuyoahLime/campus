package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingEntryReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadSummaryResult;
import com.campusguinness.ranking.application.query.port.RankingReadQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class RankingReadQueryAdapter implements RankingReadQueryPort {
    private static final String VISIBLE_WHERE =
            " d.is_enabled = true"
                    + " AND d.current_version_id = v.id"
                    + " AND v.version_status = 'PUBLISHED'";

    private final JdbcTemplate jdbc;

    RankingReadQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<RankingReadSummaryResult> list(UUID schoolId, boolean includeGlobal, int page, int size) {
        String scope = scopeClause(schoolId, includeGlobal);
        String from = " FROM ranking_definitions d"
                + " JOIN ranking_versions v ON v.definition_id = d.id"
                + " JOIN challenge_projects p ON p.id = d.project_id"
                + " LEFT JOIN schools s ON s.id = d.school_id"
                + " WHERE" + VISIBLE_WHERE + scope;
        String sql = "SELECT d.id, d.name, d.layer, d.school_id, s.name AS school_name,"
                + " d.project_id, p.name AS project_name, v.version_number, v.published_at"
                + from + " ORDER BY v.published_at DESC NULLS LAST, d.name ASC, d.id ASC"
                + " LIMIT ? OFFSET ?";
        Object[] args = schoolId == null
                ? new Object[]{size, page * size}
                : new Object[]{schoolId, size, page * size};
        List<RankingReadSummaryResult> items = jdbc.query(sql, this::mapSummary, args);
        String countSql = "SELECT COUNT(*)" + from;
        long total = schoolId == null
                ? jdbc.queryForObject(countSql, Long.class)
                : jdbc.queryForObject(countSql, Long.class, schoolId);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<RankingReadResult> detail(UUID rankingId, UUID schoolId, boolean includeGlobal) {
        String scope = scopeClause(schoolId, includeGlobal);
        String sql = "SELECT d.id, d.name, d.layer, d.school_id, s.name AS school_name,"
                + " d.project_id, p.name AS project_name, v.id AS version_id,"
                + " v.version_number, v.published_at"
                + " FROM ranking_definitions d"
                + " JOIN ranking_versions v ON v.definition_id = d.id"
                + " JOIN challenge_projects p ON p.id = d.project_id"
                + " LEFT JOIN schools s ON s.id = d.school_id"
                + " WHERE d.id = ? AND" + VISIBLE_WHERE + scope;
        Object[] args = schoolId == null
                ? new Object[]{rankingId}
                : new Object[]{rankingId, schoolId};
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            UUID versionId = rs.getObject("version_id", UUID.class);
            RankingReadSummaryResult summary = mapSummary(rs, 0);
            List<RankingEntryReadResult> entries = jdbc.query(
                    "SELECT rank_position, student_display_name, school_name, score_display_value"
                            + " FROM ranking_entries WHERE version_id = ?"
                            + " ORDER BY rank_position ASC, id ASC",
                    this::mapEntry,
                    versionId);
            return Optional.of(new RankingReadResult(
                    summary.id(), summary.name(), summary.layer(), summary.schoolId(),
                    summary.schoolName(), summary.projectId(), summary.projectName(),
                    summary.versionNumber(), summary.publishedAt(), entries));
        }, args);
    }

    private RankingReadSummaryResult mapSummary(ResultSet rs, int row) throws SQLException {
        return new RankingReadSummaryResult(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("layer"),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getInt("version_number"),
                instant(rs, "published_at"));
    }

    private RankingEntryReadResult mapEntry(ResultSet rs, int row) throws SQLException {
        return new RankingEntryReadResult(
                rs.getInt("rank_position"),
                rs.getString("student_display_name"),
                rs.getString("school_name"),
                rs.getString("score_display_value"));
    }

    private String scopeClause(UUID schoolId, boolean includeGlobal) {
        if (schoolId == null) return "";
        return includeGlobal
                ? " AND (d.school_id IS NULL OR d.school_id = ?)"
                : " AND d.school_id = ?";
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
