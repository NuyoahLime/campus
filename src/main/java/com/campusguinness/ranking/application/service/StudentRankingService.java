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
                "SELECT version.id, version.version_number, "
                        + "version.calculation_params ->> 'comparisonDirection' AS comparison_direction, "
                        + "(SELECT COUNT(*) FROM ranking_entries entry "
                        + " WHERE entry.version_id = version.id) AS ranked_student_count "
                        + "FROM ranking_definitions definition "
                        + "JOIN ranking_versions version ON version.id = definition.current_version_id "
                        + "WHERE definition.activity_project_id = ? "
                        + "AND version.version_status = 'PUBLISHED' "
                        + "AND version.withdrawn_at IS NULL",
                activityProjectId);
        if (pubRows.isEmpty()) return Optional.empty();

        var pub = pubRows.getFirst();
        UUID versionId = (UUID) pub.get("id");

        var entryRows = jdbc.queryForList(
                "SELECT rank_position, student_id, score_display_value "
                        + "FROM ranking_entries "
                        + "WHERE version_id = ? ORDER BY rank_position, id",
                versionId);

        List<StudentRankEntry> entries = entryRows.stream()
                .map(r -> new StudentRankEntry(
                        (int) r.get("rank_position"), (UUID) r.get("student_id"),
                        (String) r.get("score_display_value"),
                        currentStudentId != null && currentStudentId.equals(r.get("student_id"))))
                .toList();

        return Optional.of(new StudentRankingResult(activityProjectId,
                (int) pub.get("version_number"), (String) pub.get("comparison_direction"),
                ((Number) pub.get("ranked_student_count")).intValue(), entries));
    }

    public Optional<StudentOwnRankResult> getMyRank(UUID activityProjectId, UUID currentStudentId) {
        var pubRows = jdbc.queryForList(
                "SELECT version.id, version.version_number, "
                        + "version.calculation_params ->> 'comparisonDirection' AS comparison_direction, "
                        + "(SELECT COUNT(*) FROM ranking_entries entry "
                        + " WHERE entry.version_id = version.id) AS ranked_student_count "
                        + "FROM ranking_definitions definition "
                        + "JOIN ranking_versions version ON version.id = definition.current_version_id "
                        + "WHERE definition.activity_project_id = ? "
                        + "AND version.version_status = 'PUBLISHED' "
                        + "AND version.withdrawn_at IS NULL",
                activityProjectId);
        if (pubRows.isEmpty()) return Optional.empty();

        var pub = pubRows.getFirst();
        UUID versionId = (UUID) pub.get("id");

        var entryRows = jdbc.queryForList(
                "SELECT rank_position, score_display_value FROM ranking_entries " +
                "WHERE version_id = ? AND student_id = ?", versionId, currentStudentId);
        if (entryRows.isEmpty()) return Optional.empty();

        var entry = entryRows.getFirst();
        return Optional.of(new StudentOwnRankResult(activityProjectId,
                (int) pub.get("version_number"), (String) pub.get("comparison_direction"),
                ((Number) pub.get("ranked_student_count")).intValue(),
                (int) entry.get("rank_position"),
                (String) entry.get("score_display_value")));
    }
}
