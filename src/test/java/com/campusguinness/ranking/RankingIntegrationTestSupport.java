package com.campusguinness.ranking;

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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class RankingIntegrationTestSupport
        extends PostgreSqlIntegrationTestSupport {

    @Autowired protected JdbcTemplate jdbc;

    protected UUID schoolId;
    protected UUID otherSchoolId;
    protected UUID adminId;
    protected UUID otherAdminId;
    protected UUID teacherId;
    protected UUID studentId;
    protected UUID studentMembershipId;
    protected UUID activityId;
    protected UUID projectId;
    protected UUID ruleVersionId;
    protected UUID activityProjectId;
    protected UUID scoreAttemptId;

    protected String fixtureSuffix;

    @BeforeEach
    void seedRankingFixture() {
        fixtureSuffix = UUID.randomUUID().toString().substring(0, 8);
        schoolId = createSchool("Ranking School " + fixtureSuffix);
        otherSchoolId = createSchool("Other Ranking School " + fixtureSuffix);
        adminId = createUser("ranking-admin-" + fixtureSuffix);
        otherAdminId = createUser("ranking-other-admin-" + fixtureSuffix);
        teacherId = createUser("ranking-teacher-" + fixtureSuffix);
        studentId = createUser("ranking-student-" + fixtureSuffix);
        membership(adminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        membership(otherAdminId, otherSchoolId, "SCHOOL_ADMIN", "ACTIVE");
        membership(teacherId, schoolId, "TEACHER", "ACTIVE");
        studentMembershipId = membership(
                studentId, schoolId, "STUDENT", "ACTIVE");

        projectId = createProject(
                "Ranking Project " + fixtureSuffix,
                "INTEGER",
                "HIGHER_BETTER",
                "BEST",
                null,
                true,
                0,
                adminId);
        ruleVersionId = jdbc.queryForObject(
                "SELECT current_rule_version_id FROM challenge_projects WHERE id=?",
                UUID.class,
                projectId);
        activityId = createActivity(
                schoolId,
                adminId,
                "Ranking Activity " + fixtureSuffix,
                "ENDED");
        activityProjectId = attachProject(
                activityId, projectId, ruleVersionId);
        assignStudent(
                activityId, activityProjectId, studentMembershipId, adminId);
        scoreAttemptId = createScore(
                activityProjectId,
                schoolId,
                studentId,
                teacherId,
                "INTEGER",
                new BigDecimal("100"),
                null,
                null,
                "APPROVED",
                true,
                Instant.parse("2026-07-30T08:00:00Z"));
    }

    @AfterEach
    void cleanRankingFixture() {
        jdbc.update("""
                DELETE FROM achievement_records
                WHERE ranking_version_id IN (
                  SELECT version.id
                  FROM ranking_versions version
                  JOIN ranking_definitions definition
                    ON definition.id = version.definition_id
                  JOIN activity_projects ap
                    ON ap.id = definition.activity_project_id
                  JOIN activities activity
                    ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM ranking_entry_score_sources
                WHERE entry_id IN (
                  SELECT entry.id
                  FROM ranking_entries entry
                  JOIN ranking_versions version ON version.id = entry.version_id
                  JOIN ranking_definitions definition
                    ON definition.id = version.definition_id
                  JOIN activity_projects ap
                    ON ap.id = definition.activity_project_id
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM ranking_entries
                WHERE version_id IN (
                  SELECT version.id
                  FROM ranking_versions version
                  JOIN ranking_definitions definition
                    ON definition.id = version.definition_id
                  JOIN activity_projects ap
                    ON ap.id = definition.activity_project_id
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                UPDATE ranking_definitions
                SET current_version_id = NULL
                WHERE id IN (
                  SELECT definition.id
                  FROM ranking_definitions definition
                  JOIN activity_projects ap
                    ON ap.id = definition.activity_project_id
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM ranking_versions
                WHERE definition_id IN (
                  SELECT definition.id
                  FROM ranking_definitions definition
                  JOIN activity_projects ap
                    ON ap.id = definition.activity_project_id
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM ranking_definitions
                WHERE activity_project_id IN (
                  SELECT ap.id
                  FROM activity_projects ap
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM score_review_records
                WHERE score_attempt_id IN (
                  SELECT score.id
                  FROM score_attempts score
                  JOIN activity_projects ap
                    ON ap.id = score.activity_project_id
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM score_attempts
                WHERE activity_project_id IN (
                  SELECT ap.id
                  FROM activity_projects ap
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM responsible_teachers
                WHERE activity_project_id IN (
                  SELECT ap.id
                  FROM activity_projects ap
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM activity_project_participants
                WHERE activity_project_id IN (
                  SELECT ap.id
                  FROM activity_projects ap
                  JOIN activities activity ON activity.id = ap.activity_id
                  WHERE activity.title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM activity_participants
                WHERE activity_id IN (
                  SELECT id FROM activities WHERE title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM activity_projects
                WHERE activity_id IN (
                  SELECT id FROM activities WHERE title LIKE ?)
                """, "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update(
                "DELETE FROM activities WHERE title LIKE ?",
                "%Ranking Activity " + fixtureSuffix + "%");
        jdbc.update("""
                UPDATE challenge_projects
                SET current_rule_version_id = NULL
                WHERE name LIKE ?
                """, "%Ranking Project " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM project_rule_versions
                WHERE project_id IN (
                  SELECT id FROM challenge_projects WHERE name LIKE ?)
                """, "%Ranking Project " + fixtureSuffix + "%");
        jdbc.update(
                "DELETE FROM challenge_projects WHERE name LIKE ?",
                "%Ranking Project " + fixtureSuffix + "%");
        jdbc.update("""
                DELETE FROM student_profiles
                WHERE membership_id IN (
                  SELECT id FROM school_memberships
                  WHERE school_id IN (?,?))
                """, schoolId, otherSchoolId);
        jdbc.update(
                "DELETE FROM school_memberships WHERE school_id IN (?,?)",
                schoolId,
                otherSchoolId);
        jdbc.update(
                "DELETE FROM users WHERE username LIKE ?",
                "%-" + fixtureSuffix);
        jdbc.update(
                "DELETE FROM schools WHERE id IN (?,?)",
                schoolId,
                otherSchoolId);
    }

    protected UUID createSchool(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO schools(
                  id,name,unified_code_type,unified_code,internal_code,school_type,
                  region,address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                name,
                "USCC",
                "UC-" + id.toString().substring(0, 8),
                "IC-" + id.toString().substring(0, 8),
                "PRIMARY",
                "Region",
                "Address",
                "Contact",
                "123",
                "ranking@example.com",
                "NORMAL");
        return id;
    }

    protected UUID createUser(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,account_status)
                VALUES (?,?,?,?)
                """, id, username, "$2a$10$rankinghash00000000000000", "NORMAL");
        return id;
    }

    protected UUID membership(
            UUID userId, UUID school, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(
                  id,user_id,school_id,role_in_school,status,
                  started_at,created_at,version)
                VALUES (?,?,?,?,?,now(),now(),1)
                """, id, userId, school, role, status);
        return id;
    }

    protected UUID createProject(
            String name,
            String storageType,
            String direction,
            String effectiveRule,
            String gradeOrder,
            boolean allowTie,
            Integer decimalPlaces,
            UUID createdBy) {
        UUID project = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO challenge_projects(
                  id,name,category,description,rules_text,
                  score_storage_type,score_indicator_type,comparison_direction,
                  score_unit,decimal_places,grade_order,allow_tie,
                  effective_score_rule,project_status,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                project,
                name,
                "SPORT",
                "Description",
                "Rules",
                storageType,
                "GRADE".equals(storageType) ? "GRADE" : "NUMERIC",
                direction,
                "points",
                decimalPlaces,
                gradeOrder,
                allowTie,
                effectiveRule,
                "PUBLISHED",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                1);
        jdbc.update("""
                INSERT INTO project_rule_versions(
                  id,project_id,version_number,score_storage_type,
                  score_indicator_type,comparison_direction,score_unit,
                  decimal_places,grade_order,rules_text,effective_score_rule,
                  created_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                rule,
                project,
                1,
                storageType,
                "GRADE".equals(storageType) ? "GRADE" : "NUMERIC",
                direction,
                "points",
                decimalPlaces,
                gradeOrder,
                "Rules",
                effectiveRule,
                createdBy,
                Timestamp.from(Instant.now()));
        jdbc.update(
                "UPDATE challenge_projects SET current_rule_version_id=? WHERE id=?",
                rule,
                project);
        return project;
    }

    protected UUID createActivity(
            UUID school, UUID creator, String title, String status) {
        UUID activity = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                  id,school_id,title,start_time,end_time,location,
                  execution_status,public_status,created_by,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                activity,
                school,
                title,
                Timestamp.from(Instant.now().minusSeconds(3600)),
                Timestamp.from(Instant.now()),
                "Gym",
                status,
                "NOT_SUBMITTED",
                creator,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                1);
        return activity;
    }

    protected UUID attachProject(UUID activity, UUID project, UUID rule) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_projects(
                  id,activity_id,project_id,rule_version_id,created_at)
                VALUES (?,?,?,?,now())
                """, id, activity, project, rule);
        return id;
    }

    protected void assignStudent(
            UUID activity,
            UUID activityProject,
            UUID studentMembership,
            UUID assignedBy) {
        UUID participant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_participants(
                  id,activity_id,student_membership_id,created_at)
                VALUES (?,?,?,now())
                """, participant, activity, studentMembership);
        jdbc.update("""
                INSERT INTO activity_project_participants(
                  id,activity_project_id,activity_participant_id,
                  assigned_by,assigned_at)
                VALUES (?,?,?,?,now())
                """, UUID.randomUUID(), activityProject, participant, assignedBy);
    }

    protected UUID createScore(
            UUID activityProject,
            UUID school,
            UUID student,
            UUID enteredBy,
            String storageType,
            BigDecimal value,
            Long durationMs,
            String grade,
            String status,
            boolean currentEffective,
            Instant businessTime) {
        UUID id = UUID.randomUUID();
        int attempt = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1
                FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                """, Integer.class, activityProject, student);
        jdbc.update("""
                INSERT INTO score_attempts(
                  id,school_id,activity_project_id,student_id,attempt_number,
                  score_storage_type,score_value,score_duration_ms,score_grade,
                  score_business_time,time_source,is_current_effective,
                  score_status,entered_by,submitted_at,is_manual_makeup,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                school,
                activityProject,
                student,
                attempt,
                storageType,
                value,
                durationMs,
                grade,
                Timestamp.from(businessTime),
                "TEACHER",
                currentEffective,
                status,
                enteredBy,
                Timestamp.from(businessTime),
                false,
                Timestamp.from(businessTime),
                Timestamp.from(businessTime),
                1);
        return id;
    }

    protected RequestPostProcessor auth(
            UUID userId, UUID membershipSchool, String role) {
        var memberships = membershipSchool == null
                ? List.<SchoolMembershipRecord>of()
                : List.of(new SchoolMembershipRecord(membershipSchool, role));
        var identity = new ResolvedIdentity(
                userId, role, membershipSchool, "NORMAL");
        var details = new CampusGuinnessUserDetails(
                userId,
                "test-" + userId,
                "hash",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships,
                identity);
        var authentication = new UsernamePasswordAuthenticationToken(
                details, details.getPassword(), details.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
