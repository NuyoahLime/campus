package com.campusguinness.infrastructure.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class AccountProvisioningSecurityIT extends PostgreSqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID schoolId; UUID studentId; String u;

    @BeforeEach void setup() {
        schoolId = UUID.randomUUID(); studentId = UUID.randomUUID();
        u = UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,'USCC','SC-"+u+"','INT-"+u+"','PRIMARY','Test','Addr','Name','12345','a@b.com','NORMAL')", schoolId);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", studentId, "secs-"+u, "$2a$10$hAnonDummyHashForTest", "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), studentId, schoolId, "STUDENT", "ACTIVE");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM school_memberships WHERE user_id=?", studentId);
        jdbc.update("DELETE FROM users WHERE id=?", studentId);
        jdbc.update("DELETE FROM schools WHERE id=?", schoolId);
    }

    @Test void unauthenticatedDeniedOnProvisioning() throws Exception {
        mvc.perform(post("/api/v1/admin/schools/" + schoolId + "/administrators")
                .with(csrf()).contentType("application/json")
                .content("{\"username\":\"x\",\"temporaryPassword\":\"P@ss123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void unauthenticatedDeniedOnSchoolAdminProvisioning() throws Exception {
        mvc.perform(post("/api/v1/school-admin/accounts")
                .with(csrf()).contentType("application/json")
                .content("{\"username\":\"x\",\"temporaryPassword\":\"P@ss123!\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isUnauthorized());
    }
}
