package com.campusguinness.ranking.application.service;

import com.campusguinness.activity.application.port.ActivityProjectPort;
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
    private final JdbcTemplate jdbc;

    public RankingPublicationService(ActivityProjectPort projectPort,
                                      ScoreAttemptRepository scoreAttemptRepo,
                                      JdbcTemplate jdbc) {
        this.projectPort = projectPort;
        this.scoreAttemptRepo = scoreAttemptRepo;
        this.jdbc = jdbc;
    }

    public record PublicationResult(UUID versionId, UUID activityProjectId, int versionNumber,
                                     String direction, int totalRanked,
                                     List<RankingCalculator.RankingEntry> entries) {}

    public PublicationResult publish(UUID activityProjectId, UUID publishedBy) {
        projectPort.findById(activityProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + activityProjectId));

        var approved = scoreAttemptRepo.findApprovedByActivityProjectId(activityProjectId);
        if (approved.isEmpty()) {
            throw new IllegalStateException("No APPROVED scores to rank");
        }

        ComparisonDirection direction = ComparisonDirection.HIGHER_BETTER;
        var entries = RankingCalculator.rank(approved, direction);

        UUID versionId = UUID.randomUUID();
        int vn = nextVersionNumber(activityProjectId);
        Instant now = Instant.now();

        // Create ranking version
        jdbc.update("INSERT INTO ranking_versions (id, definition_id, version_number, comparison_direction, " +
                "tie_policy, effective_score_rule, ranked_student_count, version_status, published_by, published_at, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                versionId, activityProjectId, vn, direction.name(), "COMPETITION",
                "BEST", entries.size(), "PUBLISHED", publishedBy, now, now);

        // Create entries
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
                "ORDER BY version_number DESC LIMIT 1", activityProjectId);
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
}
