package com.campusguinness.school.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SchoolQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private SchoolQueryAdapter adapter;
    @Autowired private SchoolJpaRepository jpa;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanLeakedData() {
        // Delete in reverse FK order — children before parents
        jdbc.update("DELETE FROM appeal_records");
        jdbc.update("DELETE FROM score_appeals");
        jdbc.update("DELETE FROM score_review_records");
        jdbc.update("DELETE FROM score_correction_records");
        jdbc.update("DELETE FROM abnormal_score_entries");
        jdbc.update("DELETE FROM score_attempts");
        jdbc.update("DELETE FROM ranking_entry_score_sources");
        jdbc.update("DELETE FROM ranking_entries");
        jdbc.update("DELETE FROM ranking_versions");
        jdbc.update("DELETE FROM l3_authorizations");
        jdbc.update("DELETE FROM ranking_definitions");
        jdbc.update("DELETE FROM project_rule_compatibilities");
        jdbc.update("DELETE FROM activity_projects");
        jdbc.update("DELETE FROM media_review_records");
        jdbc.update("DELETE FROM media");
        jdbc.update("DELETE FROM result_versions");
        jdbc.update("DELETE FROM activity_results");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM audit_records");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM activities");
        jdbc.update("DELETE FROM project_rule_versions");
        jdbc.update("DELETE FROM teacher_profiles");
        jdbc.update("DELETE FROM student_profiles");
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM school_registrations");
        jdbc.update("DELETE FROM schools");
    }

    @Test @DisplayName("returns only NORMAL schools, sorted by name ASC")
    void filtersNormalOnly() {
        var now = Instant.now();
        jpa.saveAll(List.of(
                school("B-School", "NORMAL", now),
                school("A-School", "NORMAL", now),
                school("Pending", "PENDING_ENABLE", now),
                school("Suspended", "SUSPENDED", now),
                school("Disabled", "DISABLED", now)
        ));
        var result = adapter.findNormal(0, 10);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).name()).isEqualTo("A-School");
        assertThat(result.items().get(1).name()).isEqualTo("B-School");
    }

    @Test @DisplayName("ties on name resolved by id ASC")
    void tiesResolvedByIdAsc() {
        var now = Instant.now();
        var s1 = school("Same", "NORMAL", now); s1.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var s2 = school("Same", "NORMAL", now); s2.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        jpa.saveAll(List.of(s1, s2));
        var result = adapter.findNormal(0, 10);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo(s2.getId()); // lower id first on tie
    }

    @Test @DisplayName("paginates without overlap or gaps, excludes non-NORMAL")
    void paginatesCompletely() {
        for (int i = 0; i < 5; i++)
            jpa.save(school("S" + i, "NORMAL", Instant.now()));
        jpa.save(school("P", "PENDING_ENABLE", Instant.now()));
        jpa.save(school("X", "SUSPENDED", Instant.now()));
        var allIds = new java.util.HashSet<UUID>();
        var p0 = adapter.findNormal(0, 2); p0.items().forEach(s -> allIds.add(s.id()));
        var p1 = adapter.findNormal(1, 2); p1.items().forEach(s -> allIds.add(s.id()));
        var p2 = adapter.findNormal(2, 2); p2.items().forEach(s -> allIds.add(s.id()));
        assertThat(p0.items()).hasSize(2);
        assertThat(p1.items()).hasSize(2);
        assertThat(p2.items()).hasSize(1);
        assertThat(allIds).hasSize(5);
    }

    @Test @DisplayName("beyond last page returns empty")
    void beyondLastPageReturnsEmpty() {
        jpa.save(school("X", "NORMAL", Instant.now()));
        var result = adapter.findNormal(5, 10);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test @DisplayName("school-admin provisioning accepts pending and normal schools only")
    void schoolAdminProvisioningEligibility() {
        var pending = school("Pending", "PENDING_ENABLE", Instant.now());
        var normal = school("Normal", "NORMAL", Instant.now());
        var suspended = school("Suspended", "SUSPENDED", Instant.now());
        var disabled = school("Disabled", "DISABLED", Instant.now());
        jpa.saveAll(List.of(pending, normal, suspended, disabled));

        assertThat(adapter.isEligibleForMembership(pending.getId())).isTrue();
        assertThat(adapter.isEligibleForMembership(normal.getId())).isTrue();
        assertThat(adapter.isEligibleForMembership(suspended.getId())).isFalse();
        assertThat(adapter.isEligibleForMembership(disabled.getId())).isFalse();
    }

    private SchoolEntity school(String name, String status, Instant now) {
        var e = new SchoolEntity();
        e.setId(UUID.randomUUID()); e.setName(name); e.setUnifiedCodeType("USCC");
        e.setUnifiedCode(UUID.randomUUID().toString()); e.setInternalCode("INT-" + UUID.randomUUID().toString().substring(0, 8));
        e.setSchoolType("PRIMARY"); e.setRegion("Beijing"); e.setAddress("addr");
        e.setContactName("n"); e.setContactPhone("p"); e.setContactEmail("e");
        e.setSchoolStatus(status); e.setCreatedAt(now); e.setUpdatedAt(now);
        return e;
    }
}
