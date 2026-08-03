package com.campusguinness.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScoreAppealAuthorizationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private UUID studentId, schoolAdminId, superAdminId, teacherId, schoolId;
    private String studentName, schoolAdminName, superAdminName, teacherName;
    private UUID scoreAttemptId, appealId;
    private static final String RAW_PW = "testPass123";
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM spring_session_attributes"); jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM school_memberships"); jdbc.update("DELETE FROM score_appeals"); jdbc.update("DELETE FROM score_attempts"); jdbc.update("DELETE FROM users"); jdbc.update("DELETE FROM schools");

        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)", schoolId,"sa","USCC","SA9","SCH-SA","MIDDLE_SCHOOL","GZ","r","c","1","t@t.cn","NORMAL");

        superAdminId = UUID.randomUUID(); superAdminName = "saa-sa-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", superAdminId, superAdminName, encoder.encode(RAW_PW), "NORMAL", "SUPER_ADMIN");

        schoolAdminId = UUID.randomUUID(); schoolAdminName = "saa-adm-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", schoolAdminId, schoolAdminName, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), schoolAdminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");

        studentId = UUID.randomUUID(); studentName = "saa-stu-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", studentId, studentName, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), studentId, schoolId, "STUDENT", "ACTIVE");

        teacherId = UUID.randomUUID(); teacherName = "saa-tch-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", teacherId, teacherName, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), teacherId, schoolId, "TEACHER", "ACTIVE");

        // Create a score attempt owned by the student (needed for submit)
        scoreAttemptId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO projects(id,name,category,score_storage_type,status,created_at,updated_at,version) VALUES (?,?,?,?,?,now(),now(),0)", projectId,"p","A","NUMERIC","PUBLISHED");
        jdbc.update("INSERT INTO score_attempts(id,student_id,school_id,project_id,attempt_number,score_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,now(),now(),0)", scoreAttemptId, studentId, schoolId, projectId, 1, "DRAFT");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM spring_session_attributes"); jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM school_memberships"); jdbc.update("DELETE FROM score_appeals"); jdbc.update("DELETE FROM score_attempts"); jdbc.update("DELETE FROM users"); jdbc.update("DELETE FROM schools");
        jdbc.update("DELETE FROM projects");
    }

    private static String rand() { return UUID.randomUUID().toString().substring(0,8); }

    private MvcResult login(String username) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
    }

    private String sessionCookie(MvcResult r) {
        for (String h : r.getResponse().getHeaders("Set-Cookie")) {
            if (h.startsWith("SESSION=")) return h.split(";")[0].substring("SESSION=".length());
        }
        return "";
    }

    // ── submit role checks ──

    @Test void anonymousSubmitReturns401() throws Exception {
        String json = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnauthorized());
    }

    @Test void studentSubmitAllowed() throws Exception {
        var r = login(studentName);
        String json = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r))).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());
    }

    @Test void schoolAdminSubmitReturns403() throws Exception {
        var r = login(schoolAdminName);
        String json = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r))).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
    }

    @Test void superAdminSubmitReturns403() throws Exception {
        var r = login(superAdminName);
        String json = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r))).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
    }

    // ── begin-processing role checks ──

    @Test void studentBeginProcessingReturns403() throws Exception {
        var r = login(studentName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r)))).andExpect(status().isForbidden());
    }

    @Test void teacherBeginProcessingReturns403() throws Exception {
        var r = login(teacherName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r)))).andExpect(status().isForbidden());
    }

    @Test void schoolAdminBeginProcessingAllowed() throws Exception {
        // Create appeal first, then try to begin-processing
        var studentR = login(studentName);
        String json = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        var submitR = mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(studentR))).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn();
        UUID createdId = UUID.fromString(new ObjectMapper().readTree(submitR.getResponse().getContentAsString()).get("id").asText());

        var admR = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + createdId + "/begin-processing").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(admR)))).andExpect(status().isOk());
    }

    // ── withdraw role checks ──

    @Test void schoolAdminWithdrawReturns403() throws Exception {
        var r = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/withdraw").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r)))).andExpect(status().isForbidden());
    }

    @Test void studentWithdrawOwnAppealSucceeds() throws Exception {
        var studentR = login(studentName);
        String json = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        var submitR = mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(studentR))).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn();
        UUID createdId = UUID.fromString(new ObjectMapper().readTree(submitR.getResponse().getContentAsString()).get("id").asText());

        var r2 = login(studentName);
        mvc.perform(post("/api/v1/score-appeals/" + createdId + "/withdraw").with(csrf()).cookie(new Cookie("SESSION", sessionCookie(r2)))).andExpect(status().isOk());
    }
}
