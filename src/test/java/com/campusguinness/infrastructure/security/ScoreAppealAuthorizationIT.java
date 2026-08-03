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
    private final ObjectMapper mapper = new ObjectMapper();

    private UUID studentId, student2Id, schoolAdminId, superAdminId, teacherId, schoolId;
    private String studentName, student2Name, schoolAdminName, superAdminName, teacherName;
    private UUID scoreAttemptId, scoreAttempt2Id;
    private static final String RAW_PW = "testPass123";

    @BeforeEach
    void setup() {
        String prefix = "saa-" + UUID.randomUUID().toString().substring(0,6) + "-";
        studentName = prefix + "stu"; student2Name = prefix + "st2"; schoolAdminName = prefix + "adm";
        superAdminName = prefix + "sa"; teacherName = prefix + "tch";

        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)", schoolId,prefix+"sch","USCC","S"+prefix,"SCH","MIDDLE_SCHOOL","GZ","r","c","1","t@t.cn","NORMAL");

        superAdminId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", superAdminId, superAdminName, encoder.encode(RAW_PW), "NORMAL", "SUPER_ADMIN");
        schoolAdminId = mkUser(schoolAdminName, "SCHOOL_ADMIN");
        teacherId = mkUser(teacherName, "TEACHER");
        studentId = mkUser(studentName, "STUDENT");
        student2Id = mkUser(student2Name, "STUDENT");

        // Create challenge project + rule version + activity + activity_project + score_attempts
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)", projectId,"AppealTest","MATH","INTEGER","NUMERIC","HIGHER_BETTER",false,"BEST","PUBLISHED");
        UUID ruleVersionId = UUID.randomUUID();
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,created_by) VALUES (?,?,?,?,?,?,?,?)", ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", teacherId);
        UUID activityId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)", activityId, schoolId, "Appeal Activity", "PUBLISHED", "NOT_SUBMITTED", teacherId);
        UUID activityProjectId = UUID.randomUUID();
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)", activityProjectId, activityId, projectId, ruleVersionId);

        scoreAttemptId = UUID.randomUUID();
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,is_current_effective,score_status,entered_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?)", scoreAttemptId, schoolId, activityProjectId, studentId, 1, "INTEGER", 100, true, "APPROVED", teacherId, 1);
        scoreAttempt2Id = UUID.randomUUID();
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,is_current_effective,score_status,entered_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?)", scoreAttempt2Id, schoolId, activityProjectId, student2Id, 1, "INTEGER", 100, true, "APPROVED", teacherId, 1);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM spring_session_attributes"); jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM appeal_records");
        jdbc.update("DELETE FROM score_appeals");
        jdbc.update("DELETE FROM score_attempts");
        jdbc.update("DELETE FROM activity_projects");
        jdbc.update("DELETE FROM project_rule_versions");
        jdbc.update("DELETE FROM activities");
        jdbc.update("DELETE FROM challenge_projects");
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM schools");
    }

    private UUID mkUser(String name, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", id, name, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), id, schoolId, role, "ACTIVE");
        return id;
    }

    private MvcResult login(String username) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
    }
    private String session(MvcResult r) { for (String h : r.getResponse().getHeaders("Set-Cookie")) if (h.startsWith("SESSION=")) return h.split(";")[0].substring("SESSION=".length()); return ""; }
    private Cookie sCookie(MvcResult r) { return new Cookie("SESSION", session(r)); }

    private UUID submitAppeal(String username, UUID attemptId) throws Exception {
        var r = login(username);
        var body = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(attemptId, "SCORE", "reason"));
        var result = mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(sCookie(r)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        return UUID.fromString(new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    // ── submit ──
    @Test void anonymousSubmitReturns401() throws Exception {
        var body = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }
    @Test void studentSubmitAllowed() throws Exception {
        var r = login(studentName);
        var body = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(sCookie(r)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }
    @Test void schoolAdminSubmitReturns403() throws Exception {
        var r = login(schoolAdminName);
        var body = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(sCookie(r)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
    @Test void superAdminSubmitReturns403() throws Exception {
        var r = login(superAdminName);
        var body = mapper.writeValueAsString(new com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest(scoreAttemptId, "SCORE", "reason"));
        mvc.perform(post("/api/v1/score-appeals").with(csrf()).cookie(sCookie(r)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }

    // ── begin-processing ──
    @Test void studentBeginProcessingReturns403() throws Exception {
        var r = login(studentName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing").with(csrf()).cookie(sCookie(r))).andExpect(status().isForbidden());
    }
    @Test void teacherBeginProcessingReturns403() throws Exception {
        var r = login(teacherName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing").with(csrf()).cookie(sCookie(r))).andExpect(status().isForbidden());
    }
    @Test void schoolAdminBeginProcessingAllowed() throws Exception {
        UUID appealId = submitAppeal(studentName, scoreAttemptId);
        var r = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/begin-processing").with(csrf()).cookie(sCookie(r))).andExpect(status().isOk());
    }
    @Test void superAdminBeginProcessingAllowed() throws Exception {
        UUID appealId = submitAppeal(studentName, scoreAttemptId);
        var r = login(superAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/begin-processing").with(csrf()).cookie(sCookie(r))).andExpect(status().isOk());
    }

    // ── reject ──
    @Test void studentRejectReturns403() throws Exception {
        var r = login(studentName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/reject").with(csrf()).cookie(sCookie(r)).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"no\"}")).andExpect(status().isForbidden());
    }
    @Test void schoolAdminRejectAllowed() throws Exception {
        UUID appealId = submitAppeal(studentName, scoreAttemptId);
        var studentR = login(studentName);
        var admR = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/begin-processing").with(csrf()).cookie(sCookie(admR))).andExpect(status().isOk());
        var admR2 = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/reject").with(csrf()).cookie(sCookie(admR2)).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"no\"}")).andExpect(status().isOk());
    }
    @Test void superAdminRejectAllowed() throws Exception {
        UUID appealId = submitAppeal(studentName, scoreAttemptId);
        var admR = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/begin-processing").with(csrf()).cookie(sCookie(admR))).andExpect(status().isOk());
        var saR = login(superAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/reject").with(csrf()).cookie(sCookie(saR)).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"no\"}")).andExpect(status().isOk());
    }

    // ── withdraw ──
    @Test void schoolAdminWithdrawReturns403() throws Exception {
        var r = login(schoolAdminName);
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/withdraw").with(csrf()).cookie(sCookie(r))).andExpect(status().isForbidden());
    }
    @Test void studentWithdrawOwnAppealSucceeds() throws Exception {
        UUID appealId = submitAppeal(studentName, scoreAttemptId);
        var r = login(studentName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/withdraw").with(csrf()).cookie(sCookie(r))).andExpect(status().isOk());
    }
    @Test void studentCannotWithdrawAnotherStudentsAppeal() throws Exception {
        UUID appealId = submitAppeal(student2Name, scoreAttempt2Id);
        var r = login(studentName);
        mvc.perform(post("/api/v1/score-appeals/" + appealId + "/withdraw").with(csrf()).cookie(sCookie(r))).andExpect(status().isNotFound());
    }
}
