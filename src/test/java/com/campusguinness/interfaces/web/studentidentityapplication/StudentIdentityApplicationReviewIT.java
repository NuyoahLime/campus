package com.campusguinness.interfaces.web.studentidentityapplication;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
class StudentIdentityApplicationReviewIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String runPrefix = "phase6-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminA2;
    private UUID ordinaryUser;
    private String adminAUsername;
    private String adminA2Username;
    private String ordinaryUsername;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        schoolB = UUID.randomUUID();
        adminA = UUID.randomUUID();
        adminA2 = UUID.randomUUID();
        ordinaryUser = UUID.randomUUID();
        adminAUsername = username("admin-a");
        adminA2Username = username("admin-a2");
        ordinaryUsername = username("ordinary");
        insertSchool(schoolA, "A");
        insertSchool(schoolB, "B");
        insertUser(adminA, adminAUsername, "NORMAL", null, "AdminPass123!");
        insertUser(adminA2, adminA2Username, "NORMAL", null, "AdminPass123!");
        insertUser(ordinaryUser, ordinaryUsername, "NORMAL", null, "AdminPass123!");
        insertMembership(UUID.randomUUID(), adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(UUID.randomUUID(), adminA2, schoolA, "SCHOOL_ADMIN");
        insertMembership(UUID.randomUUID(), ordinaryUser, schoolA, "STUDENT");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM audit_records WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?) OR actor_id IN (SELECT id FROM users WHERE username LIKE ?)",
                runPrefix + "%", runPrefix + "%");
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
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM student_identity_applications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?) OR school_id IN (SELECT id FROM schools WHERE name LIKE ?)",
                runPrefix + "%", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
    }

    @Test
    void schoolAdminListsAndViewsOnlyOwnSchoolApplications() throws Exception {
        var studentA = registerStudent(schoolA, "list-a");
        var studentB = registerStudent(schoolB, "list-b");
        var adminSession = login(adminAUsername, "AdminPass123!");

        mvc.perform(get(base(schoolA)).cookie(adminSession.cookies()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].applicationId").value(studentA.applicationId().toString()))
                .andExpect(jsonPath("$.items[0].username").value(studentA.username()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());

        mvc.perform(get(base(schoolA) + "/" + studentA.applicationId()).cookie(adminSession.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(studentA.applicationId().toString()))
                .andExpect(jsonPath("$.proofFileCount").value(0))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get(base(schoolB)).cookie(adminSession.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));

        mvc.perform(get(base(schoolA) + "/" + studentB.applicationId()).cookie(adminSession.cookies()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_APPLICATION_NOT_FOUND"));
    }

    @Test
    void approveCreatesNormalUserStudentMembershipProfileAndAudit() throws Exception {
        var student = registerStudent(schoolA, "approve");
        assertLoginDenied(student.username(), "SecurePassword123!", 403, "STUDENT_APPROVAL_PENDING");
        var adminSession = login(adminAUsername, "AdminPass123!");

        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.accountStatus").value("NORMAL"))
                .andExpect(jsonPath("$.membershipRole").value("STUDENT"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));

        assertThat(applicationStatus(student.applicationId())).isEqualTo("APPROVED");
        assertThat(applicationReviewer(student.applicationId())).isEqualTo(adminA);
        assertThat(applicationReviewedAtPresent(student.applicationId())).isTrue();
        assertThat(applicationReason(student.applicationId())).isNull();
        assertThat(userStatus(student.userId())).isEqualTo("NORMAL");
        assertThat(platformRole(student.userId())).isNull();
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isEqualTo(1);
        assertThat(countMemberships(student.userId(), schoolA, "SCHOOL_ADMIN")).isZero();
        assertThat(countMemberships(student.userId(), schoolA, "TEACHER")).isZero();
        assertThat(countStudentProfiles(student.userId())).isEqualTo(1);
        assertThat(countAudit("STUDENT_APPLICATION_APPROVED", student.applicationId())).isEqualTo(1);

        login(student.username(), "SecurePassword123!");
        var studentSession = login(student.username(), "SecurePassword123!");
        mvc.perform(withCsrf(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked\",\"initialPassword\":\"Password123!\"}"), studentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectKeepsUserPendingAndCreatesNoMembershipOrProfile() throws Exception {
        var student = registerStudent(schoolA, "reject");
        var adminSession = login(adminAUsername, "AdminPass123!");

        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" student number mismatch \"}"), adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.reason").value("student number mismatch"));

        assertThat(applicationStatus(student.applicationId())).isEqualTo("REJECTED");
        assertThat(applicationReviewer(student.applicationId())).isEqualTo(adminA);
        assertThat(applicationReason(student.applicationId())).isEqualTo("student number mismatch");
        assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isZero();
        assertThat(countStudentProfiles(student.userId())).isZero();
        assertThat(countAudit("STUDENT_APPLICATION_REJECTED", student.applicationId())).isEqualTo(1);
        assertLoginDenied(student.username(), "SecurePassword123!", 403, "STUDENT_APPLICATION_REJECTED");
    }

    @Test
    void authenticationAuthorizationAndCsrfBoundariesHold() throws Exception {
        var student = registerStudent(schoolA, "authz");
        var anonymousCsrf = csrfToken();
        mvc.perform(get(base(schoolA))).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), anonymousCsrf))
                .andExpect(status().isUnauthorized());

        var ordinarySession = login(ordinaryUsername, "AdminPass123!");
        mvc.perform(get(base(schoolA)).cookie(ordinarySession.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), ordinarySession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"), ordinarySession))
                .andExpect(status().isForbidden());

        var adminSession = login(adminAUsername, "AdminPass123!");
        mvc.perform(post(base(schoolA) + "/" + student.applicationId() + "/approve").cookie(adminSession.cookies()))
                .andExpect(status().isForbidden());

        assertThat(applicationStatus(student.applicationId())).isEqualTo("PENDING");
        assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isZero();
        assertThat(countStudentProfiles(student.userId())).isZero();
    }

    @Test
    void concurrentApproveAllowsOnlyOneSuccess() throws Exception {
        var student = registerStudent(schoolA, "concurrent-approve");
        var session1 = login(adminAUsername, "AdminPass123!");
        var session2 = login(adminA2Username, "AdminPass123!");

        var result = runConcurrently(
                () -> mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), session1)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), session2)).andReturn().getResponse().getStatus()
        );

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.conflicts()).isEqualTo(1);
        assertThat(applicationStatus(student.applicationId())).isEqualTo("APPROVED");
        assertThat(userStatus(student.userId())).isEqualTo("NORMAL");
        assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isEqualTo(1);
        assertThat(countStudentProfiles(student.userId())).isEqualTo(1);
    }

    @Test
    void approveRejectRaceAllowsOnlyOneTerminalOutcome() throws Exception {
        var student = registerStudent(schoolA, "race");
        var session1 = login(adminAUsername, "AdminPass123!");
        var session2 = login(adminA2Username, "AdminPass123!");

        var result = runConcurrently(
                () -> mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/approve"), session1)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(withCsrf(post(base(schoolA) + "/" + student.applicationId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"race rejected\"}"), session2)).andReturn().getResponse().getStatus()
        );

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.conflicts()).isEqualTo(1);
        String status = applicationStatus(student.applicationId());
        assertThat(status).isIn("APPROVED", "REJECTED");
        if ("APPROVED".equals(status)) {
            assertThat(userStatus(student.userId())).isEqualTo("NORMAL");
            assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isEqualTo(1);
            assertThat(countStudentProfiles(student.userId())).isEqualTo(1);
        } else {
            assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
            assertThat(countMemberships(student.userId(), schoolA, "STUDENT")).isZero();
            assertThat(countStudentProfiles(student.userId())).isZero();
        }
    }

    private ConcurrentResult runConcurrently(CheckedStatusCall first, CheckedStatusCall second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var unexpected = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        for (CheckedStatusCall call : List.of(first, second)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    int status = call.execute();
                    if (status == 200) successes.incrementAndGet();
                    else if (status == 409) conflicts.incrementAndGet();
                    else unexpected.incrementAndGet();
                } catch (Exception ex) {
                    unexpected.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpected.get()).isZero();
        return new ConcurrentResult(successes.get(), conflicts.get());
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
                username
        );
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

    private void assertLoginDenied(String username, String password, int status, String code) throws Exception {
        var csrf = csrfToken();
        mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"), csrf))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
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
        return List.of(left, right).stream()
                .flatMap(Arrays::stream)
                .toArray(Cookie[]::new);
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
                "13800000000", "phase6@example.com", "NORMAL");
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

    private boolean applicationReviewedAtPresent(UUID applicationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT reviewed_at IS NOT NULL FROM student_identity_applications WHERE id = ?",
                Boolean.class, applicationId));
    }

    private String applicationReason(UUID applicationId) {
        return jdbc.queryForObject("SELECT rejection_reason FROM student_identity_applications WHERE id = ?",
                String.class, applicationId);
    }

    private String userStatus(UUID userId) {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, userId);
    }

    private String platformRole(UUID userId) {
        return jdbc.queryForObject("SELECT platform_role FROM users WHERE id = ?", String.class, userId);
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
    private record ConcurrentResult(int successes, int conflicts) {}

    @FunctionalInterface
    private interface CheckedStatusCall {
        int execute() throws Exception;
    }
}
