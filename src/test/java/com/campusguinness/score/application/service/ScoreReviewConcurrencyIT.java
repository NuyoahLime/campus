package com.campusguinness.score.application.service;

import com.campusguinness.score.ScoreReviewIntegrationTestSupport;
import com.campusguinness.score.application.exception.ScoreReviewConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreReviewConcurrencyIT extends ScoreReviewIntegrationTestSupport {
    @Autowired ScoreReviewApplicationService service;

    @Test
    void concurrentApproveSameAttemptOnlyOneSucceeds() throws Exception {
        List<Boolean> outcomes = concurrentApprove();
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("""
                SELECT score_status FROM score_attempts WHERE id=?
                """, String.class, attemptId)).isEqualTo("APPROVED");
    }

    @Test
    void concurrentApproveWritesSingleReviewRecord() throws Exception {
        List<Boolean> outcomes = concurrentApprove();
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id=?
                """, Integer.class, attemptId)).isEqualTo(1);
    }

    @Test
    void concurrentEffectiveSwitchLeavesExactlyOneEffectiveScore() throws Exception {
        UUIDHolder previous = new UUIDHolder(insertAttempt(
                schoolId, teacherId, "APPROVED", true, 90));
        List<Boolean> outcomes = concurrentApprove();
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_attempts
                WHERE activity_project_id=? AND student_id=? AND is_current_effective=true
                """, Integer.class, activityProjectId, studentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT is_current_effective FROM score_attempts WHERE id=?
                """, Boolean.class, previous.value)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT score_status FROM score_attempts WHERE id=?
                """, String.class, previous.value)).isEqualTo("APPROVED");
    }

    private List<Boolean> concurrentApprove() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> approveWhenReleased(ready, start));
            Future<Boolean> second = executor.submit(() -> approveWhenReleased(ready, start));
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }

    private boolean approveWhenReleased(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.approve(attemptId, adminId, "concurrent review", null);
            return true;
        } catch (ScoreReviewConflictException expected) {
            return false;
        }
    }

    private record UUIDHolder(java.util.UUID value) {
    }
}
