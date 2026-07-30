package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.exception.ScoreEntryConflictException;
import com.campusguinness.score.application.port.ScoreAttemptNumberAllocatorPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ScoreAttemptNumberAllocatorAdapter implements ScoreAttemptNumberAllocatorPort {
    private final JdbcTemplate jdbc;

    ScoreAttemptNumberAllocatorAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int allocateNext(
            UUID activityProjectId,
            UUID activityParticipantId,
            UUID studentId) {
        var lockedAssignments = jdbc.queryForList("""
                SELECT id
                FROM activity_project_participants
                WHERE activity_project_id = ?
                  AND activity_participant_id = ?
                FOR UPDATE
                """, UUID.class, activityProjectId, activityParticipantId);
        if (lockedAssignments.isEmpty()) {
            throw new ScoreEntryConflictException(
                    "Participant is no longer assigned to this activity project");
        }
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1
                FROM score_attempts
                WHERE activity_project_id = ?
                  AND student_id = ?
                """, Integer.class, activityProjectId, studentId);
        return next == null ? 1 : next;
    }
}
