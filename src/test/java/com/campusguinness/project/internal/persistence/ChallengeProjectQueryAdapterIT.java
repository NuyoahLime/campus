package com.campusguinness.project.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ChallengeProjectQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ChallengeProjectQueryAdapter adapter;
    @Autowired private ChallengeProjectJpaRepository jpa;

    @Test @DisplayName("returns only PUBLISHED projects, sorted by createdAt DESC")
    void filtersPublishedOnly() {
        var now = Instant.now();
        jpa.saveAll(List.of(
                project("A", "PUBLISHED", now.plusSeconds(1)),
                project("B", "PUBLISHED", now),
                project("C", "DRAFT", now),
                project("D", "ARCHIVED", now)
        ));
        var result = adapter.findPublished(0, 10);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).name()).isEqualTo("A"); // most recent first
        assertThat(result.items().get(1).name()).isEqualTo("B");
    }

    @Test @DisplayName("paginates correctly without overlap")
    void paginatesWithoutOverlap() {
        for (int i = 0; i < 5; i++)
            jpa.save(project("P" + i, "PUBLISHED", Instant.now().minusSeconds(i)));
        var page0 = adapter.findPublished(0, 2);
        var page1 = adapter.findPublished(1, 2);
        assertThat(page0.items()).hasSize(2);
        assertThat(page1.items()).hasSize(2);
        assertThat(page0.items().get(0).id()).isNotEqualTo(page1.items().get(0).id());
        assertThat(page0.items().get(0).id()).isNotEqualTo(page1.items().get(1).id());
    }

    @Test @DisplayName("beyond last page returns empty")
    void beyondLastPageReturnsEmpty() {
        jpa.save(project("X", "PUBLISHED", Instant.now()));
        var result = adapter.findPublished(5, 10);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    private ChallengeProjectEntity project(String name, String status, Instant createdAt) {
        var e = new ChallengeProjectEntity();
        e.setId(UUID.randomUUID()); e.setName(name); e.setCategory("MATH");
        e.setScoreStorageType("INTEGER"); e.setScoreIndicatorType("NUMERIC");
        e.setComparisonDirection("HIGHER_BETTER"); e.setEffectiveScoreRule("BEST");
        e.setAllowTie(false); e.setProjectStatus(status);
        e.setCreatedAt(createdAt); e.setUpdatedAt(createdAt);
        return e;
    }
}
