package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.RankingGenerationRepository;
import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.service.GeneratedRankingEntry;
import com.campusguinness.ranking.application.service.GeneratedRankingSnapshot;
import com.campusguinness.ranking.application.service.RankingGenerationScope;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Transactional
class RankingGenerationRepositoryAdapter implements RankingGenerationRepository {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    RankingGenerationRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RankingGenerationResult saveGeneratedSnapshot(
            RankingDefinition definition,
            RankingGenerationScope scope,
            RankingGenerationContext context,
            GeneratedRankingSnapshot snapshot) {
        jdbc.queryForObject("SELECT id FROM ranking_definitions WHERE id = ? FOR UPDATE", UUID.class, definition.id().value());
        UUID previousVersionId = jdbc.query("""
                SELECT id
                FROM ranking_versions
                WHERE definition_id = ?
                ORDER BY version_number DESC, created_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, definition.id().value());
        Integer maxVersion = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_number), 0)
                FROM ranking_versions
                WHERE definition_id = ?
                """, Integer.class, definition.id().value());
        int versionNumber = maxVersion == null ? 1 : maxVersion + 1;
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO ranking_versions(
                    id, definition_id, version_number, previous_version_id, version_status,
                    calculation_params, data_scope_snapshot, authorization_ids_snapshot,
                    generated_at, created_reason, created_at
                ) VALUES (?, ?, ?, ?, 'GENERATED', ?::jsonb, ?::jsonb, ?::jsonb, ?, 'GENERATED', ?)
                """,
                versionId,
                definition.id().value(),
                versionNumber,
                previousVersionId,
                json(calculationParams(context, snapshot)),
                json(dataScopeSnapshot(scope, context, snapshot)),
                jsonValue(snapshot.authorizationIdsSnapshot()),
                Timestamp.from(now),
                Timestamp.from(now));
        insertEntries(versionId, snapshot.entries());
        return new RankingGenerationResult(definition.id().value(), versionId, versionNumber, snapshot.entryCount(), "GENERATED", now);
    }

    private void insertEntries(UUID versionId, List<GeneratedRankingEntry> entries) {
        for (GeneratedRankingEntry entry : entries) {
            UUID rankingEntryId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO ranking_entries(
                        id, version_id, student_id, rank_position, student_display_name,
                        school_name, score_display_value, rule_version_id, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    rankingEntryId,
                    versionId,
                    entry.studentId(),
                    entry.rankPosition(),
                    entry.studentDisplayName(),
                    entry.schoolName(),
                    entry.scoreDisplayValue(),
                    entry.ruleVersionId(),
                    Timestamp.from(Instant.now()));
            jdbc.update("""
                    INSERT INTO ranking_entry_score_sources(
                        id, entry_id, student_id, score_attempt_id, created_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    rankingEntryId,
                    entry.studentId(),
                    entry.scoreAttemptId(),
                    Timestamp.from(Instant.now()));
        }
    }

    private String calculationParams(RankingGenerationContext context, GeneratedRankingSnapshot snapshot) {
        return """
                {
                  "tiePolicy": %s,
                  "ruleVersionNumber": %d,
                  "scoreStorageType": %s,
                  "comparisonDirection": %s
                }
                """.formatted(jsonValue(snapshot.tiePolicy()), context.ruleVersionNumber(),
                jsonValue(context.scoreStorageType()), jsonValue(context.comparisonDirection()));
    }

    private String dataScopeSnapshot(RankingGenerationScope scope, RankingGenerationContext context, GeneratedRankingSnapshot snapshot) {
        if (scope.layer() == RankingLayer.L3) {
            return """
                    {
                      "layer": "L3",
                      "projectId": %s,
                      "ruleVersionId": %s,
                      "selectionPolicy": %s,
                      "authorizationCount": %d,
                      "authorizationIds": %s
                    }
                    """.formatted(
                    jsonValue(context.projectId().toString()),
                    jsonValue(context.ruleVersionId().toString()),
                    jsonValue(scope.selectionPolicy()),
                    snapshot.authorizationIdsSnapshot().size(),
                    jsonValue(snapshot.authorizationIdsSnapshot()));
        }
        if (scope.layer() == RankingLayer.L2) {
            return """
                    {
                      "layer": "L2",
                      "schoolId": %s,
                      "projectId": %s,
                      "ruleVersionId": %s,
                      "selectionPolicy": %s,
                      "grade": %s,
                      "className": %s,
                      "activityPeriodStart": %s,
                      "activityPeriodEnd": %s
                    }
                    """.formatted(
                    jsonValue(context.schoolId().toString()),
                    jsonValue(context.projectId().toString()),
                    jsonValue(context.ruleVersionId().toString()),
                    jsonValue(scope.selectionPolicy()),
                    jsonNullable(scope.grade()),
                    jsonNullable(scope.className()),
                    jsonNullable(scope.activityPeriodStart() == null ? null : scope.activityPeriodStart().toString()),
                    jsonNullable(scope.activityPeriodEnd() == null ? null : scope.activityPeriodEnd().toString()));
        }
        return """
                {
                  "layer": "L1",
                  "activityProjectId": %s,
                  "activityId": %s,
                  "schoolId": %s,
                  "projectId": %s,
                  "ruleVersionId": %s
                }
                """.formatted(jsonValue(scope.activityProjectId().toString()),
                jsonValue(context.activityId().toString()),
                jsonValue(context.schoolId().toString()),
                jsonValue(context.projectId().toString()),
                jsonValue(context.ruleVersionId().toString()));
    }

    private String json(String raw) {
        try {
            return JSON.readTree(raw).toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: snapshot payload is invalid.");
        }
    }

    private String jsonValue(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: snapshot payload is invalid.");
        }
    }

    private String jsonValue(List<UUID> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate ranking: snapshot payload is invalid.");
        }
    }

    private String jsonNullable(String value) {
        return value == null ? "null" : jsonValue(value);
    }
}
