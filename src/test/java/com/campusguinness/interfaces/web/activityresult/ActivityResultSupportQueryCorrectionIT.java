package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.result.application.query.ActivityResultGovernanceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/** PostgreSQL evidence for every support-query acceptance case. */
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class ActivityResultSupportQueryCorrectionIT extends ActivityResultReadTestSupport {
    private static final String GOVERNANCE_LIST = "/api/v1/super-admin/activity-results/governance";
    private static final String GOVERNANCE_DETAIL = "/api/v1/super-admin/activity-results/{id}/governance";
    private static final String REVIEW_LIST = "/api/v1/super-admin/activity-results/public-reviews";
    private static final String REVIEW_DETAIL = "/api/v1/super-admin/activity-results/{id}/public-review";
    @Autowired private ActivityResultGovernanceQueryService governanceQueryService;

    @Test
    void gq01NeverPublicIsExcludedAndGovernanceDetailIsNotFound() throws Exception {
        Fixture fixture = insertResult(schoolA, "Never public", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 1, 1, null);

        mvc.perform(get(GOVERNANCE_LIST).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].resultId", not(hasItem(fixture.resultId().toString()))));
        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void gq03ReplacementNotSubmittedKeepsOldPublicPointer() throws Exception {
        Fixture fixture = insertResult(schoolA, "Replacement not submitted", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 2, 1, 1);

        governanceContainsExactPublicVersion(fixture, "NOT_SUBMITTED", false);
    }

    @Test
    void gq04ReplacementPendingReviewKeepsOldPublicPointer() throws Exception {
        Fixture fixture = insertResult(schoolA, "Replacement pending", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PENDING_PUBLIC_REVIEW", false, 2, 2, 1);

        governanceContainsExactPublicVersion(fixture, "PENDING_PUBLIC_REVIEW", false);
    }

    @Test
    void gq05AndGq09TakedownRemainsDiscoverableAfterReload() throws Exception {
        Fixture fixture = insertResult(schoolA, "Takedown reload", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/takedown", fixture.resultId())
                        .with(superAdmin()).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"safety\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(GOVERNANCE_LIST).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].resultId", hasItem(fixture.resultId().toString())))
                .andExpect(jsonPath("$.items[0].publicStatus").value("PLATFORM_TAKEDOWN"))
                .andExpect(jsonPath("$.items[0].publicVisibilityBlocked").value(true));
        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublicVersionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.currentPublicVersion.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.publicVisibilityBlocked").value(true));
    }

    @Test
    void gq06AndGq10ResetRemainsBlockedAndReadableAfterReload() throws Exception {
        Fixture fixture = insertResult(schoolA, "Reset reload", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        takedown(fixture.resultId());

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reset-takedown", fixture.resultId())
                        .with(superAdmin()).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"reviewed\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(GOVERNANCE_LIST).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].resultId", hasItem(fixture.resultId().toString())))
                .andExpect(jsonPath("$.items[0].publicStatus").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.items[0].publicVisibilityBlocked").value(true));
        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCandidateVersionId").doesNotExist())
                .andExpect(jsonPath("$.currentPublicVersionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.currentPublicVersion.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.publicVisibilityBlocked").value(true));
    }

    @Test
    void gq07CancelledPublicResultRemainsInGovernanceScope() throws Exception {
        Fixture fixture = insertResult(schoolA, "Cancelled public", "CANCELLED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);

        mvc.perform(get(GOVERNANCE_LIST).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].resultId", hasItem(fixture.resultId().toString())));
        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityExecutionStatus").value("CANCELLED"));
    }

    @Test
    void gq11GovernanceHistoryExcludesReviewNoise() throws Exception {
        Fixture fixture = insertResult(schoolA, "History noise", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        Instant at = Instant.parse("2026-09-01T00:00:00Z");
        appendReview(fixture, "SUBMITTED", fixture.v1(), null, at);
        appendReview(fixture, "APPROVED", fixture.v1(), null, at.plusSeconds(1));
        appendReview(fixture, "TAKEDOWN", fixture.v1(), "close", at.plusSeconds(2));
        appendReview(fixture, "RESET", fixture.v1(), "reopen", at.plusSeconds(3));

        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk());
        authenticateSuperAdminForQueryService();
        assertThat(governanceQueryService.detail(fixture.resultId()).governanceHistory())
                .extracting(entry -> entry.action())
                .containsExactly("TAKEDOWN", "RESET");
    }

    @Test
    void gq12GovernanceHistorySpansAllResultVersions() throws Exception {
        Fixture fixture = insertResult(schoolA, "History versions", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 2, 2);
        Instant at = Instant.parse("2026-09-02T00:00:00Z");
        appendReview(fixture, "TAKEDOWN", fixture.v1(), "old public", at);
        appendReview(fixture, "RESET", fixture.v1(), "old public reset", at.plusSeconds(1));

        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublicVersionId").value(fixture.v2().toString()));
        authenticateSuperAdminForQueryService();
        assertThat(governanceQueryService.detail(fixture.resultId()).governanceHistory())
                .extracting(entry -> entry.resultVersionId())
                .containsExactly(fixture.v1(), fixture.v1());
    }

    @Test
    @Transactional
    void gq13BrokenPublicPointerReturnsControlled409() throws Exception {
        Fixture fixture = insertResult(schoolA, "Broken governance pointer", "PUBLISHED",
                "INTERNAL_PUBLISHED", "PUBLIC", false, null, 1, 1);
        jdbc.execute("SET LOCAL session_replication_role = replica");
        jdbc.update("UPDATE activity_results SET current_public_version_id = ? WHERE id = ?",
                UUID.randomUUID(), fixture.resultId());
        jdbc.execute("SET LOCAL session_replication_role = origin");

        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_RESULT_DATA_CONSISTENCY"));
    }

    @Test
    void gq14GovernanceFiltersNormalizeAndRejectInvalidStatus() throws Exception {
        Fixture publicResult = insertResult(schoolA, "Activity filter match", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        Fixture blockedResult = insertResult(schoolA, "Takedown filter match", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_TAKEDOWN", true, null, 1, 1);
        Fixture otherSchool = insertResult(schoolB, "Other activity", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);

        mvc.perform(get(GOVERNANCE_LIST).param("publicStatus", " PLATFORM_TAKEDOWN ").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].resultId").value(blockedResult.resultId().toString()));
        mvc.perform(get(GOVERNANCE_LIST).param("blocked", "true").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(GOVERNANCE_LIST).param("blocked", "false").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].resultId", hasItem(publicResult.resultId().toString())))
                .andExpect(jsonPath("$.items[*].resultId", hasItem(otherSchool.resultId().toString())))
                .andExpect(jsonPath("$.items[*].resultId", not(hasItem(blockedResult.resultId().toString()))));
        mvc.perform(get(GOVERNANCE_LIST).param("q", "  ACTIVITY FILTER MATCH  ").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].resultId").value(publicResult.resultId().toString()));
        mvc.perform(get(GOVERNANCE_LIST).param("q", "V1").with(superAdmin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(GOVERNANCE_LIST).param("q", prefix + "-a").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(GOVERNANCE_LIST).param("publicStatus", "NOT_A_STATUS").with(superAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gq15GovernanceOrderingUsesUpdatedAtThenResultIdAndPagination() throws Exception {
        Fixture first = insertResult(schoolA, "Order first", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        Fixture second = insertResult(schoolA, "Order second", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        Fixture third = insertResult(schoolA, "Order third", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        setUpdatedAt(first.resultId(), Instant.parse("2026-01-01T00:00:00Z"));
        setUpdatedAt(second.resultId(), Instant.parse("2026-01-03T00:00:00Z"));
        setUpdatedAt(third.resultId(), Instant.parse("2026-01-02T00:00:00Z"));

        mvc.perform(get(GOVERNANCE_LIST).param("page", "0").param("size", "1").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(second.resultId().toString()));
        mvc.perform(get(GOVERNANCE_LIST).param("page", "1").param("size", "1").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(third.resultId().toString()));

        Instant tie = Instant.parse("2026-01-04T00:00:00Z");
        setUpdatedAt(first.resultId(), tie);
        setUpdatedAt(second.resultId(), tie);
        List<UUID> expected = jdbc.queryForList("""
                SELECT id FROM activity_results
                WHERE id IN (?, ?, ?)
                ORDER BY updated_at DESC, id DESC
                LIMIT 2
                """, UUID.class, first.resultId(), second.resultId(), third.resultId());
        mvc.perform(get(GOVERNANCE_LIST).param("size", "2").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(expected.get(0).toString()))
                .andExpect(jsonPath("$.items[1].resultId").value(expected.get(1).toString()));
    }

    @Test
    void gq17GovernanceReadsDoNotCreateOrMutateRows() throws Exception {
        Fixture fixture = insertResult(schoolA, "Read purity", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        int resultsBefore = count("activity_results", fixture.resultId());
        int versionsBefore = count("result_versions", fixture.resultId());
        int reviewsBefore = count("result_review_records", fixture.resultId());
        int formatBefore = count("result_format_edit_records", fixture.resultId());
        Instant updatedBefore = jdbc.queryForObject(
                "SELECT updated_at FROM activity_results WHERE id = ?", Timestamp.class, fixture.resultId()).toInstant();

        mvc.perform(get(GOVERNANCE_LIST).with(superAdmin())).andExpect(status().isOk());
        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin())).andExpect(status().isOk());

        assertThat(count("activity_results", fixture.resultId())).isEqualTo(resultsBefore);
        assertThat(count("result_versions", fixture.resultId())).isEqualTo(versionsBefore);
        assertThat(count("result_review_records", fixture.resultId())).isEqualTo(reviewsBefore);
        assertThat(count("result_format_edit_records", fixture.resultId())).isEqualTo(formatBefore);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM activity_results WHERE id = ?", Timestamp.class,
                fixture.resultId()).toInstant()).isEqualTo(updatedBefore);
    }

    @Test
    void rq05Rq06Rq07PendingReviewReadsAllExecutionStatuses() throws Exception {
        for (String executionStatus : List.of("PUBLISHED", "IN_PROGRESS", "ENDED")) {
            Fixture fixture = insertResult(schoolA, "Pending " + executionStatus, executionStatus,
                    "INTERNAL_PUBLISHED", "PENDING_PUBLIC_REVIEW", false, 1, 1, null);
            appendReview(fixture, "SUBMITTED", fixture.v1(), null, Instant.parse("2026-02-01T00:00:00Z"));

            mvc.perform(get(REVIEW_LIST).with(superAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].resultId", hasItem(fixture.resultId().toString())))
                    .andExpect(jsonPath("$.items[*].activityExecutionStatus", hasItem(executionStatus)));
            mvc.perform(get(REVIEW_DETAIL, fixture.resultId()).with(superAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activityExecutionStatus").value(executionStatus))
                    .andExpect(jsonPath("$.candidateVersionId").value(fixture.v1().toString()));
        }
    }

    @Test
    void rq09CancelledPendingReviewDetailRemainsReadable() throws Exception {
        Fixture fixture = insertResult(schoolA, "Cancelled pending detail", "CANCELLED",
                "INTERNAL_PUBLISHED", "PENDING_PUBLIC_REVIEW", false, 1, 1, null);
        appendReview(fixture, "SUBMITTED", fixture.v1(), null, Instant.parse("2026-02-02T00:00:00Z"));

        mvc.perform(get(REVIEW_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityExecutionStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.candidateVersionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.history[0].action").value("SUBMITTED"));
    }

    @Test
    void rq11CancelledApproveIsDeniedWithoutStateOrHistoryChange() throws Exception {
        Fixture fixture = pendingCancelled("Cancelled approve");

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/approve-public-review", fixture.resultId())
                        .with(superAdmin()).with(csrf()))
                .andExpect(status().isConflict());
        assertPendingUnchanged(fixture, "APPROVED");
    }

    @Test
    void rq12CancelledRejectIsDeniedWithoutStateOrHistoryChange() throws Exception {
        Fixture fixture = pendingCancelled("Cancelled reject");

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reject-public-review", fixture.resultId())
                        .with(superAdmin()).with(csrf()).contentType("application/json")
                        .content("{\"reason\":\"not allowed\"}"))
                .andExpect(status().isConflict());
        assertPendingUnchanged(fixture, "REJECTED");
    }

    @Test
    void rq13PendingOrderingUsesSubmittedAtThenResultId() throws Exception {
        Fixture early = pending("Early", Instant.parse("2026-03-01T00:00:00Z"));
        Fixture late = pending("Late", Instant.parse("2026-03-03T00:00:00Z"));
        Fixture tie = pending("Tie", Instant.parse("2026-03-03T00:00:00Z"));
        UUID tieFirst = jdbc.queryForObject("""
                SELECT result_id
                FROM result_review_records
                WHERE action = 'SUBMITTED' AND result_id IN (?, ?, ?)
                ORDER BY submitted_at ASC, result_id ASC
                OFFSET 1 LIMIT 1
                """, UUID.class, early.resultId(), late.resultId(), tie.resultId());

        mvc.perform(get(REVIEW_LIST).param("page", "0").param("size", "1").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(early.resultId().toString()));
        mvc.perform(get(REVIEW_LIST).param("page", "1").param("size", "1").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(tieFirst.toString()));
    }

    private void governanceContainsExactPublicVersion(Fixture fixture, String publicStatus, boolean blocked)
            throws Exception {
        mvc.perform(get(GOVERNANCE_LIST).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].resultId", hasItem(fixture.resultId().toString())))
                .andExpect(jsonPath("$.items[0].publicStatus").value(publicStatus));
        mvc.perform(get(GOVERNANCE_DETAIL, fixture.resultId()).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublicVersionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.currentPublicVersion.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.publicVisibilityBlocked").value(blocked));
    }

    private void takedown(UUID resultId) throws Exception {
        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/takedown", resultId)
                        .with(superAdmin()).with(csrf()).contentType("application/json")
                        .content("{\"reason\":\"safety\"}"))
                .andExpect(status().isOk());
    }

    private Fixture pendingCancelled(String title) {
        Fixture fixture = insertResult(schoolA, title, "CANCELLED", "INTERNAL_PUBLISHED",
                "PENDING_PUBLIC_REVIEW", false, 1, 1, null);
        appendReview(fixture, "SUBMITTED", fixture.v1(), null, Instant.parse("2026-03-04T00:00:00Z"));
        return fixture;
    }

    private Fixture pending(String title, Instant submittedAt) {
        Fixture fixture = insertResult(schoolA, title, "PUBLISHED", "INTERNAL_PUBLISHED",
                "PENDING_PUBLIC_REVIEW", false, 1, 1, null);
        appendReview(fixture, "SUBMITTED", fixture.v1(), null, submittedAt);
        return fixture;
    }

    private void assertPendingUnchanged(Fixture fixture, String forbiddenAction) {
        assertThat(jdbc.queryForObject("SELECT result_public_status FROM activity_results WHERE id = ?",
                String.class, fixture.resultId())).isEqualTo("PENDING_PUBLIC_REVIEW");
        assertThat(jdbc.queryForObject("SELECT current_candidate_version_id FROM activity_results WHERE id = ?",
                UUID.class, fixture.resultId())).isEqualTo(fixture.v1());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM result_review_records WHERE result_id = ? AND action = ?",
                Integer.class, fixture.resultId(), forbiddenAction)).isZero();
    }

    private int count(String table, UUID resultId) {
        String sql = "activity_results".equals(table)
                ? "SELECT count(*) FROM activity_results WHERE id = ?"
                : "SELECT count(*) FROM " + table + " WHERE result_id = ?";
        return jdbc.queryForObject(sql, Integer.class, resultId);
    }

    private void authenticateSuperAdminForQueryService() {
        var details = new CampusGuinnessUserDetails(
                superAdmin, prefix + "-direct-query", "{noop}password", "NORMAL",
                java.util.Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }
}
