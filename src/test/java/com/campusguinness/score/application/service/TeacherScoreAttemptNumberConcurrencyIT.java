package com.campusguinness.score.application.service;

import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import com.campusguinness.score.application.command.CreateTeacherScoreCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherScoreAttemptNumberConcurrencyIT
        extends ScoreEntryIntegrationTestSupport {
    @Autowired TeacherScoreEntryApplicationService service;

    private UUID responsibleAssignmentId;

    @BeforeEach
    void assignResponsibleTeacher() {
        UUID membershipId = jdbc.queryForObject("""
                SELECT id FROM school_memberships
                WHERE user_id=? AND school_id=? AND role_in_school='TEACHER'
                """, UUID.class, teacherId, schoolId);
        responsibleAssignmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO responsible_teachers(
                  id,activity_project_id,teacher_membership_id,created_at)
                VALUES (?,?,?,?)
                """, responsibleAssignmentId, activityProjectId,
                membershipId, ts(Instant.now()));
    }

    @AfterEach
    void removeResponsibleTeacher() {
        jdbc.update(
                "DELETE FROM responsible_teachers WHERE id=?",
                responsibleAssignmentId);
    }

    @Test
    void concurrentTeacherSubmissionAllocatesDistinctNumbers() throws Exception {
        runConcurrentCreates();

        List<Integer> numbers = attemptNumbers();
        assertThat(numbers).containsExactly(1, 2);
    }

    @Test
    void concurrentTeacherSubmissionCreatesTwoRows() throws Exception {
        runConcurrentCreates();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                """, Integer.class, activityProjectId, studentId)).isEqualTo(2);
    }

    @Test
    void attemptNumbersRemainSequential() throws Exception {
        service.createAndSubmit(teacherId, command(80L));
        runConcurrentCreates();

        assertThat(attemptNumbers()).containsExactly(1, 2, 3);
    }

    @Test
    void concurrentSubmitSameDraftOnlyOneSucceeds() throws Exception {
        UUID draft = addAttempt(
                schoolId, activityProjectId, studentId, teacherId,
                1, "INTEGER", java.math.BigDecimal.valueOf(80),
                null, null, "DRAFT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> submitWhenReleased(draft, ready, start));
            Future<Boolean> second = executor.submit(
                    () -> submitWhenReleased(draft, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("""
                    SELECT score_status FROM score_attempts WHERE id=?
                    """, String.class, draft)).isEqualTo("PENDING_REVIEW");
        } finally {
            executor.shutdownNow();
        }
    }

    private void runConcurrentCreates() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = executor.submit(
                    () -> createWhenReleased(80L, ready, start));
            Future<UUID> second = executor.submit(
                    () -> createWhenReleased(90L, ready, start));
            ready.await();
            start.countDown();
            assertThat(first.get()).isNotEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createWhenReleased(
            long value,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return service.createAndSubmit(teacherId, command(value));
    }

    private boolean submitWhenReleased(
            UUID attemptId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            service.submitDraft(teacherId, attemptId);
            return true;
        } catch (RuntimeException expectedConflict) {
            return false;
        }
    }

    private CreateTeacherScoreCommand command(long value) {
        return new CreateTeacherScoreCommand(
                activityProjectId,
                studentId,
                value,
                null,
                null,
                null,
                Instant.parse("2026-07-30T10:00:00Z"),
                "ON_SITE_RECORD");
    }

    private List<Integer> attemptNumbers() {
        return jdbc.queryForList("""
                SELECT attempt_number FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                ORDER BY attempt_number
                """, Integer.class, activityProjectId, studentId);
    }
}
