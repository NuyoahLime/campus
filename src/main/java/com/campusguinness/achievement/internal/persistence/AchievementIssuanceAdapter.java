package com.campusguinness.achievement.internal.persistence;

import com.campusguinness.achievement.application.exception.AchievementVerificationCodeCollisionException;
import com.campusguinness.achievement.application.port.AchievementIssuancePort;
import com.campusguinness.achievement.application.query.model.AchievementIssueResult;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class AchievementIssuanceAdapter implements AchievementIssuancePort {

    private static final String DETAIL_SELECT = """
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
            JOIN activity_projects activity_project
              ON activity_project.id = record.activity_project_id
            JOIN activities activity
              ON activity.id = activity_project.activity_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    AchievementIssuanceAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(
            noRollbackFor =
                    AchievementVerificationCodeCollisionException.class)
    public Optional<AchievementIssueResult> issueForSchool(
            UUID schoolId,
            UUID rankingEntryId,
            UUID issuedBy,
            String verificationCode) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("rankingEntryId", rankingEntryId)
                .addValue("issuedBy", issuedBy)
                .addValue("verificationCode", verificationCode);
        return issue(
                params,
                " AND activity.school_id = :schoolId ",
                " AND activity.school_id = :schoolId ");
    }

    @Override
    @Transactional(
            noRollbackFor =
                    AchievementVerificationCodeCollisionException.class)
    public Optional<AchievementIssueResult> issueForActivityProject(
            UUID activityProjectId,
            UUID rankingEntryId,
            UUID issuedBy,
            String verificationCode) {
        var params = new MapSqlParameterSource()
                .addValue("activityProjectId", activityProjectId)
                .addValue("rankingEntryId", rankingEntryId)
                .addValue("issuedBy", issuedBy)
                .addValue("verificationCode", verificationCode);
        return issue(
                params,
                " AND activity_project.id = :activityProjectId ",
                " AND record.activity_project_id = :activityProjectId ");
    }

    @Override
    @Transactional
    public void revokeByRankingVersion(
            UUID rankingVersionId, UUID revokedBy, String reason) {
        jdbc.update("""
                UPDATE achievement_records
                SET status = 'REVOKED',
                    revoked_at = now(),
                    revoked_by = :revokedBy,
                    revocation_reason = :reason
                WHERE ranking_version_id = :rankingVersionId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource()
                        .addValue("rankingVersionId", rankingVersionId)
                        .addValue("revokedBy", revokedBy)
                        .addValue("reason", reason));
    }

    private Optional<AchievementIssueResult> issue(
            MapSqlParameterSource params,
            String eligibilityScope,
            String recordScope) {
        List<UUID> inserted = jdbc.query("""
                INSERT INTO achievement_records(
                  activity_project_id,
                  ranking_version_id,
                  ranking_entry_id,
                  student_id,
                  rank_snapshot,
                  score_value_snapshot,
                  score_storage_type,
                  school_name_snapshot,
                  activity_title_snapshot,
                  project_name_snapshot,
                  ranking_version_number_snapshot,
                  record_title,
                  verification_code,
                  status,
                  issued_at,
                  issued_by)
                SELECT activity_project.id,
                       version.id,
                       entry.id,
                       entry.student_id,
                       entry.rank_position,
                       entry.score_display_value,
                       version.calculation_params ->> 'scoreStorageType',
                       school.name,
                       activity.title,
                       project.name,
                       version.version_number,
                       activity.title || ' · ' || project.name
                         || ' · 第' || entry.rank_position || '名',
                       :verificationCode,
                       'ACTIVE',
                       now(),
                       :issuedBy
                FROM ranking_entries entry
                JOIN ranking_versions version
                  ON version.id = entry.version_id
                 AND version.version_status = 'PUBLISHED'
                 AND version.withdrawn_at IS NULL
                JOIN ranking_definitions definition
                  ON definition.id = version.definition_id
                 AND definition.layer = 'L1'
                 AND definition.current_version_id = version.id
                JOIN activity_projects activity_project
                  ON activity_project.id = definition.activity_project_id
                JOIN activities activity
                  ON activity.id = activity_project.activity_id
                JOIN schools school
                  ON school.id = activity.school_id
                JOIN challenge_projects project
                  ON project.id = activity_project.project_id
                WHERE entry.id = :rankingEntryId
                  AND entry.student_id IS NOT NULL
                  AND entry.student_display_name IS NOT NULL
                  AND entry.score_display_value IS NOT NULL
                  AND version.calculation_params ->> 'scoreStorageType'
                      IS NOT NULL
                """ + eligibilityScope + """
                ON CONFLICT DO NOTHING
                RETURNING id
                """,
                params,
                (rs, rowNumber) -> rs.getObject("id", UUID.class));

        List<SchoolAdminAchievementDetail> records = jdbc.query(
                DETAIL_SELECT + """
                WHERE record.ranking_entry_id = :rankingEntryId
                """ + recordScope,
                params,
                (rs, rowNumber) ->
                        AchievementRecordQueryAdapter.mapAdminDetail(
                                rs, !inserted.isEmpty()));
        if (!records.isEmpty()) {
            boolean created = !inserted.isEmpty();
            SchoolAdminAchievementDetail record =
                    records.getFirst().withCreated(created);
            return Optional.of(new AchievementIssueResult(record, created));
        }

        Boolean eligible = jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1
                  FROM ranking_entries entry
                  JOIN ranking_versions version
                    ON version.id = entry.version_id
                   AND version.version_status = 'PUBLISHED'
                   AND version.withdrawn_at IS NULL
                  JOIN ranking_definitions definition
                    ON definition.id = version.definition_id
                   AND definition.layer = 'L1'
                   AND definition.current_version_id = version.id
                  JOIN activity_projects activity_project
                    ON activity_project.id = definition.activity_project_id
                  JOIN activities activity
                    ON activity.id = activity_project.activity_id
                  WHERE entry.id = :rankingEntryId
                """ + eligibilityScope + """
                )
                """,
                params,
                Boolean.class);
        if (Boolean.TRUE.equals(eligible)) {
            throw new AchievementVerificationCodeCollisionException();
        }
        return Optional.empty();
    }
}
