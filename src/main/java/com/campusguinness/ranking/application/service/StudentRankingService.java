package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.exception.RankingNotFoundException;
import com.campusguinness.ranking.application.query.model.StudentRankingEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility facade for the original student activity-project ranking paths.
 * All access control and current-version decisions live in
 * {@link StudentRankingApplicationService}.
 */
@Service
public class StudentRankingService {

    private final StudentRankingApplicationService applicationService;

    public StudentRankingService(
            StudentRankingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public record StudentRankEntry(
            int rank,
            String scoreValue,
            boolean isCurrentStudent) {
    }

    public record StudentRankingResult(
            UUID activityProjectId,
            int version,
            String direction,
            int totalRanked,
            List<StudentRankEntry> entries) {
    }

    public record StudentOwnRankResult(
            UUID activityProjectId,
            int version,
            String direction,
            int totalRanked,
            int rank,
            String scoreValue) {
    }

    public Optional<StudentRankingResult> getCurrentRanking(
            UUID activityProjectId, UUID currentStudentId) {
        try {
            var detail = applicationService.getCurrentRanking(
                    currentStudentId, activityProjectId);
            return Optional.of(new StudentRankingResult(
                    detail.activityProjectId(),
                    detail.versionNumber(),
                    detail.comparisonDirection(),
                    Math.toIntExact(detail.totalRanked()),
                    detail.entries().stream()
                            .map(StudentRankingService::toLegacyEntry)
                            .toList()));
        } catch (RankingNotFoundException ignored) {
            return Optional.empty();
        }
    }

    public Optional<StudentOwnRankResult> getMyRank(
            UUID activityProjectId, UUID currentStudentId) {
        try {
            var detail = applicationService.getCurrentRanking(
                    currentStudentId, activityProjectId);
            StudentRankingEntry own = detail.entries().stream()
                    .filter(StudentRankingEntry::isCurrentStudent)
                    .findFirst()
                    .orElseThrow(() -> new RankingNotFoundException(
                            "Ranking not found"));
            return Optional.of(new StudentOwnRankResult(
                    detail.activityProjectId(),
                    detail.versionNumber(),
                    detail.comparisonDirection(),
                    Math.toIntExact(detail.totalRanked()),
                    own.rankPosition(),
                    own.scoreDisplayValue()));
        } catch (RankingNotFoundException ignored) {
            return Optional.empty();
        }
    }

    private static StudentRankEntry toLegacyEntry(StudentRankingEntry entry) {
        return new StudentRankEntry(
                entry.rankPosition(),
                entry.scoreDisplayValue(),
                entry.isCurrentStudent());
    }
}
