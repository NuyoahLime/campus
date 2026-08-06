package com.campusguinness.interfaces.web.studentidentityapplication;

import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.port.StudentProfileCommandPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class StudentIdentityApplicationReviewRollbackIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean StudentProfileCommandPort profiles;
    @MockitoBean AuditRecordCommandPort audit;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String runPrefix = "phase6rb-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolA;
    private UUID adminA;
    private String adminAUsername;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        adminA = UUID.randomUUID();
        adminAUsername = username("admin-a");
        insertSchool(schoolA, "A");
        insertUser(adminA, adminAUsername, "NORMAL", null, "AdminPass123!");
        insertMembership(UUID.randomUUID(), adminA, schoolA, "SCHOOL_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM student_identity_applications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?) OR school_id IN (SELECT id FROM schools WHERE name LIKE ?)",
                runPrefix + "%", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
    }

    @Test
    void approvalRollsBackWhenProfileCreationFails() throws Exception {
        var student = registerStudent(schoolA, "profile-fail");
        var adminSession = login(adminAUsername, "AdminPass123!");
        org.mockito.Mockito.doThrow(new RuntimeException("profile failed")).when(profiles).create(org.mockito.ArgumentMatchers.any());

        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), adminSession))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(applicationStatus(student.applicationId())).isEqualTo("PENDING");
        assertThat(applicationReviewer(student.applicationId())).isNull();
        assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isZero();
        assertThat(countStudentProfiles(student.userId())).isZero();
        assertThat(countAudit("STUDENT_APPLICATION_APPROVED", student.applicationId())).isZero();
    }

    @Test
    void approvalRollsBackWhenAuditWriteFails() throws Exception {
        var student = registerStudent(schoolA, "audit-fail");
        var adminSession = login(adminAUsername, "AdminPass123!");
        org.mockito.Mockito.doThrow(new RuntimeException("audit failed")).when(audit).record(org.mockito.ArgumentMatchers.any());

        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), adminSession))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(applicationStatus(student.applicationId())).isEqualTo("PENDING");
        assertThat(applicationReviewer(student.applicationId())).isNull();
        assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isZero();
        assertThat(countStudentProfiles(student.userId())).isZero();
        assertThat(countAudit("STUDENT_APPLICATION_APPROVED", student.applicationId())).isZero();
    }

    @Test
    void approvalIsRejectedWhenActiveMembershipAlreadyExists() throws Exception {
        var student = registerStudent(schoolA, "membership-conflict");
        insertMembership(UUID.randomUUID(), student.userId(), schoolA, "STUDENT");
        var adminSession = login(adminAUsername, "AdminPass123!");

        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), adminSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_MEMBERSHIP_CONFLICT"));

        assertThat(applicationStatus(student.applicationId())).isEqualTo("PENDING");
        assertThat(applicationReviewer(student.applicationId())).isNull();
        assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isEqualTo(1);
        assertThat(countStudentProfiles(student.userId())).isZero();
        assertThat(countAudit("STUDENT_APPLICATION_APPROVED", student.applicationId())).isZero();
    }

    private RegisteredStudent registerStudent(UUID schoolId, String label) throws Exception {
        String username = username(label);
        var csrf = csrfToken();
        var result = mvc.perform(withCsrf(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(username, schoolId)), csrf))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new RegisteredStudent(
                UUID.fromString(body.get("userId").asText()),
                UUID.fromString(body.get("applicationId").asText()),
                username);
    }

    private AuthSession login(String username, String password) throws Exception {
        var csrf = csrfToken();
        var result = mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"), csrf))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthSession(merge(csrf.cookies(), result.getResponse().getCookies()));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, AuthSession session)
            throws Exception {
        return withCsrf(request, csrfToken(session));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfMaterial csrf) {
        return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
    }

    private CsrfMaterial csrfToken() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                result.getResponse().getCookies());
    }

    private CsrfMaterial csrfToken(AuthSession session) throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf").cookie(session.cookies()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                merge(session.cookies(), result.getResponse().getCookies()));
    }

    private Cookie[] merge(Cookie[] left, Cookie[] right) {
        return List.of(left, right).stream().flatMap(Arrays::stream).toArray(Cookie[]::new);
    }

    private String registrationBody(String username, UUID schoolId) {
        return """
                {
                  "username": "%s",
                  "password": "SecurePassword123!",
                  "confirmPassword": "SecurePassword123!",
                  "realName": "Student %s",
                  "schoolId": "%s",
                  "studentNumber": "SN-%s",
                  "grade": "Grade 10",
                  "className": "Class 1",
                  "proofFileKeys": []
                }
                """.formatted(username, username, schoolId, username.substring(username.length() - 8));
    }

    private String base(UUID schoolId) {
        return "/api/v1/schools/" + schoolId + "/student-identity-applications";
    }

    private void insertSchool(UUID id, String label) {
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-school-" + label, "USCC", runPrefix + "-code-" + label,
                runPrefix + "-internal-" + label, "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", "phase6rb@example.com", "NORMAL");
    }

    private void insertUser(UUID id, String username, String status, String platformRole, String rawPassword) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, username, encoder.encode(rawPassword), status, platformRole);
    }

    private void insertMembership(UUID id, UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, id, userId, schoolId, role);
    }

    private String username(String label) {
        return runPrefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String applicationStatus(UUID applicationId) {
        return jdbc.queryForObject("SELECT application_status FROM student_identity_applications WHERE id = ?",
                String.class, applicationId);
    }

    private UUID applicationReviewer(UUID applicationId) {
        return jdbc.queryForObject("SELECT reviewed_by FROM student_identity_applications WHERE id = ?",
                UUID.class, applicationId);
    }

    private String userStatus(UUID userId) {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, userId);
    }

    private int countMemberships(UUID userId, UUID schoolId, String role) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM school_memberships
                WHERE user_id = ? AND school_id = ? AND role_in_school = ? AND status = 'ACTIVE'
                """, Integer.class, userId, schoolId, role);
    }

    private int countStudentProfiles(UUID userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM student_profiles sp
                JOIN school_memberships sm ON sm.id = sp.membership_id
                WHERE sm.user_id = ?
                """, Integer.class, userId);
    }

    private int countAudit(String action, UUID applicationId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_records
                WHERE action = ? AND target_id = ?
                """, Integer.class, action, applicationId);
    }

    private record RegisteredStudent(UUID userId, UUID applicationId, String username) {}
    private record AuthSession(Cookie[] cookies) {}
    private record CsrfMaterial(String headerName, String token, Cookie[] cookies) {}
}
