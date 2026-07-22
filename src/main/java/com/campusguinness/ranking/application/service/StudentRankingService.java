package com.campusguinness.ranking.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class StudentRankingService {
    private final JdbcTemplate jdbc;

    public StudentRankingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record StudentRankEntry(int rank, UUID studentId, String scoreValue, boolean isCurrentStudent) {}
    public record StudentRankingResult(UUID activityProjectId, int version, String direction,
                                        int totalRanked, List<StudentRankEntry> entries) {}
    public record StudentOwnRankResult(UUID activityProjectId, int version, String direction,
                                        int totalRanked, int rank, String scoreValue) {}

    public Optional<StudentRankingResult> getCurrentRanking(UUID activityProjectId, UUID currentStudentId) {
        var pubRows = jdbc.queryForList(
                "SELECT id, version_number, comparison_direction, ranked_student_count " +
                "FROM ranking_versions WHERE definition_id = ? AND version_status = 'PUBLISHED' " +
                "ORDER BY version_number DESC LIMIT 1", activityProjectId);
        if (pubRows.isEmpty()) return Optional.empty();

        var pub = pubRows.getFirst();
        UUID versionId = (UUID) pub.get("id");

        var entryRows = jdbc.queryForList(
                "SELECT rank, student_id, score_value FROM ranking_entries " +
                "WHERE version_id = ? ORDER BY rank, student_id", versionId);

        List<StudentRankEntry> entries = entryRows.stream()
                .map(r -> new StudentRankEntry(
                        (int) r.get("rank"), (UUID) r.get("student_id"),
                        (String) r.get("score_value"),
                        currentStudentId.equals(r.get("student_id"))))
                .toList();

        return Optional.of(new StudentRankingResult(activityProjectId,
                (int) pub.get("version_number"), (String) pub.get("comparison_direction"),
                (int) pub.get("ranked_student_count"), entries));
    }

    public Optional<StudentOwnRankResult> getMyRank(UUID activityProjectId, UUID currentStudentId) {
        var pubRows = jdbc.queryForList(
                "SELECT id, version_number, comparison_direction, ranked_student_count " +
                "FROM ranking_versions WHERE definition_id = ? AND version_status = 'PUBLISHED' " +
                "ORDER BY version_number DESC LIMIT 1", activityProjectId);
        if (pubRows.isEmpty()) return Optional.empty();

        var pub = pubRows.getFirst();
        UUID versionId = (UUID) pub.get("id");

        var entryRows = jdbc.queryForList(
                "SELECT rank, score_value FROM ranking_entries " +
                "WHERE version_id = ? AND student_id = ?", versionId, currentStudentId);
        if (entryRows.isEmpty()) return Optional.empty();

        var entry = entryRows.getFirst();
        return Optional.of(new StudentOwnRankResult(activityProjectId,
                (int) pub.get("version_number"), (String) pub.get("comparison_direction"),
                (int) pub.get("ranked_student_count"), (int) entry.get("rank"),
                (String) entry.get("score_value")));
    }
}
