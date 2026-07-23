package com.campusguinness.project.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.project.application.query.model.PublicProjectListFilter;
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
        assertThat(page0.items().get(0).projectId()).isNotEqualTo(page1.items().get(0).projectId());
        assertThat(page0.items().get(0).projectId()).isNotEqualTo(page1.items().get(1).projectId());
    }

    @Test @DisplayName("beyond last page returns empty")
    void beyondLastPageReturnsEmpty() {
        jpa.save(project("X", "PUBLISHED", Instant.now()));
        var result = adapter.findPublished(5, 10);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test @DisplayName("filters by keyword matching name case-insensitively")
    void filtersByKeywordName() {
        jpa.saveAll(List.of(
                projectWithDesc("数学竞赛", "PUBLISHED", "数学竞赛描述"),
                projectWithDesc("英语比赛", "PUBLISHED", "英语比赛描述"),
                projectWithDesc("语文考试", "PUBLISHED", "语文考试描述")
        ));
        var filter = new PublicProjectListFilter("数学", null, null, null, null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(1); // matches 数学竞赛 name (description also has 数学 but same project)
        assertThat(result.items().get(0).name()).isEqualTo("数学竞赛");
    }

    @Test @DisplayName("filters by keyword matching description case-insensitively")
    void filtersByKeywordDescription() {
        jpa.saveAll(List.of(
                projectWithDesc("项目A", "PUBLISHED", "关于田径运动的项目"),
                projectWithDesc("项目B", "PUBLISHED", "关于游泳运动的项目"),
                projectWithDesc("项目C", "PUBLISHED", "其他描述")
        ));
        var filter = new PublicProjectListFilter("田径", null, null, null, null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).name()).isEqualTo("项目A");
    }

    @Test @DisplayName("filters by category exact match")
    void filtersByCategory() {
        jpa.saveAll(List.of(
                projectWithCategory("P1", "PUBLISHED", "MATH"),
                projectWithCategory("P2", "PUBLISHED", "SPEED"),
                projectWithCategory("P3", "PUBLISHED", "MATH")
        ));
        var filter = new PublicProjectListFilter(null, "MATH", null, null, null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test @DisplayName("filters by scoreStorageType exact match")
    void filtersByScoreStorageType() {
        jpa.saveAll(List.of(
                projectWithStorageType("P1", "PUBLISHED", "INTEGER"),
                projectWithStorageType("P2", "PUBLISHED", "DECIMAL"),
                projectWithStorageType("P3", "PUBLISHED", "INTEGER")
        ));
        var filter = new PublicProjectListFilter(null, null, "INTEGER", null, null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test @DisplayName("filters by venueKeyword matching venueRequirements")
    void filtersByVenueKeyword() {
        var p1 = project("P1", "PUBLISHED", Instant.now());
        p1.setVenueRequirements("需要标准田径场");
        var p2 = project("P2", "PUBLISHED", Instant.now());
        p2.setVenueRequirements("需要游泳池");
        var p3 = project("P3", "PUBLISHED", Instant.now());
        p3.setVenueRequirements(null);
        jpa.saveAll(List.of(p1, p2, p3));
        var filter = new PublicProjectListFilter(null, null, null, "田径", null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).name()).isEqualTo("P1");
    }

    @Test @DisplayName("filters by equipmentKeyword matching equipmentRequirements")
    void filtersByEquipmentKeyword() {
        var p1 = project("P1", "PUBLISHED", Instant.now());
        p1.setEquipmentRequirements("需要秒表和计时器");
        var p2 = project("P2", "PUBLISHED", Instant.now());
        p2.setEquipmentRequirements("需要量尺");
        var p3 = project("P3", "PUBLISHED", Instant.now());
        jpa.saveAll(List.of(p1, p2, p3));
        var filter = new PublicProjectListFilter(null, null, null, null, "秒表");
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).name()).isEqualTo("P1");
    }

    @Test @DisplayName("multiple filters combine with AND logic")
    void combinesFiltersWithAnd() {
        var p1 = project("P1", "PUBLISHED", Instant.now());
        p1.setCategory("MATH"); p1.setScoreStorageType("INTEGER"); p1.setVenueRequirements("教室");
        var p2 = project("P2", "PUBLISHED", Instant.now());
        p2.setCategory("MATH"); p2.setScoreStorageType("DECIMAL"); p2.setVenueRequirements("教室");
        jpa.saveAll(List.of(p1, p2));
        var filter = new PublicProjectListFilter(null, "MATH", "INTEGER", "教室", null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).name()).isEqualTo("P1");
    }

    @Test @DisplayName("findPublishedById returns published project")
    void findPublishedByIdReturnsPublished() {
        var e = projectWithDesc("详细项目", "PUBLISHED", "完整描述");
        e.setVenueRequirements("需要操场");
        e.setEquipmentRequirements("需要篮球");
        e.setRulesText("比赛规则...");
        e.setScoreUnit("米");
        e.setDecimalPlaces(2);
        e.setGradeOrder("一年级,二年级,三年级");
        jpa.save(e);

        var result = adapter.findPublishedById(e.getId());
        assertThat(result).isPresent();
        assertThat(result.get().projectId()).isEqualTo(e.getId());
        assertThat(result.get().description()).isEqualTo("完整描述");
        assertThat(result.get().venueRequirements()).isEqualTo("需要操场");
        assertThat(result.get().equipmentRequirements()).isEqualTo("需要篮球");
        assertThat(result.get().rulesText()).isEqualTo("比赛规则...");
        assertThat(result.get().scoreUnit()).isEqualTo("米");
        assertThat(result.get().decimalPlaces()).isEqualTo(2);
        assertThat(result.get().gradeOrder()).isEqualTo("一年级,二年级,三年级");
    }

    @Test @DisplayName("findPublishedById returns empty for DRAFT project")
    void findPublishedByIdReturnsEmptyForDraft() {
        var e = project("DRAFT_PROJ", "DRAFT", Instant.now());
        jpa.save(e);
        var result = adapter.findPublishedById(e.getId());
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("findPublishedById returns empty for ARCHIVED project")
    void findPublishedByIdReturnsEmptyForArchived() {
        var e = project("ARCHIVED_PROJ", "ARCHIVED", Instant.now());
        jpa.save(e);
        var result = adapter.findPublishedById(e.getId());
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("findPublishedById returns empty for non-existent ID")
    void findPublishedByIdReturnsEmptyForNonExistent() {
        var result = adapter.findPublishedById(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("description summary is truncated to 200 chars")
    void descriptionSummaryTruncated() {
        var e = projectWithDesc("P", "PUBLISHED", "A".repeat(300));
        jpa.save(e);
        var result = adapter.findPublished(0, 1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).descriptionSummary()).hasSize(200);
    }

    @Test @DisplayName("list result contains scoreUnit field")
    void listResultContainsScoreUnit() {
        var e = project("P", "PUBLISHED", Instant.now());
        e.setScoreUnit("秒");
        jpa.save(e);
        var result = adapter.findPublished(0, 1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).scoreUnit()).isEqualTo("秒");
    }

    @Test @DisplayName("keyword is case-insensitive")
    void keywordIsCaseInsensitive() {
        jpa.saveAll(List.of(
                projectWithDesc("Math Competition", "PUBLISHED", "A math contest"),
                projectWithDesc("English Test", "PUBLISHED", "An english exam")
        ));
        var filter = new PublicProjectListFilter("MATH", null, null, null, null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).name()).isEqualTo("Math Competition");
    }

    @Test @DisplayName("empty filter returns all published")
    void emptyFilterReturnsAllPublished() {
        jpa.saveAll(List.of(
                project("P1", "PUBLISHED", Instant.now()),
                project("P2", "PUBLISHED", Instant.now()),
                project("P3", "DRAFT", Instant.now())
        ));
        var filter = new PublicProjectListFilter(null, null, null, null, null);
        var result = adapter.findPublished(filter, 0, 10);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    // ── helpers ──

    private ChallengeProjectEntity project(String name, String status, Instant createdAt) {
        var e = new ChallengeProjectEntity();
        e.setId(UUID.randomUUID()); e.setName(name); e.setCategory("MATH");
        e.setScoreStorageType("INTEGER"); e.setScoreIndicatorType("NUMERIC");
        e.setComparisonDirection("HIGHER_BETTER"); e.setEffectiveScoreRule("BEST");
        e.setAllowTie(false); e.setProjectStatus(status);
        e.setCreatedAt(createdAt); e.setUpdatedAt(createdAt);
        return e;
    }

    private ChallengeProjectEntity projectWithDesc(String name, String status, String desc) {
        var e = project(name, status, Instant.now());
        e.setDescription(desc);
        return e;
    }

    private ChallengeProjectEntity projectWithCategory(String name, String status, String category) {
        var e = project(name, status, Instant.now());
        e.setCategory(category);
        return e;
    }

    private ChallengeProjectEntity projectWithStorageType(String name, String status, String storageType) {
        var e = project(name, status, Instant.now());
        e.setScoreStorageType(storageType);
        return e;
    }
}
