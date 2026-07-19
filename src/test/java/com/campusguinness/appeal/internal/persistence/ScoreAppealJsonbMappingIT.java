package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Transactional
class ScoreAppealJsonbMappingIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ScoreAppealRepository appealRepo;
    @Autowired private JdbcTemplate jdbc;

    private UUID schoolId, studentId, attemptId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID(); studentId = UUID.randomUUID(); attemptId = UUID.randomUUID();
        UUID enteredById = UUID.randomUUID(); UUID projectId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID(); UUID apId = UUID.randomUUID();
        UUID ruleVerId = UUID.randomUUID();

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId,"s","USCC","u","i","PRIMARY","Beijing","a","n","p","e","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", studentId,"stu","h","NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", enteredById,"tch","h","NORMAL");
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

    @Test @DisplayName("INSERT with JSON array evidence → jsonb_typeof = array")
    void insertJsonArrayEvidence() {
        var appeal = ScoreAppeal.create(new ScoreAppeal.Builder()
                .id(new ScoreAppealId(UUID.randomUUID())).schoolId(schoolId)
                .scoreAttemptId(attemptId).studentId(studentId)
                .appealType("SCORE").appealReason("wrong")
                .evidenceFileKeys("[\"key-a\",\"key-b\"]"));
        appealRepo.save(appeal);

        var type = jdbc.queryForObject("SELECT jsonb_typeof(evidence_file_keys) FROM score_appeals WHERE id = ?",
                String.class, appeal.id().value());
        assertThat(type).isEqualTo("array");
    }

    @Test @DisplayName("INSERT with null evidence succeeds")
    void insertNullEvidence() {
        var appeal = ScoreAppeal.create(new ScoreAppeal.Builder()
                .id(new ScoreAppealId(UUID.randomUUID())).schoolId(schoolId)
                .scoreAttemptId(attemptId).studentId(studentId)
                .appealType("SCORE").appealReason("wrong"));
        appealRepo.save(appeal);

        var isNull = jdbc.queryForObject("SELECT evidence_file_keys IS NULL FROM score_appeals WHERE id = ?",
                Boolean.class, appeal.id().value());
        assertThat(isNull).isTrue();
    }
}
