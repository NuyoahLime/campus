package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.RankingPublicationPort;
import com.campusguinness.ranking.application.query.model.CalculatedRankingEntry;
import com.campusguinness.ranking.application.query.model.RankingVersionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RankingPublicationAdapter implements RankingPublicationPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RankingPublicationAdapter(
            NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public int nextVersionNumber(UUID definitionId) {
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_number), 0) + 1
                FROM ranking_versions
                WHERE definition_id = :definitionId
                """,
                new MapSqlParameterSource("definitionId", definitionId),
                Integer.class);
        return next == null ? 1 : next;
    }

    @Override
    public UUID createPublishedVersion(
            UUID definitionId,
            int versionNumber,
            UUID previousVersionId,
            UUID publishedBy,
            Map<String, Object> calculationParams,
            Map<String, Object> dataScopeSnapshot) {
        UUID versionId = UUID.randomUUID();
        var params = new MapSqlParameterSource()
                .addValue("id", versionId)
                .addValue("definitionId", definitionId)
                .addValue("versionNumber", versionNumber)
                .addValue("previousVersionId", previousVersionId)
                .addValue("calculationParams", json(calculationParams))
                .addValue("dataScopeSnapshot", json(dataScopeSnapshot))
                .addValue("publishedBy", publishedBy);
        jdbc.update("""
                INSERT INTO ranking_versions(
                  id, definition_id, version_number, previous_version_id,
                  version_status, calculation_params, data_scope_snapshot,
                  authorization_ids_snapshot, generated_at, published_at,
                  published_by, created_reason, created_at)
                VALUES (
                  :id, :definitionId, :versionNumber, :previousVersionId,
                  'PUBLISHED', CAST(:calculationParams AS jsonb),
                  CAST(:dataScopeSnapshot AS jsonb), '[]'::jsonb,
                  now(), now(), :publishedBy, 'MANUAL', now())
                """, params);
        return versionId;
    }

    @Override
    public void saveEntries(
            UUID versionId, List<CalculatedRankingEntry> entries) {
        for (CalculatedRankingEntry entry : entries) {
            UUID entryId = UUID.randomUUID();
            var params = new MapSqlParameterSource()
                    .addValue("id", entryId)
                    .addValue("versionId", versionId)
                    .addValue("studentId", entry.studentId())
                    .addValue("rankPosition", entry.rankPosition())
                    .addValue("studentDisplayName", entry.studentDisplayName())
                    .addValue("schoolName", entry.schoolName())
                    .addValue("scoreDisplayValue", entry.scoreDisplayValue())
                    .addValue("ruleVersionId", entry.ruleVersionId())
                    .addValue("scoreAttemptId", entry.scoreAttemptId());
            jdbc.update("""
                    INSERT INTO ranking_entries(
                      id, version_id, student_id, rank_position,
                      student_display_name, school_name, score_display_value,
                      rule_version_id, created_at)
                    VALUES (
                      :id, :versionId, :studentId, :rankPosition,
                      :studentDisplayName, :schoolName, :scoreDisplayValue,
                      :ruleVersionId, now())
                    """, params);
            jdbc.update("""
                    INSERT INTO ranking_entry_score_sources(
                      entry_id, student_id, score_attempt_id, created_at)
                    VALUES (
                      :id, :studentId, :scoreAttemptId, now())
                    """, params);
        }
    }

    @Override
    public void markReplaced(UUID versionId) {
        int changed = jdbc.update("""
                UPDATE ranking_versions
                SET version_status = 'REPLACED'
                WHERE id = :versionId
                  AND version_status = 'PUBLISHED'
                  AND withdrawn_at IS NULL
                """, new MapSqlParameterSource("versionId", versionId));
        if (changed != 1) {
            throw new IllegalStateException(
                    "Current ranking version cannot be replaced");
        }
    }

    @Override
    public Optional<LockedVersion> lockVersion(UUID versionId) {
        List<LockedVersion> rows = jdbc.query("""
                SELECT id, version_status, withdrawn_at
                FROM ranking_versions
                WHERE id = :versionId
                FOR UPDATE
                """,
                new MapSqlParameterSource("versionId", versionId),
                (rs, rowNumber) -> {
                    Timestamp withdrawnAt = rs.getTimestamp("withdrawn_at");
                    return new LockedVersion(
                            rs.getObject("id", UUID.class),
                            RankingVersionStatus.valueOf(
                                    rs.getString("version_status")),
                            withdrawnAt == null ? null : withdrawnAt.toInstant());
                });
        return rows.stream().findFirst();
    }

    @Override
    public void withdrawVersion(UUID versionId, UUID withdrawnBy, String reason) {
        int changed = jdbc.update("""
                UPDATE ranking_versions
                SET version_status = 'WITHDRAWN',
                    withdrawn_at = now(),
                    withdrawn_by = :withdrawnBy,
                    withdrawal_reason = :reason
                WHERE id = :versionId
                  AND version_status = 'PUBLISHED'
                  AND withdrawn_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("versionId", versionId)
                .addValue("withdrawnBy", withdrawnBy)
                .addValue("reason", reason));
        if (changed != 1) {
            throw new IllegalStateException("Ranking version cannot be withdrawn");
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Ranking snapshot could not be serialized", exception);
        }
    }
}
