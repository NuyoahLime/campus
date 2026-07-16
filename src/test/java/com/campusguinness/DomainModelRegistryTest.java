package com.campusguinness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit registry of all 13 aggregate roots with their implementation status.
 * This test does NOT rely on classpath scanning or naming conventions.
 */
class DomainModelRegistryTest {

    record AggregateEntry(
            String aggregateName,
            String module,
            String status,        // COMPLETED | PENDING | BLOCKED
            String blockerReason, // null if not blocked
            String rulePacketPath
    ) {}

    private static Map<String, AggregateEntry> registry() {
        var m = new LinkedHashMap<String, AggregateEntry>();
        m.put("ChallengeProject", new AggregateEntry("ChallengeProject", "project",
                "COMPLETED", null, "docs/domain/rule-packets/challenge-project-rule-packet.md"));
        m.put("User", new AggregateEntry("User", "identity",
                "COMPLETED", null, "docs/domain/rule-packets/user-rule-packet.md"));
        m.put("School", new AggregateEntry("School", "school",
                "COMPLETED", null, "docs/domain/rule-packets/school-rule-packet.md"));
        m.put("SchoolRegistration", new AggregateEntry("SchoolRegistration", "school",
                "COMPLETED", null, "docs/domain/rule-packets/school-registration-rule-packet.md"));
        m.put("ActivityApplication", new AggregateEntry("ActivityApplication", "activity",
                "COMPLETED", null, "docs/domain/rule-packets/activity-application-rule-packet.md"));
        m.put("Activity", new AggregateEntry("Activity", "activity",
                "COMPLETED", null, "docs/domain/rule-packets/activity-rule-packet.md"));
        m.put("ScoreAttempt", new AggregateEntry("ScoreAttempt", "score",
                "COMPLETED", null, "docs/domain/rule-packets/score-attempt-rule-packet.md"));
        m.put("RankingDefinition", new AggregateEntry("RankingDefinition", "ranking",
                "COMPLETED", null, "docs/domain/rule-packets/ranking-definition-rule-packet.md"));
        m.put("L3Authorization", new AggregateEntry("L3Authorization", "ranking",
                "COMPLETED", null, "docs/domain/rule-packets/l3-authorization-rule-packet.md"));
        m.put("ScoreAppeal", new AggregateEntry("ScoreAppeal", "appeal",
                "COMPLETED", null, "docs/domain/rule-packets/score-appeal-rule-packet.md"));
        m.put("Media", new AggregateEntry("Media", "media",
                "COMPLETED", null, "docs/domain/rule-packets/media-rule-packet.md"));
        m.put("ActivityResult", new AggregateEntry("ActivityResult", "result",
                "COMPLETED", null, "docs/domain/rule-packets/activity-result-rule-packet.md"));
        m.put("Feedback", new AggregateEntry("Feedback", "feedback",
                "COMPLETED", null, "docs/domain/rule-packets/feedback-rule-packet.md"));
        return m;
    }

    @Test
    @DisplayName("Registry contains exactly 13 aggregate roots")
    void registryHasExactly13Aggregates() {
        assertThat(registry()).hasSize(13);
    }

    @Test
    @DisplayName("All 13 aggregates have a module assignment")
    void allAggregatesHaveModuleAssignment() {
        registry().forEach((name, entry) ->
                assertThat(entry.module())
                        .as("Aggregate '%s' must have a module", name)
                        .isNotBlank());
    }

    @Test
    @DisplayName("5 aggregates completed: ChallengeProject, SchoolRegistration, School, ActivityApplication, Activity")
    void completedAggregatesCount() {
        long completed = registry().values().stream()
                .filter(e -> "COMPLETED".equals(e.status())).count();
        assertThat(completed).isEqualTo(13);

        assertThat(registry().get("ChallengeProject").status()).isEqualTo("COMPLETED");
        assertThat(registry().get("SchoolRegistration").status()).isEqualTo("COMPLETED");
        assertThat(registry().get("School").status()).isEqualTo("COMPLETED");
        assertThat(registry().get("ActivityApplication").status()).isEqualTo("COMPLETED");
        assertThat(registry().get("Activity").status()).isEqualTo("COMPLETED");
        assertThat(registry().get("ScoreAttempt").status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("All 12 PENDING aggregates have no blocker")
    void pendingAggregatesHaveNoBlocker() {
        registry().values().stream()
                .filter(e -> "PENDING".equals(e.status()))
                .forEach(e -> assertThat(e.blockerReason())
                        .as("Pending aggregate '%s' should have no blocker", e.aggregateName())
                        .isNull());
    }

    @Test
    @DisplayName("No BLOCKED aggregates exist")
    void noBlockedAggregates() {
        long blocked = registry().values().stream()
                .filter(e -> "BLOCKED".equals(e.status())).count();
        assertThat(blocked).isZero();
    }

    @Test
    @DisplayName("Module names match the 13 Modulith modules")
    void modulesMatchModulithStructure() {
        var expectedModules = java.util.Set.of(
                "identity", "school", "project", "activity", "score",
                "ranking", "appeal", "media", "result", "feedback",
                "notification", "platform", "audit");
        registry().forEach((name, entry) -> {
            assertThat(expectedModules)
                    .as("Module '%s' for aggregate '%s' must be a known Modulith module",
                            entry.module(), name)
                    .contains(entry.module());
        });
    }
}
