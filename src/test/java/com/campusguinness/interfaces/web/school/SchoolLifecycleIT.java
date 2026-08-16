package com.campusguinness.interfaces.web.school;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class SchoolLifecycleIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private final String runPrefix = "stage15-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID superAdminId;

    @BeforeEach
    void setUp() {
        superAdminId = insertUser("super-admin", "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM audit_records WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM school_admin_invitations WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void activateUsesFrozenNormalActiveSchoolAdminDefinition() throws Exception {
        UUID schoolId = insertSchool("count-rules", "PENDING_ENABLE");
        addValidAdmin(schoolId, "valid-one");

        UUID pendingUser = insertUser("pending", "PENDING_ACTIVATION", null);
        insertMembership(pendingUser, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        insertInvitation(pendingUser, schoolId, "PENDING", Instant.now().plusSeconds(3600));

        UUID inactiveAdmin = insertUser("inactive", "NORMAL", null);
        insertMembership(inactiveAdmin, schoolId, "SCHOOL_ADMIN", "ENDED");

        UUID teacher = insertUser("teacher", "NORMAL", null);
        insertMembership(teacher, schoolId, "TEACHER", "ACTIVE");

        performLifecycle(schoolId, "activate", "administrators configured")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_CONFIGURATION_INSUFFICIENT"));
        assertThat(schoolStatus(schoolId)).isEqualTo("PENDING_ENABLE");
        assertThat(auditCount(schoolId, "SCHOOL_ACTIVATE")).isZero();

        addValidAdmin(schoolId, "valid-two");
        performLifecycle(schoolId, "activate", "administrators configured")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
        assertThat(auditCount(schoolId, "SCHOOL_ACTIVATE")).isEqualTo(1);
    }

    @Test
    void lifecycleChainPersistsEveryTransitionAndAuthoritativeAudit() throws Exception {
        UUID schoolId = insertSchool("full-chain", "PENDING_ENABLE");
        addValidAdmin(schoolId, "chain-one");
        addValidAdmin(schoolId, "chain-two");

        performLifecycle(schoolId, "activate", "administrators configured")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NORMAL"));
        performLifecycle(schoolId, "suspend", "platform governance pause")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        performLifecycle(schoolId, "restore", "issue resolved")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NORMAL"));
        performLifecycle(schoolId, "disable", "school ended operations")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        performLifecycle(schoolId, "re-enable", "school reapplied")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_ENABLE"));

        assertThat(schoolStatus(schoolId)).isEqualTo("PENDING_ENABLE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_records WHERE school_id = ? AND target_type = 'SCHOOL'",
                Integer.class,
                schoolId
        )).isEqualTo(5);
        assertAudit(schoolId, "SCHOOL_ACTIVATE", "PENDING_ENABLE", "NORMAL", "administrators configured");
        assertAudit(schoolId, "SCHOOL_SUSPEND", "NORMAL", "SUSPENDED", "platform governance pause");
        assertAudit(schoolId, "SCHOOL_RESTORE", "SUSPENDED", "NORMAL", "issue resolved");
        assertAudit(schoolId, "SCHOOL_DISABLE", "NORMAL", "DISABLED", "school ended operations");
        assertAudit(schoolId, "SCHOOL_REENABLE", "DISABLED", "PENDING_ENABLE", "school reapplied");
    }

    @Test
    void restoreAlsoRequiresTwoActiveAdmins() throws Exception {
        UUID schoolId = insertSchool("restore-count", "SUSPENDED");
        addValidAdmin(schoolId, "restore-one");

        performLifecycle(schoolId, "restore", "issue resolved")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_CONFIGURATION_INSUFFICIENT"));
        assertThat(schoolStatus(schoolId)).isEqualTo("SUSPENDED");

        addValidAdmin(schoolId, "restore-two");
        performLifecycle(schoolId, "restore", "issue resolved")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    void invalidTransitionAndUnknownSchoolUseStableErrors() throws Exception {
        UUID pendingSchool = insertSchool("invalid", "PENDING_ENABLE");

        performLifecycle(pendingSchool, "suspend", "not allowed")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SCHOOL_STATE_TRANSITION"));
        performLifecycle(UUID.randomUUID(), "activate", "valid reason")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    @Test
    void concurrentActivateProducesExactlyOneSuccessAndOneAudit() throws Exception {
        UUID schoolId = insertSchool("concurrent", "PENDING_ENABLE");
        addValidAdmin(schoolId, "concurrent-one");
        addValidAdmin(schoolId, "concurrent-two");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return lifecycleStatus(schoolId, "activate", "concurrent activation one");
            });
            var second = executor.submit(() -> {
                start.await();
                return lifecycleStatus(schoolId, "activate", "concurrent activation two");
            });
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 409);
        }

        assertThat(schoolStatus(schoolId)).isEqualTo("NORMAL");
        assertThat(auditCount(schoolId, "SCHOOL_ACTIVATE")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions performLifecycle(
            UUID schoolId,
            String action,
            String reason
    ) throws Exception {
        return mvc.perform(post("/api/v1/schools/{id}/{action}", schoolId, action)
                .with(principal())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private int lifecycleStatus(UUID schoolId, String action, String reason) throws Exception {
        return performLifecycle(schoolId, action, reason)
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private RequestPostProcessor principal() {
        return user(new CampusGuinnessUserDetails(
                superAdminId,
                runPrefix + "-principal",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
                List.of()
        ));
    }

    private UUID insertSchool(String label, String status) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?, ?, 'USCC', ?, ?, 'UNIVERSITY', 'Zhejiang',
                          'Stage 15 address', 'Stage 15 contact', '13800000015',
                          'stage15@example.com', ?)
                """, id, runPrefix + "-" + label, "S15-U-" + suffix, "S15-I-" + suffix, status);
        return id;
    }

    private UUID insertUser(String label, String status, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label + "-" + id.toString().substring(0, 8),
                "{noop}password", status, platformRole);
        return id;
    }

    private void addValidAdmin(UUID schoolId, String label) {
        UUID userId = insertUser(label, "NORMAL", null);
        insertMembership(userId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
    }

    private void insertMembership(UUID userId, UUID schoolId, String role, String status) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, schoolId, role, status);
    }

    private void insertInvitation(UUID userId, UUID schoolId, String status, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO school_admin_invitations(
                    id, user_id, school_id, role_in_school, invitation_code_hash,
                    invitation_status, expires_at, created_by
                ) VALUES (?, ?, ?, 'SCHOOL_ADMIN', 'stage15-hash', ?, ?, ?)
                """, UUID.randomUUID(), userId, schoolId, status,
                java.sql.Timestamp.from(expiresAt), superAdminId);
    }

    private String schoolStatus(UUID schoolId) {
        return jdbc.queryForObject(
                "SELECT school_status FROM schools WHERE id = ?",
                String.class,
                schoolId
        );
    }

    private int auditCount(UUID schoolId, String action) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_records WHERE school_id = ? AND action = ?",
                Integer.class,
                schoolId,
                action
        );
    }

    private void assertAudit(
            UUID schoolId,
            String action,
            String oldStatus,
            String newStatus,
            String reason
    ) throws Exception {
        var row = jdbc.queryForMap(
                "SELECT actor_id, target_id, detail::text AS detail FROM audit_records WHERE school_id = ? AND action = ?",
                schoolId,
                action
        );
        assertThat(row.get("actor_id")).isEqualTo(superAdminId);
        assertThat(row.get("target_id")).isEqualTo(schoolId);
        var detail = objectMapper.readTree((String) row.get("detail"));
        assertThat(detail.get("targetSchoolId").asText()).isEqualTo(schoolId.toString());
        assertThat(detail.get("oldStatus").asText()).isEqualTo(oldStatus);
        assertThat(detail.get("newStatus").asText()).isEqualTo(newStatus);
        assertThat(detail.get("reason").asText()).isEqualTo(reason);
    }
}
