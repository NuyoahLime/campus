package com.campusguinness.activity.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ActivityParticipantRosterService {
    private final JdbcTemplate jdbc;

    public ActivityParticipantRosterService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Participant(UUID applicationId, UUID studentId, String displayName, String status) {}
    public record ProjectParticipant(UUID applicationId, UUID studentId, String displayName,
            long attemptCount, boolean hasScoreAttempt, UUID latestAttemptId,
            String latestAttemptStatus, String latestScoreValue, boolean hasApprovedScore) {}

    public List<Participant> listActivityParticipants(UUID activityId) {
        return jdbc.queryForList(
                "SELECT aa.id AS app_id, aa.applicant_id AS student_id " +
                "FROM activity_applications aa WHERE aa.activity_id = ? AND aa.application_status = 'APPROVED' " +
                "ORDER BY aa.applicant_id", activityId).stream()
                .map(r -> new Participant((UUID)r.get("app_id"), (UUID)r.get("student_id"), null, "APPROVED"))
                .toList();
    }

    public List<ProjectParticipant> listProjectParticipants(UUID activityProjectId) {
        var apps = jdbc.queryForList(
                "SELECT DISTINCT ap.application_id AS app_id, aa.applicant_id AS student_id " +
                "FROM activity_project_participants ap JOIN activity_applications aa ON ap.activity_application_id = aa.id " +
                "WHERE ap.activity_project_id = ? AND aa.application_status = 'APPROVED'", activityProjectId);

        List<ProjectParticipant> result = new ArrayList<>();
        for (var app : apps) {
            UUID studentId = (UUID) app.get("student_id");
            UUID appId = (UUID) app.get("app_id");
            var scores = jdbc.queryForList(
                    "SELECT id, score_status, score_value, score_duration_ms, score_grade, score_storage_type " +
                    "FROM score_attempts WHERE activity_project_id = ? AND student_id = ? " +
                    "ORDER BY COALESCE(submitted_at, created_at) DESC, id DESC",
                    activityProjectId, studentId);

            long count = scores.size();
            boolean hasAttempt = count > 0;
            boolean hasApproved = scores.stream().anyMatch(s -> "APPROVED".equals(s.get("score_status")));
            String latestStatus = hasAttempt ? (String) scores.getFirst().get("score_status") : null;
            String latestScore = hasAttempt ? displayScore(scores.getFirst()) : null;
            UUID latestId = hasAttempt ? (UUID) scores.getFirst().get("id") : null;

            result.add(new ProjectParticipant(appId, studentId, null, count, hasAttempt,
                    latestId, latestStatus, latestScore, hasApproved));
        }
        return result;
    }

    @Transactional
    public void assignParticipant(UUID activityProjectId, UUID applicationId, UUID assignedBy) {
        var rows = jdbc.queryForList("SELECT activity_id FROM activity_projects WHERE id = ?", UUID.class, activityProjectId);
        if (rows.isEmpty()) throw new IllegalArgumentException("ActivityProject not found: " + activityProjectId);

        var appRows = jdbc.queryForList(
                "SELECT application_status FROM activity_applications WHERE id = ?", String.class, applicationId);
        if (appRows.isEmpty()) throw new IllegalArgumentException("ActivityApplication not found: " + applicationId);
        if (!"APPROVED".equals(appRows.getFirst()))
            throw new IllegalStateException("Application not APPROVED");

        var existing = jdbc.queryForList(
                "SELECT 1 FROM activity_project_participants WHERE activity_project_id = ? AND activity_application_id = ?",
                activityProjectId, applicationId);
        if (!existing.isEmpty())
            throw new IllegalStateException("Participant already assigned to this project");

        jdbc.update("INSERT INTO activity_project_participants (id, activity_project_id, activity_application_id, assigned_by, assigned_at) VALUES (?,?,?,?,now())",
                UUID.randomUUID(), activityProjectId, applicationId, assignedBy);
    }

    @Transactional
    public void unassignParticipant(UUID activityProjectId, UUID applicationId) {
        var existing = jdbc.queryForList(
                "SELECT 1 FROM activity_project_participants WHERE activity_project_id = ? AND activity_application_id = ?",
                activityProjectId, applicationId);
        if (existing.isEmpty()) throw new IllegalArgumentException("Participant not assigned to this project");

        var scores = jdbc.queryForList(
                "SELECT 1 FROM score_attempts sa JOIN activity_applications aa ON sa.student_id = aa.applicant_id " +
                "WHERE sa.activity_project_id = ? AND aa.id = ?", activityProjectId, applicationId);
        if (!scores.isEmpty())
            throw new IllegalStateException("Cannot unassign participant with existing score attempts");

        jdbc.update("DELETE FROM activity_project_participants WHERE activity_project_id = ? AND activity_application_id = ?",
                activityProjectId, applicationId);
    }

    private String displayScore(Map<String, Object> row) {
        return switch ((String) row.get("score_storage_type")) {
            case "INTEGER" -> row.get("score_value") != null ? String.valueOf(((Number)row.get("score_value")).longValue()) : null;
            case "DECIMAL" -> row.get("score_value") != null ? row.get("score_value").toString() : null;
            case "DURATION" -> row.get("score_duration_ms") != null ? row.get("score_duration_ms") + "ms" : null;
            case "GRADE" -> (String) row.get("score_grade");
            default -> null;
        };
    }
    // ── Student Self-Service ──

    public record MyProjectAssignment(UUID activityProjectId, UUID activityId, UUID applicationId,
            long attemptCount, boolean hasScoreAttempt, UUID latestAttemptId,
            String latestAttemptStatus, boolean hasApprovedScore, java.time.Instant assignedAt) {}

    public List<MyProjectAssignment> listMyAssignments(UUID applicantId) {
        var rows = jdbc.queryForList(
                "SELECT app.activity_project_id, ap.activity_id, app.activity_application_id, app.assigned_at " +
                "FROM activity_project_participants app " +
                "JOIN activity_applications aa ON app.activity_application_id = aa.id " +
                "WHERE aa.applicant_id = ? AND aa.application_status = 'APPROVED' " +
                "ORDER BY app.assigned_at DESC", applicantId);

        List<MyProjectAssignment> result = new ArrayList<>();
        for (var row : rows) {
            UUID projectId = (UUID) row.get("activity_project_id");
            UUID appId = (UUID) row.get("activity_application_id");
            var scores = jdbc.queryForList(
                    "SELECT id, score_status FROM score_attempts " +
                    "WHERE activity_project_id = ? AND student_id = ? " +
                    "ORDER BY COALESCE(submitted_at, created_at) DESC, id DESC",
                    projectId, applicantId);
            long count = scores.size();
            boolean hasAttempt = count > 0;
            boolean hasApproved = scores.stream().anyMatch(s -> "APPROVED".equals(s.get("score_status")));
            String latestStatus = hasAttempt ? (String) scores.getFirst().get("score_status") : null;
            UUID latestId = hasAttempt ? (UUID) scores.getFirst().get("id") : null;
            java.sql.Timestamp ts = (java.sql.Timestamp) row.get("assigned_at");

            result.add(new MyProjectAssignment(projectId, (UUID) row.get("activity_id"), appId,
                    count, hasAttempt, latestId, latestStatus, hasApproved,
                    ts != null ? ts.toInstant() : null));
        }
        return result;
    }

    public Optional<MyProjectAssignment> getMyAssignment(UUID activityProjectId, UUID applicantId) {
        var rows = jdbc.queryForList(
                "SELECT app.activity_application_id, ap.activity_id, app.assigned_at " +
                "FROM activity_project_participants app JOIN activity_projects ap ON app.activity_project_id = ap.id " +
                "JOIN activity_applications aa ON app.activity_application_id = aa.id " +
                "WHERE app.activity_project_id = ? AND aa.applicant_id = ? AND aa.application_status = 'APPROVED'",
                activityProjectId, applicantId);
        if (rows.isEmpty()) return Optional.empty();

        var row = rows.getFirst();
        UUID appId = (UUID) row.get("activity_application_id");
        var scores = jdbc.queryForList(
                "SELECT id, score_status FROM score_attempts WHERE activity_project_id = ? AND student_id = ? " +
                "ORDER BY COALESCE(submitted_at, created_at) DESC, id DESC",
                activityProjectId, applicantId);
        long count = scores.size();
        boolean hasAttempt = count > 0;
        boolean hasApproved = scores.stream().anyMatch(s -> "APPROVED".equals(s.get("score_status")));
        String latestStatus = hasAttempt ? (String) scores.getFirst().get("score_status") : null;
        UUID latestId = hasAttempt ? (UUID) scores.getFirst().get("id") : null;
        java.sql.Timestamp ts = (java.sql.Timestamp) row.get("assigned_at");

        return Optional.of(new MyProjectAssignment(activityProjectId, (UUID) row.get("activity_id"), appId,
                count, hasAttempt, latestId, latestStatus, hasApproved,
                ts != null ? ts.toInstant() : null));
    }
}
