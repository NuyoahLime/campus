package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.RankingDefinitionPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RankingDefinitionAdapter implements RankingDefinitionPort {

    private final NamedParameterJdbcTemplate jdbc;

    public RankingDefinitionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LockedDefinition getOrCreateAndLock(
            UUID activityProjectId,
            UUID schoolId,
            UUID projectId,
            String name,
            String tieBreakRule,
            UUID createdBy) {
        UUID definitionId = UUID.randomUUID();
        var params = new MapSqlParameterSource()
                .addValue("id", definitionId)
                .addValue("activityProjectId", activityProjectId)
                .addValue("schoolId", schoolId)
                .addValue("projectId", projectId)
                .addValue("name", name)
                .addValue("tieBreakRule", tieBreakRule)
                .addValue("createdBy", createdBy);
        jdbc.update("""
                INSERT INTO ranking_definitions(
                  id, layer, name, school_id, project_id, activity_project_id,
                  tie_break_rule, is_enabled, current_version_id,
                  created_by, created_at, updated_at, version)
                VALUES (
                  :id, 'L1', :name, :schoolId, :projectId, :activityProjectId,
                  :tieBreakRule, true, NULL, :createdBy, now(), now(), 1)
                ON CONFLICT DO NOTHING
                """, params);
        return lockExisting(schoolId, activityProjectId)
                .orElseThrow(() -> new IllegalStateException(
                        "Ranking definition was not created"));
    }

    @Override
    public Optional<LockedDefinition> lockExisting(
            UUID schoolId, UUID activityProjectId) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId);
        List<LockedDefinition> rows = jdbc.query("""
                SELECT definition.id, definition.current_version_id
                FROM ranking_definitions definition
                JOIN activity_projects ap
                  ON ap.id = definition.activity_project_id
                JOIN activities activity
                  ON activity.id = ap.activity_id
                WHERE definition.activity_project_id = :activityProjectId
                  AND definition.school_id = :schoolId
                  AND activity.school_id = :schoolId
                  AND definition.layer = 'L1'
                FOR UPDATE OF definition
                """, params, (rs, rowNumber) -> new LockedDefinition(
                rs.getObject("id", UUID.class),
                rs.getObject("current_version_id", UUID.class)));
        return rows.stream().findFirst();
    }

    @Override
    public void pointToCurrentVersion(UUID definitionId, UUID versionId) {
        int changed = jdbc.update("""
                UPDATE ranking_definitions
                SET current_version_id = :versionId,
                    updated_at = now(),
                    version = version + 1
                WHERE id = :definitionId
                """, new MapSqlParameterSource()
                .addValue("definitionId", definitionId)
                .addValue("versionId", versionId));
        if (changed != 1) {
            throw new IllegalStateException("Ranking definition current pointer update failed");
        }
    }

    @Override
    public void clearCurrentVersion(UUID definitionId, UUID expectedVersionId) {
        int changed = jdbc.update("""
                UPDATE ranking_definitions
                SET current_version_id = NULL,
                    updated_at = now(),
                    version = version + 1
                WHERE id = :definitionId
                  AND current_version_id = :expectedVersionId
                """, new MapSqlParameterSource()
                .addValue("definitionId", definitionId)
                .addValue("expectedVersionId", expectedVersionId));
        if (changed != 1) {
            throw new IllegalStateException("Ranking definition current pointer changed");
        }
    }
}
