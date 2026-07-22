package com.campusguinness.achievement.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class AchievementRecordService {
    private final JdbcTemplate jdbc;

    public AchievementRecordService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Record(UUID id, UUID activityProjectId, UUID studentId, int rank,
            String scoreValue, String storageType, String title, String verificationCode,
            String status, Instant issuedAt, UUID issuedBy, Instant revokedAt, String revocationReason) {}

    public Record issue(UUID activityProjectId, UUID rankingEntryId, UUID issuedBy) {
        // Find current non-withdrawn ranking version
        var vRows = jdbc.queryForList(
                "SELECT id, version_number FROM ranking_versions " +
                "WHERE definition_id = ? AND version_status = 'PUBLISHED' AND withdrawn_at IS NULL " +
                "ORDER BY version_number DESC LIMIT 1", activityProjectId);
        if (vRows.isEmpty()) throw new IllegalStateException("No current published ranking");
        UUID versionId = (UUID) vRows.getFirst().get("id");

        // Load ranking entry
        var eRows = jdbc.queryForList(
                "SELECT student_id, rank, score_value, score_storage_type FROM ranking_entries " +
                "WHERE id = ? AND version_id = ?", rankingEntryId, versionId);
        if (eRows.isEmpty()) throw new IllegalArgumentException("Ranking entry not found in current version");

        var entry = eRows.getFirst();
        UUID id = UUID.randomUUID();
        String code = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        jdbc.update("INSERT INTO achievement_records (id, activity_project_id, ranking_version_id, ranking_entry_id, " +
                "student_id, rank_snapshot, score_value_snapshot, score_storage_type, record_title, verification_code, " +
                "status, issued_at, issued_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, activityProjectId, versionId, rankingEntryId, entry.get("student_id"),
                entry.get("rank"), entry.get("score_value"), entry.get("score_storage_type"),
                "Achievement Record", code, "ACTIVE", now, issuedBy);

        return new Record(id, activityProjectId, (UUID)entry.get("student_id"), (int)entry.get("rank"),
                (String)entry.get("score_value"), (String)entry.get("score_storage_type"),
                "Achievement Record", code, "ACTIVE", now, issuedBy, null, null);
    }

    @Transactional(readOnly = true)
    public List<Record> listMine(UUID studentId) {
        return jdbc.queryForList(
                "SELECT * FROM achievement_records WHERE student_id = ? ORDER BY issued_at DESC", studentId)
                .stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Record> getMine(UUID id, UUID studentId) {
        var rows = jdbc.queryForList(
                "SELECT * FROM achievement_records WHERE id = ? AND student_id = ?", id, studentId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(map(rows.getFirst()));
    }

    @Transactional(readOnly = true)
    public List<Record> listByProject(UUID activityProjectId) {
        return jdbc.queryForList(
                "SELECT * FROM achievement_records WHERE activity_project_id = ? ORDER BY rank ASC", activityProjectId)
                .stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Record> verify(String verificationCode) {
        var rows = jdbc.queryForList(
                "SELECT * FROM achievement_records WHERE verification_code = ?", verificationCode);
        return rows.isEmpty() ? Optional.empty() : Optional.of(map(rows.getFirst()));
    }

    public void revokeByRankingVersion(UUID versionId, UUID revokedBy, String reason) {
        jdbc.update("UPDATE achievement_records SET status = 'REVOKED', revoked_at = now(), " +
                "revoked_by = ?, revocation_reason = ? WHERE ranking_version_id = ? AND status = 'ACTIVE'",
                revokedBy, reason, versionId);
    }

    private Record map(Map<String, Object> r) {
        return new Record((UUID)r.get("id"), (UUID)r.get("activity_project_id"), (UUID)r.get("student_id"),
                (int)r.get("rank_snapshot"), (String)r.get("score_value_snapshot"),
                (String)r.get("score_storage_type"), (String)r.get("record_title"),
                (String)r.get("verification_code"), (String)r.get("status"),
                (Instant)r.get("issued_at"), (UUID)r.get("issued_by"),
                (Instant)r.get("revoked_at"), (String)r.get("revocation_reason"));
    }
}
