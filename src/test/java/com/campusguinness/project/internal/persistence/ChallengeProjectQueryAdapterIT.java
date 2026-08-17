package com.campusguinness.project.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.project.application.query.model.ChallengeProjectGovernanceListResult;
import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ChallengeProjectQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ChallengeProjectQueryAdapter adapter;
    @Autowired private ChallengeProjectJpaRepository jpa;
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
        jdbc.update("DELETE FROM project_rule_versions");
        jdbc.update("DELETE FROM challenge_projects");
    }

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

    @Test @DisplayName("public detail exposes only PUBLISHED projects")
    void publicDetailProtectsNonPublishedProjects() {
        var published = jpa.save(project("Published", "PUBLISHED", Instant.now()));
        var draft = jpa.save(project("Draft", "DRAFT", Instant.now()));
        var archived = jpa.save(project("Archived", "ARCHIVED", Instant.now()));

        assertThat(adapter.findPublishedById(published.getId())).isPresent();
        assertThat(adapter.findPublishedById(draft.getId())).isEmpty();
        assertThat(adapter.findPublishedById(archived.getId())).isEmpty();
    }

    @Test @DisplayName("filters public projects by category and literal keyword")
    void filtersPublicProjects() {
        var math = project("Math 100% Relay", "PUBLISHED", Instant.now());
        math.setDescription("Fast team event");
        var science = project("Science Quiz", "PUBLISHED", Instant.now().minusSeconds(1));
        science.setCategory("SCIENCE");
        science.setDescription("Logic and experiments");
        jpa.saveAll(List.of(math, science));

        assertThat(adapter.findPublished(0, 10, "math", null).items())
                .extracting(ChallengeProjectListResult::name)
                .containsExactly("Math 100% Relay");
        assertThat(adapter.findPublished(0, 10, null, "logic").items())
                .extracting(ChallengeProjectListResult::name)
                .containsExactly("Science Quiz");
        assertThat(adapter.findPublished(0, 10, null, "100%").items())
                .extracting(ChallengeProjectListResult::name)
                .containsExactly("Math 100% Relay");
    }

    @Test @DisplayName("filters governance projects without nullable SQL parameters")
    void filtersGovernanceProjects() {
        var draft = project("Draft Math", "DRAFT", Instant.now());
        var published = project("Published Math", "PUBLISHED", Instant.now().minusSeconds(1));
        jpa.saveAll(List.of(draft, published));

        var result = adapter.findGovernance(0, 10, "DRAFT", "math", "draft");

        assertThat(result.items())
                .extracting(ChallengeProjectGovernanceListResult::name)
                .containsExactly("Draft Math");
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
