package com.campusguinness.score.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class StudentScoreQueryAdapterIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private StudentScoreQueryAdapter adapter;
    @Autowired private JdbcTemplate jdbc;

    private UUID schoolA;
    private UUID studentA;
    private UUID studentB;
    private UUID activityProjectA;
    private UUID approvedA;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        studentA = UUID.randomUUID();
        studentB = UUID.randomUUID();
        UUID enteredBy = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID ruleV1 = UUID.randomUUID();
        UUID ruleV2 = UUID.randomUUID();
        UUID activity = UUID.randomUUID();
        activityProjectA = UUID.randomUUID();
        approvedA = UUID.randomUUID();

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolA, "Score Read School", "USCC", "SCORE-" + schoolA.toString().substring(0, 8), "SINT-" + schoolA.toString().substring(0, 8),
                "UNIVERSITY", "Test Region", "address", "contact", "phone", "score@example.com", "NORMAL");
        for (UUID user : List.of(studentA, studentB, enteredBy)) {
            jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                    user, "score-read-" + user, "hash", "NORMAL");
        }
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,score_unit,effective_score_rule,project_status,current_rule_version_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                project, "Historical Score Project", "SPORTS", "INTEGER", "NUMERIC", "HIGHER_BETTER", "次", "BEST", "PUBLISHED", null);
        insertRule(ruleV1, project, 1, "历史规则 V1");
        insertRule(ruleV2, project, 2, "当前规则 V2");
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", ruleV2, project);
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",
                activity, schoolA, "Score Read Activity", "PUBLISHED", "PUBLIC", enteredBy);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                activityProjectA, activity, project, ruleV1);
        insertScore(approvedA, studentA, enteredBy, "APPROVED", true, 1, "10");
        insertScore(UUID.randomUUID(), studentA, enteredBy, "APPROVED", false, 2, "20");
        insertScore(UUID.randomUUID(), studentA, enteredBy, "DRAFT", false, 3, "30");
        insertScore(UUID.randomUUID(), studentA, enteredBy, "PENDING_REVIEW", false, 4, "40");
        insertScore(UUID.randomUUID(), studentA, enteredBy, "REJECTED", false, 5, "50");
        insertScore(UUID.randomUUID(), studentA, enteredBy, "INVALIDATED", false, 6, "60");
        insertScore(UUID.randomUUID(), studentB, enteredBy, "APPROVED", true, 7, "70");
    }

    @Test
    void exposesOnlyApprovedScoresWithinTheServerScope() {
        var page = adapter.findVisibleByStudent(studentA, schoolA, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().scoreAttemptId()).isEqualTo(approvedA);
        assertThat(adapter.findVisibleById(approvedA, studentB, schoolA)).isEmpty();
    }

    @Test
    void keepsActivityRuleSnapshotWhenProjectCurrentRuleChanges() {
        var detail = adapter.findVisibleById(approvedA, studentA, schoolA).orElseThrow();

        assertThat(detail.ruleVersionNumber()).isEqualTo(1);
        assertThat(detail.rulesText()).isEqualTo("历史规则 V1");
        assertThat(detail.scoreValue()).isEqualTo("10");
    }

    private void insertRule(UUID id, UUID project, int version, String text) {
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,score_unit,effective_score_rule,rules_text,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, project, version, "INTEGER", "NUMERIC", "HIGHER_BETTER", "次", "BEST", text, studentA);
    }

    private void insertScore(UUID id, UUID student, UUID enteredBy, String status, boolean currentEffective,
                             int attempt, String value) {
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,score_status,is_current_effective,entered_by,score_business_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, schoolA, activityProjectA, student, attempt, "INTEGER", new java.math.BigDecimal(value),
                status, currentEffective, enteredBy,
                java.sql.Timestamp.from(Instant.now().minusSeconds(attempt)));
    }
}
