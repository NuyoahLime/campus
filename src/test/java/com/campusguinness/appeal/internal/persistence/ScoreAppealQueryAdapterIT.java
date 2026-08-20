package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ScoreAppealQueryAdapterIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private ScoreAppealQueryAdapter adapter;
    @Autowired private JdbcTemplate jdbc;

    private UUID schoolA;
    private UUID schoolB;
    private UUID studentA;
    private UUID studentB;
    private UUID appealA;
    private UUID appealB;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("A");
        schoolB = insertSchool("B");
        studentA = insertUser("student-a");
        studentB = insertUser("student-b");
        UUID enteredBy = insertUser("entered-by");

        UUID scoreA = insertScoreGraph(schoolA, studentA, enteredBy, "Score Appeal Activity A", "Score Appeal Project A");
        UUID scoreB = insertScoreGraph(schoolB, studentB, enteredBy, "Score Appeal Activity B", "Score Appeal Project B");
        appealA = insertAppeal(schoolA, studentA, scoreA, "SUBMITTED");
        appealB = insertAppeal(schoolB, studentB, scoreB, "PROCESSING");
    }

    @Test
    void studentScopeListsAndReadsOnlyOwnSchoolAppeals() {
        var page = adapter.findByStudent(studentA, schoolA, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).extracting("appealId").containsExactly(appealA);
        assertThat(adapter.findByIdAndStudent(appealA, studentA, schoolA)).isPresent();
        assertThat(adapter.findByIdAndStudent(appealB, studentA, schoolA)).isEmpty();
    }

    @Test
    void schoolScopeListsAndReadsOnlySameSchoolAppeals() {
        var page = adapter.findBySchool(schoolA, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).extracting("appealId").containsExactly(appealA);
        assertThat(adapter.findByIdAndSchool(appealA, schoolA)).isPresent();
        assertThat(adapter.findByIdAndSchool(appealB, schoolA)).isEmpty();
    }

    @Test
    void historicalRankingAppealRemainsReadable() {
        UUID scoreAttemptId = jdbc.queryForObject(
                "SELECT score_attempt_id FROM score_appeals WHERE id = ?", UUID.class, appealA);
        UUID rankingAppeal = insertAppeal(schoolA, studentA, scoreAttemptId, "RANKING", "SUBMITTED");

        assertThat(adapter.findByIdAndStudent(rankingAppeal, studentA, schoolA))
                .get().extracting("appealType").isEqualTo("RANKING");
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, "Stage21 Appeal School " + label, "USCC", "ST21-A-" + label + "-" + id.toString().substring(0, 8),
                "ST21-AI-" + label + "-" + id.toString().substring(0, 8), "UNIVERSITY", "Region", "Address",
                "Contact", "13800000000", "stage21-appeal-" + label + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                id, "stage21-appeal-" + label + "-" + id.toString().substring(0, 8), "{noop}password", "NORMAL");
        return id;
    }

    private UUID insertScoreGraph(UUID schoolId, UUID studentId, UUID enteredBy, String activityName, String projectName) {
        UUID project = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        UUID activity = UUID.randomUUID();
        UUID activityProject = UUID.randomUUID();
        UUID score = UUID.randomUUID();
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,score_unit,effective_score_rule,project_status,current_rule_version_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                project, projectName, "SPORTS", "INTEGER", "NUMERIC", "HIGHER_BETTER", "times", "BEST", "PUBLISHED", null);
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,score_unit,effective_score_rule,rules_text,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                rule, project, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "times", "BEST", "rules", enteredBy);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", rule, project);
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",
                activity, schoolId, activityName, "PUBLISHED", "PUBLIC", enteredBy);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                activityProject, activity, project, rule);
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,score_status,entered_by,score_business_time) VALUES (?,?,?,?,?,?,?,?,?,?)",
                score, schoolId, activityProject, studentId, 1, "INTEGER", BigDecimal.TEN, "APPROVED", enteredBy,
                java.sql.Timestamp.from(Instant.now()));
        return score;
    }

    private UUID insertAppeal(UUID schoolId, UUID studentId, UUID scoreId, String status) {
        return insertAppeal(schoolId, studentId, scoreId, "SCORE", status);
    }

    private UUID insertAppeal(UUID schoolId, UUID studentId, UUID scoreId, String appealType, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO score_appeals(id,school_id,score_attempt_id,student_id,appeal_type,appeal_reason,appeal_status) VALUES (?,?,?,?,?,?,?)",
                id, schoolId, scoreId, studentId, appealType, "reason", status);
        return id;
    }
}
