package com.campusguinness.interfaces.web.auth;

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

import java.sql.Timestamp;
import java.time.Instant;
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
class StudentIdentityResubmissionIT {

    private static final String PASSWORD = "SecurePassword123!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String runPrefix = "phase12-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolId;
    private UUID adminId;
    private String adminUsername;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        adminUsername = username("admin");
        insertSchool(schoolId, "school");
        insertUser(adminId, adminUsername, "NORMAL", null, "AdminPass123!");
        insertMembership(UUID.randomUUID(), adminId, schoolId, "SCHOOL_ADMIN");
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
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM student_identity_applications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?) OR school_id IN (SELECT id FROM schools WHERE name LIKE ?)",
                runPrefix + "%", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
    }

    @Test
    void rejectedApplicantResubmitsNewPendingApplicationAndPreservesRejectedHistory() throws Exception {
        var student = rejectedStudent("history", "OLD-SN", "Old Name");
        var oldReview = reviewSnapshot(student.applicationId());
        var csrf = csrfToken();

        var result = mvc.perform(withCsrf(post("/api/v1/auth/student/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resubmitBody(student.username(), PASSWORD, "New Name", "NEW-SN")), csrf))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.userId").value(student.userId().toString()))
                .andExpect(jsonPath("$.username").value(student.username()))
                .andExpect(jsonPath("$.schoolId").value(schoolId.toString()))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        UUID newApplicationId = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString())
                .get("applicationId").asText());

        assertThat(applicationCount(student.userId())).isEqualTo(2);
        assertThat(pendingApplicationCount(student.userId())).isEqualTo(1);
        assertThat(applicationStatus(student.applicationId())).isEqualTo("REJECTED");
        assertThat(applicationText(student.applicationId(), "real_name")).isEqualTo("Old Name");
        assertThat(applicationText(student.applicationId(), "student_number")).isEqualTo("OLD-SN");
        assertThat(reviewSnapshot(student.applicationId())).isEqualTo(oldReview);
        assertThat(applicationStatus(newApplicationId)).isEqualTo("PENDING");
        assertThat(applicationText(newApplicationId, "real_name")).isEqualTo("New Name");
        assertThat(applicationText(newApplicationId, "student_number")).isEqualTo("NEW-SN");
        assertThat(applicationSchoolId(newApplicationId)).isEqualTo(schoolId);
        assertThat(userStatus(student.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(countMemberships(student.userId(), schoolId, "STUDENT")).isZero();
        assertThat(countStudentProfiles(student.userId())).isZero();
        assertThat(latestApplicationId(student.userId())).isEqualTo(newApplicationId);

        mvc.perform(get("/api/v1/auth/me").cookie(merge(csrf.cookies(), result.getResponse().getCookies())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void nonRejectedLatestApplicationsAreNotResubmittable() throws Exception {
        var pending = pendingStudent("pending");
        assertResubmitDenied(pending.username(), PASSWORD, "STUDENT_APPLICATION_NOT_RESUBMITTABLE");
        assertThat(applicationCount(pending.userId())).isEqualTo(1);

        var approved = approvedPendingUser("approved");
        assertResubmitDenied(approved.username(), PASSWORD, "STUDENT_APPLICATION_NOT_RESUBMITTABLE");
        assertThat(applicationCount(approved.userId())).isEqualTo(1);

        var normal = rejectedStudent("normal", "NORMAL-SN", "Normal Name");
        jdbc.update("UPDATE users SET account_status = 'NORMAL' WHERE id = ?", normal.userId());
        insertMembership(UUID.randomUUID(), normal.userId(), schoolId, "STUDENT");
        assertResubmitDenied(normal.username(), PASSWORD, "STUDENT_APPLICATION_NOT_RESUBMITTABLE");
        assertThat(applicationCount(normal.userId())).isEqualTo(1);

        var temporarilyLocked = rejectedStudent("locked", "LOCKED-SN", "Locked Name");
        jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(300)), temporarilyLocked.userId());
        assertResubmitStatus(temporarilyLocked.username(), PASSWORD, 401, "ACCOUNT_LOCKED");
        assertThat(applicationCount(temporarilyLocked.userId())).isEqualTo(1);
    }

    @Test
    void credentialFailuresAreGenericAndOtherUserSpoofingIsImpossible() throws Exception {
        var rejected = rejectedStudent("victim", "VICTIM-SN", "Victim Name");
        var other = pendingStudent("other");

        assertCredentialFailure(rejected.username(), "wrong-password");
        assertCredentialFailure(username("missing"), PASSWORD);

        assertResubmitDenied(other.username(), PASSWORD, "STUDENT_APPLICATION_NOT_RESUBMITTABLE");
        assertThat(applicationCount(rejected.userId())).isEqualTo(1);
        assertThat(applicationCount(other.userId())).isEqualTo(1);
    }

    @Test
    void resubmittedApplicationUsesExistingReviewFlowAndLatestLoginState() throws Exception {
        var student = rejectedStudent("review", "REVIEW-SN-1", "Review One");
        assertLoginDenied(student.username(), PASSWORD, 403, "STUDENT_APPLICATION_REJECTED");

        UUID second = resubmit(student.username(), "Review Two", "REVIEW-SN-2");
        assertLoginDenied(student.username(), PASSWORD, 403, "STUDENT_APPROVAL_PENDING");

        var adminSession = login(adminUsername, "AdminPass123!");
        mvc.perform(withCsrf(post(base() + "/" + second + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"still mismatched\"}"), adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("REJECTED"));
        assertThat(applicationStatus(student.applicationId())).isEqualTo("REJECTED");
        assertThat(applicationStatus(second)).isEqualTo("REJECTED");
        assertLoginDenied(student.username(), PASSWORD, 403, "STUDENT_APPLICATION_REJECTED");

        UUID third = resubmit(student.username(), "Review Three", "REVIEW-SN-3");
        mvc.perform(withCsrf(post(base() + "/" + third + "/approve"), adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.accountStatus").value("NORMAL"))
                .andExpect(jsonPath("$.membershipRole").value("STUDENT"));

        assertThat(applicationStatus(student.applicationId())).isEqualTo("REJECTED");
        assertThat(applicationStatus(second)).isEqualTo("REJECTED");
        assertThat(applicationStatus(third)).isEqualTo("APPROVED");
        assertThat(userStatus(student.userId())).isEqualTo("NORMAL");
        assertThat(countMemberships(student.userId(), schoolId, "STUDENT")).isEqualTo(1);
        assertThat(countStudentProfiles(student.userId())).isEqualTo(1);

        var studentSession = login(student.username(), PASSWORD);
        mvc.perform(get("/api/v1/auth/me").cookie(studentSession.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_STUDENT"));
    }

    @Test
    void concurrentResubmissionCreatesOnlyOnePendingApplication() throws Exception {
        var student = rejectedStudent("concurrent", "CONCURRENT-SN", "Concurrent One");
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
                    int status = mvc.perform(withCsrf(post("/api/v1/auth/student/resubmit")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(resubmitBody(student.username(), PASSWORD, "Concurrent Two", "CONCURRENT-SN-2")), csrf))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 201) successes.incrementAndGet();
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

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();
        assertThat(applicationCount(student.userId())).isEqualTo(2);
        assertThat(pendingApplicationCount(student.userId())).isEqualTo(1);
    }

    private void assertCredentialFailure(String username, String password) throws Exception {
        mvc.perform(withCsrf(post("/api/v1/auth/student/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resubmitBody(username, password, "No Leak", "NO-LEAK")), csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private void assertResubmitDenied(String username, String password, String code) throws Exception {
        assertResubmitStatus(username, password, 409, code);
    }

    private void assertResubmitStatus(String username, String password, int status, String code) throws Exception {
        mvc.perform(withCsrf(post("/api/v1/auth/student/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resubmitBody(username, password, "Denied Name", "DENIED-SN")), csrfToken()))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private UUID resubmit(String username, String realName, String studentNumber) throws Exception {
        var result = mvc.perform(withCsrf(post("/api/v1/auth/student/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resubmitBody(username, PASSWORD, realName, studentNumber)), csrfToken()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("applicationId").asText());
    }

    private RegisteredStudent rejectedStudent(String label, String studentNumber, String realName) {
        UUID userId = UUID.randomUUID();
        String username = username(label);
        insertUser(userId, username, "PENDING_ACTIVATION", null, PASSWORD);
        UUID applicationId = insertApplication(userId, "REJECTED", studentNumber, realName);
        return new RegisteredStudent(userId, applicationId, username);
    }

    private RegisteredStudent pendingStudent(String label) {
        UUID userId = UUID.randomUUID();
        String username = username(label);
        insertUser(userId, username, "PENDING_ACTIVATION", null, PASSWORD);
        UUID applicationId = insertApplication(userId, "PENDING", label + "-SN", "Pending Name");
        return new RegisteredStudent(userId, applicationId, username);
    }

    private RegisteredStudent approvedPendingUser(String label) {
        UUID userId = UUID.randomUUID();
        String username = username(label);
        insertUser(userId, username, "PENDING_ACTIVATION", null, PASSWORD);
        UUID applicationId = insertApplication(userId, "APPROVED", label + "-SN", "Approved Name");
        return new RegisteredStudent(userId, applicationId, username);
    }

    private UUID insertApplication(UUID userId, String status, String studentNumber, String realName) {
        UUID applicationId = UUID.randomUUID();
        UUID reviewer = "PENDING".equals(status) ? null : adminId;
        jdbc.update("""
                INSERT INTO student_identity_applications(
                    id, user_id, school_id, real_name, student_number, grade, class_name,
                    application_status, reviewed_by, reviewed_at, rejection_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, applicationId, userId, schoolId, realName, studentNumber, "Grade 10", "Class 1",
                status, reviewer, reviewer == null ? null : Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                "REJECTED".equals(status) ? "student number mismatch" : null);
        return applicationId;
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
        mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"), csrfToken()))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
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

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, AuthSession session)
            throws Exception {
        var csrf = csrfToken(session);
        return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
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

    private String resubmitBody(String username, String password, String realName, String studentNumber) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "realName": " %s ",
                  "studentNumber": " %s ",
                  "grade": " Grade 11 ",
                  "className": " Class 2 ",
                  "proofFileKeys": []
                }
                """.formatted(username, password, realName, studentNumber);
    }

    private void insertSchool(UUID id, String label) {
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-" + label, "USCC", runPrefix + "-code-" + label,
                runPrefix + "-internal-" + label, "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", "phase12@example.com", "NORMAL");
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

    private String base() {
        return "/api/v1/schools/" + schoolId + "/student-identity-applications";
    }

    private int applicationCount(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM student_identity_applications WHERE user_id = ?",
                Integer.class, userId);
    }

    private int pendingApplicationCount(UUID userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM student_identity_applications
                WHERE user_id = ? AND application_status = 'PENDING'
                """, Integer.class, userId);
    }

    private UUID latestApplicationId(UUID userId) {
        return jdbc.queryForObject("""
                SELECT id FROM student_identity_applications
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, UUID.class, userId);
    }

    private String applicationStatus(UUID applicationId) {
        return jdbc.queryForObject("SELECT application_status FROM student_identity_applications WHERE id = ?",
                String.class, applicationId);
    }

    private UUID applicationSchoolId(UUID applicationId) {
        return jdbc.queryForObject("SELECT school_id FROM student_identity_applications WHERE id = ?",
                UUID.class, applicationId);
    }

    private String applicationText(UUID applicationId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM student_identity_applications WHERE id = ?",
                String.class, applicationId);
    }

    private ReviewSnapshot reviewSnapshot(UUID applicationId) {
        return jdbc.queryForObject("""
                SELECT reviewed_by, reviewed_at, rejection_reason
                FROM student_identity_applications
                WHERE id = ?
                """, (rs, rowNum) -> new ReviewSnapshot(
                (UUID) rs.getObject("reviewed_by"),
                rs.getTimestamp("reviewed_at").toInstant(),
                rs.getString("rejection_reason")), applicationId);
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

    private record RegisteredStudent(UUID userId, UUID applicationId, String username) {}
    private record AuthSession(Cookie[] cookies) {}
    private record CsrfMaterial(String headerName, String token, Cookie[] cookies) {}
    private record ReviewSnapshot(UUID reviewedBy, Instant reviewedAt, String rejectionReason) {}
}
