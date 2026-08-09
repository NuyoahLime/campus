package com.campusguinness.interfaces.web.security;

import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class AuthorizationEndpointMatrixIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String runPrefix = "phase11-matrix-" + UUID.randomUUID();
    private UUID schoolId;
    private UUID studentUserId;
    private UUID schoolAdminUserId;
    private UUID superAdminUserId;

    @BeforeEach
    void setUp() {
        schoolId = insertSchool();
        studentUserId = insertUser("student", null);
        schoolAdminUserId = insertUser("school-admin", null);
        superAdminUserId = insertUser("super-admin", "SUPER_ADMIN");
        insertMembership(studentUserId, schoolId, "STUDENT");
        insertMembership(schoolAdminUserId, schoolId, "SCHOOL_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM feedbacks WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM media_review_records WHERE media_id IN (SELECT id FROM media WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM media WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM activity_results WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM score_review_records WHERE score_attempt_id IN (SELECT id FROM score_attempts WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM score_correction_records WHERE original_score_id IN (SELECT id FROM score_attempts WHERE school_id = ?) OR new_score_id IN (SELECT id FROM score_attempts WHERE school_id = ?)", schoolId, schoolId);
        jdbc.update("DELETE FROM abnormal_score_entries WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM score_attempts WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM score_appeals WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM l3_authorizations WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM ranking_definitions WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM responsible_teachers WHERE activity_project_id IN (SELECT id FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE school_id = ?))", schoolId);
        jdbc.update("DELETE FROM activity_participants WHERE activity_id IN (SELECT id FROM activities WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM activity_applications WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM activities WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM school_registrations WHERE school_name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM challenge_projects WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void endpointMatrixRows() throws Exception {
        var rows = matrix();
        assertThat(rows).hasSize(58);
        for (Row row : rows) {
            assertRow(row);
        }
    }

    private void assertRow(Row row) throws Exception {
        assertAnonymous(row);
        assertRole(row, Role.STUDENT);
        assertRole(row, Role.SCHOOL_ADMIN);
        assertRole(row, Role.SUPER_ADMIN);
    }

    private void assertAnonymous(Row row) throws Exception {
        int status = mvc.perform(builder(row)).andReturn().getResponse().getStatus();
        if (row.number() == 6) {
            assertThat(status).as(row + " anonymous").isEqualTo(204);
            return;
        }
        if (row.allowed().allowsAnonymous()) {
            assertThat(status).as(row + " anonymous").isNotIn(401, 403);
        } else {
            assertThat(status).as(row + " anonymous").isEqualTo(401);
        }
    }

    private void assertRole(Row row, Role role) throws Exception {
        int status = mvc.perform(builder(row).with(principal(role))).andReturn().getResponse().getStatus();
        if (row.allowed().allows(role)) {
            assertThat(status).as(row + " " + role).isNotIn(401, 403);
        } else {
            assertThat(status).as(row + " " + role).isEqualTo(403);
        }
    }

    private MockHttpServletRequestBuilder builder(Row row) {
        var builder = request(HttpMethod.valueOf(row.method()), row.path());
        if (row.requiresCsrf()) {
            builder.with(csrf());
        }
        if (row.body() != null) {
            builder.contentType("application/json").content(row.body());
        }
        if (row.method().equals("OPTIONS")) {
            builder.header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET");
        }
        return builder;
    }

    private RequestPostProcessor principal(Role role) {
        var userId = switch (role) {
            case STUDENT -> studentUserId;
            case SCHOOL_ADMIN -> schoolAdminUserId;
            case SUPER_ADMIN -> superAdminUserId;
        };
        var memberships = role == Role.SUPER_ADMIN
                ? List.<AuthenticatedSchoolMembership>of()
                : List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, role.name()));
        var details = new CampusGuinnessUserDetails(
                userId,
                "phase11-" + role.name().toLowerCase(),
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role.name())),
                memberships);
        return user(details);
    }

    private List<Row> matrix() {
        String id = UUID.randomUUID().toString();
        String school = schoolId.toString();
        String body = "{}";
        String reason = "{\"reason\":\"phase11\"}";
        String feedbackSubmit = """
                {"schoolId":"%s","feedbackType":"GENERAL","content":"phase11"}
                """.formatted(school);
        String scoreAppealSubmit = """
                {"schoolId":"%s","scoreAttemptId":"%s","appealType":"SCORE","appealReason":"phase11"}
                """.formatted(school, UUID.randomUUID());
        String handler = "{\"handlerId\":\"" + UUID.randomUUID() + "\"}";
        String activityCreate = """
                {"schoolId":"%s","title":"phase11"}
                """.formatted(school);
        String activityApprove = "{\"activityId\":\"" + UUID.randomUUID() + "\"}";
        String challengeCreate = """
                {
                  "name": "phase11-%s",
                  "category": "GENERAL",
                  "scoreStorageType": "INTEGER",
                  "scoreIndicatorType": "NUMERIC",
                  "comparisonDirection": "HIGHER_BETTER",
                  "effectiveScoreRule": "BEST",
                  "allowTie": true
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8));
        String schoolRegistrationApprove = """
                {"comment":"phase11","schoolId":"%s"}
                """.formatted(school);
        String rankingCreate = """
                {"layer":"L1","name":"phase11","schoolId":"%s","projectId":"%s"}
                """.formatted(school, UUID.randomUUID());
        String l3Create = """
                {"schoolId":"%s","projectId":"%s","ruleVersionId":"%s"}
                """.formatted(school, UUID.randomUUID(), UUID.randomUUID());
        return List.of(
                row(1, "GET", "/actuator/health", Allowed.PUBLIC),
                row(2, "GET", "/actuator/info", Allowed.PUBLIC),
                row(3, "OPTIONS", "/api/v1/schools", Allowed.PUBLIC),
                row(4, "GET", "/api/v1/auth/csrf", Allowed.PUBLIC),
                row(5, "POST", "/api/v1/auth/login", Allowed.PUBLIC, body),
                row(6, "POST", "/api/v1/auth/logout", Allowed.AUTHENTICATED),
                row(7, "GET", "/api/v1/auth/me", Allowed.AUTHENTICATED),
                row(8, "POST", "/api/v1/auth/student/register", Allowed.PUBLIC, body),
                row(9, "POST", "/api/v1/auth/school-admin/activate", Allowed.PUBLIC, body),
                row(10, "POST", "/api/v1/users", Allowed.SUPER_ADMIN, body),
                row(11, "POST", "/api/v1/users/" + id + "/activate", Allowed.SUPER_ADMIN),
                row(12, "POST", "/api/v1/users/" + id + "/disable", Allowed.SUPER_ADMIN),
                row(13, "POST", "/api/v1/users/" + id + "/re-enable", Allowed.SUPER_ADMIN),
                row(14, "POST", "/api/v1/school-admin-invitations", Allowed.SUPER_ADMIN, body),
                row(15, "POST", "/api/v1/school-admin-invitations/" + id + "/revoke", Allowed.SUPER_ADMIN),
                row(16, "POST", "/api/v1/school-admin-invitations/" + id + "/regenerate", Allowed.SUPER_ADMIN),
                row(17, "GET", "/api/v1/schools/" + school + "/student-identity-applications", Allowed.SCHOOL_ADMIN),
                row(18, "GET", "/api/v1/schools/" + school + "/student-identity-applications/" + id, Allowed.SCHOOL_ADMIN),
                row(19, "POST", "/api/v1/schools/" + school + "/student-identity-applications/" + id + "/approve", Allowed.SCHOOL_ADMIN),
                row(20, "POST", "/api/v1/schools/" + school + "/student-identity-applications/" + id + "/reject", Allowed.SCHOOL_ADMIN, reason),
                row(21, "GET", "/api/v1/schools", Allowed.PUBLIC),
                row(22, "GET", "/api/v1/schools/" + id, Allowed.SUPER_ADMIN),
                row(23, "POST", "/api/v1/schools/" + id + "/activate", Allowed.SUPER_ADMIN),
                row(24, "POST", "/api/v1/schools/" + id + "/disable", Allowed.SUPER_ADMIN, reason),
                row(25, "POST", "/api/v1/school-registrations", Allowed.PUBLIC, body),
                row(26, "POST", "/api/v1/school-registrations/" + id + "/approve", Allowed.SUPER_ADMIN, schoolRegistrationApprove),
                row(27, "POST", "/api/v1/school-registrations/" + id + "/reject", Allowed.SUPER_ADMIN, reason),
                row(28, "POST", "/api/v1/school-registrations/" + id + "/withdraw", Allowed.NONE),
                row(29, "GET", "/api/v1/challenge-projects", Allowed.PUBLIC),
                row(30, "POST", "/api/v1/challenge-projects", Allowed.SUPER_ADMIN, challengeCreate),
                row(31, "GET", "/api/v1/challenge-projects/" + id, Allowed.PUBLIC),
                row(32, "POST", "/api/v1/challenge-projects/" + id + "/publish", Allowed.SUPER_ADMIN),
                row(33, "GET", "/api/v1/activities", Allowed.PUBLIC),
                row(34, "POST", "/api/v1/activities", Allowed.SCHOOL_ADMIN, activityCreate),
                row(35, "POST", "/api/v1/activities/" + id + "/publish", Allowed.SCHOOL_ADMIN),
                row(36, "POST", "/api/v1/activity-applications", Allowed.NONE, activityCreate),
                row(37, "POST", "/api/v1/activity-applications/" + id + "/approve", Allowed.SCHOOL_ADMIN, activityApprove),
                row(38, "POST", "/api/v1/activity-applications/" + id + "/reject", Allowed.SCHOOL_ADMIN, reason),
                row(39, "POST", "/api/v1/activity-applications/" + id + "/withdraw", Allowed.NONE),
                row(40, "POST", "/api/v1/activity-results/" + id + "/publish", Allowed.SCHOOL_ADMIN),
                row(41, "POST", "/api/v1/score-attempts", Allowed.NONE, scoreAttemptSubmit()),
                row(42, "POST", "/api/v1/score-appeals", Allowed.STUDENT, scoreAppealSubmit),
                row(43, "POST", "/api/v1/score-appeals/" + id + "/begin-processing", Allowed.SCHOOL_ADMIN, handler),
                row(44, "POST", "/api/v1/score-appeals/" + id + "/reject", Allowed.SCHOOL_ADMIN, "{\"resolution\":\"phase11\"}"),
                row(45, "POST", "/api/v1/score-appeals/" + id + "/withdraw", Allowed.STUDENT),
                row(46, "POST", "/api/v1/ranking-definitions", Allowed.SCHOOL_ADMIN, rankingCreate),
                row(47, "POST", "/api/v1/ranking-definitions/" + id + "/enable", Allowed.SCHOOL_ADMIN),
                row(48, "POST", "/api/v1/ranking-definitions/" + id + "/disable", Allowed.SCHOOL_ADMIN),
                row(49, "POST", "/api/v1/l3-authorizations", Allowed.SCHOOL_ADMIN, l3Create),
                row(50, "POST", "/api/v1/l3-authorizations/" + id + "/approve", Allowed.SUPER_ADMIN, body),
                row(51, "POST", "/api/v1/l3-authorizations/" + id + "/withdraw", Allowed.SCHOOL_ADMIN, reason),
                row(52, "POST", "/api/v1/media", Allowed.NONE, mediaRegister()),
                row(53, "POST", "/api/v1/media/" + id + "/internal-review", Allowed.NONE),
                row(54, "POST", "/api/v1/media/" + id + "/internal-approve", Allowed.SCHOOL_ADMIN),
                row(55, "POST", "/api/v1/feedbacks", Allowed.STUDENT, feedbackSubmit),
                row(56, "POST", "/api/v1/feedbacks/" + id + "/begin-processing", Allowed.SCHOOL_ADMIN, handler),
                row(57, "POST", "/api/v1/feedbacks/" + id + "/resolve", Allowed.SCHOOL_ADMIN, "{\"reply\":\"phase11\"}"),
                row(58, "POST", "/api/v1/feedbacks/" + id + "/close", Allowed.STUDENT, reason)
        );
    }

    private String scoreAttemptSubmit() {
        return """
                {
                  "schoolId":"%s",
                  "activityProjectId":"%s",
                  "studentId":"%s",
                  "attemptNumber":1,
                  "scoreStorageType":"INTEGER",
                  "integerValue":1,
                  "enteredBy":"%s"
                }
                """.formatted(schoolId, UUID.randomUUID(), studentUserId, schoolAdminUserId);
    }

    private String mediaRegister() {
        return """
                {
                  "schoolId":"%s",
                  "activityId":"%s",
                  "fileKey":"phase11/key",
                  "fileName":"phase11.txt",
                  "fileType":"TEXT",
                  "fileFormat":"txt",
                  "fileSizeBytes":1,
                  "checksum":"abc"
                }
                """.formatted(schoolId, UUID.randomUUID());
    }

    private UUID insertSchool() {
        var id = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-school", "USCC", "phase11-uc-" + suffix,
                "phase11-ic-" + suffix, "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", "phase11@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label, "{noop}password", "NORMAL", platformRole);
        return id;
    }

    private void insertMembership(UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private Row row(int number, String method, String path, Allowed allowed) {
        return row(number, method, path, allowed, null);
    }

    private Row row(int number, String method, String path, Allowed allowed, String body) {
        return new Row(number, method, path, allowed, body);
    }

    private enum Role {
        STUDENT,
        SCHOOL_ADMIN,
        SUPER_ADMIN
    }

    private enum Allowed {
        NONE,
        PUBLIC,
        AUTHENTICATED,
        STUDENT,
        SCHOOL_ADMIN,
        SUPER_ADMIN;

        boolean allowsAnonymous() {
            return this == PUBLIC;
        }

        boolean allows(Role role) {
            return switch (this) {
                case PUBLIC, AUTHENTICATED -> true;
                case STUDENT -> role == Role.STUDENT;
                case SCHOOL_ADMIN -> role == Role.SCHOOL_ADMIN;
                case SUPER_ADMIN -> role == Role.SUPER_ADMIN;
                case NONE -> false;
            };
        }
    }

    private record Row(int number, String method, String path, Allowed allowed, String body) {
        boolean requiresCsrf() {
            return method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE");
        }
    }
}
