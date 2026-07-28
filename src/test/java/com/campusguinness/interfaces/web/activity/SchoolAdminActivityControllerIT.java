package com.campusguinness.interfaces.web.activity;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.infrastructure.security.PrimaryIdentityResolver.ResolvedIdentity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolAdminActivityControllerIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId, userId, otherSchoolId, studentId, teacherId;
    UUID activityId;
    final List<UUID> createdProjectIds = new ArrayList<>();

    @BeforeEach void setUp() {
        schoolId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        activityId = UUID.randomUUID();

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "My School", "USCC", "MY-01", "INT-MY", "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                otherSchoolId, "Other School", "USCC", "OTH-01", "INT-OTH", "PRIMARY", "Shanghai", "addr", "n", "p", "e", "NORMAL");

        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                userId, "sa-" + UUID.randomUUID().toString().substring(0, 6), "$2a$10$hash0000000000000000000000", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                studentId, "st-" + UUID.randomUUID().toString().substring(0, 6), "$2a$10$hash0000000000000000000000", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                teacherId, "te-" + UUID.randomUUID().toString().substring(0, 6), "$2a$10$hash0000000000000000000000", "NORMAL");

        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), userId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), studentId, schoolId, "STUDENT", "ACTIVE");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), teacherId, schoolId, "TEACHER", "ACTIVE");
    }

    @AfterEach void tearDown() {
        // FK-safe deletion order: activity_projects → project_rule_versions → challenge_projects → activities → identities
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE school_id IN (?,?))", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM activities WHERE school_id IN (?,?)", schoolId, otherSchoolId);
        for (UUID pid : createdProjectIds) {
            jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", pid);
            jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", pid);
            jdbc.update("DELETE FROM challenge_projects WHERE id=?", pid);
        }
        createdProjectIds.clear();
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?,?,?)", userId, studentId, teacherId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", userId, studentId, teacherId);
        jdbc.update("DELETE FROM schools WHERE id IN (?,?)", schoolId, otherSchoolId);
    }

    // ── principal helper ──

    private RequestPostProcessor authUser(UUID uid, UUID sid, String role) {
        List<SchoolMembershipRecord> memberships = sid != null
                ? List.of(new SchoolMembershipRecord(sid, role))
                : List.of();
        var identity = new ResolvedIdentity(uid, role, sid, "NORMAL");
        var details = new CampusGuinnessUserDetails(
                uid, "test-" + uid, "hash", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships, identity);
        var auth = new UsernamePasswordAuthenticationToken(details, details.getPassword(), details.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    // ── Authentication / Authorization ──

    @Test @DisplayName("unauthenticated returns 401")
    void unauthenticated401() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("STUDENT returns 403")
    void student403() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities")
                        .with(authUser(studentId, schoolId, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("TEACHER returns 403")
    void teacher403() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities")
                        .with(authUser(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("SCHOOL_ADMIN returns 200 for own school")
    void schoolAdmin200() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk());
    }

    // ── Cross-school isolation ──

    @Test @DisplayName("cannot access activity of other school — returns 404")
    void crossSchoolIsolation() throws Exception {
        UUID otherActivityId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                otherActivityId, otherSchoolId, "Other Act", "DRAFT", "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);

        mvc.perform(get("/api/v1/school-admin/activities/" + otherActivityId)
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("list excludes other school activities")
    void listExcludesOtherSchool() throws Exception {
        seedActivity("My Activity", "DRAFT");
        UUID otherActId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                otherActId, otherSchoolId, "Other Activity", "DRAFT", "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);

        mvc.perform(get("/api/v1/school-admin/activities")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("My Activity"));
    }

    // ── List filtering ──

    @Test @DisplayName("list filters by executionStatus")
    void listFiltersByExecutionStatus() throws Exception {
        seedActivity("Draft A", "DRAFT");
        seedActivity("Published B", "PUBLISHED");

        mvc.perform(get("/api/v1/school-admin/activities?executionStatus=DRAFT")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Draft A"));
    }

    @Test @DisplayName("list filters by publicStatus")
    void listFiltersByPublicStatus() throws Exception {
        seedActivity("Pending", "PUBLISHED");
        jdbc.update("UPDATE activities SET public_status=? WHERE id=?", "PENDING_PLATFORM_REVIEW", activityId);
        UUID a2 = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                a2, schoolId, "Public", "PUBLISHED", "PUBLIC", userId, Instant.now(), Instant.now(), 1);

        mvc.perform(get("/api/v1/school-admin/activities?publicStatus=PUBLIC")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].publicStatus").value("PUBLIC"));
    }

    @Test @DisplayName("list filters by keyword")
    void listFiltersByKeyword() throws Exception {
        seedActivity("Math Challenge", "DRAFT");
        seedActivity("Science Fair", "DRAFT");

        mvc.perform(get("/api/v1/school-admin/activities?keyword=math")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Math Challenge"));
    }

    @Test @DisplayName("list includes totalPages in response")
    void listIncludesTotalPages() throws Exception {
        seedActivity("A", "DRAFT");
        mvc.perform(get("/api/v1/school-admin/activities")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(1)));
    }

    @Test @DisplayName("list rejects invalid executionStatus with 400")
    void listRejectsInvalidExecutionStatus() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?executionStatus=INVALID")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("list rejects keyword over 100 chars with 400")
    void listRejectsTooLongKeyword() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?keyword=" + "A".repeat(101))
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("list rejects negative page with 400")
    void listRejectsNegativePage() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?page=-1")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("list rejects size 0 with 400")
    void listRejectsSizeZero() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?size=0")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("list rejects size over 100 with 400")
    void listRejectsSizeOver100() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?size=101")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // ── Create ──

    @Test @DisplayName("create returns 201 with valid payload")
    void createReturns201() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Activity\",\"description\":\"A test activity\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityId").isNotEmpty())
                .andExpect(jsonPath("$.executionStatus").value("DRAFT"));
    }

    @Test @DisplayName("create rejects endTime before startTime with 400")
    void createRejectsInvalidTime() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bad Time\",\"startTime\":\"2026-09-02T00:00:00Z\",\"endTime\":\"2026-09-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Update ──

    @Test @DisplayName("update DRAFT returns 200")
    void updateDraftReturns200() throws Exception {
        seedActivity("Original Title", "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("DRAFT"));
    }

    @Test @DisplayName("update non-DRAFT rejected")
    void updateNonDraftRejected() throws Exception {
        seedActivity("Published Activity", "PUBLISHED");

        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Changed\"}"))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("update rejects invalid time with 400")
    void updateRejectsInvalidTime() throws Exception {
        seedActivity("Time Test", "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2026-12-31T00:00:00Z\",\"endTime\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Publish ──

    @Test @DisplayName("publish with complete data returns 200 and updates DB")
    void publishCompleteReturns200() throws Exception {
        UUID actId = seedPublishableActivity();

        mvc.perform(post("/api/v1/school-admin/activities/" + actId + "/publish")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("PUBLISHED"));

        String dbStatus = jdbc.queryForObject("SELECT execution_status FROM activities WHERE id=?", String.class, actId);
        assertThat(dbStatus).isEqualTo("PUBLISHED");
    }

    @Test @DisplayName("publish without project returns 409")
    void publishWithoutProjectReturns409() throws Exception {
        UUID actId = seedCompleteActivityWithoutProject();

        mvc.perform(post("/api/v1/school-admin/activities/" + actId + "/publish")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isConflict());
    }

    // ── Projects ──

    @Test @DisplayName("add PUBLISHED project succeeds and persists correct rule_version_id")
    void addPublishedProjectSucceeds() throws Exception {
        seedActivity("Project Test", "DRAFT");
        var pf = seedPublishedProject();

        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/projects")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + pf.projectId + "\"}"))
                .andExpect(status().isOk());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_projects WHERE activity_id=? AND project_id=?",
                Integer.class, activityId, pf.projectId);
        assertThat(count).isEqualTo(1);

        UUID actualRuleVersionId = jdbc.queryForObject(
                "SELECT rule_version_id FROM activity_projects WHERE activity_id=? AND project_id=?",
                UUID.class, activityId, pf.projectId);
        assertThat(actualRuleVersionId).isEqualTo(pf.ruleVersionId);
    }

    @Test @DisplayName("add project without current rule version returns 409")
    void addProjectWithoutCurrentRuleVersionReturns409() throws Exception {
        seedActivity("Project Test", "DRAFT");
        UUID projectId = UUID.randomUUID();
        // Project is PUBLISHED but has no current_rule_version_id set
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "No Rule Project", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", true, "BEST", "PUBLISHED");
        createdProjectIds.add(projectId);

        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/projects")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("remove project succeeds and verifies DB deletion")
    void removeProjectSucceeds() throws Exception {
        seedActivity("Project Test", "DRAFT");
        var pf = seedPublishedProject();
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), activityId, pf.projectId, pf.ruleVersionId);

        mvc.perform(delete("/api/v1/school-admin/activities/" + activityId + "/projects/" + pf.projectId)
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM activity_projects WHERE activity_id=?", Integer.class, activityId);
        assertThat(count).isEqualTo(0);
    }

    // ── DRAFT-only project configuration ──

    @Test @DisplayName("addProject to PUBLISHED activity returns 409")
    void addProjectToPublishedActivityReturns409() throws Exception {
        seedActivity("Published", "PUBLISHED");
        var pf = seedPublishedProject();

        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/projects")
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + pf.projectId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("removeProject from PUBLISHED activity returns 409")
    void removeProjectFromPublishedActivityReturns409() throws Exception {
        seedActivity("Published", "PUBLISHED");
        var pf = seedPublishedProject();
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), activityId, pf.projectId, pf.ruleVersionId);

        mvc.perform(delete("/api/v1/school-admin/activities/" + activityId + "/projects/" + pf.projectId)
                        .with(csrf())
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isConflict());
    }

    // ── Full flow: project publish → rule version → activity project ──

    @Test @DisplayName("full flow: SUPER_ADMIN publishes project → SCHOOL_ADMIN adds to activity with real rule version")
    void fullFlowPublishToActivityProject() throws Exception {
        UUID superAdminId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                superAdminId, "super-" + UUID.randomUUID().toString().substring(0, 6),
                "$2a$10$hash0000000000000000000000", "NORMAL", "SUPER_ADMIN");

        try {
            // Step 1: SUPER_ADMIN creates and publishes a project
            var superAuth = authUser(superAdminId, null, "SUPER_ADMIN");
            jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                    projectId, "FullFlow Project", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", true, "BEST", "DRAFT");
            createdProjectIds.add(projectId);

            mvc.perform(post("/api/v1/challenge-projects/" + projectId + "/publish")
                            .with(csrf())
                            .with(superAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));

            // Assert project_rule_versions was created
            Integer ruleCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_rule_versions WHERE project_id=? AND version_number=1",
                    Integer.class, projectId);
            assertThat(ruleCount).isEqualTo(1);

            // Assert current_rule_version_id is set
            UUID currentRv = jdbc.queryForObject(
                    "SELECT current_rule_version_id FROM challenge_projects WHERE id=?",
                    UUID.class, projectId);
            assertThat(currentRv).isNotNull();

            // Step 2: SCHOOL_ADMIN creates DRAFT activity
            seedActivity("Full Flow Activity", "DRAFT");

            // Step 3: SCHOOL_ADMIN adds the published project
            mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/projects")
                            .with(csrf())
                            .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"projectId\":\"" + projectId + "\"}"))
                    .andExpect(status().isOk());

            // Assert activity_projects.rule_version_id equals the project's current rule version
            UUID actualRv = jdbc.queryForObject(
                    "SELECT rule_version_id FROM activity_projects WHERE activity_id=? AND project_id=?",
                    UUID.class, activityId, projectId);
            assertThat(actualRv).isEqualTo(currentRv);
        } finally {
            jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE school_id=?)", schoolId);
            jdbc.update("DELETE FROM activities WHERE school_id=?", schoolId);
            jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", projectId);
            jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", projectId);
            jdbc.update("DELETE FROM challenge_projects WHERE id=?", projectId);
            createdProjectIds.remove(projectId);
            jdbc.update("DELETE FROM users WHERE id=?", superAdminId);
        }
    }

    // ── CSRF ──

    @Test @DisplayName("write without CSRF returns 403")
    void writeWithoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities")
                        .with(authUser(userId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    /** Holds projectId + ruleVersionId for fixture wiring. */
    record ProjectFixture(UUID projectId, UUID ruleVersionId) {}

    // ── helpers ──

    private void seedActivity(String title, String executionStatus) {
        activityId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                activityId, schoolId, title, executionStatus, "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);
    }

    /** Complete activity with title, time range, location — but no project. */
    private UUID seedCompleteActivityWithoutProject() {
        UUID actId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,description,start_time,end_time,location,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                actId, schoolId, "Complete Without Project", "desc",
                Instant.now(), Instant.now().plusSeconds(86400), "Gym",
                "DRAFT", "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);
        return actId;
    }

    /** Complete activity with title, time range, location, and one PUBLISHED project with rule version. */
    private UUID seedPublishableActivity() {
        UUID actId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,description,start_time,end_time,location,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                actId, schoolId, "Ready Activity", "desc",
                Instant.now(), Instant.now().plusSeconds(86400), "Gym",
                "DRAFT", "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);
        var pf = seedPublishedProject();
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), actId, pf.projectId, pf.ruleVersionId);
        return actId;
    }

    /**
     * Creates a PUBLISHED challenge_project with a current rule version.
     * Returns projectId + ruleVersionId so callers can wire activity_projects correctly.
     */
    private ProjectFixture seedPublishedProject() {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();

        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "Test Project " + projectId.toString().substring(0, 8),
                "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", true, "BEST", "PUBLISHED");

        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,created_by) VALUES (?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", userId);

        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=? WHERE id=?", ruleVersionId, projectId);

        createdProjectIds.add(projectId);
        return new ProjectFixture(projectId, ruleVersionId);
    }
}
