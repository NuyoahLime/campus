# ADR-015: L2 Ranking Policy Baseline

> Status: Accepted
> Date: 2026-09-02

## Context

L1 Ranking is closed for generation, publication, and management frontend.
Before L2 implementation, the product policy must close several gaps that would
otherwise let L2 redefine Stage26 effective-score semantics or expose rankings
outside their intended audience.

The key open questions were:

- Whether L2 accumulates or selects among multiple activity scores.
- Whether L2 reselects effective ScoreAttempts.
- Whether different RuleVersions can be merged automatically.
- Which dimension filters are supported by authoritative data.
- Which time field drives activity-period filtering.
- Whether L2 internal display follows L3 public masking.
- Whether L2 is merely an expansion of L1.

## Decision

L2 is a same-school ranking bound to one ChallengeProject. It is not L1 with
multiple activities exposed as an implementation detail.

L2 consumes only Stage26 authoritative effective scores:

```text
score_status = APPROVED
AND is_current_effective = true
```

L2 does not reselect effective ScoreAttempts. BEST_SCORE in L2 means selecting
one representative current-effective score per student across ActivityProjects
under the same ChallengeProject. It does not replace Score module effective
score selection.

L2 v1 uses the safe RuleVersion subset:

```text
SAME_RULE_VERSION_ONLY
```

Different RuleVersions are not automatically merged, even if a compatibility
table exists. SUPER_ADMIN-approved compatibility is a future extension.

L2 v1 supports grade, class, and activity-period filters. Gender is not
supported because there is no current authoritative schema source.

L2 activity-period filtering uses the execution period of the activity attached
to the ActivityProject. ScoreAttempt audit timestamps are not valid for this
filter.

L2 internal same-school views display the RankingEntry student_display_name
snapshot. L3 public views remain masked or authorization-controlled.

L2 definitions must be unique per school and ChallengeProject. Current schema
support is representational but not fully enforcing; implementation must add the
database invariant before enabling L2 creation.

## Consequences

L2 generation needs a candidate reducer before the existing ranking calculator.
The calculator remains responsible only for ranking positions.

The L2 implementation phase must include tests for:

- Stage26 current-effective-only candidate input.
- Cross-activity BEST_SCORE candidate reduction.
- Same RuleVersion only.
- Grade/class/activity-period filters.
- Anonymous denial for L2.
- Same-school Student and School Admin visibility.
- L2 internal display name snapshots.
- L2 definition uniqueness.

No L2 production implementation is introduced by this ADR.
