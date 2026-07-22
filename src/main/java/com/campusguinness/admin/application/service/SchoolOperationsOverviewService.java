package com.campusguinness.admin.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class SchoolOperationsOverviewService {
    private final JdbcTemplate jdbc;

    public SchoolOperationsOverviewService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record MetricGroup(long total, long draft, long published, long inProgress, long ended, long cancelled) {}
    public record AppGroup(long total, long submitted, long approved, long rejected, long withdrawn) {}
    public record ScoreGroup(long total, long pendingReview, long approved, long rejected, long invalidated) {}
    public record AppealGroup(long total, long submitted, long processing, long resolved, long rejected, long withdrawn) {}
    public record FeedbackGroup(long total, long submitted, long processing, long resolved, long closed) {}
    public record MediaGroup(long total, long pendingReview, long approved, long publicCount, long awaitingPublication) {}
    public record RankingInfo(long publishedCount, long awaitingPublication) {}
    public record PendingActions(long applications, long scoreReviews, long appeals, long feedbacks, long mediaReviews, long mediaAwaitingPub, long rankingPubs) {}

    public record Overview(UUID schoolId, Instant generatedAt, MetricGroup activities, AppGroup applications,
                            ScoreGroup scoreAttempts, AppealGroup scoreAppeals, FeedbackGroup feedbacks,
                            MediaGroup media, RankingInfo rankings, PendingActions pendingActions) {}

    public Overview getOverview(UUID schoolId) {
        var activities = loadActivities(schoolId);
        var applications = loadApplications(schoolId);
        var scoreAttempts = loadScoreAttempts(schoolId);
        var scoreAppeals = loadScoreAppeals(schoolId);
        var feedbacks = loadFeedbacks(schoolId);
        var media = loadMedia(schoolId);
        var rankings = loadRankings(schoolId);

        var pending = new PendingActions(
                applications.submitted,
                scoreAttempts.pendingReview,
                scoreAppeals.submitted,
                feedbacks.submitted,
                media.pendingReview,
                media.approved - media.publicCount,
                rankings.awaitingPublication);

        return new Overview(schoolId, Instant.now(), activities, applications,
                scoreAttempts, scoreAppeals, feedbacks, media, rankings, pending);
    }

    private MetricGroup loadActivities(UUID schoolId) {
        var rows = jdbc.queryForList(
                "SELECT execution_status, count(*) AS cnt FROM activities WHERE school_id = ? GROUP BY execution_status", schoolId);
        long total = 0, draft = 0, published = 0, inProgress = 0, ended = 0, cancelled = 0;
        for (var r : rows) { long c = ((Number)r.get("cnt")).longValue(); total += c;
            switch ((String)r.get("execution_status")) {
                case "DRAFT" -> draft = c; case "PUBLISHED" -> published = c;
                case "IN_PROGRESS" -> inProgress = c; case "ENDED" -> ended = c;
                case "CANCELLED" -> cancelled = c;
            }
        }
        return new MetricGroup(total, draft, published, inProgress, ended, cancelled);
    }

    private AppGroup loadApplications(UUID schoolId) {
        var rows = jdbc.queryForList(
                "SELECT application_status, count(*) AS cnt FROM activity_applications WHERE school_id = ? GROUP BY application_status", schoolId);
        long t = 0, sub = 0, app = 0, rej = 0, wd = 0;
        for (var r : rows) { long c = ((Number)r.get("cnt")).longValue(); t += c;
            switch ((String)r.get("application_status")) { case "SUBMITTED" -> sub = c; case "APPROVED" -> app = c; case "REJECTED" -> rej = c; case "WITHDRAWN" -> wd = c; }
        }
        return new AppGroup(t, sub, app, rej, wd);
    }

    private ScoreGroup loadScoreAttempts(UUID schoolId) {
        var rows = jdbc.queryForList(
                "SELECT score_status, count(*) AS cnt FROM score_attempts WHERE school_id = ? GROUP BY score_status", schoolId);
        long t = 0, pr = 0, app = 0, rej = 0, inv = 0;
        for (var r : rows) { long c = ((Number)r.get("cnt")).longValue(); t += c;
            switch ((String)r.get("score_status")) { case "PENDING_REVIEW" -> pr = c; case "APPROVED" -> app = c; case "REJECTED" -> rej = c; case "INVALIDATED" -> inv = c; }
        }
        return new ScoreGroup(t, pr, app, rej, inv);
    }

    private AppealGroup loadScoreAppeals(UUID schoolId) {
        var rows = jdbc.queryForList(
                "SELECT appeal_status, count(*) AS cnt FROM score_appeals WHERE school_id = ? GROUP BY appeal_status", schoolId);
        long t = 0, sub = 0, proc = 0, res = 0, rej = 0, wd = 0;
        for (var r : rows) { long c = ((Number)r.get("cnt")).longValue(); t += c;
            switch ((String)r.get("appeal_status")) { case "SUBMITTED" -> sub = c; case "PROCESSING" -> proc = c; case "RESOLVED" -> res = c; case "REJECTED" -> rej = c; case "WITHDRAWN" -> wd = c; }
        }
        return new AppealGroup(t, sub, proc, res, rej, wd);
    }

    private FeedbackGroup loadFeedbacks(UUID schoolId) {
        var rows = jdbc.queryForList(
                "SELECT feedback_status, count(*) AS cnt FROM feedbacks WHERE school_id = ? GROUP BY feedback_status", schoolId);
        long t = 0, sub = 0, proc = 0, res = 0, clo = 0;
        for (var r : rows) { long c = ((Number)r.get("cnt")).longValue(); t += c;
            switch ((String)r.get("feedback_status")) { case "SUBMITTED" -> sub = c; case "PROCESSING" -> proc = c; case "RESOLVED" -> res = c; case "CLOSED" -> clo = c; }
        }
        return new FeedbackGroup(t, sub, proc, res, clo);
    }

    private MediaGroup loadMedia(UUID schoolId) {
        var totalRow = jdbc.queryForList("SELECT count(*) AS cnt FROM media WHERE school_id = ?", schoolId);
        long total = ((Number)totalRow.getFirst().get("cnt")).longValue();
        var statusRows = jdbc.queryForList(
                "SELECT internal_status, public_status, count(*) AS cnt FROM media WHERE school_id = ? GROUP BY internal_status, public_status", schoolId);
        long pr = 0, app = 0, pub = 0;
        for (var r : statusRows) {
            long c = ((Number)r.get("cnt")).longValue();
            String is = (String)r.get("internal_status");
            String ps = (String)r.get("public_status");
            if ("PENDING_INTERNAL_REVIEW".equals(is)) pr += c;
            if ("INTERNAL_APPROVED".equals(is)) { app += c; if ("PUBLIC".equals(ps)) pub += c; }
        }
        return new MediaGroup(total, pr, app, pub, app - pub);
    }

    private RankingInfo loadRankings(UUID schoolId) {
        var pubRow = jdbc.queryForList(
                "SELECT count(*) AS cnt FROM ranking_versions rv JOIN activities a ON a.id = rv.definition_id::uuid WHERE a.school_id = ? AND rv.version_status = 'PUBLISHED'", schoolId);
        long pub = ((Number)pubRow.getFirst().get("cnt")).longValue();
        var awaitRow = jdbc.queryForList(
                "SELECT count(*) AS cnt FROM activity_projects ap JOIN activities a ON a.id = ap.activity_id " +
                "WHERE a.school_id = ? AND a.execution_status = 'ENDED' " +
                "AND NOT EXISTS (SELECT 1 FROM ranking_versions rv WHERE rv.definition_id = ap.id AND rv.version_status = 'PUBLISHED')", schoolId);
        long await = ((Number)awaitRow.getFirst().get("cnt")).longValue();
        return new RankingInfo(pub, await);
    }
}
