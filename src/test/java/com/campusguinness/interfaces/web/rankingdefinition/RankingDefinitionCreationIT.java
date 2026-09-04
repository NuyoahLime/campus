package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RankingDefinitionCreationIT extends PostgreSqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String prefix = "ranking-def-create-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolId;
    private UUID adminId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                schoolId, prefix + "-school", "USCC", prefix + "-uc", prefix + "-ic",
                "PRIMARY", "Beijing", "Address", "Contact", "13800000000",
                prefix + "@example.com", "NORMAL");
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                adminId, prefix + "-admin", "{noop}password", "NORMAL");
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, 'SCHOOL_ADMIN', 'ACTIVE')
                """, UUID.randomUUID(), adminId, schoolId);
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                projectId, prefix + "-project", "SPORTS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "points", "BEST", "PUBLISHED");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ranking_definitions WHERE name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM challenge_projects WHERE name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", prefix + "%");
    }

    @Test
    void duplicateL2DefinitionReturnsControlledConflictInsteadOfInternalError() throws Exception {
        mvc.perform(post("/api/v1/ranking-definitions")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(l2Payload(prefix + "-l2-a", projectId, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/ranking-definitions")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(l2Payload(prefix + "-l2-b", projectId, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("L2_RANKING_DEFINITION_ALREADY_EXISTS"));

        assertThat(l2DefinitionCount()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateL2DefinitionCreationRemainsProtectedByDatabaseUniqueness() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createConcurrent("concurrent-a", ready, start));
            var second = executor.submit(() -> createConcurrent("concurrent-b", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(l2DefinitionCount()).isEqualTo(1);
    }

    @Test
    void reverseActivityPeriodIsRejectedBeforePersistingDefinition() throws Exception {
        mvc.perform(post("/api/v1/ranking-definitions")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(l2Payload(prefix + "-reverse", projectId,
                                "2026-01-02T00:00:00Z", "2026-01-01T00:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot generate ranking: activityPeriodStart must not be after activityPeriodEnd."));

        assertThat(l2DefinitionCount()).isZero();
    }

    private int createConcurrent(String label, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return mvc.perform(post("/api/v1/ranking-definitions")
                            .with(admin())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(l2Payload(prefix + "-" + label, projectId, null, null)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private int l2DefinitionCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_definitions
                WHERE layer = 'L2' AND school_id = ? AND project_id = ?
                """, Integer.class, schoolId, projectId);
    }

    private String l2Payload(String name, UUID projectId, String start, String end) {
        StringBuilder filters = new StringBuilder("{\"selectionPolicy\":\"BEST_SCORE\"");
        if (start != null) {
            filters.append(",\"activityPeriodStart\":\"").append(start).append("\"");
        }
        if (end != null) {
            filters.append(",\"activityPeriodEnd\":\"").append(end).append("\"");
        }
        filters.append("}");
        return """
                {
                  "layer": "L2",
                  "name": "%s",
                  "projectId": "%s",
                  "dimensionFilters": "%s"
                }
                """.formatted(name, projectId, filters.toString().replace("\"", "\\\""));
    }

    private RequestPostProcessor admin() {
        var details = new CampusGuinnessUserDetails(
                adminId,
                prefix + "-admin",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
        return user(details);
    }
}
