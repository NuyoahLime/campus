package com.campusguinness.ranking.application.service;

import com.campusguinness.achievement.application.service.AchievementRecordService;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class RankingPublicationService {
    private final ActivityProjectPort projectPort;
    private final ScoreAttemptRepository scoreAttemptRepo;
    private final ActivityRepository activityRepository;
    private final JdbcTemplate jdbc;
    private final AchievementRecordService achievementService;

    public RankingPublicationService(ActivityProjectPort projectPort,
                                      ScoreAttemptRepository scoreAttemptRepo,
                                      ActivityRepository activityRepository,
                                      JdbcTemplate jdbc,
                                      AchievementRecordService achievementService) {
        this.projectPort = projectPort;
        this.scoreAttemptRepo = scoreAttemptRepo;
        this.activityRepository = activityRepository;
        this.jdbc = jdbc;
        this.achievementService = achievementService;
    }

    public record PublicationResult(UUID versionId, UUID activityProjectId, int versionNumber,
                                     String direction, int totalRanked,
                                     List<RankingCalculator.RankingEntry> entries) {}

    public PublicationResult publish(UUID activityProjectId, UUID publishedBy) {
        var ap = projectPort.findById(activityProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + activityProjectId));

        var activity = activityRepository.findById(new ActivityId(ap.activityId()))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        var status = activity.executionStatus();
        if (status != ExecutionStatus.ENDED) {
            throw new IllegalStateException("Ranking publication not allowed when activity is " + status);
        }

        var approved = scoreAttemptRepo.findApprovedByActivityProjectId(activityProjectId);
        if (approved.isEmpty()) {
            throw new IllegalStateException("No APPROVED scores to rank");
        }

        ComparisonDirection direction = ComparisonDirection.HIGHER_BETTER;
        var entries = RankingCalculator.rank(approved, direction);

        UUID versionId = UUID.randomUUID();
        int vn = nextVersionNumber(activityProjectId);
        Instant now = Instant.now();

        jdbc.update("INSERT INTO ranking_versions (id, definition_id, version_number, comparison_direction, " +
                "tie_policy, effective_score_rule, ranked_student_count, version_status, published_by, published_at, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                versionId, activityProjectId, vn, direction.name(), "COMPETITION",
                "BEST", entries.size(), "PUBLISHED", publishedBy, now, now);

        for (var e : entries) {
            jdbc.update("INSERT INTO ranking_entries (id, version_id, student_id, rank, score_value, created_at) " +
                    "VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), versionId, e.studentId(), e.rank(), e.scoreDisplay(), now);
        }

        return new PublicationResult(versionId, activityProjectId, vn, direction.name(), entries.size(), entries);
    }

    public Optional<PublicationResult> getCurrent(UUID activityProjectId) {
        var rows = jdbc.queryForList(
                "SELECT id, version_number, comparison_direction, ranked_student_count " +
                "FROM ranking_versions WHERE definition_id = ? AND version_status = 'PUBLISHED' " +
                "AND withdrawn_at IS NULL ORDER BY version_number DESC LIMIT 1", activityProjectId);
        if (rows.isEmpty()) return Optional.empty();

        var row = rows.getFirst();
        UUID versionId = (UUID) row.get("id");

        var entryRows = jdbc.queryForList(
                "SELECT rank, student_id, score_value FROM ranking_entries WHERE version_id = ? ORDER BY rank, student_id",
                versionId);

        List<RankingCalculator.RankingEntry> entries = entryRows.stream()
                .map(r -> new RankingCalculator.RankingEntry(
                        (int) r.get("rank"), (UUID) r.get("student_id"), null, (String) r.get("score_value")))
                .toList();

        return Optional.of(new PublicationResult(versionId, activityProjectId,
                (int) row.get("version_number"), (String) row.get("comparison_direction"),
                (int) row.get("ranked_student_count"), entries));
    }

    private int nextVersionNumber(UUID activityProjectId) {
        var rows = jdbc.queryForList(
                "SELECT COALESCE(MAX(version_number), 0) + 1 AS next FROM ranking_versions WHERE definition_id = ?",
                Integer.class, activityProjectId);
        return rows.isEmpty() ? 1 : rows.getFirst();
    }
    // ── Withdrawal ──

    public record HistoryItem(UUID versionId, int versionNumber, String status, Instant publishedAt,
            UUID publishedBy, Instant withdrawnAt, UUID withdrawnBy, String withdrawalReason, int entryCount) {}

    public void withdraw(UUID activityProjectId, UUID withdrawnBy, String reason) {
        var rows = jdbc.queryForList(
                "SELECT id FROM ranking_versions WHERE definition_id = ? AND version_status = 'PUBLISHED' " +
                "AND withdrawn_at IS NULL ORDER BY version_number DESC LIMIT 1", activityProjectId);
        if (rows.isEmpty()) throw new IllegalArgumentException("No current published ranking to withdraw");
        UUID versionId = (UUID) rows.getFirst().get("id");
        jdbc.update("UPDATE ranking_versions SET withdrawn_at = now(), withdrawn_by = ?, withdrawal_reason = ? WHERE id = ?",
                withdrawnBy, reason, versionId);
        achievementService.revokeByRankingVersion(versionId, withdrawnBy, "Ranking withdrawn: " + reason);
    }

    @Transactional(readOnly = true)
    public boolean hasCurrent(UUID activityProjectId) {
        var rows = jdbc.queryForList(
                "SELECT 1 FROM ranking_versions WHERE definition_id = ? AND version_status = 'PUBLISHED' " +
                "AND withdrawn_at IS NULL ORDER BY version_number DESC LIMIT 1", activityProjectId);
        return !rows.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> getHistory(UUID activityProjectId) {
        return jdbc.queryForList(
                "SELECT rv.id, rv.version_number, rv.published_at, rv.published_by, " +
                "rv.withdrawn_at, rv.withdrawn_by, rv.withdrawal_reason, " +
                "(SELECT count(*) FROM ranking_entries WHERE version_id = rv.id) AS entry_count " +
                "FROM ranking_versions rv WHERE rv.definition_id = ? ORDER BY rv.version_number DESC",
                activityProjectId).stream()
                .map(r -> {
                    boolean withdrawn = r.get("withdrawn_at") != null;
                    return new HistoryItem((UUID)r.get("id"), (int)r.get("version_number"),
                            withdrawn ? "WITHDRAWN" : "CURRENT",
                            (Instant)r.get("published_at"), (UUID)r.get("published_by"),
                            (Instant)r.get("withdrawn_at"), (UUID)r.get("withdrawn_by"),
                            (String)r.get("withdrawal_reason"),
                            ((Number)r.get("entry_count")).intValue());
                }).toList();
    }

    // ── Fix current query to exclude withdrawn ──
}
