# L3 Ranking Generation Baseline v1

> Baseline: `d42433ecbb9b2162e3ea527da65c5f11ed69ce82`
> Scope: platform-wide L3 ranking generation only

## 1. Owner

L3 generation is operated by `SUPER_ADMIN`.
It does not inherit school-admin ranking semantics.

## 2. Definition Contract

L3 ranking definitions are platform-scoped:

- `layer = L3`
- `schoolId = null`
- `projectId` required
- `dimensionFilters` carries `ruleVersionId`

The definition is created only through the super-admin ranking-definition flow.

## 3. Generation Contract

Generation reads a generated L3 definition and creates an immutable
`GENERATED` ranking version.

Generation consumes only:

- approved scores
- current effective scores
- same challenge project
- same rule version
- same school as the usable authorization scope

The selection policy is `BEST_SCORE`.

## 4. Visibility And Privacy

L3 generation must apply authorization-driven visibility:

- school name can be masked or shown according to authorization
- student display name can be masked or anonymous according to authorization

Generation does not publish raw student identity data.

## 5. Snapshot Rules

Generation produces immutable snapshot data:

- `RankingVersion`
- `RankingEntry`
- `ranking_entry_score_sources`

Publication is separate and must not recalculate ranking.

## 6. Explicitly Out Of Scope

This baseline does not implement:

- L3 publication
- L3 management frontend
- ActivityResult
- Media
- Notification
