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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountActivationControllerIT extends PostgreSqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID userId; String username;

    @BeforeEach void setup() {
        userId = UUID.randomUUID(); username = "ctrl-" + UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,activation_issued_at,activation_expires_at) VALUES (?,?,?,?,now(),now() + INTERVAL '72 hours')", userId, username, "$2a$10$hash1234567890123456789012345", "PENDING_ACTIVATION");
    }

    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id=?", userId); }

    @Test void withoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/auth/activate")
                .contentType("application/json")
                .content("{\"username\":\"x\",\"temporaryPassword\":\"x\",\"newPassword\":\"P@ssw0rd!\",\"confirmPassword\":\"P@ssw0rd!\"}"))
                .andExpect(status().isForbidden());
    }
}
