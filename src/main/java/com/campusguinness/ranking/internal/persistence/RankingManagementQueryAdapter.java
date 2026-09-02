package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingManagementDefinitionResult;
import com.campusguinness.ranking.application.query.model.RankingManagementEntryResult;
import com.campusguinness.ranking.application.query.model.RankingManagementVersionResult;
import com.campusguinness.ranking.application.query.port.RankingManagementQueryPort;
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
class RankingManagementQueryAdapter implements RankingManagementQueryPort {
    private final JdbcTemplate jdbc;

    RankingManagementQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<RankingManagementDefinitionResult> list(UUID schoolId, int page, int size) {
        String from = fromClause() + " WHERE d.layer = 'L1' AND d.school_id = ?";
        List<RankingManagementDefinitionResult> items = jdbc.query(
                selectClause() + from + " ORDER BY d.updated_at DESC, d.name ASC, d.id ASC LIMIT ? OFFSET ?",
                (rs, row) -> mapDefinition(rs, false),
                schoolId, size, page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, schoolId);
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<RankingManagementDefinitionResult> detail(UUID definitionId, UUID schoolId) {
        return jdbc.query(selectClause() + fromClause()
                        + " WHERE d.id = ? AND d.layer = 'L1' AND d.school_id = ?",
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapDefinition(rs, true));
                },
                definitionId, schoolId);
    }

    private String selectClause() {
        return """
                SELECT d.id, d.name, d.layer, d.is_enabled, d.school_id, s.name AS school_name,
                       d.project_id, p.name AS project_name,
                       ap.id AS activity_project_id, a.id AS activity_id, a.title AS activity_title,
                       gv.id AS generated_version_id, gv.version_number AS generated_version_number,
                       gv.version_status AS generated_version_status, gv.generated_at AS generated_at,
                       gv.published_at AS generated_published_at, gv.entry_count AS generated_entry_count,
                       cv.id AS current_version_id, cv.version_number AS current_version_number,
                       cv.version_status AS current_version_status, cv.generated_at AS current_generated_at,
                       cv.published_at AS current_published_at, cv.entry_count AS current_entry_count
                """;
    }

    private String fromClause() {
        return """
                 FROM ranking_definitions d
                 JOIN challenge_projects p ON p.id = d.project_id
                 JOIN schools s ON s.id = d.school_id
                 LEFT JOIN activity_projects ap ON ap.id = (d.dimension_filters ->> 'activityProjectId')::uuid
                 LEFT JOIN activities a ON a.id = ap.activity_id
                 LEFT JOIN LATERAL (
                     SELECT v.id, v.version_number, v.version_status, v.generated_at, v.published_at,
                            (SELECT COUNT(*) FROM ranking_entries e WHERE e.version_id = v.id) AS entry_count
                     FROM ranking_versions v
                     WHERE v.definition_id = d.id AND v.version_status = 'GENERATED'
                     ORDER BY v.version_number DESC, v.created_at DESC, v.id DESC
                     LIMIT 1
                 ) gv ON true
                 LEFT JOIN LATERAL (
                     SELECT v.id, v.version_number, v.version_status, v.generated_at, v.published_at,
                            (SELECT COUNT(*) FROM ranking_entries e WHERE e.version_id = v.id) AS entry_count
                     FROM ranking_versions v
                     WHERE v.id = d.current_version_id
                       AND v.definition_id = d.id
                       AND v.version_status = 'PUBLISHED'
                     LIMIT 1
                 ) cv ON true
                """;
    }

    private RankingManagementDefinitionResult mapDefinition(ResultSet rs, boolean includeGeneratedEntries)
            throws SQLException {
        RankingManagementVersionResult generated = mapVersion(rs, "generated", includeGeneratedEntries);
        RankingManagementVersionResult current = mapVersion(rs, "current", false);
        return new RankingManagementDefinitionResult(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("layer"),
                rs.getBoolean("is_enabled"),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getObject("activity_project_id", UUID.class),
                generated,
                current);
    }

    private RankingManagementVersionResult mapVersion(ResultSet rs, String prefix, boolean includeEntries)
            throws SQLException {
        UUID id = rs.getObject(prefix + "_version_id", UUID.class);
        if (id == null) return null;
        List<RankingManagementEntryResult> entries = includeEntries ? entries(id) : List.of();
        return new RankingManagementVersionResult(
                id,
                rs.getInt(prefix + "_version_number"),
                rs.getString(prefix + "_version_status"),
                instant(rs, prefix.equals("generated") ? "generated_at" : "current_generated_at"),
                instant(rs, prefix.equals("generated") ? "generated_published_at" : "current_published_at"),
                rs.getInt(prefix + "_entry_count"),
                entries);
    }

    private List<RankingManagementEntryResult> entries(UUID versionId) {
        return jdbc.query("""
                SELECT rank_position, student_display_name, school_name, score_display_value
                FROM ranking_entries
                WHERE version_id = ?
                ORDER BY rank_position ASC, id ASC
                """, (rs, row) -> new RankingManagementEntryResult(
                rs.getInt("rank_position"),
                rs.getString("student_display_name"),
                rs.getString("school_name"),
                rs.getString("score_display_value")), versionId);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
