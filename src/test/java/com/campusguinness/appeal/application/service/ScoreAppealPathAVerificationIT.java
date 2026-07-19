package com.campusguinness.appeal.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.score.internal.domain.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ScoreAppealPathAVerificationIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ScoreAppealCorrectionService svc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private UUID schoolId, studentId, oldAttemptId, appealId;
    private UUID enteredById;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID(); studentId = UUID.randomUUID();
        oldAttemptId = UUID.randomUUID(); appealId = UUID.randomUUID();
        enteredById = UUID.randomUUID();
        UUID projectId = UUID.randomUUID(); UUID activityId = UUID.randomUUID();
        UUID apId = UUID.randomUUID(); UUID ruleVerId = UUID.randomUUID();
        UUID handlerId = UUID.randomUUID();

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
}
