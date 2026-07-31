package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.StudentCurrentRankingDetail;
import com.campusguinness.ranking.application.query.model.StudentOwnRanking;
import com.campusguinness.ranking.application.query.model.StudentRankingAvailability;
import com.campusguinness.ranking.application.query.model.StudentRankingEntry;
import com.campusguinness.ranking.application.query.model.StudentRankingProjectItem;
import com.campusguinness.ranking.application.query.model.TiePolicy;
import com.campusguinness.ranking.application.query.port.StudentRankingQueryPort;
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
class StudentRankingQueryAdapter implements StudentRankingQueryPort {

    private static final String PROJECT_ROWS = """
            WITH project_rows AS (
              SELECT ap.id AS activity_project_id,
                     activity.id AS activity_id,
                     activity.title AS activity_title,
                     activity.execution_status,
                     activity.updated_at AS activity_updated_at,
                     school.id AS school_id,
                     school.name AS school_name,
                     project.id AS project_id,
                     project.name AS project_name,
                     project.score_storage_type,
                     project.score_unit,
                     project.comparison_direction,
                     current_version.version_number AS current_version_number,
                     current_version.published_at,
                     CASE WHEN current_version.id IS NULL THEN NULL
                          ELSE (SELECT COUNT(*)
                                FROM ranking_entries entry
                                WHERE entry.version_id = current_version.id)
                     END AS total_ranked,
                     own_entry.rank_position AS my_rank,
                     own_entry.score_display_value AS my_score_display_value,
                     CASE
                       WHEN project.comparison_direction = 'NO_RANKING'
                         THEN 'DISABLED'
                       WHEN current_version.id IS NOT NULL
                         THEN 'CURRENT'
                       WHEN latest_version.version_status = 'WITHDRAWN'
                         THEN 'WITHDRAWN'
                       ELSE 'NOT_PUBLISHED'
                     END AS ranking_availability
              FROM school_memberships student_membership
              JOIN activity_participants participant
                ON participant.student_membership_id = student_membership.id
              JOIN activity_project_participants assignment
                ON assignment.activity_participant_id = participant.id
              JOIN activity_projects ap
                ON ap.id = assignment.activity_project_id
              JOIN activities activity
                ON activity.id = ap.activity_id
               AND activity.id = participant.activity_id
               AND activity.school_id = student_membership.school_id
              JOIN schools school
                ON school.id = activity.school_id
              JOIN challenge_projects project
                ON project.id = ap.project_id
              LEFT JOIN ranking_definitions definition
                ON definition.activity_project_id = ap.id
               AND definition.layer = 'L1'
               AND definition.is_enabled = true
              LEFT JOIN ranking_versions current_version
                ON current_version.id = definition.current_version_id
               AND current_version.version_status = 'PUBLISHED'
               AND current_version.withdrawn_at IS NULL
              LEFT JOIN LATERAL (
                SELECT version.version_status
                FROM ranking_versions version
                WHERE version.definition_id = definition.id
                ORDER BY version.version_number DESC
                LIMIT 1
              ) latest_version ON true
              LEFT JOIN ranking_entries own_entry
                ON own_entry.version_id = current_version.id
               AND own_entry.student_id = student_membership.user_id
              WHERE student_membership.user_id = :actorId
                AND student_membership.role_in_school = 'STUDENT'
                AND student_membership.status = 'ACTIVE'
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    StudentRankingQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<StudentRankingProjectItem> findRankingProjects(
            UUID actorId,
            String executionStatus,
            String rankingAvailability,
            String keyword,
            int page,
            int size) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("executionStatus", executionStatus)
                .addValue("rankingAvailability", rankingAvailability)
                .addValue("keyword", keywordPattern(keyword))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String where = """
                WHERE (CAST(:executionStatus AS text) IS NULL
                       OR execution_status = :executionStatus)
                  AND (CAST(:rankingAvailability AS text) IS NULL
                       OR ranking_availability = :rankingAvailability)
                  AND (CAST(:keyword AS text) IS NULL
                       OR LOWER(school_name) LIKE :keyword ESCAPE '\\'
                       OR LOWER(activity_title) LIKE :keyword ESCAPE '\\'
                       OR LOWER(project_name) LIKE :keyword ESCAPE '\\')
                """;
        Long total = jdbc.queryForObject(
                PROJECT_ROWS + "SELECT COUNT(*) FROM project_rows " + where,
                params,
                Long.class);
        List<StudentRankingProjectItem> items = jdbc.query(
                PROJECT_ROWS + "SELECT * FROM project_rows " + where + """
                        ORDER BY CASE WHEN ranking_availability = 'CURRENT'
                                      THEN 0 ELSE 1 END,
                                 published_at DESC NULLS LAST,
                                 activity_updated_at DESC,
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
    public Optional<StudentCurrentRankingDetail> findAccessibleCurrentRanking(
            UUID actorId, UUID activityProjectId) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("activityProjectId", activityProjectId);
        List<CurrentVersionRow> versions = jdbc.query("""
                SELECT ap.id AS activity_project_id,
                       activity.id AS activity_id,
                       activity.title AS activity_title,
                       school.name AS school_name,
                       project.id AS project_id,
                       project.name AS project_name,
                       version.id AS version_id,
                       version.version_number,
                       version.published_at,
                       version.calculation_params ->> 'scoreStorageType'
                         AS score_storage_type,
                       version.calculation_params ->> 'scoreUnit'
                         AS score_unit,
                       version.calculation_params ->> 'comparisonDirection'
                         AS comparison_direction,
                       version.calculation_params ->> 'effectiveScoreRule'
                         AS effective_score_rule,
                       COALESCE(
                         version.calculation_params ->> 'tiePolicy',
                         definition.tie_break_rule,
                         'COMPETITION') AS tie_policy,
                       (SELECT COUNT(*)
                        FROM ranking_entries entry
                        WHERE entry.version_id = version.id) AS total_ranked,
                       own_entry.rank_position AS my_rank,
                       own_entry.score_display_value AS my_score_display_value
                FROM school_memberships student_membership
                JOIN activity_participants participant
                  ON participant.student_membership_id = student_membership.id
                JOIN activity_project_participants assignment
                  ON assignment.activity_participant_id = participant.id
                JOIN activity_projects ap
                  ON ap.id = assignment.activity_project_id
                JOIN activities activity
                  ON activity.id = ap.activity_id
                 AND activity.id = participant.activity_id
                 AND activity.school_id = student_membership.school_id
                JOIN schools school ON school.id = activity.school_id
                JOIN challenge_projects project ON project.id = ap.project_id
                JOIN ranking_definitions definition
                  ON definition.activity_project_id = ap.id
                 AND definition.layer = 'L1'
                 AND definition.is_enabled = true
                JOIN ranking_versions version
                  ON version.id = definition.current_version_id
                 AND version.version_status = 'PUBLISHED'
                 AND version.withdrawn_at IS NULL
                LEFT JOIN ranking_entries own_entry
                  ON own_entry.version_id = version.id
                 AND own_entry.student_id = student_membership.user_id
                WHERE student_membership.user_id = :actorId
                  AND student_membership.role_in_school = 'STUDENT'
                  AND student_membership.status = 'ACTIVE'
                  AND ap.id = :activityProjectId
                  AND project.comparison_direction <> 'NO_RANKING'
                """,
                params,
                (rs, rowNumber) -> mapCurrentVersion(rs));
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        CurrentVersionRow version = versions.getFirst();
        List<StudentRankingEntry> entries = findEntries(
                version.versionId(), actorId);
        return Optional.of(new StudentCurrentRankingDetail(
                version.activityProjectId(),
                version.activityId(),
                version.activityTitle(),
                version.schoolName(),
                version.projectId(),
                version.projectName(),
                version.scoreStorageType(),
                version.scoreUnit(),
                version.comparisonDirection(),
                version.effectiveScoreRule(),
                TiePolicy.valueOf(version.tiePolicy()),
                version.versionNumber(),
                version.publishedAt(),
                version.totalRanked(),
                version.myRank(),
                version.myScoreDisplayValue(),
                entries));
    }

    @Override
    public Optional<StudentOwnRanking> findOwnCurrentRanking(
            UUID actorId, UUID activityProjectId) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("activityProjectId", activityProjectId);
        List<StudentOwnRanking> rows = jdbc.query("""
                SELECT ap.id AS activity_project_id,
                       version.version_number,
                       version.published_at,
                       own_entry.rank_position,
                       own_entry.score_display_value,
                       (SELECT COUNT(*)
                        FROM ranking_entries entry
                        WHERE entry.version_id = version.id) AS total_ranked
                FROM school_memberships student_membership
                JOIN activity_participants participant
                  ON participant.student_membership_id = student_membership.id
                JOIN activity_project_participants assignment
                  ON assignment.activity_participant_id = participant.id
                JOIN activity_projects ap
                  ON ap.id = assignment.activity_project_id
                JOIN activities activity
                  ON activity.id = ap.activity_id
                 AND activity.id = participant.activity_id
                 AND activity.school_id = student_membership.school_id
                JOIN challenge_projects project ON project.id = ap.project_id
                JOIN ranking_definitions definition
                  ON definition.activity_project_id = ap.id
                 AND definition.layer = 'L1'
                 AND definition.is_enabled = true
                JOIN ranking_versions version
                  ON version.id = definition.current_version_id
                 AND version.version_status = 'PUBLISHED'
                 AND version.withdrawn_at IS NULL
                JOIN ranking_entries own_entry
                  ON own_entry.version_id = version.id
                 AND own_entry.student_id = student_membership.user_id
                WHERE student_membership.user_id = :actorId
                  AND student_membership.role_in_school = 'STUDENT'
                  AND student_membership.status = 'ACTIVE'
                  AND ap.id = :activityProjectId
                  AND project.comparison_direction <> 'NO_RANKING'
                """,
                params,
                (rs, rowNumber) -> new StudentOwnRanking(
                        rs.getObject("activity_project_id", UUID.class),
                        rs.getInt("version_number"),
                        rs.getInt("rank_position"),
                        rs.getString("score_display_value"),
                        rs.getLong("total_ranked"),
                        instant(rs, "published_at")));
        return rows.stream().findFirst();
    }

    @Override
    public boolean existsAccessibleAssignment(
            UUID actorId, UUID activityProjectId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1
                  FROM school_memberships student_membership
                  JOIN activity_participants participant
                    ON participant.student_membership_id = student_membership.id
                  JOIN activity_project_participants assignment
                    ON assignment.activity_participant_id = participant.id
                  JOIN activity_projects ap
                    ON ap.id = assignment.activity_project_id
                  JOIN activities activity
                    ON activity.id = ap.activity_id
                   AND activity.id = participant.activity_id
                   AND activity.school_id = student_membership.school_id
                  WHERE student_membership.user_id = :actorId
                    AND student_membership.role_in_school = 'STUDENT'
                    AND student_membership.status = 'ACTIVE'
                    AND ap.id = :activityProjectId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("actorId", actorId)
                        .addValue("activityProjectId", activityProjectId),
                Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private List<StudentRankingEntry> findEntries(
            UUID versionId, UUID actorId) {
        return jdbc.query("""
                SELECT entry.rank_position,
                       entry.student_display_name,
                       entry.score_display_value,
                       entry.student_id = :actorId AS is_current_student
                FROM ranking_entries entry
                JOIN ranking_entry_score_sources source
                  ON source.entry_id = entry.id
                 AND source.student_id = entry.student_id
                WHERE entry.version_id = :versionId
                ORDER BY entry.rank_position ASC, source.score_attempt_id ASC
                """,
                new MapSqlParameterSource()
                        .addValue("actorId", actorId)
                        .addValue("versionId", versionId),
                (rs, rowNumber) -> new StudentRankingEntry(
                        rs.getInt("rank_position"),
                        rs.getString("student_display_name"),
                        rs.getString("score_display_value"),
                        rs.getBoolean("is_current_student")));
    }

    private static StudentRankingProjectItem mapProjectItem(ResultSet rs)
            throws SQLException {
        return new StudentRankingProjectItem(
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getString("execution_status"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getString("score_storage_type"),
                rs.getString("score_unit"),
                rs.getString("comparison_direction"),
                StudentRankingAvailability.valueOf(
                        rs.getString("ranking_availability")),
                nullableInteger(rs, "current_version_number"),
                instant(rs, "published_at"),
                nullableLong(rs, "total_ranked"),
                nullableInteger(rs, "my_rank"),
                rs.getString("my_score_display_value"));
    }

    private static CurrentVersionRow mapCurrentVersion(ResultSet rs)
            throws SQLException {
        return new CurrentVersionRow(
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getString("school_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("version_id", UUID.class),
                rs.getInt("version_number"),
                instant(rs, "published_at"),
                rs.getString("score_storage_type"),
                rs.getString("score_unit"),
                rs.getString("comparison_direction"),
                rs.getString("effective_score_rule"),
                rs.getString("tie_policy"),
                rs.getLong("total_ranked"),
                nullableInteger(rs, "my_rank"),
                rs.getString("my_score_display_value"));
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

    private record CurrentVersionRow(
            UUID activityProjectId,
            UUID activityId,
            String activityTitle,
            String schoolName,
            UUID projectId,
            String projectName,
            UUID versionId,
            int versionNumber,
            Instant publishedAt,
            String scoreStorageType,
            String scoreUnit,
            String comparisonDirection,
            String effectiveScoreRule,
            String tiePolicy,
            long totalRanked,
            Integer myRank,
            String myScoreDisplayValue) {
    }
}
