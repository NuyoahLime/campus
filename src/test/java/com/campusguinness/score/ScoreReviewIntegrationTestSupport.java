package com.campusguinness.score;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.infrastructure.security.PrimaryIdentityResolver.ResolvedIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class ScoreReviewIntegrationTestSupport extends PostgreSqlIntegrationTestSupport {
    @Autowired protected JdbcTemplate jdbc;

    protected UUID schoolId;
    protected UUID otherSchoolId;
    protected UUID adminId;
    protected UUID entrantId;
    protected UUID studentId;
    protected UUID teacherId;
    protected UUID activityId;
    protected UUID projectId;
    protected UUID ruleVersionId;
    protected UUID activityProjectId;
    protected UUID attemptId;

    @BeforeEach
    void seedScoreReviewFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        schoolId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        school(schoolId, "Review School " + suffix);
        school(otherSchoolId, "Other School " + suffix);
        adminId = user("review-admin-" + suffix);
        entrantId = user("review-entrant-" + suffix);
        studentId = user("review-student-" + suffix);
        teacherId = user("review-teacher-" + suffix);
        membership(adminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        membership(entrantId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        membership(studentId, schoolId, "STUDENT", "ACTIVE");
        membership(teacherId, schoolId, "TEACHER", "ACTIVE");

        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO challenge_projects(
                  id,name,category,score_storage_type,score_indicator_type,
                  comparison_direction,score_unit,decimal_places,allow_tie,
                  effective_score_rule,project_status,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, projectId, "Review Project " + suffix, "SPORT", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "次", 0, true, "BEST", "PUBLISHED",
                ts(Instant.now()), ts(Instant.now()), 1);
        jdbc.update("""
                INSERT INTO project_rule_versions(
                  id,project_id,version_number,score_storage_type,score_indicator_type,
                  comparison_direction,effective_score_rule,score_unit,decimal_places,
                  created_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, ruleVersionId, projectId, 1, "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "BEST", "次", 0, adminId, ts(Instant.now()));
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=? WHERE id=?",
                ruleVersionId, projectId);

        activityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                  id,school_id,title,execution_status,public_status,created_by,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, activityId, schoolId, "Review Activity " + suffix, "PUBLISHED",
                "NOT_SUBMITTED", adminId, ts(Instant.now()), ts(Instant.now()), 1);
        activityProjectId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id,created_at)
                VALUES (?,?,?,?,?)
                """, activityProjectId, activityId, projectId, ruleVersionId, ts(Instant.now()));
        attemptId = insertAttempt(schoolId, entrantId, "PENDING_REVIEW", false, 100);
    }

    @AfterEach
    void cleanScoreReviewFixture() {
        jdbc.update("""
                DELETE FROM score_review_records
                WHERE score_attempt_id IN (
                  SELECT id FROM score_attempts WHERE school_id IN (?,?))
                """, schoolId, otherSchoolId);
        jdbc.update("DELETE FROM score_attempts WHERE school_id IN (?,?)", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM activity_projects WHERE id=?", activityProjectId);
        jdbc.update("DELETE FROM activities WHERE id=?", activityId);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", projectId);
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", projectId);
        jdbc.update("DELETE FROM challenge_projects WHERE id=?", projectId);
        jdbc.update("DELETE FROM school_memberships WHERE school_id IN (?,?)", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?,?)",
                adminId, entrantId, studentId, teacherId);
        jdbc.update("DELETE FROM schools WHERE id IN (?,?)", schoolId, otherSchoolId);
    }

    protected UUID insertAttempt(
            UUID attemptSchoolId, UUID enteredBy, String status,
            boolean currentEffective, int value) {
        UUID id = UUID.randomUUID();
        int attemptNumber = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_number),0)+1
                FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                """, Integer.class, activityProjectId, studentId);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO score_attempts(
                  id,school_id,activity_project_id,student_id,attempt_number,
                  score_storage_type,score_value,score_business_time,time_source,
                  is_current_effective,score_status,entered_by,submitted_at,
                  is_manual_makeup,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, attemptSchoolId, activityProjectId, studentId, attemptNumber,
                "INTEGER", value, ts(now), "TEACHER", currentEffective, status,
                enteredBy, ts(now), false, ts(now), ts(now), 1);
        return id;
    }

    protected RequestPostProcessor auth(UUID userId, UUID membershipSchool, String role) {
        var memberships = membershipSchool == null
                ? List.<SchoolMembershipRecord>of()
                : List.of(new SchoolMembershipRecord(membershipSchool, role));
        var identity = new ResolvedIdentity(userId, role, membershipSchool, "NORMAL");
        var details = new CampusGuinnessUserDetails(
                userId, "test-" + userId, "hash", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)), memberships, identity);
        var authentication = new UsernamePasswordAuthenticationToken(
                details, details.getPassword(), details.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    private UUID user(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,account_status)
                VALUES (?,?,?,?)
                """, id, username, "$2a$10$hash0000000000000000000000", "NORMAL");
        return id;
    }

    private void school(UUID id, String name) {
        jdbc.update("""
                INSERT INTO schools(
                  id,name,unified_code_type,unified_code,internal_code,school_type,
                  region,address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, name, "USCC", "UC-" + id.toString().substring(0, 8),
                "IC-" + id.toString().substring(0, 8), "PRIMARY",
                "Region", "Address", "Contact", "123", "test@example.com", "NORMAL");
    }

    private void membership(UUID user, UUID school, String role, String status) {
        jdbc.update("""
                INSERT INTO school_memberships(
                  id,user_id,school_id,role_in_school,status,started_at,created_at,version)
                VALUES (?,?,?,?,?,now(),now(),1)
                """, UUID.randomUUID(), user, school, role, status);
    }

    protected static Timestamp ts(Instant value) {
        return Timestamp.from(value);
    }
}
