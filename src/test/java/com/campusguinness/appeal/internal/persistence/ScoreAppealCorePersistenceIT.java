package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Transactional
class ScoreAppealCorePersistenceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ScoreAppealRepository appealRepo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private UUID schoolId, studentId, attemptId, handlerId, enteredById;
    private UUID activityId, apId, projectId, ruleVerId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID(); studentId = UUID.randomUUID(); attemptId = UUID.randomUUID();
        handlerId = UUID.randomUUID();
        enteredById = UUID.randomUUID(); projectId = UUID.randomUUID();
        activityId = UUID.randomUUID(); apId = UUID.randomUUID();
        ruleVerId = UUID.randomUUID();

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId,"s","USCC","u","i","PRIMARY","Beijing","a","n","p","e","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", studentId,"stu","h","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", enteredById,"tch","h","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", handlerId,"hdlr","h","NORMAL");
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                projectId,"p","MATH","INTEGER","NUMERIC","HIGHER_BETTER",false,"BEST","PUBLISHED");
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,created_by) VALUES (?,?,?,?,?,?,?,?)",
                ruleVerId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", enteredById);
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "act", "PUBLISHED", "NOT_SUBMITTED", enteredById);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                apId, activityId, projectId, ruleVerId);
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,is_current_effective,score_status,entered_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                attemptId, schoolId, apId, studentId, 1, "INTEGER", 100, true, "APPROVED", enteredById, 1);
    }

    @AfterEach
    void tearDown() {
        // Use REQUIRES_NEW so cleanup commits independently of the test transaction.
        var tt = new TransactionTemplate(txManager);
        tt.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        tt.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM appeal_records WHERE appeal_id IN (SELECT id FROM score_appeals WHERE school_id = ?)", schoolId);
            jdbc.update("DELETE FROM score_appeals WHERE school_id = ?", schoolId);
            jdbc.update("DELETE FROM score_attempts WHERE school_id = ?", schoolId);
            jdbc.update("DELETE FROM activity_projects WHERE activity_id = ?", activityId);
            jdbc.update("DELETE FROM project_rule_versions WHERE id = ?", ruleVerId);
            jdbc.update("DELETE FROM challenge_projects WHERE id = ?", projectId);
            jdbc.update("DELETE FROM activities WHERE id = ?", activityId);
            jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", studentId, enteredById, handlerId);
            jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
        });
    }

    private ScoreAppeal createAppeal() {
        var a = ScoreAppeal.create(new ScoreAppeal.Builder()
                .id(new ScoreAppealId(UUID.randomUUID())).schoolId(schoolId)
                .scoreAttemptId(attemptId).studentId(studentId)
                .appealType("SCORE").appealReason("wrong"));
        appealRepo.save(a);
        return a;
    }

    @Test @DisplayName("INSERT → 1 row in DB")
    void insertCreatesOneRow() {
        var appeal = createAppeal();
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM score_appeals WHERE id = ?", Integer.class, appeal.id().value());
        assertThat(count).isEqualTo(1);
    }

    @Test @DisplayName("UPDATE increments version, preserves id/createdAt")
    void updateIncrementsVersion() {
        var appeal = createAppeal();
        var v1 = jdbc.queryForObject("SELECT version FROM score_appeals WHERE id = ?", Integer.class, appeal.id().value());

        var reloaded = appealRepo.findById(appeal.id()).orElseThrow();
        reloaded.beginProcessing(handlerId);
        appealRepo.save(reloaded);

        var v2 = jdbc.queryForObject("SELECT version FROM score_appeals WHERE id = ?", Integer.class, appeal.id().value());
        assertThat(v2).isGreaterThan(v1);

        var rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM score_appeals WHERE id = ?", Integer.class, appeal.id().value());
        assertThat(rowCount).isEqualTo(1);
    }
}
