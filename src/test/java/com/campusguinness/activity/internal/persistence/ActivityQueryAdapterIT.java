package com.campusguinness.activity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolation strategy: clears shared Testcontainers tables in FK-safe order
 * both before AND after each test method, so that:
 * <ul>
 *   <li>Incoming contamination from other IT classes is removed before assertions.</li>
 *   <li>This class's own test data does not leak to subsequent IT classes.</li>
 * </ul>
 * The appeal integration tests (ScoreAppealPathAVerificationIT,
 * ScoreAppealCorePersistenceIT, ScoreAppealJsonbMappingIT) separately own
 * responsibility for their own cleanup and will be addressed in a follow-up.
 */
class ActivityQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ActivityQueryAdapter adapter;
    @Autowired private ActivityJpaRepository jpa;
    @Autowired private JdbcTemplate jdbc;

    private UUID schoolId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        cleanActivityTestData();
        schoolId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "Test School", "USCC", "USCC-"+UUID.randomUUID().toString().substring(0,8),
                "INT-"+UUID.randomUUID().toString().substring(0,8), "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                userId, "user-"+UUID.randomUUID().toString().substring(0,8), "hash", "NORMAL");
    }

    @AfterEach
    void tearDown() {
        cleanActivityTestData();
    }

    /**
     * FK-safe cleanup of tables that may contain test data leaking from
     * other IT classes that share the same Testcontainers instance.
     * All child tables use ON DELETE RESTRICT, so deletion order matters.
     */
    private void cleanActivityTestData() {
        // FK-safe order — all child tables use ON DELETE RESTRICT
        jdbc.update("DELETE FROM appeal_records");
        jdbc.update("DELETE FROM score_appeals");
        jdbc.update("DELETE FROM score_review_records");
        jdbc.update("DELETE FROM score_correction_records");
        jdbc.update("DELETE FROM abnormal_score_entries");
        jdbc.update("DELETE FROM score_attempts");
        jdbc.update("DELETE FROM activity_projects");
        jdbc.update("DELETE FROM activity_participants");
        jdbc.update("DELETE FROM media");
        jdbc.update("DELETE FROM activity_results");
        jdbc.update("DELETE FROM activities");
    }

    @Test @DisplayName("returns only public execution statuses, excludes CANCELLED")
    void filtersPublicStatuses() {
        var now = Instant.now();
        jpa.saveAll(List.of(
                activity("Published", "PUBLISHED", now),
                activity("InProgress", "IN_PROGRESS", now.plusSeconds(1)),
                activity("Ended", "ENDED", now.minusSeconds(1)),
                activity("Cancelled", "CANCELLED", now),
                activity("Draft", "DRAFT", now)
        ));
        var result = adapter.findPublic(0, 10, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.items().stream().map(a -> a.title())).containsExactlyInAnyOrder("Published", "InProgress", "Ended");
    }

    @Test @DisplayName("does not expose non-public activities even with a public execution status")
    void excludesNonPublicActivity() {
        jpa.save(activity("Visible", "PUBLISHED", Instant.now(), "PUBLIC"));
        jpa.save(activity("Hidden", "PUBLISHED", Instant.now().plusSeconds(1), "NOT_SUBMITTED"));

        var result = adapter.findPublic(0, 10, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).extracting(ActivityListResult::title).containsExactly("Visible");
    }

    @Test @DisplayName("ties on startTime resolved by id DESC")
    void tiesResolvedByIdDesc() {
        var base = Instant.parse("2026-01-01T00:00:00Z");
        var a1 = activity("First", "PUBLISHED", base); a1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var a2 = activity("Second", "PUBLISHED", base); a2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        jpa.saveAll(List.of(a1, a2));
        var result = adapter.findPublic(0, 10, List.of("PUBLISHED","IN_PROGRESS","ENDED"));
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo(a2.getId());
    }

    @Test @DisplayName("paginates without overlap or gaps")
    void paginatesCompletely() {
        for (int i = 0; i < 5; i++)
            jpa.save(activity("A" + i, "PUBLISHED", Instant.now().minusSeconds(i)));
        var allIds = new java.util.HashSet<UUID>();
        var p0 = adapter.findPublic(0, 2, List.of("PUBLISHED","IN_PROGRESS","ENDED"));
        p0.items().forEach(a -> allIds.add(a.id()));
        var p1 = adapter.findPublic(1, 2, List.of("PUBLISHED","IN_PROGRESS","ENDED"));
        p1.items().forEach(a -> allIds.add(a.id()));
        var p2 = adapter.findPublic(2, 2, List.of("PUBLISHED","IN_PROGRESS","ENDED"));
        p2.items().forEach(a -> allIds.add(a.id()));
        assertThat(p0.items()).hasSize(2);
        assertThat(p1.items()).hasSize(2);
        assertThat(p2.items()).hasSize(1);
        assertThat(allIds).hasSize(5);
    }

    @Test @DisplayName("beyond last page returns empty")
    void beyondLastPageReturnsEmpty() {
        jpa.save(activity("X", "PUBLISHED", Instant.now()));
        var result = adapter.findPublic(5, 10, List.of("PUBLISHED","IN_PROGRESS","ENDED"));
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test @DisplayName("detail uses the activity's historical rule version snapshot")
    void detailUsesHistoricalRuleVersion() {
        var projectId = UUID.randomUUID();
        var ruleVersionId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?)",
                projectId, "Historical Project", "SPORT", "INTEGER", "VALUE", "HIGHER_BETTER", "BEST", "PUBLISHED");
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,rules_text,allow_tie,effective_score_rule,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 3, "INTEGER", "VALUE", "HIGHER_BETTER", "Snapshot rules V3", true, "BEST", userId);
        jdbc.update("INSERT INTO activities(id,school_id,title,description,execution_status,public_status,created_by,start_time,end_time,location) VALUES (?,?,?,?,?,?,?,?,?,?)",
                activityId, schoolId, "Public activity", "Public description", "PUBLISHED", "PUBLIC", userId,
                java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),
                "Main court");
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), activityId, projectId, ruleVersionId);

        var result = adapter.findPublicById(activityId, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().schoolName()).isEqualTo("Test School");
        assertThat(result.orElseThrow().projects()).singleElement()
                .satisfies(project -> {
                    assertThat(project.projectName()).isEqualTo("Historical Project");
                    assertThat(project.ruleVersionNumber()).isEqualTo(3);
                    assertThat(project.rulesText()).isEqualTo("Snapshot rules V3");
                });
    }

    private ActivityEntity activity(String title, String execStatus, Instant startTime) {
        return activity(title, execStatus, startTime, "PUBLIC");
    }

    private ActivityEntity activity(String title, String execStatus, Instant startTime, String publicStatus) {
        var e = new ActivityEntity();
        e.setId(UUID.randomUUID()); e.setSchoolId(schoolId); e.setCreatedBy(userId);
        e.setTitle(title); e.setExecutionStatus(execStatus); e.setPublicStatus(publicStatus);
        e.setStartTime(startTime); e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }
}
