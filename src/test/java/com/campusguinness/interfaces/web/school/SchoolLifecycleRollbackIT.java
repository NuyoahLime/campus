package com.campusguinness.interfaces.web.school;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class SchoolLifecycleRollbackIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AuditRecordCommandPort audit;

    private final String runPrefix = "stage15-rb-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolId;
    private UUID superAdminId;

    @BeforeEach
    void setUp() {
        superAdminId = insertUser("super", "NORMAL", "SUPER_ADMIN");
        schoolId = insertSchool();
        addAdmin("one");
        addAdmin("two");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM audit_records WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM school_memberships WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
    }

    @Test
    void auditFailureRollsBackSchoolStatus() throws Exception {
        doThrow(new RuntimeException("audit failed")).when(audit).record(any());

        mvc.perform(post("/api/v1/schools/{id}/activate", schoolId)
                        .with(user(new CampusGuinnessUserDetails(
                                superAdminId,
                                runPrefix + "-principal",
                                "{noop}password",
                                "NORMAL",
                                Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
                                List.of()
                        )))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"administrators configured\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(jdbc.queryForObject(
                "SELECT school_status FROM schools WHERE id = ?", String.class, schoolId
        )).isEqualTo("PENDING_ENABLE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_records WHERE school_id = ?", Integer.class, schoolId
        )).isZero();
    }

    private UUID insertSchool() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?, ?, 'USCC', ?, ?, 'UNIVERSITY', 'Zhejiang',
                          'Stage 15 rollback address', 'Stage 15 contact', '13800000015',
                          'stage15-rb@example.com', 'PENDING_ENABLE')
                """, id, runPrefix + "-school", "S15-RU-" + suffix, "S15-RI-" + suffix);
        return id;
    }

    private UUID insertUser(String label, String status, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label + "-" + id.toString().substring(0, 8),
                "{noop}password", status, role);
        return id;
    }

    private void addAdmin(String label) {
        UUID adminId = insertUser(label, "NORMAL", null);
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, 'SCHOOL_ADMIN', 'ACTIVE')
                """, UUID.randomUUID(), adminId, schoolId);
    }
}
