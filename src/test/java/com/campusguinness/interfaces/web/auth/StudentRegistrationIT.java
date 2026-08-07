package com.campusguinness.interfaces.web.auth;

import com.campusguinness.school.application.query.model.StudentRegistrationSchool;
import com.campusguinness.school.application.query.port.StudentRegistrationSchoolQueryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class StudentRegistrationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired FakeStudentRegistrationSchoolQuery schools;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String runPrefix = "phase5-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        insertSchool(schoolId, "NORMAL");
        schools.reset();
        schools.put(new StudentRegistrationSchool(schoolId, true, true));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE FROM student_profiles
                WHERE membership_id IN (
                    SELECT m.id FROM school_memberships m
                    JOIN users u ON u.id = m.user_id
                    WHERE u.username LIKE ?
                )
                """, runPrefix + "%");
        jdbc.update("""
                DELETE FROM teacher_profiles
                WHERE membership_id IN (
                    SELECT m.id FROM school_memberships m
                    JOIN users u ON u.id = m.user_id
                    WHERE u.username LIKE ?
                )
                """, runPrefix + "%");
        jdbc.update("""
                DELETE FROM school_memberships
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                """, runPrefix + "%");
        jdbc.update("""
                DELETE FROM student_identity_applications
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                   OR school_id IN (SELECT id FROM schools WHERE name LIKE ?)
                """, runPrefix + "%", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
    }

    @Test
    void anonymousRegistrationWithCsrfCreatesPendingUserAndPendingApplicationOnly() throws Exception {
        String username = username("success");
        var csrf = csrfToken();

        var result = mvc.perform(withCsrf(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(username, schoolId)), csrf))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.schoolId").value(schoolId.toString()))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.confirmPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        UUID userId = UUID.fromString(body.get("userId").asText());
        UUID applicationId = UUID.fromString(body.get("applicationId").asText());

        assertThat(userStatus(userId)).isEqualTo("PENDING_ACTIVATION");
        assertThat(platformRole(userId)).isNull();
        assertThat(passwordHash(userId)).isNotEqualTo("SecurePassword123!");
        assertThat(encoder.matches("SecurePassword123!", passwordHash(userId))).isTrue();
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
        assertThat(applicationUserId(applicationId)).isEqualTo(userId);
        assertThat(applicationSchoolId(applicationId)).isEqualTo(schoolId);
        assertThat(applicationText(applicationId, "real_name")).isEqualTo("Zhang San");
        assertThat(applicationText(applicationId, "student_number")).isEqualTo("20260001");
        assertThat(applicationText(applicationId, "grade")).isEqualTo("Grade 10");
        assertThat(applicationText(applicationId, "class_name")).isEqualTo("Class 1");
        assertThat(applicationText(applicationId, "evidence_file_key")).isNull();
        assertThat(reviewFieldsEmpty(applicationId)).isTrue();
        assertThat(countMemberships(userId)).isZero();
        assertThat(countStudentProfiles(userId)).isZero();
        assertThat(countTeacherProfiles(userId)).isZero();

        mvc.perform(get("/api/v1/auth/me").cookie(csrf.cookies()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void applicationForeignKeyFailureRollsBackCreatedUser() throws Exception {
        UUID missingSchoolId = UUID.randomUUID();
        String username = username("rollback");
        schools.put(new StudentRegistrationSchool(missingSchoolId, true, true));

        mvc.perform(withCsrf(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(username, missingSchoolId)), csrfToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(countUsersByUsername(username)).isZero();
        assertThat(countApplicationsByUsername(username)).isZero();
    }

    @Test
    void concurrentSameUsernameLeavesOneUserAndOneApplication() throws Exception {
        String username = username("concurrent");
        var csrf1 = csrfToken();
        var csrf2 = csrfToken();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var unexpected = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);

        for (var csrf : List.of(csrf1, csrf2)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    int status = mvc.perform(withCsrf(post("/api/v1/auth/student/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registrationBody(username, schoolId)), csrf))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 201) {
                        successes.incrementAndGet();
                    } else if (status == 409) {
                        conflicts.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception ex) {
                    unexpected.incrementAndGet();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();
        assertThat(countUsersByUsername(username)).isEqualTo(1);
        assertThat(countApplicationsByUsername(username)).isEqualTo(1);
        assertThat(countMembershipsByUsername(username)).isZero();
    }

    @Test
    void csrfIsRequiredAndFailuresDoNotCreateData() throws Exception {
        String missingCsrfUsername = username("missing-csrf");
        mvc.perform(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(missingCsrfUsername, schoolId)))
                .andExpect(status().isForbidden());

        String wrongCsrfUsername = username("wrong-csrf");
        var csrf = csrfToken();
        mvc.perform(post("/api/v1/auth/student/register")
                        .header(csrf.headerName(), "wrong-token")
                        .cookie(csrf.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(wrongCsrfUsername, schoolId)))
                .andExpect(status().isForbidden());

        assertThat(countUsersByUsername(missingCsrfUsername)).isZero();
        assertThat(countUsersByUsername(wrongCsrfUsername)).isZero();
        assertThat(countApplicationsByUsername(missingCsrfUsername)).isZero();
        assertThat(countApplicationsByUsername(wrongCsrfUsername)).isZero();
        assertThat(countMembershipsByUsername(missingCsrfUsername)).isZero();
        assertThat(countMembershipsByUsername(wrongCsrfUsername)).isZero();
    }

    @Test
    void pendingActivationStudentCannotLoginWithCorrectPassword() throws Exception {
        String username = username("pending-login");
        var csrf = csrfToken();
        mvc.perform(withCsrf(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(username, schoolId)), csrf))
                .andExpect(status().isCreated());

        mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"SecurePassword123!\"}"), csrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_APPROVAL_PENDING"));

        mvc.perform(get("/api/v1/auth/me").cookie(csrf.cookies()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void existingSuperAdminLoginAndLogoutCsrfRemainUnchanged() throws Exception {
        UUID superAdminId = UUID.randomUUID();
        String username = username("super-admin");
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                superAdminId, username, encoder.encode("AdminPass123!"), "NORMAL", "SUPER_ADMIN");

        var csrf = csrfToken();
        mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"AdminPass123!\"}"), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(superAdminId.toString()))
                .andExpect(jsonPath("$.accountStatus").value("NORMAL"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_SUPER_ADMIN"))
                .andExpect(jsonPath("$.schoolMemberships").isEmpty());

        mvc.perform(post("/api/v1/auth/logout").cookie(csrf.cookies()))
                .andExpect(status().isForbidden());
    }

    private CsrfMaterial csrfToken() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                result.getResponse().getCookies());
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfMaterial csrf) {
        return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
    }

    private String registrationBody(String username, UUID schoolId) {
        return """
                {
                  "username": "%s",
                  "password": "SecurePassword123!",
                  "confirmPassword": "SecurePassword123!",
                  "realName": " Zhang San ",
                  "schoolId": "%s",
                  "studentNumber": " 20260001 ",
                  "grade": " Grade 10 ",
                  "className": " Class 1 ",
                  "proofFileKeys": []
                }
                """.formatted(username, schoolId);
    }

    private void insertSchool(UUID id, String status) {
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-school-" + id.toString().substring(0, 8),
                "USCC", runPrefix + "-code-" + id.toString().substring(0, 8),
                runPrefix + "-int-" + id.toString().substring(0, 6),
                "PRIMARY", "Beijing", "Address", "Contact", "13800000000",
                "phase5@example.com", status);
    }

    private String username(String label) {
        return runPrefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String userStatus(UUID userId) {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, userId);
    }

    private String platformRole(UUID userId) {
        return jdbc.queryForObject("SELECT platform_role FROM users WHERE id = ?", String.class, userId);
    }

    private String passwordHash(UUID userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    private String applicationStatus(UUID applicationId) {
        return jdbc.queryForObject(
                "SELECT application_status FROM student_identity_applications WHERE id = ?",
                String.class, applicationId);
    }

    private UUID applicationUserId(UUID applicationId) {
        return jdbc.queryForObject(
                "SELECT user_id FROM student_identity_applications WHERE id = ?",
                UUID.class, applicationId);
    }

    private UUID applicationSchoolId(UUID applicationId) {
        return jdbc.queryForObject(
                "SELECT school_id FROM student_identity_applications WHERE id = ?",
                UUID.class, applicationId);
    }

    private String applicationText(UUID applicationId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM student_identity_applications WHERE id = ?",
                String.class, applicationId);
    }

    private boolean reviewFieldsEmpty(UUID applicationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT reviewed_by IS NULL
                   AND reviewed_at IS NULL
                   AND rejection_reason IS NULL
                FROM student_identity_applications
                WHERE id = ?
                """, Boolean.class, applicationId));
    }

    private int countMemberships(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM school_memberships WHERE user_id = ?",
                Integer.class, userId);
    }

    private int countStudentProfiles(UUID userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM student_profiles sp
                JOIN school_memberships sm ON sm.id = sp.membership_id
                WHERE sm.user_id = ?
                """, Integer.class, userId);
    }

    private int countTeacherProfiles(UUID userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM teacher_profiles tp
                JOIN school_memberships sm ON sm.id = tp.membership_id
                WHERE sm.user_id = ?
                """, Integer.class, userId);
    }

    private int countUsersByUsername(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class, username);
    }

    private int countApplicationsByUsername(String username) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM student_identity_applications a
                JOIN users u ON u.id = a.user_id
                WHERE u.username = ?
                """, Integer.class, username);
    }

    private int countMembershipsByUsername(String username) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM school_memberships m
                JOIN users u ON u.id = m.user_id
                WHERE u.username = ?
                """, Integer.class, username);
    }

    private record CsrfMaterial(String headerName, String token, Cookie[] cookies) {}

    @TestConfiguration
    static class StudentRegistrationITConfig {
        @Bean
        @Primary
        FakeStudentRegistrationSchoolQuery studentRegistrationSchoolQueryPort() {
            return new FakeStudentRegistrationSchoolQuery();
        }
    }

    static class FakeStudentRegistrationSchoolQuery implements StudentRegistrationSchoolQueryPort {
        private final ConcurrentHashMap<UUID, StudentRegistrationSchool> schools = new ConcurrentHashMap<>();

        void reset() {
            schools.clear();
        }

        void put(StudentRegistrationSchool school) {
            schools.put(school.schoolId(), school);
        }

        @Override
        public StudentRegistrationSchool findForStudentRegistration(UUID schoolId) {
            return schools.getOrDefault(schoolId, new StudentRegistrationSchool(schoolId, false, false));
        }
    }
}
