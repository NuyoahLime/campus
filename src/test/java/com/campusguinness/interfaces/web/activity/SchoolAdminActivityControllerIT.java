package com.campusguinness.interfaces.web.activity;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolAdminActivityControllerIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId, userId, otherSchoolId, studentId;
    UUID activityId;

    @BeforeEach void setUp() {
        schoolId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        activityId = UUID.randomUUID();

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "My School", "USCC", "MY-01", "INT-MY", "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                otherSchoolId, "Other School", "USCC", "OTH-01", "INT-OTH", "PRIMARY", "Shanghai", "addr", "n", "p", "e", "NORMAL");

        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                userId, "sa-" + UUID.randomUUID().toString().substring(0, 6), "$2a$10$hash0000000000000000000000", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                studentId, "st-" + UUID.randomUUID().toString().substring(0, 6), "$2a$10$hash0000000000000000000000", "NORMAL");

        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), userId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), studentId, schoolId, "STUDENT", "ACTIVE");
    }

    @AfterEach void tearDown() {
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE school_id IN (?,?))", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM activities WHERE school_id IN (?,?)", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?,?)", userId, studentId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", userId, studentId);
        jdbc.update("DELETE FROM schools WHERE id IN (?,?)", schoolId, otherSchoolId);
    }

    // ── Authentication / Authorization ──

    @Test @DisplayName("unauthenticated returns 401")
    void unauthenticated401() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("STUDENT returns 403")
    @WithMockUser(username = "st", roles = {"STUDENT"})
    void student403() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("TEACHER returns 403")
    @WithMockUser(username = "te", roles = {"TEACHER"})
    void teacher403() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("SCHOOL_ADMIN returns 200 for own school")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void schoolAdmin200() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities"))
                .andExpect(status().isOk());
    }

    // ── Cross-school isolation ──

    @Test @DisplayName("cannot access activity of other school — returns 404")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void crossSchoolIsolation() throws Exception {
        UUID otherActivityId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                otherActivityId, otherSchoolId, "Other Act", "DRAFT", "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);

        mvc.perform(get("/api/v1/school-admin/activities/" + otherActivityId))
                .andExpect(status().isNotFound());
    }

    // ── List filtering ──

    @Test @DisplayName("list filters by executionStatus")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void listFiltersByExecutionStatus() throws Exception {
        seedActivity("Draft A", "DRAFT");
        seedActivity("Published B", "PUBLISHED");

        mvc.perform(get("/api/v1/school-admin/activities?executionStatus=DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Draft A"));
    }

    @Test @DisplayName("list filters by keyword")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void listFiltersByKeyword() throws Exception {
        seedActivity("Math Challenge", "DRAFT");
        seedActivity("Science Fair", "DRAFT");

        mvc.perform(get("/api/v1/school-admin/activities?keyword=math"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Math Challenge"));
    }

    @Test @DisplayName("list rejects invalid executionStatus with 400")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void listRejectsInvalidExecutionStatus() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?executionStatus=INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("list rejects keyword over 100 chars with 400")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void listRejectsTooLongKeyword() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?keyword=" + "A".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("list rejects negative page with 400")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void listRejectsNegativePage() throws Exception {
        mvc.perform(get("/api/v1/school-admin/activities?page=-1"))
                .andExpect(status().isBadRequest());
    }

    // ── Create ──

    @Test @DisplayName("create returns 201 with valid payload")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void createReturns201() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Activity\",\"description\":\"A test activity\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityId").isNotEmpty())
                .andExpect(jsonPath("$.executionStatus").value("DRAFT"));
    }

    @Test @DisplayName("create rejects endTime before startTime with 400")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void createRejectsInvalidTime() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bad Time\",\"startTime\":\"2026-09-02T00:00:00Z\",\"endTime\":\"2026-09-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Update ──

    @Test @DisplayName("update DRAFT returns 200")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void updateDraftReturns200() throws Exception {
        seedActivity("Original Title", "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("DRAFT"));
    }

    @Test @DisplayName("update non-DRAFT rejected")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void updateNonDraftRejected() throws Exception {
        seedActivity("Published Activity", "PUBLISHED");

        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Changed\"}"))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("update rejects invalid time with 400")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void updateRejectsInvalidTime() throws Exception {
        seedActivity("Time Test", "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2026-12-31T00:00:00Z\",\"endTime\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Publish ──

    @Test @DisplayName("publish DRAFT returns 200")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void publishDraftReturns200() throws Exception {
        seedActivity("Ready to Publish", "DRAFT");

        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/publish")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("PUBLISHED"));
    }

    // ── CSRF ──

    @Test @DisplayName("write without CSRF returns 403")
    @WithMockUser(username = "sa", roles = {"SCHOOL_ADMIN"})
    void writeWithoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    // ── helpers ──

    private void seedActivity(String title, String executionStatus) {
        activityId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                activityId, schoolId, title, executionStatus, "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);
    }
}
