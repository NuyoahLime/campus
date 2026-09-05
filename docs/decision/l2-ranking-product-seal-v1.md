# L2 Ranking Product Seal v1

> Baseline: `508f1205608a1675a4e2ebec944ebe703ec892c9`
> Scope: final product seal for completed L2 ranking policy, generation,
> publication, management frontend, and E2E evidence.

## 1. Seal Status

| Area | Status |
| --- | --- |
| Phase4A L2 Policy | CLOSED |
| Phase4B L2 Generation | CLOSED |
| Phase4C L2 Publication | CLOSED |
| Phase4D L2 Management Frontend | CLOSED |
| L2 Product Seal | READY_FOR_FINAL_ACCEPTANCE |

## 2. L2 Production Capability

Current master supports the complete L2 same-school ranking flow:

1. `RankingDefinition` with `layer = L2`.
2. One L2 definition per `school_id` + ChallengeProject.
3. L2 generation from Stage26 authoritative effective scores.
4. Cross-activity candidate selection by `BEST_SCORE`.
5. Same-RuleVersion enforcement.
6. Grade, class, and activity-period filters.
7. Immutable generated `RankingVersion` preview.
8. L2 publication.
9. Same-school student and school-admin read.
10. L1/L2 management at `/school-admin/ranking-management`.

## 3. Effective Score Source

L2 generation consumes only authoritative Stage26 effective scores:

```text
ScoreAttempt.score_status = APPROVED
AND
ScoreAttempt.is_current_effective = true
```

Ranking does not reselect effective scores. It does not choose by latest
attempt, attempt number, administrative designation, or local best/last
effective-score policy. Score lifecycle and correction semantics remain owned
by the Score module.

## 4. L2 Selection And Rule Version Policy

L2 v1 uses:

```text
L2_CROSS_ACTIVITY_SELECTION_POLICY = BEST_SCORE
L2_RULE_VERSION_POLICY = SAME_RULE_VERSION_ONLY
```

The L2 candidate reducer runs after Stage26 effective-score filtering. For one
student with eligible scores across multiple ActivityProjects, the reducer keeps
the best business value according to the frozen RuleVersion comparator and
retains:

```text
selected ScoreAttemptId
selected ActivityProjectId
selected RuleVersionId
```

Those identifiers are persisted through `ranking_entry_score_sources` for
snapshot traceability.

## 5. Filters

Supported L2 filters:

```text
GRADE
CLASS
ACTIVITY_PERIOD
```

Activity-period filtering uses the authoritative activity execution period:

```text
activities.start_time
activities.end_time
```

The backend rejects reverse ranges before persisting a definition:

```text
activityPeriodStart > activityPeriodEnd -> reject
```

Open-ended and equal ranges remain valid unless a later frozen product decision
changes that rule.

## 6. Visibility Seal

| Access | L2 visibility |
| --- | --- |
| Anonymous public list/detail | DENY |
| Same-school student list/detail | ALLOW |
| Same-school school-admin list/detail | ALLOW |
| Other-school student/admin | DENY |
| Super Admin production-view override | NOT_IMPLEMENTED |

L2 is a same-school internal ranking. It does not become publicly visible after
publication.

## 7. Lifecycle Seal

The sealed L2 lifecycle is:

```text
RankingDefinition
  -> Generate
  -> GENERATED immutable RankingVersion
  -> Preview
  -> Publish
  -> PUBLISHED
```

When a new generated version is published:

```text
previous current PUBLISHED -> REPLACED
current_version_id -> target published version
published_at -> server generated timestamp
```

Generated versions remain invisible to public/student read surfaces until
publication. L2 public visibility remains denied even after publication.

## 8. Snapshot Seal

`RankingEntry` rows and `ranking_entry_score_sources` rows are frozen generated
snapshots. Publication changes version state and current-version pointers only.
Publication must not:

```text
recalculate ranking
reselect effective scores
rewrite RankingEntry
rewrite ranking_entry_score_sources
```

## 9. Guardrails

Duplicate L2 definition guard:

```text
same school + same ChallengeProject + layer L2
  -> database uniqueness violation
  -> HTTP 409
  -> L2_RANKING_DEFINITION_ALREADY_EXISTS
```

The physical database invariant remains:

```text
uq_ranking_def_l2_school_project
UNIQUE (school_id, project_id) WHERE layer = 'L2'
```

## 10. Management Frontend Seal

`/school-admin/ranking-management` supports L1 and L2. The sealed L2 product
flow is:

```text
Create
-> ChallengeProject
-> Dimension Filters
-> Generate
-> Preview
-> Reload
-> Publish
-> Same-school Read
-> Reload
-> Disable
```

The management page keeps core ranking-definition data isolated from auxiliary
ChallengeProject/Activity loading failures, so existing definitions remain
viewable when auxiliary create-form data fails to load.

## 11. Explicitly Out Of Scope

The L2 product seal does not implement or change:

```text
L3 ranking
L3 management frontend
withdraw / unpublish
Score lifecycle
Effective Score semantics
Correction
Appeal
ActivityResult
Media
Notification
Teacher runtime
```

## 12. Next Stage

```text
RANKING_PRODUCTION_L2_PRODUCT_SEAL_PR_FINAL_ACCEPTANCE
```

After the seal PR is accepted, the next product implementation track is:

```text
L3 policy / authorization closure
-> L3 generation
-> L3 publication
-> L3 management frontend
-> ActivityResult closure
-> Production readiness
-> Full real-data E2E / UI closure
```
