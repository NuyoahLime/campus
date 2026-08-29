package com.campusguinness.appeal.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.score.internal.domain.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ScoreAppealPathAVerificationIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ScoreAppealCorrectionService svc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private com.campusguinness.score.application.port.ScoreWriteContextPort scoreContext;

    private UUID schoolId, studentId, oldAttemptId, appealId;
    private UUID enteredById;
    private UUID activityId, apId, projectId, ruleVerId, handlerId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID(); studentId = UUID.randomUUID();
        oldAttemptId = UUID.randomUUID(); appealId = UUID.randomUUID();
        enteredById = UUID.randomUUID();
        projectId = UUID.randomUUID(); activityId = UUID.randomUUID();
        apId = UUID.randomUUID(); ruleVerId = UUID.randomUUID();
        handlerId = UUID.randomUUID();

        String uc = "UC-" + UUID.randomUUID().toString().substring(0, 8);
        String ic = "IC-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId,"s","USCC",uc,ic,"PRIMARY","Beijing","a","n","p","e","NORMAL");
        String un1 = "u-" + UUID.randomUUID().toString().substring(0, 6);
        String un2 = "u-" + UUID.randomUUID().toString().substring(0, 6);
        String un3 = "u-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", studentId,un1,"h","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", enteredById,un2,"h","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", handlerId,un3,"h","NORMAL");
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                projectId,"p","MATH","INTEGER","NUMERIC","HIGHER_BETTER",false,"BEST","PUBLISHED");
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,created_by) VALUES (?,?,?,?,?,?,?,?)",
                ruleVerId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", enteredById);
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "act", "PUBLISHED", "NOT_SUBMITTED", enteredById);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                apId, activityId, projectId, ruleVerId);
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,is_current_effective,score_status,entered_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                oldAttemptId, schoolId, apId, studentId, 1, "INTEGER", 100, true, "APPROVED", enteredById, 1);
        jdbc.update("INSERT INTO score_appeals(id,school_id,score_attempt_id,student_id,appeal_type,appeal_reason,appeal_status,handler_id,version) VALUES (?,?,?,?,?,?,?,?,?)",
                appealId, schoolId, oldAttemptId, studentId, "SCORE", "wrong score", "PROCESSING", handlerId, 1);
    }

    @AfterEach
    void tearDown() {
        // FK-safe deletion of owned data. This class has no @Transactional,
        // so JdbcTemplate operations auto-commit immediately.
        jdbc.update("DELETE FROM appeal_records WHERE appeal_id IN (SELECT id FROM score_appeals WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM score_appeals WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM score_correction_records WHERE original_score_id IN (SELECT id FROM score_attempts WHERE school_id = ?)"
                + " OR new_score_id IN (SELECT id FROM score_attempts WHERE school_id = ?)", schoolId, schoolId);
        jdbc.update("DELETE FROM score_attempts WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM activity_projects WHERE activity_id = ?", activityId);
        jdbc.update("DELETE FROM project_rule_versions WHERE id = ?", ruleVerId);
        jdbc.update("DELETE FROM challenge_projects WHERE id = ?", projectId);
        jdbc.update("DELETE FROM activities WHERE id = ?", activityId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", studentId, enteredById, handlerId);
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
    }

    @Test @DisplayName("full Path A success: appeal resolved, old invalidated, new current effective")
    void fullPathASuccess() {
        var tt = new TransactionTemplate(txManager);
        tt.executeWithoutResult(status -> {
            svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "fixed", enteredById);
        });

        var appealStatus = jdbc.queryForObject("SELECT appeal_status FROM score_appeals WHERE id = ?", String.class, appealId);
        assertThat(appealStatus).isEqualTo("RESOLVED");

        var oldStatus = jdbc.queryForObject("SELECT score_status, is_current_effective FROM score_attempts WHERE id = ?", (rs, i) -> new Object[]{rs.getString("score_status"), rs.getBoolean("is_current_effective")}, oldAttemptId);
        assertThat(oldStatus[0]).isEqualTo("INVALIDATED");
        assertThat(oldStatus[1]).isEqualTo(false);

        var count = jdbc.queryForObject("SELECT COUNT(*) FROM score_attempts WHERE student_id = ? AND activity_project_id = ? AND is_current_effective = true",
                Integer.class, studentId, jdbc.queryForObject("SELECT activity_project_id FROM score_attempts WHERE id = ?", UUID.class, oldAttemptId));
        assertThat(count).isEqualTo(1);

        var replacement = jdbc.queryForMap("SELECT id, replaces_id, score_status, is_current_effective, is_manual_makeup FROM score_attempts WHERE replaces_id = ?",
                oldAttemptId);
        assertThat(replacement.get("replaces_id")).isEqualTo(oldAttemptId);
        assertThat(replacement.get("score_status")).isEqualTo("APPROVED");
        assertThat(replacement.get("is_current_effective")).isEqualTo(true);
        assertThat(replacement.get("is_manual_makeup")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_correction_records WHERE original_score_id = ?",
                Integer.class, oldAttemptId)).isEqualTo(1);
    }

    @Test @DisplayName("transaction rollback: all state unchanged on failure")
    void rollbackOnFailure() {
        var tt = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tt.executeWithoutResult(status -> {
            svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "fixed", enteredById);
            throw new RuntimeException("simulated failure after save");
        })).isInstanceOf(RuntimeException.class);

        var appealStatus = jdbc.queryForObject("SELECT appeal_status FROM score_appeals WHERE id = ?", String.class, appealId);
        assertThat(appealStatus).isEqualTo("PROCESSING");

        var oldStatus = jdbc.queryForObject("SELECT score_status, is_current_effective FROM score_attempts WHERE id = ?", (rs, i) -> new Object[]{rs.getString("score_status"), rs.getBoolean("is_current_effective")}, oldAttemptId);
        assertThat(oldStatus[0]).isEqualTo("APPROVED");
        assertThat(oldStatus[1]).isEqualTo(true);
    }

    @Test @DisplayName("correction record failure rolls back score and appeal mutations")
    void correctionRecordFailureRollsBackAllMutations() {
        var tt = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tt.executeWithoutResult(status ->
                svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), null, enteredById)))
                .isNotNull();

        assertThat(jdbc.queryForObject("SELECT appeal_status FROM score_appeals WHERE id = ?", String.class, appealId))
                .isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT score_status FROM score_attempts WHERE id = ?", String.class, oldAttemptId))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_attempts WHERE replaces_id = ?",
                Integer.class, oldAttemptId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_correction_records WHERE original_score_id = ?",
                Integer.class, oldAttemptId)).isZero();
    }

    @Test @DisplayName("correcting a non-current attempt replaces the prior current effective score")
    void correctionOfNonCurrentAttemptStillMakesReplacementCurrent() {
        UUID priorCurrentId = UUID.randomUUID();
        jdbc.update("UPDATE score_attempts SET is_current_effective = false WHERE id = ?", oldAttemptId);
        jdbc.update("""
                INSERT INTO score_attempts(
                    id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,
                    score_value,is_current_effective,score_status,entered_by,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, priorCurrentId, schoolId, apId, studentId, 3, "INTEGER",
                80, true, "APPROVED", enteredById, 1);

        var tt = new TransactionTemplate(txManager);
        tt.executeWithoutResult(status ->
                svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "fixed", enteredById));

        assertThat(jdbc.queryForObject("SELECT score_status FROM score_attempts WHERE id = ?",
                String.class, oldAttemptId)).isEqualTo("INVALIDATED");
        assertThat(jdbc.queryForObject("SELECT is_current_effective FROM score_attempts WHERE id = ?",
                Boolean.class, priorCurrentId)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_attempts
                WHERE student_id = ? AND activity_project_id = ? AND is_current_effective = true
                """, Integer.class, studentId, apId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT is_current_effective FROM score_attempts WHERE replaces_id = ?
                """, Boolean.class, oldAttemptId)).isTrue();
    }

    @Test @DisplayName("historical correction allocates after the latest existing attempt")
    void correctionOfHistoricalAttemptAllocatesAfterLatestAttemptNumber() {
        insertAttempt(UUID.randomUUID(), 2, false, 80);

        runCorrection("fixed");

        assertThat(jdbc.queryForObject("""
                SELECT attempt_number FROM score_attempts
                WHERE replaces_id = ?
                """, Integer.class, oldAttemptId)).isEqualTo(3);
    }

    @Test @DisplayName("correction with multiple later attempts allocates the next sequence number")
    void correctionWithMultipleLaterAttemptsAllocatesAfterMaximum() {
        insertAttempt(UUID.randomUUID(), 2, false, 80);
        insertAttempt(UUID.randomUUID(), 3, false, 70);

        runCorrection("fixed");

        assertThat(jdbc.queryForObject("""
                SELECT attempt_number FROM score_attempts
                WHERE replaces_id = ?
                """, Integer.class, oldAttemptId)).isEqualTo(4);
    }

    @Test @DisplayName("repeated request fails — no duplicate correction")
    void repeatedRequestFails() {
        var tt = new TransactionTemplate(txManager);
        tt.executeWithoutResult(status -> svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "fixed", enteredById));

        assertThatThrownBy(() -> tt.executeWithoutResult(status ->
                svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(300), "again", enteredById)))
                .isNotNull();

        var count = jdbc.queryForObject("SELECT COUNT(*) FROM score_attempts WHERE replaces_id = ?", Integer.class, oldAttemptId);
        assertThat(count).isEqualTo(1);
    }

    @Test @DisplayName("optimistic lock: stale transaction fails")
    void staleTransactionFails() {
        var tt = new TransactionTemplate(txManager);
        tt.executeWithoutResult(status -> svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "fixed", enteredById));

        assertThatThrownBy(() -> tt.executeWithoutResult(status ->
                svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(300), "stale", enteredById)))
                .isNotNull();
    }

    @Test @DisplayName("correction and normal draft allocation share the project serialization lock")
    void correctionAndDraftAllocationDoNotCollide() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> correction = executor.submit(() -> runConcurrent(ready, start, () -> {
                new TransactionTemplate(txManager).executeWithoutResult(status ->
                        svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "fixed", enteredById));
            }));
            Future<Throwable> draft = executor.submit(() -> runConcurrent(ready, start, () -> {
                new TransactionTemplate(txManager).executeWithoutResult(status -> {
                    int number = scoreContext.nextAttemptNumber(apId, studentId);
                    jdbc.update("""
                            INSERT INTO score_attempts(
                                id,school_id,activity_project_id,student_id,attempt_number,
                                score_storage_type,score_value,is_current_effective,score_status,entered_by,version)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?)
                            """, UUID.randomUUID(), schoolId, apId, studentId, number,
                            "INTEGER", 60, false, "DRAFT", enteredById, 1);
                });
            }));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(correction.get(15, TimeUnit.SECONDS)).isNull();
            assertThat(draft.get(15, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_attempts
                WHERE student_id = ? AND activity_project_id = ?
                """, Integer.class, studentId, apId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_attempts
                WHERE student_id = ? AND activity_project_id = ? AND is_current_effective = true
                """, Integer.class, studentId, apId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_attempts
                WHERE student_id = ? AND activity_project_id = ?
                  AND attempt_number IN (2, 3)
                """, Integer.class, studentId, apId)).isEqualTo(2);
    }

    private Throwable runConcurrent(CountDownLatch ready, CountDownLatch start, Runnable operation) {
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            operation.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void runCorrection(String reason) {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), reason, enteredById));
    }

    private void insertAttempt(UUID id, int number, boolean current, int value) {
        jdbc.update("""
                INSERT INTO score_attempts(
                    id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,
                    score_value,is_current_effective,score_status,entered_by,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, id, schoolId, apId, studentId, number, "INTEGER", value,
                current, "APPROVED", enteredById, 1);
    }
}
