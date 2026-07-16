package com.campusguinness.school.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SchoolQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private SchoolQueryAdapter adapter;
    @Autowired private SchoolJpaRepository jpa;

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
