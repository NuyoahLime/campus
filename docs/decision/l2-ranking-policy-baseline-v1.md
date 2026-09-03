# L2 Ranking Policy Baseline v1

> Status: Accepted for implementation baseline
> Date: 2026-09-02
> Scope: Ranking Production Phase4A L2 policy closure
> Baseline: 099e3fd3b5f56a0376c3a7e8b6c2ec9a48035af6

## Purpose

This document freezes the L2 ranking product policy before any L2 production
implementation begins. It is a policy decision only. It does not introduce Java
production code, frontend code, database migration, API, controller, or service
changes.

## L2 Definition

| Field | Decision |
|------|----------|
| Layer | L2 |
| Name | Same-school ranking |
| Owner | SCHOOL_ADMIN |
| Scope | Single school |
| Binding | One ChallengeProject |
| Audience | Users in the same school |

L2 is not "L1 across multiple activities" in the UI or API model. L2 is a
school-scoped ranking definition bound to one ChallengeProject. Its candidate
data can come from multiple ActivityProjects under that ChallengeProject, but
the generation contract must reduce those candidates to one ranking candidate
per student before ranking positions are calculated.

## Effective Score Source

L2 consumes the Stage26 authoritative effective score only:

```text
ScoreAttempt
  -> score_status = APPROVED
  -> is_current_effective = true
  -> L2 candidate input
```

L2 must not reselect the effective score. In particular, L2 must not implement
its own BEST, LAST, ADMIN_DESIGNATED, latest-attempt, max-attempt-number, or
manual effective-score selection over ScoreAttempts.

```text
L2_RESELECT_EFFECTIVE_SCORE = NO
```

## Cross-Activity Candidate Selection

When the same student has multiple current-effective APPROVED ScoreAttempts for
the same ChallengeProject through different ActivityProjects, L2 v1 uses:

```text
L2_CROSS_ACTIVITY_SELECTION_POLICY = BEST_SCORE
```

The selection is performed after Stage26 effective-score filtering and before
ranking calculation:

```text
Effective Score
  -> L2 Candidate Reducer
  -> Ranking Calculator
  -> RankingEntry
```

The reducer selects one representative candidate per student according to the
frozen RuleVersion comparison semantics:

| Storage type | Direction | Representative candidate |
|-------------|-----------|--------------------------|
| INTEGER | HIGHER_BETTER | Maximum numeric score |
| INTEGER | LOWER_BETTER | Minimum numeric score |
| DECIMAL | HIGHER_BETTER | Maximum BigDecimal score |
| DECIMAL | LOWER_BETTER | Minimum BigDecimal score |
| DURATION | HIGHER_BETTER | Maximum duration value |
| DURATION | LOWER_BETTER | Minimum duration value |
| GRADE | gradeOrder | Best grade according to frozen gradeOrder |

If two candidate scores are equal after business comparison, the business value
remains tied. Persistence order may use a deterministic technical tiebreaker
such as ScoreAttempt id, but that tiebreaker must not change the business rank.

```text
CALCULATOR_ONLY_RANKING = YES
```

The ranking calculator receives already-reduced candidates and calculates rank
positions. It must not perform L2 candidate reduction.

## RuleVersion Compatibility

L2 v1 uses the safe subset:

```text
L2_RULE_VERSION_POLICY = SAME_RULE_VERSION_ONLY
```

Allowed candidate set:

- Same ChallengeProject.
- Same school as the RankingDefinition.
- Same frozen RuleVersion.
- Same score storage type and score unit implied by the RuleVersion.
- Same comparison direction and gradeOrder implied by the RuleVersion.
- Stage26 current-effective APPROVED ScoreAttempts only.

Forbidden in L2 v1:

- Automatic merge across different RuleVersions.
- Automatic unit conversion.
- Automatic direction conversion.
- Use of project_rule_compatibilities as an implicit merge approval.

Future extension may allow SUPER_ADMIN-approved compatibility, but that is not
part of Phase4B L2 generation.

## Dimension Filter Contract

L2 v1 supports these filters:

```text
SUPPORTED_L2_FILTERS = GRADE, CLASS, ACTIVITY_PERIOD
```

| Filter | Source | Status |
|--------|--------|--------|
| grade | student_profiles.grade | Supported |
| class | student_profiles.class_name | Supported |
| activity_period | activity.start_time/activity.end_time for the owning ActivityProject | Supported |
| gender | none in current authoritative schema | Not supported |

The filter contract must be stored in RankingDefinition.dimension_filters and
copied into RankingVersion.data_scope_snapshot or calculation_params during
generation. A generated version must remain immutable if live student profile
or activity data changes later.

## Activity Time Policy

```text
L2_TIME_FILTER_SOURCE = ACTIVITY_PERIOD
```

L2 activity-period filtering uses the activity execution period attached to the
ActivityProject. In the current schema this is the owning activity's
start_time/end_time.

Forbidden time sources for L2 activity-period filtering:

- ScoreAttempt created_at.
- ScoreAttempt submitted_at.
- ScoreAttempt reviewed_at.
- Any audit timestamp unrelated to the activity execution period.

## Visibility Policy

| Actor | L1 | L2 | L3 |
|-------|----|----|----|
| Anonymous | Deny | Deny | Allow published public rankings |
| Student | Own school only | Own school only | Allow published public rankings |
| School Admin | Own school only | Own school only | Allow published public rankings |
| Super Admin | No production-view override in v1 | No production-view override in v1 | Administrative capability only |

```text
SUPER_ADMIN_OVERRIDE = NOT_IMPLEMENTED
```

Super Admin management permissions do not automatically imply unrestricted
production ranking visibility. Any future override must be specified and tested
as a separate policy change.

## Privacy Policy

L2 is a same-school internal ranking. L2 v1 displays the generated snapshot
student_display_name to same-school authorized users.

L3 public rankings use masked or privacy-approved display names according to
the public ranking privacy policy. L2 must not inherit L3 public masking by
default, and L3 must not expose L2 internal display names.

```text
L2_PRIVACY_POLICY = INTERNAL_SAME_SCHOOL_DISPLAY_NAME
L3_PRIVACY_POLICY = PUBLIC_MASKED_OR_AUTHORIZED_DISPLAY
```

## Schema Policy

L2 definitions are unique per school and ChallengeProject:

```text
L2_DEFINITION_UNIQUENESS = ONE_PER_SCHOOL_PROJECT
```

The current schema can represent L2 definitions, but the physical schema does
not yet enforce the documented partial uniqueness invariant. L2 implementation
must include a migration or equivalent database constraint before enabling L2
definition creation in production.

```text
SCHEMA_CHANGE_REQUIRED = YES
```

Expected invariant:

```sql
UNIQUE (school_id, project_id) WHERE layer = 'L2'
```

The migration belongs to the L2 implementation phase, not to this policy
closure commit.

## Old Branch Review

| Branch | Classification | Notes |
|--------|----------------|-------|
| origin/feat/school-admin-ranking-management-14g | REUSABLE_CONCEPT / STALE | Contains older school-admin ranking management concepts and tests; must not be merged or cherry-picked because it predates the current Stage26 score and authorization semantics. |
| origin/feat/student-l1-ranking-14h | REUSABLE_CONCEPT / STALE / CONFLICT | Contains older student ranking read and frontend concepts; direct reuse conflicts with the current L1 backend/frontend seal and L2 visibility policy. |

## Implementation Readiness

```text
CROSS_ACTIVITY_POLICY = CLOSED
RULE_VERSION_POLICY = CLOSED
FILTER_POLICY = CLOSED
TIME_POLICY = CLOSED
VISIBILITY_POLICY = CLOSED
PRIVACY_POLICY = CLOSED
SCHEMA_POLICY = CLOSED
L2_IMPLEMENTATION_READY = YES
```

Phase4B may start L2 generation only within this frozen policy boundary.
