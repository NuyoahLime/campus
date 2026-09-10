# L3 Ranking Management Frontend Product Seal v1

> Baseline: `4cd8780bcf83509a184f9752fd71ed9833b7e50b`
> Scope: durable product seal for the completed L3 ranking management
> frontend after PR #64 implementation, merge, and post-merge exact-SHA
> acceptance.

## 1. Seal Status

| Area | Status |
| --- | --- |
| L3 Ranking Management Frontend | COMPLETE_VERTICAL_SLICE |
| L3 Ranking Management Frontend Product Seal | READY_FOR_FINAL_ACCEPTANCE |

The implementation is accepted on master. This Product Seal remains
`READY_FOR_FINAL_ACCEPTANCE` until this documentation PR completes independent
review, exact-head CI, merge, post-merge verification, and state sync.

## 2. Product Scope

SuperAdmin has the complete platform L3 ranking management flow:

1. L3 `RankingDefinition` list.
2. L3 `RankingDefinition` detail.
3. L3 `RankingDefinition` create.
4. ChallengeProject selector.
5. RuleVersion selector.
6. Generate.
7. Generated immutable snapshot preview.
8. Publish.
9. Published ranking read integration.
10. Disable.
11. Enable.
12. Page reload state restoration.

The SuperAdmin frontend route is:

```text
/super-admin/ranking-management
```

SuperAdmin navigation is integrated with this route.

## 3. Authorization Boundary

```text
SUPER_ADMIN
  -> may manage platform L3 ranking definitions

SCHOOL_ADMIN
  -> may not use L3 management APIs

STUDENT
  -> may not use L3 management APIs
```

L3 management queries and commands accept only:

```text
layer = L3
AND
school_id IS NULL
```

The management path must not return or operate on L1, L2, or school-scoped
corrupt L3 definitions.

SuperAdmin L3 management authority does not create a SuperAdmin override for
L1/L2 school ranking production management.

## 4. SchoolAdmin Fail-Closed Seal

The generic SchoolAdmin RankingDefinition enable/disable contract remains:

```text
same-school L1/L2 -> allowed according to the existing contract
L3              -> DENY
schoolId == null -> DENY
```

SchoolAdmin cannot mutate a platform L3 definition even when its UUID is known.
This is enforced by the Application Service boundary, not by hiding controls in
the frontend.

## 5. Optimistic Lock Seal

`RankingDefinition.version` round-trips correctly:

```text
Persistence Entity -> Domain -> Persistence Entity
```

The accepted implementation therefore supports:

```text
Disable
  -> persisted version advances
  -> reload
  -> Enable
```

The sequence does not produce a false optimistic-lock failure caused by a
discarded domain version. `L3RankingManagementApplicationServiceIT` covers the
continuous Disable -> Enable sequence.

## 6. Error UX Seal

The structured backend error:

```text
L3_RANKING_NO_USABLE_AUTHORIZATION
```

is mapped to an explicit administrator-facing message:

```text
No approved usable L3 authorization currently matches this project and RuleVersion.
```

This error is distinct from a generic `409` conflict:

```text
generic 409 -> state changed / refresh guidance
400         -> safe business or invalid-request message
```

The frontend explains existing backend errors. It does not reinterpret or
redefine L3 generation rules.

## 7. Generation And Publication Boundary

The management frontend consumes the already sealed L3 Generation and
Publication capabilities. It does not:

- recalculate rankings
- select `ScoreAttempt` rows
- change `BEST_SCORE`
- change the effective-score contract
- change authorization matching
- rebuild privacy identity
- modify immutable snapshots
- change publication replacement semantics

Generation and publication remain independently owned business capabilities.

## 8. Verification Evidence

The accepted implementation is:

```text
Implementation PR = #64

Accepted feature head =
982da0526dec6ecb23276154ab8c654c3a39247a

Implementation merge/master =
4cd8780bcf83509a184f9752fd71ed9833b7e50b
```

Post-merge Backend CI:

```text
Run ID = 34433358901
Head SHA = 4cd8780bcf83509a184f9752fd71ed9833b7e50b
Event = push
Branch = master
Conclusion = success
Run attempt = 2

Surefire = 1020
Failsafe = 296
Total backend tests = 1316
Failures = 0
Errors = 0
Skipped = 0

L3RankingManagementApplicationServiceIT = 3 / 3 PASS
```

Post-merge Stage26 Full E2E:

```text
Run ID = 34433359104
Head SHA = 4cd8780bcf83509a184f9752fd71ed9833b7e50b
Event = push
Branch = master
Conclusion = success

Total = 21
Passed = 21
Failed = 0
Skipped = 0
Product retries = 0
```

Key product evidence:

```text
L3 authorization frontend = PASS
L3 generation = PASS
L3 management structured-error mapping = PASS
L3 no-usable-authorization UX = PASS
L3 management create/generate/publish/reload/disable/enable = PASS
L3 management Student/SchoolAdmin denial = PASS
L3 publication = PASS
L1 management regression = PASS
L2 management regression = PASS
Stage26 lifecycle regression = PASS
```

The first post-merge Backend attempt failed during external Maven dependency
resolution before product verification. The same exact master SHA was rerun
without code, document, workflow, or migration changes and passed on attempt 2.
This infrastructure rerun is not a product retry.

## 9. Explicit No-Migration / No-Rewrite

```text
Production code changed = NO
Test code changed = NO
Frontend code changed = NO
Workflow changed = NO
Migration = NONE
```

This seal records the accepted implementation. It does not change L1/L2
ranking semantics, L3 authorization, L3 generation, L3 publication, privacy,
BEST_SCORE, published snapshot, or public ranking read behavior.

## 10. Explicitly Out Of Scope

This Product Seal does not implement:

```text
ActivityResult
Audit / Platform Ops
Media
Notification
ActivityApplication
Student profile management
SchoolAdmin student management
Password recovery UI
Task/Outbox worker
Production Readiness
Real-data E2E closure
```

## 11. Deferred Minors

Project-level deferred minors remain:

1. Activity scope selector currently requires manual UUID entry.
2. Surefire/Failsafe URLClassPath compatibility flag lacks maintenance
   comment.

These are not Product Seal PR-specific minors. The validator baseline remains
10 warnings; warnings are not renamed as minors. CI action deprecation notices
remain Production Readiness debt and are not changed here.

## 12. Next Stage

After this documentation PR is independently reviewed, merged, and
post-merge verified:

```text
RANKING_PRODUCTION_L3_RANKING_MANAGEMENT_FRONTEND_PRODUCT_SEAL_REVIEW
```

The Product Seal must not be marked `CLOSED` by this document alone.
