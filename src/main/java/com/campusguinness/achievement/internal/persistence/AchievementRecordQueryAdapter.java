package com.campusguinness.achievement.internal.persistence;

import com.campusguinness.achievement.application.query.model.AchievementRecordDetail;
import com.campusguinness.achievement.application.query.model.AchievementRecordItem;
import com.campusguinness.achievement.application.query.model.AchievementStatus;
import com.campusguinness.achievement.application.query.model.PublicAchievementVerification;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementItem;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementStatus;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
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
class AchievementRecordQueryAdapter implements AchievementRecordQueryPort {

    private static final String ADMIN_SELECT = """
            SELECT record.id AS record_id,
                   record.activity_project_id,
                   record.ranking_version_id,
                   record.ranking_version_number_snapshot,
                   record.ranking_entry_id,
                   record.student_id,
                   entry.student_display_name,
                   record.school_name_snapshot,
                   record.activity_title_snapshot,
                   record.project_name_snapshot,
                   record.rank_snapshot,
                   record.score_value_snapshot,
                   record.score_storage_type,
                   record.record_title,
                   record.verification_code,
                   record.status,
                   record.issued_at,
                   record.issued_by,
                   issuer.username AS issued_by_name,
                   record.revoked_at,
                   record.revoked_by,
                   record.revocation_reason
            FROM achievement_records record
            JOIN ranking_entries entry
              ON entry.id = record.ranking_entry_id
            JOIN users issuer
              ON issuer.id = record.issued_by
            """;

    private final NamedParameterJdbcTemplate jdbc;

    AchievementRecordQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<AchievementRecordItem> findStudentRecords(
            UUID studentId,
            String status,
            String keyword,
            int page,
            int size) {
        var params = pageParams(status, keyword, page, size)
                .addValue("studentId", studentId);
        String where = """
                WHERE record.student_id = :studentId
                  AND (CAST(:status AS text) IS NULL
                       OR record.status = :status)
                  AND (CAST(:keyword AS text) IS NULL
                       OR LOWER(record.school_name_snapshot) LIKE :keyword ESCAPE '\\'
                       OR LOWER(record.activity_title_snapshot) LIKE :keyword ESCAPE '\\'
                       OR LOWER(record.project_name_snapshot) LIKE :keyword ESCAPE '\\'
                       OR LOWER(record.record_title) LIKE :keyword ESCAPE '\\'
                       OR record.verification_code LIKE :keyword ESCAPE '\\')
                """;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM achievement_records record " + where,
                params,
                Long.class);
        List<AchievementRecordItem> items = jdbc.query("""
                SELECT record.id AS record_id,
                       record.record_title,
                       record.school_name_snapshot,
                       record.activity_title_snapshot,
                       record.project_name_snapshot,
                       record.ranking_version_number_snapshot,
                       record.rank_snapshot,
                       record.score_value_snapshot,
                       record.score_storage_type,
                       record.verification_code,
                       record.status,
                       record.issued_at,
                       record.revoked_at
                FROM achievement_records record
                """ + where + """
                ORDER BY record.issued_at DESC, record.id DESC
                LIMIT :limit OFFSET :offset
                """,
                params,
                (rs, rowNumber) -> mapStudentItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<AchievementRecordDetail> findStudentRecord(
            UUID studentId, UUID recordId) {
        List<AchievementRecordDetail> rows = jdbc.query("""
                SELECT record.id AS record_id,
                       record.record_title,
                       record.school_name_snapshot,
                       record.activity_title_snapshot,
                       record.project_name_snapshot,
                       record.ranking_version_number_snapshot,
                       record.rank_snapshot,
                       record.score_value_snapshot,
                       record.score_storage_type,
                       record.verification_code,
                       record.status,
                       record.issued_at,
                       record.revoked_at,
                       record.ranking_version_id,
                       record.activity_project_id,
                       record.revocation_reason
                FROM achievement_records record
                WHERE record.id = :recordId
                  AND record.student_id = :studentId
                """,
                new MapSqlParameterSource()
                        .addValue("recordId", recordId)
                        .addValue("studentId", studentId),
                (rs, rowNumber) -> mapStudentDetail(rs));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PublicAchievementVerification> findPublicVerification(
            String verificationCode) {
        List<PublicAchievementVerification> rows = jdbc.query("""
                SELECT record.record_title,
                       record.school_name_snapshot,
                       record.activity_title_snapshot,
                       record.project_name_snapshot,
                       record.ranking_version_number_snapshot,
                       record.rank_snapshot,
                       record.score_value_snapshot,
                       record.score_storage_type,
                       record.status,
                       record.issued_at,
                       record.revoked_at
                FROM achievement_records record
                WHERE record.verification_code = :verificationCode
                """,
                new MapSqlParameterSource(
                        "verificationCode", verificationCode),
                (rs, rowNumber) -> {
                    AchievementStatus status = status(rs);
                    return new PublicAchievementVerification(
                            status == AchievementStatus.ACTIVE,
                            status,
                            rs.getString("record_title"),
                            rs.getString("school_name_snapshot"),
                            rs.getString("activity_title_snapshot"),
                            rs.getString("project_name_snapshot"),
                            rs.getInt("ranking_version_number_snapshot"),
                            rs.getInt("rank_snapshot"),
                            rs.getString("score_value_snapshot"),
                            rs.getString("score_storage_type"),
                            instant(rs, "issued_at"),
                            instant(rs, "revoked_at"));
                });
        return rows.stream().findFirst();
    }

    @Override
    public boolean existsSchoolProject(
            UUID schoolId, UUID activityProjectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1
                  FROM activity_projects activity_project
                  JOIN activities activity
                    ON activity.id = activity_project.activity_id
                  WHERE activity_project.id = :activityProjectId
                    AND activity.school_id = :schoolId)
                """,
                new MapSqlParameterSource()
                        .addValue("schoolId", schoolId)
                        .addValue("activityProjectId", activityProjectId),
                Boolean.class));
    }

    @Override
    public boolean existsSchoolL1Version(
            UUID schoolId, UUID rankingVersionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1
                  FROM ranking_versions version
                  JOIN ranking_definitions definition
                    ON definition.id = version.definition_id
                   AND definition.layer = 'L1'
                  JOIN activity_projects activity_project
                    ON activity_project.id = definition.activity_project_id
                  JOIN activities activity
                    ON activity.id = activity_project.activity_id
                  WHERE version.id = :rankingVersionId
                    AND activity.school_id = :schoolId)
                """,
                new MapSqlParameterSource()
                        .addValue("schoolId", schoolId)
                        .addValue("rankingVersionId", rankingVersionId),
                Boolean.class));
    }

    @Override
    public QueryPage<SchoolAdminAchievementItem> findSchoolProjectRecords(
            UUID schoolId,
            UUID activityProjectId,
            String status,
            String keyword,
            int page,
            int size) {
        var params = pageParams(status, keyword, page, size)
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId);
        String joins = """
                FROM achievement_records record
                JOIN ranking_entries entry
                  ON entry.id = record.ranking_entry_id
                JOIN activity_projects activity_project
                  ON activity_project.id = record.activity_project_id
                JOIN activities activity
                  ON activity.id = activity_project.activity_id
                """;
        String where = """
                WHERE activity.school_id = :schoolId
                  AND record.activity_project_id = :activityProjectId
                  AND (CAST(:status AS text) IS NULL
                       OR record.status = :status)
                  AND (CAST(:keyword AS text) IS NULL
                       OR LOWER(entry.student_display_name) LIKE :keyword ESCAPE '\\'
                       OR LOWER(record.activity_title_snapshot) LIKE :keyword ESCAPE '\\'
                       OR LOWER(record.project_name_snapshot) LIKE :keyword ESCAPE '\\'
                       OR record.verification_code LIKE :keyword ESCAPE '\\')
                """;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + joins + where, params, Long.class);
        List<SchoolAdminAchievementItem> items = jdbc.query("""
                SELECT record.id AS record_id,
                       record.ranking_entry_id,
                       record.student_id,
                       entry.student_display_name,
                       record.record_title,
                       record.school_name_snapshot,
                       record.activity_title_snapshot,
                       record.project_name_snapshot,
                       record.ranking_version_number_snapshot,
                       record.rank_snapshot,
                       record.score_value_snapshot,
                       record.score_storage_type,
                       record.verification_code,
                       record.status,
                       record.issued_at,
                       record.revoked_at
                """ + joins + where + """
                ORDER BY record.issued_at DESC, record.id DESC
                LIMIT :limit OFFSET :offset
                """,
                params,
                (rs, rowNumber) -> mapAdminItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<SchoolAdminAchievementDetail> findSchoolRecord(
            UUID schoolId, UUID recordId) {
        List<SchoolAdminAchievementDetail> rows = jdbc.query(
                ADMIN_SELECT + """
                JOIN activity_projects activity_project
                  ON activity_project.id = record.activity_project_id
                JOIN activities activity
                  ON activity.id = activity_project.activity_id
                WHERE record.id = :recordId
                  AND activity.school_id = :schoolId
                """,
                new MapSqlParameterSource()
                        .addValue("schoolId", schoolId)
                        .addValue("recordId", recordId),
                (rs, rowNumber) -> mapAdminDetail(rs, false));
        return rows.stream().findFirst();
    }

    @Override
    public List<SchoolAdminAchievementStatus> findVersionStatuses(
            UUID schoolId, UUID rankingVersionId) {
        return jdbc.query("""
                SELECT entry.id AS ranking_entry_id,
                       record.id AS achievement_record_id,
                       record.status,
                       record.verification_code,
                       record.issued_at
                FROM ranking_entries entry
                JOIN ranking_versions version
                  ON version.id = entry.version_id
                JOIN ranking_definitions definition
                  ON definition.id = version.definition_id
                 AND definition.layer = 'L1'
                JOIN activity_projects activity_project
                  ON activity_project.id = definition.activity_project_id
                JOIN activities activity
                  ON activity.id = activity_project.activity_id
                LEFT JOIN achievement_records record
                  ON record.ranking_entry_id = entry.id
                WHERE version.id = :rankingVersionId
                  AND activity.school_id = :schoolId
                ORDER BY entry.rank_position ASC, entry.id ASC
                """,
                new MapSqlParameterSource()
                        .addValue("schoolId", schoolId)
                        .addValue("rankingVersionId", rankingVersionId),
                (rs, rowNumber) -> new SchoolAdminAchievementStatus(
                        rs.getObject("ranking_entry_id", UUID.class),
                        rs.getObject("achievement_record_id", UUID.class),
                        nullableStatus(rs),
                        rs.getString("verification_code"),
                        instant(rs, "issued_at")));
    }

    @Override
    public List<SchoolAdminAchievementDetail> findProjectRecords(
            UUID activityProjectId) {
        return jdbc.query(
                ADMIN_SELECT + """
                WHERE record.activity_project_id = :activityProjectId
                ORDER BY record.issued_at DESC, record.id DESC
                """,
                new MapSqlParameterSource(
                        "activityProjectId", activityProjectId),
                (rs, rowNumber) -> mapAdminDetail(rs, false));
    }

    private static MapSqlParameterSource pageParams(
            String status, String keyword, int page, int size) {
        return new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("keyword", keywordPattern(keyword))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
    }

    private static AchievementRecordItem mapStudentItem(ResultSet rs)
            throws SQLException {
        return new AchievementRecordItem(
                rs.getObject("record_id", UUID.class),
                rs.getString("record_title"),
                rs.getString("school_name_snapshot"),
                rs.getString("activity_title_snapshot"),
                rs.getString("project_name_snapshot"),
                rs.getInt("ranking_version_number_snapshot"),
                rs.getInt("rank_snapshot"),
                rs.getString("score_value_snapshot"),
                rs.getString("score_storage_type"),
                rs.getString("verification_code"),
                status(rs),
                instant(rs, "issued_at"),
                instant(rs, "revoked_at"));
    }

    private static AchievementRecordDetail mapStudentDetail(ResultSet rs)
            throws SQLException {
        return new AchievementRecordDetail(
                rs.getObject("record_id", UUID.class),
                rs.getString("record_title"),
                rs.getString("school_name_snapshot"),
                rs.getString("activity_title_snapshot"),
                rs.getString("project_name_snapshot"),
                rs.getInt("ranking_version_number_snapshot"),
                rs.getInt("rank_snapshot"),
                rs.getString("score_value_snapshot"),
                rs.getString("score_storage_type"),
                rs.getString("verification_code"),
                status(rs),
                instant(rs, "issued_at"),
                instant(rs, "revoked_at"),
                rs.getObject("ranking_version_id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getString("revocation_reason"));
    }

    private static SchoolAdminAchievementItem mapAdminItem(ResultSet rs)
            throws SQLException {
        return new SchoolAdminAchievementItem(
                rs.getObject("record_id", UUID.class),
                rs.getObject("ranking_entry_id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_display_name"),
                rs.getString("record_title"),
                rs.getString("school_name_snapshot"),
                rs.getString("activity_title_snapshot"),
                rs.getString("project_name_snapshot"),
                rs.getInt("ranking_version_number_snapshot"),
                rs.getInt("rank_snapshot"),
                rs.getString("score_value_snapshot"),
                rs.getString("score_storage_type"),
                rs.getString("verification_code"),
                status(rs),
                instant(rs, "issued_at"),
                instant(rs, "revoked_at"));
    }

    static SchoolAdminAchievementDetail mapAdminDetail(
            ResultSet rs, boolean created) throws SQLException {
        return new SchoolAdminAchievementDetail(
                rs.getObject("record_id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("ranking_version_id", UUID.class),
                rs.getInt("ranking_version_number_snapshot"),
                rs.getObject("ranking_entry_id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_display_name"),
                rs.getString("school_name_snapshot"),
                rs.getString("activity_title_snapshot"),
                rs.getString("project_name_snapshot"),
                rs.getInt("rank_snapshot"),
                rs.getString("score_value_snapshot"),
                rs.getString("score_storage_type"),
                rs.getString("record_title"),
                rs.getString("verification_code"),
                status(rs),
                instant(rs, "issued_at"),
                rs.getObject("issued_by", UUID.class),
                rs.getString("issued_by_name"),
                instant(rs, "revoked_at"),
                rs.getObject("revoked_by", UUID.class),
                rs.getString("revocation_reason"),
                created);
    }

    private static AchievementStatus status(ResultSet rs)
            throws SQLException {
        return AchievementStatus.valueOf(rs.getString("status"));
    }

    private static AchievementStatus nullableStatus(ResultSet rs)
            throws SQLException {
        String value = rs.getString("status");
        return value == null ? null : AchievementStatus.valueOf(value);
    }

    private static Instant instant(ResultSet rs, String column)
            throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
