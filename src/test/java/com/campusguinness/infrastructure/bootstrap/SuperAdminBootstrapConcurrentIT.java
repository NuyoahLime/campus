package com.campusguinness.infrastructure.bootstrap;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.service.BootstrapRefusedException;
import com.campusguinness.identity.application.service.SuperAdminBootstrapService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Concurrent bootstrap test: two threads, two transactions, one PostgreSQL.
 * Proves the advisory lock + empty-table gate prevents duplicate admins.
 */
class SuperAdminBootstrapConcurrentIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private SuperAdminBootstrapService bootstrapService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanBefore() {
        cleanAll();
    }

    @AfterEach
    void cleanAfter() {
        cleanAll();
    }

    private void cleanAll() {
        jdbc.update("DELETE FROM appeal_records");
        jdbc.update("DELETE FROM score_appeals");
        jdbc.update("DELETE FROM score_review_records");
        jdbc.update("DELETE FROM score_correction_records");
        jdbc.update("DELETE FROM abnormal_score_entries");
        jdbc.update("DELETE FROM score_attempts");
        jdbc.update("DELETE FROM ranking_entry_score_sources");
        jdbc.update("DELETE FROM ranking_entries");
        jdbc.update("DELETE FROM ranking_versions");
        jdbc.update("DELETE FROM l3_authorizations");
        jdbc.update("DELETE FROM ranking_definitions");
        jdbc.update("DELETE FROM project_rule_compatibilities");
        jdbc.update("DELETE FROM activity_projects");
        jdbc.update("DELETE FROM activities");
        jdbc.update("DELETE FROM project_rule_versions");
        jdbc.update("DELETE FROM challenge_projects");
        jdbc.update("DELETE FROM teacher_profiles");
        jdbc.update("DELETE FROM student_profiles");
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM school_registrations");
        jdbc.update("DELETE FROM schools");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM media_review_records");
        jdbc.update("DELETE FROM media");
        jdbc.update("DELETE FROM result_versions");
        jdbc.update("DELETE FROM activity_results");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM audit_records");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void twoConcurrentBootstrapsOnlyOneSucceeds() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                barrier.await(); // synchronize start
                bootstrapService.bootstrap("adminA", "password123");
                successCount.incrementAndGet();
            } catch (BootstrapRefusedException e) {
                failureCount.incrementAndGet();
            } catch (InterruptedException | BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join(30000);
        t2.join(30000);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        int count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(count).isEqualTo(1);

        int adminCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE platform_role = 'SUPER_ADMIN'", Integer.class);
        assertThat(adminCount).isEqualTo(1);
    }
}
