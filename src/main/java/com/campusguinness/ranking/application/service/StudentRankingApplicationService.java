package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.exception.RankingNotFoundException;
import com.campusguinness.ranking.application.exception.StudentRankingAccessException;
import com.campusguinness.ranking.application.query.model.StudentCurrentRankingDetail;
import com.campusguinness.ranking.application.query.model.StudentOwnRanking;
import com.campusguinness.ranking.application.query.model.StudentRankingProjectItem;
import com.campusguinness.ranking.application.query.port.StudentRankingQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StudentRankingApplicationService {

    private static final Set<String> EXECUTION_STATUSES = Set.of(
            "DRAFT", "PUBLISHED", "IN_PROGRESS", "ENDED", "CANCELLED");
    private static final Set<String> RANKING_AVAILABILITIES = Set.of(
            "CURRENT", "NOT_PUBLISHED", "WITHDRAWN", "DISABLED");

    private final SchoolMembershipQueryPort membershipQuery;
    private final StudentRankingQueryPort rankingQuery;

    public StudentRankingApplicationService(
            SchoolMembershipQueryPort membershipQuery,
            StudentRankingQueryPort rankingQuery) {
        this.membershipQuery = membershipQuery;
        this.rankingQuery = rankingQuery;
    }

    public QueryPage<StudentRankingProjectItem> listProjects(
            UUID actorId,
            String executionStatus,
            String rankingAvailability,
            String keyword,
            int page,
            int size) {
        requireActiveStudentMembership(actorId);
        validatePage(page, size);
        return rankingQuery.findRankingProjects(
                actorId,
                normalizeEnum(executionStatus, EXECUTION_STATUSES, "executionStatus"),
                normalizeEnum(
                        rankingAvailability,
                        RANKING_AVAILABILITIES,
                        "rankingAvailability"),
                normalizeKeyword(keyword),
                page,
                size);
    }

    public StudentCurrentRankingDetail getCurrentRanking(
            UUID actorId, UUID activityProjectId) {
        requireActiveStudentMembership(actorId);
        requireAccessibleAssignment(actorId, activityProjectId);
        return rankingQuery.findAccessibleCurrentRanking(actorId, activityProjectId)
                .orElseThrow(StudentRankingApplicationService::notFound);
    }

    public StudentOwnRanking getMyRank(
            UUID actorId, UUID activityProjectId) {
        requireActiveStudentMembership(actorId);
        requireAccessibleAssignment(actorId, activityProjectId);
        return rankingQuery.findOwnCurrentRanking(actorId, activityProjectId)
                .orElseThrow(StudentRankingApplicationService::notFound);
    }

    private void requireActiveStudentMembership(UUID actorId) {
        if (membershipQuery.findActiveStudentMembershipIds(actorId).isEmpty()) {
            throw new StudentRankingAccessException(
                    "No active student membership");
        }
    }

    private void requireAccessibleAssignment(
            UUID actorId, UUID activityProjectId) {
        if (!rankingQuery.existsAccessibleAssignment(actorId, activityProjectId)) {
            throw notFound();
        }
    }

    private static RankingNotFoundException notFound() {
        return new RankingNotFoundException("Ranking not found");
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "keyword must not exceed 100 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeEnum(
            String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100");
        }
    }
}
