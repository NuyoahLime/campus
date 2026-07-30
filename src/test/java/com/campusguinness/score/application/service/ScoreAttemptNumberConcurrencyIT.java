package com.campusguinness.score.application.service;

import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import com.campusguinness.score.application.command.CreateSchoolAdminScoreDraftCommand;
import com.campusguinness.score.application.exception.ScoreEntryConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreAttemptNumberConcurrencyIT extends ScoreEntryIntegrationTestSupport {
    @Autowired SchoolAdminScoreEntryApplicationService service;

    @Test
    void concurrentDraftCreationAllocatesDistinctAttemptNumbers() throws Exception {
        List<UUID> created = concurrentCreateDrafts();

        List<Integer> numbers = attemptNumbers(created);
        assertThat(numbers).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void concurrentDraftCreationProducesSequentialAttemptNumbers() throws Exception {
        List<UUID> created = concurrentCreateDrafts();

        assertThat(attemptNumbers(created)).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void concurrentDraftCreationCreatesTwoRowsWithoutConstraintFailure() throws Exception {
        List<UUID> created = concurrentCreateDrafts();

        assertThat(created).hasSize(2).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                """, Integer.class, activityProjectId, studentId)).isEqualTo(2);
    }

    @Test
    void concurrentSubmitSameDraftOnlyOneSucceeds() throws Exception {
        UUID draftId = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(100), null, null, "DRAFT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> submitWhenReleased(draftId, ready, start));
            Future<Boolean> second = executor.submit(
                    () -> submitWhenReleased(draftId, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("""
                SELECT score_status FROM score_attempts WHERE id=?
                """, String.class, draftId)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id=?
                """, Integer.class, draftId)).isZero();
    }

    private List<UUID> concurrentCreateDrafts() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<UUID> first = executor.submit(() -> createWhenReleased(ready, start));
            Future<UUID> second = executor.submit(() -> createWhenReleased(ready, start));
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }

    private UUID createWhenReleased(
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return service.createDraft(
                adminId,
                new CreateSchoolAdminScoreDraftCommand(
                        activityProjectId, studentId,
                        100L, null, null, null,
                        Instant.parse("2026-07-30T10:00:00Z"),
                        "ON_SITE_RECORD"));
    }

    private boolean submitWhenReleased(
            UUID draftId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.submitDraft(adminId, draftId);
            return true;
        } catch (ScoreEntryConflictException expected) {
            return false;
        }
    }

    private List<Integer> attemptNumbers(List<UUID> attemptIds) {
        return jdbc.queryForList("""
                SELECT attempt_number
                FROM score_attempts
                WHERE id IN (?,?)
                ORDER BY attempt_number
                """, Integer.class, attemptIds.get(0), attemptIds.get(1));
    }
}
