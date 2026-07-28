package com.campusguinness.activity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ActivityQueryAdapter adapter;
    @Autowired private ActivityJpaRepository jpa;
    @Autowired private JdbcTemplate jdbc;

    private UUID schoolId;
    private UUID otherSchoolId;
    private UUID userId;

    private final List<UUID> createdActivityIds = new ArrayList<>();
    private final List<UUID> createdSchoolIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        userId = UUID.randomUUID();
        createdSchoolIds.add(schoolId);
        createdSchoolIds.add(otherSchoolId);
        createdUserIds.add(userId);

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "Test School", "USCC", "USCC-"+UUID.randomUUID().toString().substring(0,8),
                "INT-"+UUID.randomUUID().toString().substring(0,8), "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                otherSchoolId, "Other", "USCC", "OTH-"+UUID.randomUUID().toString().substring(0,8),
                "INT-OTH-"+UUID.randomUUID().toString().substring(0,4), "PRIMARY", "Shanghai", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                userId, "user-"+UUID.randomUUID().toString().substring(0,8), "hash", "NORMAL");
    }

    @AfterEach
    void tearDown() {
        for (UUID actId : createdActivityIds) {
            jdbc.update("DELETE FROM activity_projects WHERE activity_id=?", actId);
            jdbc.update("DELETE FROM activities WHERE id=?", actId);
        }
        createdActivityIds.clear();
        for (UUID uid : createdUserIds) {
            jdbc.update("DELETE FROM users WHERE id=?", uid);
        }
        createdUserIds.clear();
        for (UUID sid : createdSchoolIds) {
            jdbc.update("DELETE FROM schools WHERE id=?", sid);
        }
        createdSchoolIds.clear();
    }

    private void trackActivity(UUID id) { createdActivityIds.add(id); }

    @Test @DisplayName("findPublic includes published/in-progress/ended, excludes cancelled/draft")
    void filtersPublicStatuses() {
        var now = Instant.now();
        var published = activity("Published", "PUBLISHED", now); trackActivity(published.getId());
        var ended = activity("Ended", "ENDED", now.minusSeconds(1)); trackActivity(ended.getId());
        var cancelled = activity("Cancelled", "CANCELLED", now); trackActivity(cancelled.getId());
        var draft = activity("Draft", "DRAFT", now); trackActivity(draft.getId());
        jpa.saveAll(List.of(published, ended, cancelled, draft));

        var result = adapter.findPublic(0, 1000, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));
        var ids = result.items().stream().map(a -> a.id()).toList();

        // Our published+ended must appear; cancelled+draft must NOT appear
        assertThat(ids).contains(published.getId(), ended.getId());
        assertThat(ids).doesNotContain(cancelled.getId(), draft.getId());
    }

    @Test @DisplayName("ties on startTime resolved by id DESC")
    void tiesResolvedByIdDesc() {
        var base = Instant.parse("2026-01-01T00:00:00Z");
        var a1 = activity("First", "PUBLISHED", base); a1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var a2 = activity("Second", "PUBLISHED", base); a2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        trackActivity(a1.getId()); trackActivity(a2.getId());
        jpa.saveAll(List.of(a1, a2));
        var result = adapter.findPublic(0, 20, List.of("PUBLISHED","IN_PROGRESS","ENDED"));
        // When tied on startTime, larger id (a2) should come first
        int idx1 = result.items().stream().map(a -> a.id()).toList().indexOf(a1.getId());
        int idx2 = result.items().stream().map(a -> a.id()).toList().indexOf(a2.getId());
        assertThat(idx1).isNotNegative();
        assertThat(idx2).isNotNegative();
        assertThat(idx2).isLessThan(idx1); // a2 before a1
    }

    @Test @DisplayName("beyond last page returns empty for our school-scoped query")
    void beyondLastPageReturnsEmpty() {
        var a = activity("X", "PUBLISHED", Instant.now()); trackActivity(a.getId());
        jpa.save(a);
        var result = adapter.findBySchool(schoolId, null, null, null, 5, 10);
        assertThat(result.items()).isEmpty();
    }

    @Test @DisplayName("findBySchool isolates by schoolId")
    void findBySchoolIsolatesBySchoolId() {
        var mine = activity("Mine", "DRAFT", schoolId, Instant.now()); trackActivity(mine.getId());
        var theirs = activity("Theirs", "DRAFT", otherSchoolId, Instant.now()); trackActivity(theirs.getId());
        jpa.saveAll(List.of(mine, theirs));
        var result = adapter.findBySchool(schoolId, null, null, null, 0, 20);
        assertThat(result.items().stream().map(a -> a.id())).contains(mine.getId());
        assertThat(result.items().stream().map(a -> a.id())).doesNotContain(theirs.getId());
    }

    @Test @DisplayName("findBySchool filters by executionStatus")
    void findBySchoolFiltersByExecutionStatus() {
        var draft = activity("Draft", "DRAFT", Instant.now()); trackActivity(draft.getId());
        var pub = activity("Published", "PUBLISHED", Instant.now()); trackActivity(pub.getId());
        jpa.saveAll(List.of(draft, pub));
        var result = adapter.findBySchool(schoolId, "DRAFT", null, null, 0, 20);
        assertThat(result.items().stream().map(a -> a.id())).contains(draft.getId());
        assertThat(result.items().stream().map(a -> a.id())).doesNotContain(pub.getId());
    }

    @Test @DisplayName("findBySchool filters by publicStatus")
    void findBySchoolFiltersByPublicStatus() {
        var a1 = activity("NotSubmitted", "DRAFT", Instant.now()); a1.setPublicStatus("NOT_SUBMITTED"); trackActivity(a1.getId());
        var a2 = activity("PendingReview", "PUBLISHED", Instant.now()); a2.setPublicStatus("PENDING_PLATFORM_REVIEW"); trackActivity(a2.getId());
        jpa.saveAll(List.of(a1, a2));
        var result = adapter.findBySchool(schoolId, null, "PENDING_PLATFORM_REVIEW", null, 0, 20);
        assertThat(result.items().stream().map(a -> a.id())).contains(a2.getId());
        assertThat(result.items().stream().map(a -> a.id())).doesNotContain(a1.getId());
    }

    @Test @DisplayName("findBySchool keyword case-insensitive")
    void findBySchoolKeywordCaseInsensitive() {
        var a = activity("Mathematics Competition", "DRAFT", Instant.now()); trackActivity(a.getId());
        jpa.save(a);
        var result = adapter.findBySchool(schoolId, null, null, "mathematics", 0, 20);
        assertThat(result.items().stream().map(r -> r.id())).contains(a.getId());
    }

    @Test @DisplayName("findBySchool keyword partial match")
    void findBySchoolKeywordPartialMatch() {
        var a1 = activity("Spring Coding Challenge", "DRAFT", Instant.now()); trackActivity(a1.getId());
        var a2 = activity("Autumn Math", "DRAFT", Instant.now()); trackActivity(a2.getId());
        jpa.saveAll(List.of(a1, a2));
        var result = adapter.findBySchool(schoolId, null, null, "cod", 0, 20);
        assertThat(result.items().stream().map(r -> r.id())).contains(a1.getId());
    }

    @Test @DisplayName("findBySchool empty results returns empty items")
    void findBySchoolEmptyResults() {
        var result = adapter.findBySchool(schoolId, "CANCELLED", null, null, 0, 20);
        assertThat(result.items()).isEmpty();
    }

    @Test @DisplayName("findBySchool pagination returns our activity")
    void findBySchoolPaginates() {
        for (int i = 0; i < 5; i++) {
            var a = activity("A" + i, "DRAFT", schoolId, Instant.now().minusSeconds(i));
            trackActivity(a.getId());
            jpa.save(a);
        }
        var p0 = adapter.findBySchool(schoolId, null, null, null, 0, 2);
        var p1 = adapter.findBySchool(schoolId, null, null, null, 1, 2);
        var p2 = adapter.findBySchool(schoolId, null, null, null, 2, 2);
        // All our 5 IDs should appear across the 3 pages with no overlaps
        var allIds = new java.util.LinkedHashSet<UUID>();
        p0.items().forEach(a -> allIds.add(a.id()));
        p1.items().forEach(a -> allIds.add(a.id()));
        p2.items().forEach(a -> allIds.add(a.id()));
        assertThat(p0.items()).hasSize(2);
        assertThat(p1.items()).hasSize(2);
        assertThat(p2.items()).hasSize(1);
        assertThat(allIds).hasSize(5);
    }

    private ActivityEntity activity(String title, String execStatus, Instant startTime) {
        return activity(title, execStatus, schoolId, startTime);
    }

    private ActivityEntity activity(String title, String execStatus, UUID schoolIdOverride, Instant startTime) {
        var e = new ActivityEntity();
        e.setId(UUID.randomUUID()); e.setSchoolId(schoolIdOverride); e.setCreatedBy(userId);
        e.setTitle(title); e.setExecutionStatus(execStatus); e.setPublicStatus("NOT_SUBMITTED");
        e.setStartTime(startTime); e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }
}
