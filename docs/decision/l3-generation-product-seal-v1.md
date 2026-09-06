# L3 Generation Product Seal v1

> Baseline: `a6244fd91c169c1607ad23fc814f836aa3ed4d1a`
> Scope: final product seal for completed L3 ranking generation after PR #58
> merge and post-merge exact-SHA acceptance.

## 1. Seal Status

| Area | Status |
| --- | --- |
| L3 Generation | COMPLETE_VERTICAL_SLICE |
| L3 Generation Product Seal | READY_FOR_FINAL_ACCEPTANCE |

`PR #58 = MERGED` and the merged master baseline is
`a6244fd91c169c1607ad23fc814f836aa3ed4d1a`.

## 2. Product Scope

L3 generation is a complete platform-wide ranking generation slice:

1. `SUPER_ADMIN` operates the L3 generation flow.
2. `SCHOOL_ADMIN` contributes usable authorization data only.
3. Generation consumes approved authorizations from schools whose status is
   `NORMAL`.
4. Generation reads the sealed scope contract:
   `activityIds`, `activityPeriodStart`, `activityPeriodEnd`, `grades`,
   `classNames`.
5. Generation consumes only authoritative effective scores.
6. Cross-activity selection uses `BEST_SCORE`.
7. The reducer retains selected `ScoreAttemptId`, `ActivityProjectId`, and
   `RuleVersionId` for traceability.
8. Generated output is an immutable snapshot.

## 3. Ownership And Authorization

`SUPER_ADMIN` is the ranking operator for L3 generation.
`SCHOOL_ADMIN` remains the data contributor through L3 authorization only.
The generation flow does not reuse L1/L2 management semantics.

## 4. Input Contract

L3 generation must fail closed on scope and authorization input:

```text
authorization.status = APPROVED
school.status = NORMAL
```

The sealed scope keys are:

```text
activityIds
activityPeriodStart
activityPeriodEnd
grades
classNames
```

Unknown scope keys are rejected. Reverse activity-period ranges are rejected
before persistence.

## 5. Score And Selection Rules

The sealed score source is:

```text
ScoreAttempt.score_status = APPROVED
AND
ScoreAttempt.is_current_effective = true
```

L3 generation does not reinterpret score lifecycle, correction, or effective
score semantics. Same-RuleVersion generation remains enforced.

## 6. Privacy And Snapshot Rules

L3 generation preserves sealed privacy rules:

- school identity is masked or shown only according to authorization
- student identity is opaque or masked
- raw legal names are not published

Generated `RankingVersion`, `RankingEntry`, and
`ranking_entry_score_sources` rows are immutable snapshots. Publication is
separate and must not recalculate ranking.

## 7. Verification Evidence

Post-merge acceptance on master passed:

```text
Backend CI = PASS
Surefire = 1014
Failsafe = 292
Total backend tests = 1306
Failures = 0
Errors = 0
Skipped = 0

Stage26 Full E2E = PASS
Total = 16
Passed = 16
Failed = 0
Skipped = 0
```

The generation API persists the selected immutable snapshot and opaque public
identity.

## 8. Capability Inventory Sync

This seal is reflected in
`docs/decision/current-business-capability-inventory-v2.md`:

- `L3 Generation = COMPLETE_VERTICAL_SLICE`
- `L3 Publication = NOT_IMPLEMENTED`
- `L3 Management Frontend = NOT_IMPLEMENTED`

The remaining roadmap starts with L3 Publication.

## 9. Explicitly Out Of Scope

This seal does not implement:

- L3 publication
- L3 management frontend
- ActivityResult
- Media
- Notification

## 10. Next Stage

```text
RANKING_PRODUCTION_L3_GENERATION_PRODUCT_SEAL_PR_FINAL_ACCEPTANCE
```
