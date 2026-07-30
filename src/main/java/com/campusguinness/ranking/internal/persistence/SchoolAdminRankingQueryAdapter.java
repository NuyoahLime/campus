package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingEntryItem;
import com.campusguinness.ranking.application.query.model.RankingProjectDetail;
import com.campusguinness.ranking.application.query.model.RankingProjectItem;
import com.campusguinness.ranking.application.query.model.RankingStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import com.campusguinness.ranking.application.query.model.RankingVersionStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionSummary;
import com.campusguinness.ranking.application.query.model.TiePolicy;
import com.campusguinness.ranking.application.query.port.SchoolAdminRankingQueryPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolAdminRankingQueryAdapter implements SchoolAdminRankingQueryPort {

    private static final String PROJECT_ROWS = """
            WITH score_counts AS (
              SELECT sa.activity_project_id,
                     COUNT(*) FILTER (
                       WHERE sa.score_status = 'APPROVED'
                         AND sa.is_current_effective = true
                     ) AS approved_effective_score_count,
                     COUNT(*) FILTER (
                       WHERE sa.score_status = 'PENDING_REVIEW'
                     ) AS pending_review_count
              FROM score_attempts sa
              JOIN activity_projects score_ap
                ON score_ap.id = sa.activity_project_id
              JOIN activities score_activity
                ON score_activity.id = score_ap.activity_id
              JOIN school_memberships student_membership
                ON student_membership.user_id = sa.student_id
               AND student_membership.school_id = score_activity.school_id
               AND student_membership.role_in_school = 'STUDENT'
               AND student_membership.status = 'ACTIVE'
              JOIN activity_participants participant
                ON participant.activity_id = score_activity.id
               AND participant.student_membership_id = student_membership.id
              JOIN activity_project_participants assignment
                ON assignment.activity_project_id = score_ap.id
               AND assignment.activity_participant_id = participant.id
              WHERE score_activity.school_id = :schoolId
                AND sa.school_id = :schoolId
              GROUP BY sa.activity_project_id
            ),
            project_rows AS (
              SELECT ap.id AS activity_project_id,
                     a.id AS activity_id,
                     a.title AS activity_title,
                     a.execution_status,
                     a.start_time,
                     a.end_time,
                     a.location,
                     a.updated_at AS activity_updated_at,
                     cp.id AS project_id,
                     cp.name AS project_name,
                     cp.description AS project_description,
                     cp.rules_text,
                     cp.score_storage_type,
                     cp.score_unit,
                     cp.comparison_direction,
                     cp.effective_score_rule,
                     cp.grade_order,
                     cp.decimal_places,
                     cp.allow_tie,
                     cp.current_rule_version_id,
                     COALESCE(sc.approved_effective_score_count, 0)
                       AS approved_effective_score_count,
                     COALESCE(sc.pending_review_count, 0)
                       AS pending_review_count,
                     current_version.id AS current_version_id,
                     current_version.version_number AS current_version_number,
                     current_version.published_at AS current_published_at,
                     CASE WHEN current_version.id IS NULL THEN NULL
                          ELSE (SELECT COUNT(*) FROM ranking_entries entry
                                WHERE entry.version_id = current_version.id)
                     END AS current_version_entry_count,
                     latest_version.version_status AS last_version_status,
                     latest_version.published_by AS last_published_by,
                     latest_publisher.username AS last_published_by_name,
                     latest_version.withdrawal_reason AS last_withdrawal_reason,
                     CASE
                       WHEN cp.comparison_direction = 'NO_RANKING' THEN 'DISABLED'
                       WHEN current_version.id IS NOT NULL THEN 'CURRENT'
                       WHEN latest_version.version_status = 'WITHDRAWN' THEN 'WITHDRAWN'
                       ELSE 'NOT_PUBLISHED'
                     END AS ranking_status
              FROM activity_projects ap
              JOIN activities a ON a.id = ap.activity_id
              JOIN challenge_projects cp ON cp.id = ap.project_id
              LEFT JOIN score_counts sc ON sc.activity_project_id = ap.id
              LEFT JOIN ranking_definitions definition
                ON definition.activity_project_id = ap.id
               AND definition.layer = 'L1'
              LEFT JOIN ranking_versions current_version
                ON current_version.id = definition.current_version_id
               AND current_version.version_status = 'PUBLISHED'
               AND current_version.withdrawn_at IS NULL
              LEFT JOIN LATERAL (
                SELECT version.version_status,
                       version.published_by,
                       version.withdrawal_reason
                FROM ranking_versions version
                WHERE version.definition_id = definition.id
                ORDER BY version.version_number DESC
                LIMIT 1
              ) latest_version ON true
              LEFT JOIN users latest_publisher
                ON latest_publisher.id = latest_version.published_by
              WHERE a.school_id = :schoolId
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    SchoolAdminRankingQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<RankingProjectItem> findProjects(
            UUID schoolId,
            String executionStatus,
            String rankingStatus,
            String keyword,
            int page,
            int size) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("executionStatus", executionStatus)
                .addValue("rankingStatus", rankingStatus)
                .addValue("keyword", keywordPattern(keyword))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String where = """
                WHERE (CAST(:executionStatus AS text) IS NULL
                       OR execution_status = :executionStatus)
                  AND (CAST(:rankingStatus AS text) IS NULL
                       OR ranking_status = :rankingStatus)
                  AND (CAST(:keyword AS text) IS NULL
                       OR LOWER(activity_title) LIKE :keyword ESCAPE '\\'
                       OR LOWER(project_name) LIKE :keyword ESCAPE '\\')
                """;
        Long total = jdbc.queryForObject(
                PROJECT_ROWS + "SELECT COUNT(*) FROM project_rows " + where,
                params,
                Long.class);
        List<RankingProjectItem> items = jdbc.query(
                PROJECT_ROWS + "SELECT * FROM project_rows " + where + """
                        ORDER BY activity_updated_at DESC,
                                 activity_title ASC,
                                 project_name ASC,
                                 activity_project_id ASC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNumber) -> mapProjectItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<RankingProjectDetail> findProject(
            UUID schoolId, UUID activityProjectId) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId);
        List<RankingProjectDetail> rows = jdbc.query(
                PROJECT_ROWS + """
                        SELECT *
                        FROM project_rows
                        WHERE activity_project_id = :activityProjectId
                        """,
                params,
                (rs, rowNumber) -> mapProjectDetail(rs));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<RankingVersionDetail> findCurrentVersion(
            UUID schoolId, UUID activityProjectId) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId);
        return findVersionDetail("""
                definition.current_version_id = version.id
                AND definition.activity_project_id = :activityProjectId
                AND version.version_status = 'PUBLISHED'
                AND version.withdrawn_at IS NULL
                """, params);
    }

    @Override
    public QueryPage<RankingVersionSummary> findVersions(
            UUID schoolId, UUID activityProjectId, int page, int size) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String from = """
                FROM ranking_versions version
                JOIN ranking_definitions definition
                  ON definition.id = version.definition_id
                JOIN activity_projects ap
                  ON ap.id = definition.activity_project_id
                JOIN activities activity
                  ON activity.id = ap.activity_id
                LEFT JOIN users publisher
                  ON publisher.id = version.published_by
                LEFT JOIN users withdrawer
                  ON withdrawer.id = version.withdrawn_by
                WHERE activity.school_id = :schoolId
                  AND definition.activity_project_id = :activityProjectId
                """;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + from, params, Long.class);
        List<RankingVersionSummary> items = jdbc.query("""
                SELECT version.id AS version_id,
                       version.version_number,
                       version.version_status,
                       version.published_by,
                       publisher.username AS published_by_name,
                       version.published_at,
                       version.withdrawn_by,
                       withdrawer.username AS withdrawn_by_name,
                       version.withdrawn_at,
                       version.withdrawal_reason,
                       version.created_reason,
                       (SELECT COUNT(*) FROM ranking_entries entry
                        WHERE entry.version_id = version.id) AS entry_count
                """ + from + """
                ORDER BY version.version_number DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNumber) -> mapVersionSummary(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<RankingVersionDetail> findVersion(UUID schoolId, UUID versionId) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("versionId", versionId);
        return findVersionDetail("version.id = :versionId", params);
    }

    @Override
    public Optional<UUID> findSchoolId(UUID activityProjectId) {
        var params = new MapSqlParameterSource("activityProjectId", activityProjectId);
        List<UUID> rows = jdbc.query("""
                SELECT activity.school_id
                FROM activity_projects ap
                JOIN activities activity ON activity.id = ap.activity_id
                WHERE ap.id = :activityProjectId
                """, params, (rs, rowNumber) ->
                rs.getObject("school_id", UUID.class));
        return rows.stream().findFirst();
    }

    private Optional<RankingVersionDetail> findVersionDetail(
            String predicate, MapSqlParameterSource params) {
        List<VersionRow> rows = jdbc.query("""
                SELECT version.id AS version_id,
                       version.version_number,
                       version.version_status,
                       version.published_by,
                       publisher.username AS published_by_name,
                       version.published_at,
                       version.withdrawn_by,
                       withdrawer.username AS withdrawn_by_name,
                       version.withdrawn_at,
                       version.withdrawal_reason,
                       version.created_reason,
                       definition.activity_project_id,
                       activity.title AS activity_title,
                       project.name AS project_name,
                       version.calculation_params ->> 'scoreStorageType'
                         AS score_storage_type,
                       version.calculation_params ->> 'scoreUnit' AS score_unit,
                       version.calculation_params ->> 'comparisonDirection'
                         AS comparison_direction,
                       version.calculation_params ->> 'effectiveScoreRule'
                         AS effective_score_rule,
                       version.calculation_params ->> 'tiePolicy' AS tie_policy,
                       version.calculation_params ->> 'gradeOrder' AS grade_order,
                       version.calculation_params ->> 'allowTie' AS allow_tie,
                       version.calculation_params ->> 'decimalPlaces' AS decimal_places,
                       version.calculation_params ->> 'currentRuleVersionId'
                         AS current_rule_version_id,
                       version.calculation_params ->> 'sourceFingerprint'
                         AS source_fingerprint,
                       (SELECT COUNT(*) FROM ranking_entries entry
                        WHERE entry.version_id = version.id) AS entry_count
                FROM ranking_versions version
                JOIN ranking_definitions definition
                  ON definition.id = version.definition_id
                JOIN activity_projects ap
                  ON ap.id = definition.activity_project_id
                JOIN activities activity
                  ON activity.id = ap.activity_id
                JOIN challenge_projects project
                  ON project.id = ap.project_id
                LEFT JOIN users publisher
                  ON publisher.id = version.published_by
                LEFT JOIN users withdrawer
                  ON withdrawer.id = version.withdrawn_by
                WHERE activity.school_id = :schoolId
                  AND
                """ + predicate,
                params,
                (rs, rowNumber) -> new VersionRow(
                        mapVersionSummary(rs),
                        rs.getObject("activity_project_id", UUID.class),
                        rs.getString("activity_title"),
                        rs.getString("project_name"),
                        rs.getString("score_storage_type"),
                        rs.getString("score_unit"),
                        rs.getString("comparison_direction"),
                        rs.getString("effective_score_rule"),
                        rs.getString("tie_policy"),
                        rs.getString("grade_order"),
                        Boolean.parseBoolean(rs.getString("allow_tie")),
                        parseInteger(rs.getString("decimal_places")),
                        parseUuid(rs.getString("current_rule_version_id")),
                        rs.getString("source_fingerprint")));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        VersionRow row = rows.getFirst();
        List<RankingEntryItem> entries = findEntries(row.summary().versionId());
        RankingVersionSummary summary = row.summary();
        return Optional.of(new RankingVersionDetail(
                summary.versionId(),
                summary.versionNumber(),
                summary.versionStatus(),
                summary.entryCount(),
                summary.publishedBy(),
                summary.publishedByName(),
                summary.publishedAt(),
                summary.withdrawnBy(),
                summary.withdrawnByName(),
                summary.withdrawnAt(),
                summary.withdrawalReason(),
                summary.createdReason(),
                row.activityProjectId(),
                row.activityTitle(),
                row.projectName(),
                row.scoreStorageType(),
                row.scoreUnit(),
                row.comparisonDirection(),
                row.effectiveScoreRule(),
                TiePolicy.valueOf(row.tiePolicy()),
                row.gradeOrder(),
                row.allowTie(),
                row.decimalPlaces(),
                row.currentRuleVersionId(),
                row.sourceFingerprint(),
                entries));
    }

    private List<RankingEntryItem> findEntries(UUID versionId) {
        return jdbc.query("""
                SELECT entry.rank_position,
                       entry.student_id,
                       entry.student_display_name,
                       entry.school_name,
                       entry.score_display_value,
                       source.score_attempt_id
                FROM ranking_entries entry
                JOIN ranking_entry_score_sources source
                  ON source.entry_id = entry.id
                 AND source.student_id = entry.student_id
                WHERE entry.version_id = :versionId
                ORDER BY entry.rank_position ASC, source.score_attempt_id ASC
                """,
                new MapSqlParameterSource("versionId", versionId),
                (rs, rowNumber) -> new RankingEntryItem(
                        rs.getInt("rank_position"),
                        rs.getObject("student_id", UUID.class),
                        rs.getString("student_display_name"),
                        rs.getString("school_name"),
                        rs.getString("score_display_value"),
                        rs.getObject("score_attempt_id", UUID.class),
                        null));
    }

    private static RankingProjectItem mapProjectItem(ResultSet rs)
            throws SQLException {
        String executionStatus = rs.getString("execution_status");
        String comparisonDirection = rs.getString("comparison_direction");
        long approvedCount = rs.getLong("approved_effective_score_count");
        long pendingCount = rs.getLong("pending_review_count");
        return new RankingProjectItem(
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                executionStatus,
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getString("score_storage_type"),
                rs.getString("score_unit"),
                comparisonDirection,
                rs.getString("effective_score_rule"),
                rs.getBoolean("allow_tie"),
                approvedCount,
                pendingCount,
                RankingStatus.valueOf(rs.getString("ranking_status")),
                rs.getObject("current_version_id", UUID.class),
                nullableInteger(rs, "current_version_number"),
                nullableLong(rs, "current_version_entry_count"),
                instant(rs, "current_published_at"),
                nullableVersionStatus(rs.getString("last_version_status")),
                canPreview(executionStatus, comparisonDirection),
                canPublish(
                        executionStatus, comparisonDirection, approvedCount, pendingCount));
    }

    private static RankingProjectDetail mapProjectDetail(ResultSet rs)
            throws SQLException {
        RankingProjectItem item = mapProjectItem(rs);
        return new RankingProjectDetail(
                item.activityProjectId(),
                item.activityId(),
                item.activityTitle(),
                item.executionStatus(),
                item.projectId(),
                item.projectName(),
                item.scoreStorageType(),
                item.scoreUnit(),
                item.comparisonDirection(),
                item.effectiveScoreRule(),
                item.allowTie(),
                item.approvedEffectiveScoreCount(),
                item.pendingReviewCount(),
                item.rankingStatus(),
                item.currentVersionId(),
                item.currentVersionNumber(),
                item.currentVersionEntryCount(),
                item.currentPublishedAt(),
                item.lastVersionStatus(),
                item.canPreview(),
                item.canPublish(),
                instant(rs, "start_time"),
                instant(rs, "end_time"),
                rs.getString("location"),
                rs.getString("project_description"),
                rs.getString("rules_text"),
                rs.getString("grade_order"),
                nullableInteger(rs, "decimal_places"),
                rs.getObject("current_rule_version_id", UUID.class),
                rs.getObject("last_published_by", UUID.class),
                rs.getString("last_published_by_name"),
                rs.getString("last_withdrawal_reason"));
    }

    private static RankingVersionSummary mapVersionSummary(ResultSet rs)
            throws SQLException {
        return new RankingVersionSummary(
                rs.getObject("version_id", UUID.class),
                rs.getInt("version_number"),
                RankingVersionStatus.valueOf(rs.getString("version_status")),
                rs.getLong("entry_count"),
                rs.getObject("published_by", UUID.class),
                rs.getString("published_by_name"),
                instant(rs, "published_at"),
                rs.getObject("withdrawn_by", UUID.class),
                rs.getString("withdrawn_by_name"),
                instant(rs, "withdrawn_at"),
                rs.getString("withdrawal_reason"),
                rs.getString("created_reason"));
    }

    private static boolean canPreview(String executionStatus, String direction) {
        return !"NO_RANKING".equals(direction)
                && ("IN_PROGRESS".equals(executionStatus)
                || "ENDED".equals(executionStatus));
    }

    private static boolean canPublish(
            String executionStatus, String direction, long scores, long pending) {
        return "ENDED".equals(executionStatus)
                && !"NO_RANKING".equals(direction)
                && pending == 0
                && scores > 0;
    }

    private static String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private static RankingVersionStatus nullableVersionStatus(String value) {
        return value == null ? null : RankingVersionStatus.valueOf(value);
    }

    private static Instant instant(ResultSet rs, String column)
            throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet rs, String column)
            throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column)
            throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer parseInteger(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static UUID parseUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private record VersionRow(
            RankingVersionSummary summary,
            UUID activityProjectId,
            String activityTitle,
            String projectName,
            String scoreStorageType,
            String scoreUnit,
            String comparisonDirection,
            String effectiveScoreRule,
            String tiePolicy,
            String gradeOrder,
            boolean allowTie,
            Integer decimalPlaces,
            UUID currentRuleVersionId,
            String sourceFingerprint) {
    }
}
