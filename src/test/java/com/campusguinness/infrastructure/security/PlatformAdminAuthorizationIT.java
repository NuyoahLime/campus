package com.campusguinness.infrastructure.security;

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
class PlatformAdminAuthorizationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private UUID superAdminId, schoolAdminId, studentId, teacherId, schoolId;
    private String superAdminName, schoolAdminName, studentName, teacherName;
    private static final String RAW_PW = "testPass123";

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM spring_session_attributes"); jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM school_memberships"); jdbc.update("DELETE FROM users"); jdbc.update("DELETE FROM schools");
        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)", schoolId, "paz", "USCC","9244P","SCH-P","MIDDLE_SCHOOL","GZ","r","c","1","t@t.cn","PENDING_ENABLE");

        superAdminId = UUID.randomUUID(); superAdminName = "paz-sa-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", superAdminId, superAdminName, encoder.encode(RAW_PW), "NORMAL", "SUPER_ADMIN");

        schoolAdminId = UUID.randomUUID(); schoolAdminName = "paz-adm-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", schoolAdminId, schoolAdminName, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), schoolAdminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");

        studentId = UUID.randomUUID(); studentName = "paz-stu-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", studentId, studentName, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), studentId, schoolId, "STUDENT", "ACTIVE");

        teacherId = UUID.randomUUID(); teacherName = "paz-tch-" + rand();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", teacherId, teacherName, encoder.encode(RAW_PW), "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), teacherId, schoolId, "TEACHER", "ACTIVE");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM spring_session_attributes"); jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM school_memberships"); jdbc.update("DELETE FROM users"); jdbc.update("DELETE FROM schools");
    }

    private static String rand() { return UUID.randomUUID().toString().substring(0, 8); }

    private String extractSessionValue(MvcResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith("SESSION=")) {
                String pair = header.split(";")[0]; // "SESSION=xxx"
                return pair.substring("SESSION=".length());
            }
        }
        return "";
    }

    @Test void studentCannotActivateSchool() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + studentName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/schools/" + schoolId + "/activate").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r)))).andExpect(status().isForbidden());
    }
    @Test void schoolAdminCannotActivateSchool() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + schoolAdminName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/schools/" + schoolId + "/activate").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r)))).andExpect(status().isForbidden());
    }
    @Test void superAdminCanActivateSchool() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + superAdminName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/schools/" + schoolId + "/activate").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r)))).andExpect(status().is2xxSuccessful());
    }
    @Test void anonymousCannotActivateSchool() throws Exception {
        mvc.perform(post("/api/v1/schools/" + schoolId + "/activate").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void studentCannotCreateRankingDefinition() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + studentName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        String json = "{\"layer\":\"L1\",\"name\":\"test\",\"projectId\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/v1/ranking-definitions").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r))).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
    }
    @Test void schoolAdminCannotDisableSchool() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + schoolAdminName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/schools/" + schoolId + "/disable").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r))).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"t\"}")).andExpect(status().isForbidden());
    }
    @Test void studentCanAccessOwnProfile() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + studentName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("SESSION", extractSessionValue(r)))).andExpect(status().isOk());
    }

    @Test void schoolAdminCannotApproveL3() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + schoolAdminName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/l3-authorizations/" + UUID.randomUUID() + "/approve").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test void schoolAdminCannotApproveSchoolRegistration() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + schoolAdminName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/school-registrations/" + UUID.randomUUID() + "/approve").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test void studentCannotPublishActivityResult() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + studentName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/activity-results/" + UUID.randomUUID() + "/publish").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r)))).andExpect(status().isForbidden());
    }

    @Test void teacherCannotPublishActivityResult() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + teacherName + "\",\"password\":\"" + RAW_PW + "\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/activity-results/" + UUID.randomUUID() + "/publish").with(csrf())
                .cookie(new Cookie("SESSION", extractSessionValue(r)))).andExpect(status().isForbidden());
    }
}
