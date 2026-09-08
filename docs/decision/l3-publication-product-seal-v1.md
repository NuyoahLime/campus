# L3 Publication Product Seal v1

> Baseline: `df67cf7aa1dae81e9af737aca60354e745206558`
> Scope: final product seal for completed L3 ranking publication after PR #61
> merge and post-merge exact-SHA acceptance.

## 1. Seal Status

| Area | Status |
| --- | --- |
| L3 Publication | COMPLETE_VERTICAL_SLICE |
| L3 Publication Product Seal | CLOSED |

PR #62 was merged and the Product Seal passed post-merge exact-SHA Backend CI
and Stage26 Full E2E verification. The L3 Publication Product Seal is `CLOSED`.

## 2. Product Scope

L3 publication is a complete platform-wide publication slice:

1. `SUPER_ADMIN` is the L3 publication operator.
2. L3 definitions must be platform-wide: `definition.schoolId == null`.
3. A school-scoped L3 definition fails closed before publication mutation.
4. Publication only publishes an already existing `GENERATED` snapshot.
5. Publication does not re-run generation.
6. Publication does not recalculate ranking entries.
7. Publication does not reselect score attempts.
8. Publication does not recalculate privacy.
9. The target version transitions from `GENERATED` to `PUBLISHED`.
10. `ranking_definitions.current_version_id` points to the new published
    version.
11. `published_at` is server-derived.
12. If a current published version already exists, the previous version becomes
    `REPLACED` and the new version becomes `PUBLISHED`.
13. `previousCurrentVersionId` returns the original current version.
14. Snapshot rows remain unchanged before and after publication.

## 3. Authorization Boundary

`SUPER_ADMIN` may operate L3 publication.

`SCHOOL_ADMIN` and `STUDENT` may not call the SuperAdmin L3 publication API.

The L3 publication operator grant does not expand SuperAdmin into a
SchoolAdmin production actor. SuperAdmin must not gain authority to publish
arbitrary L1 or L2 school rankings through the L3 publication path.

## 4. Snapshot Immutability

Publication is a state transition over an already generated immutable snapshot.

Publication must not:

- recalculate ranking
- select new scores
- modify `ranking_entries`
- modify `ranking_entry_score_sources`
- rebuild privacy identities
- re-run L3 authorization matching

Generation produces the snapshot. Publication publishes the snapshot.

## 5. Published Visibility

`GENERATED` L3 snapshots are not publicly visible before publication.

The current `PUBLISHED` L3 snapshot is visible through existing public ranking
read.

Accepted behavior:

- anonymous public detail is visible after publication
- public list includes published L3
- student published ranking read works
- school-admin school-scoped paths do not become L3 management paths
- L2 anonymous public visibility remains denied

## 6. Version Replacement

First publication:

```text
current_version_id = version 1
version 1 = PUBLISHED
```

Next generation and publication:

```text
old current version = REPLACED
new version = PUBLISHED
current_version_id = new version
```

The old snapshot itself must not be recalculated or modified.

## 7. Verification Evidence

PR #61 final acceptance:

```text
PR #61 = MERGED

Accepted feature head =
0a342063e0224c7a4ceeee8dc4b375352889615d

Merge/master =
df67cf7aa1dae81e9af737aca60354e745206558

Backend CI = PASS
Surefire = 1016
Failsafe = 293
Total backend tests = 1309
Failures = 0
Errors = 0
Skipped = 0

RankingPublicationApplicationServiceIT = PASS

Stage26 Full E2E = PASS
Total = 17
Passed = 17
Failed = 0
Skipped = 0
Product retries = 0
```

PR #62 product seal acceptance:

```text
Product Seal PR #62 = MERGED

Accepted seal head =
d3043fd9975576c31f359ac4d04904614587fd3d

Product Seal merge/master =
190d952ee6e039a2687cdb3ddb1dcc2cdb40426b

Post-merge Backend CI = PASS
Run ID = 34253485890
Head SHA = 190d952ee6e039a2687cdb3ddb1dcc2cdb40426b
Surefire = 1016
Failsafe = 293
Total backend tests = 1309
Failures = 0
Errors = 0
Skipped = 0

Post-merge Stage26 Full E2E = PASS
Run ID = 34253485933
Head SHA = 190d952ee6e039a2687cdb3ddb1dcc2cdb40426b
Total = 17
Passed = 17
Failed = 0
Skipped = 0
Product retries = 0
```

Product E2E:

```text
L3 publication API publishes generated snapshots
and preserves public visibility
= PASS
```

## 8. Explicit No-Migration / No-Rewrite

```text
Migration = NONE
Ranking Read changed = NO
Ranking Generation changed = NO
L3 Authorization changed = NO
```

Publication reuses the existing ranking version and immutable snapshot model.

## 9. Explicitly Out Of Scope

This seal does not implement:

- L3 Ranking Management Frontend
- full L3 management UI
- ActivityResult closure
- Media
- Notification
- Production Readiness debt

This document seals only:

```text
L3 Publication = COMPLETE_VERTICAL_SLICE
```

## 10. Deferred Minors

Project-level deferred minors:

1. Activity scope selector currently requires manual UUID entry.
2. Surefire/Failsafe URLClassPath compatibility flag lacks maintenance comment.

These are not PR-specific minors for the L3 publication product-seal docs PR.
Validator warnings are not PR-specific minors.

## 11. Next Stage

After this Product Seal PR is merged and post-merge verified, the next core
engineering stage is:

```text
RANKING_PRODUCTION_L3_RANKING_MANAGEMENT_FRONTEND
```
