# Current Three-Role Operational Responsibility Baseline v1.0

> **Superseded:** This historical Stage 24 baseline is superseded by
> `current-three-role-operational-responsibility-baseline-v1.1.md` because its
> ActivityApplication responsibility section incorrectly described student
> enrollment. The historical document is retained for decision history.

> Status: Stage 24 decision baseline
> Baseline master: `d11d47fcd2600ab02056baa934184b20ec2a5b72`
> Runtime roles: `SUPER_ADMIN`, `SCHOOL_ADMIN`, `STUDENT`
> Teacher: excluded from runtime

## 1. Purpose

This document freezes the operational responsibility model after the permanent
retirement of the Teacher role. It is a product and authorization contract for
Stages 25-30. It does not implement endpoints, migrations, UI, or workflow
changes.

Target V1 is a deployable three-role school platform. The current product is not
yet production-ready because ActivityApplication closure, Score Write, Ranking
Production and production-readiness work remain incomplete. Historical Teacher
records and specifications are compatibility evidence only and must not be
treated as permission to restore a Teacher workspace.

## 2. Runtime Roles

| Role | Scope | Operational meaning |
| --- | --- | --- |
| `SUPER_ADMIN` | Platform | Platform governance, school governance, platform projects, platform ranking governance, audit and configuration |
| `SCHOOL_ADMIN` | One active school membership | Same-school operations and review |
| `STUDENT` | One active student membership | Self actions, same-school permitted reads and public reads |

The server derives actor identity and school scope from the authenticated
principal and active membership. Client-supplied actor, reviewer, creator,
handler, uploader, or applicant identifiers are not authoritative.

## 3. Teacher Retirement Rule

`TEACHER` is `NOT_A_RUNTIME_ROLE`.

The current system must not:

- issue `ROLE_TEACHER`;
- create a Teacher membership or Teacher workspace;
- add Teacher endpoints;
- bind future operations to Teacher merely because an old specification did;
- transfer every historical Teacher responsibility to `SCHOOL_ADMIN` without an
  explicit product decision.

`TEACHER` may remain in old migrations, database constraints, fixtures,
reconstitution data, or negative tests. Such occurrences are marked
`LEGACY_COMPATIBILITY_ONLY` and are not a reason to modify historical
migrations.

## 4. ActivityApplication Responsibility

### Target contract

| Role | Allowed operation | Scope |
| --- | --- | --- |
| `STUDENT` | View available activities; submit an application; list/detail own applications; withdraw own application when allowed | Self, same-school activity |
| `SCHOOL_ADMIN` | List/detail applications; approve or reject | Same school only |
| `SUPER_ADMIN` | No school application day-to-day processing | Platform governance only |
| Teacher | None | Not a runtime role |

The student's identity must come from `CurrentActor` and one unique active
`STUDENT` membership. The server derives both `studentId` and `schoolId` from
that membership. Client-supplied `studentId`, `schoolId` and `applicantId` are
not authorization inputs. The submit request target is `activityId` plus the
actual business fields required by the application contract. The service loads
the Activity and verifies `activity.schoolId == currentStudent.schoolId`.
If an existing request still contains `schoolId`, it is
`CURRENT_IMPLEMENTATION_DEBT`, not part of the Stage 25 authorization contract.
The activity must be in an application allowed state. Duplicate application
rules, withdraw states, and approve/reject transitions must be enforced by the
application service and domain.

### Current implementation evidence

- `ActivityApplication` exists as an independent aggregate.
- The domain supports `DRAFT -> SUBMITTED`, `SUBMITTED -> APPROVED`,
  `SUBMITTED -> REJECTED`, `SUBMITTED -> WITHDRAWN`, and
  `REJECTED -> DRAFT`.
- The submit endpoint exists but is explicitly `denyAll`.
- Submit derives the applicant from `CurrentActor`, but the target school is
  still supplied by the request and requires scope validation.
- Approve and reject endpoints exist with `SCHOOL_ADMIN` authorization and
  same-school service authorization.
- Student list/detail endpoints are absent.
- Withdraw exists but is explicitly `denyAll` and the service currently does
  not enforce ownership.

### Decision

`ACTIVITY_APPLICATION_DECISION: PASS`

The target responsibility is fixed for Stage 25. Implementation remains
deferred until the student self-scope, query contract, duplicate rule and
withdraw ownership checks are closed.

## 5. Score Write Responsibility

### Target contract

| Role | Allowed operation | Scope |
| --- | --- | --- |
| `SCHOOL_ADMIN` | Create draft, edit draft, submit/confirm, approve the school's score | Same school and permitted activity/project |
| `STUDENT` | Read own approved effective scores | Self only |
| `SUPER_ADMIN` | No ordinary school score entry or confirmation | Platform governance only |
| Teacher | None | Not a runtime role |

The initial MVP may allow one active `SCHOOL_ADMIN` to enter and explicitly
confirm a score. The UI and API must make the second confirmation explicit and
must record audit information. A future dual-control or four-eyes workflow is
not required for the MVP.

### Current implementation evidence

- `ScoreAttempt` exists as an aggregate with statuses:
  `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INVALIDATED`.
- The domain supports draft editing, submit, approve, reject, return to draft
  and invalidate transitions.
- The only write service currently creates and submits an attempt.
- The write endpoint is explicitly `denyAll`.
- The request still accepts client `schoolId`, `studentId`, and `enteredBy`.
- There is no school-admin approval controller or correction application
  service.

### Decision

`SCORE_WRITE_DECISION: PASS`

The responsibility is fixed for Stage 26. The implementation must derive the
operator from `CurrentActor`, validate same-school scope, and preserve the
existing score state machine where possible.

## 6. Score Correction Principle

An approved score must never be overwritten in place.

The target correction rule is:

```text
existing APPROVED ScoreAttempt
    -> INVALIDATED
new ScoreAttempt
    -> reviewed and APPROVED
```

The old record and its audit history remain queryable. If the current model
cannot support an atomic correction workflow safely, record:

`SCORE_CORRECTION: FOLLOW_UP_REQUIRED`

Do not implement a complex correction engine in Stage 24.

## 7. Ranking Production Responsibility

Stage 23 completed published snapshot read:

- public read;
- student read with active student school scope;
- school-admin read with active school-admin school scope.

Ranking generation and publication are separate responsibilities:

| Layer | Producer | Input | Output |
| --- | --- | --- | --- |
| L1 | `SCHOOL_ADMIN` | Approved scores for an activity/project in the school | Immutable `RankingVersion` preview and published snapshot |
| L2 | `SCHOOL_ADMIN` | Approved scores for the school's configured scope | Immutable `RankingVersion` preview and published snapshot |
| L3 | `SUPER_ADMIN` | Data explicitly authorized for platform publication | Immutable `RankingVersion` preview and published snapshot |
| Any layer | `STUDENT` | None | Read only |
| Any layer | Teacher | None | Not a runtime role |

The ranking producer must use approved effective scores, the historical
ChallengeProject rule snapshot, and an explicit tie rule. Publishing creates an
immutable snapshot. Later score corrections or rule changes create a new
version; they do not mutate an already published version.

### Current implementation evidence

- Ranking definitions, versions, entries and score-source persistence are
  available for read.
- Stage 23 read APIs consume enabled definitions and published current versions.
- Definition create/enable/disable endpoints exist, but version generation and
  publication endpoints are absent.
- The current public read query does not yet restrict results to L3. This is a
  Stage 27 visibility follow-up, not permission to broaden public visibility.

`RANKING_PRODUCTION_DECISION: PASS`

## 8. Ranking Snapshot Principle

All public, student and school-admin ranking reads must read an existing
published snapshot. Read requests must not calculate, sort, or re-evaluate
`ScoreAttempt` data.

The production chain is:

```text
approved effective ScoreAttempt
    -> historical ChallengeProject rule snapshot
    -> tie rule
    -> RankingVersion
    -> RankingEntry
    -> publish
    -> Stage 23 read APIs
```

`RANKING_READ: COMPLETE_VERTICAL_SLICE`

## 9. Ranking Scope

The V1 scope is intentionally limited:

- L1: student ranking inside an activity/project;
- L2: school-scoped student ranking;
- L3: platform-public ranking based on explicit publication authorization.

`SCHOOL_AGGREGATE_RANKING: OUT_OF_SCOPE_FOR_V1`.

No school-to-school aggregate table or implied cross-school product is to be
invented until a product rule and data model exist.

## 10. SUPER_ADMIN Boundary

`SUPER_ADMIN` is a platform governance role, not a universal school operator.

Allowed:

- platform project governance;
- school registration and school lifecycle governance;
- school-admin invitation governance;
- L3 ranking generation/publication governance;
- audit and platform configuration.

Not allowed by default:

- submitting or reviewing school activity applications;
- entering or confirming ordinary school scores;
- changing ordinary school scores;
- acting as a school's routine appeal/feedback operator.

## 11. SCHOOL_ADMIN Boundary

`SCHOOL_ADMIN` is a same-school operations role.

Allowed, when the corresponding capability is implemented:

- own-school Activity management;
- own-school ActivityApplication review;
- own-school Score write and confirmation;
- own-school L1/L2 ranking production;
- own-school appeal and feedback processing;
- own-school media review where the separate Media contract permits it.

Every operation must use an active `SCHOOL_ADMIN` membership and reject
cross-school access. A platform role with an incidental membership must not
bypass this rule.

## 12. STUDENT Boundary

`STUDENT` scope is derived from one unique active student membership.

Allowed:

- public content read;
- same-school permitted content read;
- own activity applications;
- own approved score read;
- own appeals and feedback;
- ranking read.

Not allowed:

- submitting for another student;
- changing or approving scores;
- generating or publishing rankings;
- school administration.

## 13. Media and Deferred Capabilities

`MEDIA: DEFERRED`.

Media upload identity, school scope, internal reviewer identity and platform
publication rules require a separate product contract. The presence of a
`SCHOOL_ADMIN` internal-approve endpoint does not complete the Media vertical
slice. Do not restore Teacher to fill this gap.

ActivityResult, notification, audit query/export and other incomplete lines
remain outside Stage 24 implementation.

## 14. Authorization Matrix and Endpoint Target

| Capability | Current state | Target stage | Role | Object scope |
| --- | --- | --- | --- | --- |
| ActivityApplication submit | Legacy activity-creation application; endpoint denied | Frozen for V1 | No runtime submitter | Historical flow only |
| ActivityApplication list/detail | Legacy activity-creation application; no active query contract | Frozen for V1 | No runtime student operation | Historical flow only |
| ActivityApplication withdraw | Legacy activity-creation application; endpoint denied | Frozen for V1 | No runtime submitter | Historical flow only |
| ActivityApplication review | Historical approve/reject boundary | Frozen for V1 | SCHOOL_ADMIN | Same-school historical application |
| Score draft/create/edit | Domain supports draft; write endpoint denied | Stage 26 | SCHOOL_ADMIN | Same-school activity/project |
| Score submit/confirm | No approval API | Stage 26 | SCHOOL_ADMIN | Same-school score |
| Score approved read | Student read slice exists | Preserved | STUDENT | Own approved scores |
| Score correction | Invalidate transition exists; coordinator absent | Stage 26 follow-up | SCHOOL_ADMIN | Same-school score |
| Ranking read | Complete Stage 23 | Preserved | Public/STUDENT/SCHOOL_ADMIN | Published snapshot |
| Ranking L1/L2 generate/preview/publish | Not implemented | Stage 27 | SCHOOL_ADMIN | Own school |
| Ranking L3 generate/preview/publish | Not implemented | Stage 27 | SUPER_ADMIN | Authorized platform scope |
| Media upload/review/publication | Deferred | Later decision | Explicitly undecided | Same-school/platform split |

For all future write endpoints:

- actor identity comes from `CurrentActor`;
- school scope comes from active membership or the loaded resource;
- client IDs are target-resource identifiers only;
- authorization is enforced in both controller and application service where
  the service loads the resource.

## 15. Stage Lock

The accepted implementation order is:

1. Stage 25: Activity Participant Scope closure.
2. Stage 26: SchoolAdmin Score Write closure.
3. Stage 27: Ranking calculation, version and publication.
4. Stage 28: ActivityResult closure.
5. Stage 29: Production readiness.
6. Stage 30: Real-data E2E and UI closure.

Stage 24 itself does not implement any of these capabilities and does not add
business endpoints, migrations, or frontend pages.

The existing `activity_participants` table is persistence evidence only. Until a
complete participant-scope domain, authorization contract, API, frontend and
UAT exist, `Activity Participant Scope` remains `NOT_IMPLEMENTED`.

## 16. Compatibility and Non-Goals

- Historical Teacher values remain only where needed for compatibility.
- No migration cleanup is required.
- No Teacher role is restored.
- No ActivityApplication, Score Write or Ranking Calculation code is added.
- Stage 23 ranking read behavior remains unchanged in this commit.
