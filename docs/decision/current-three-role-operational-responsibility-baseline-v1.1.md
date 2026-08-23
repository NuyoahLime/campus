# Current Three-Role Operational Responsibility Baseline v1.1

> Status: Current Stage 24.2 semantic baseline
> Supersedes: `current-three-role-operational-responsibility-baseline-v1.0.md`
> Baseline master: `9989295d6b782f431c43f1dfd43c241d37bed5d8`
> Runtime roles: `SUPER_ADMIN`, `SCHOOL_ADMIN`, `STUDENT`
> Teacher: excluded from runtime

## 1. Purpose

This document corrects the ActivityApplication semantic defect in the historical
Stage 24 baseline. It is a documentation and responsibility contract only; it
does not implement endpoints, migrations, UI or workflow changes.

The current V1 product has three runtime roles. Historical Teacher requirements
remain compatibility evidence, not permission to restore a Teacher workspace or
silently transfer every Teacher responsibility to `SCHOOL_ADMIN`.

## 2. ActivityApplication Meaning

`ActivityApplication` means `ACTIVITY_CREATION_APPLICATION`.

It is an independent aggregate representing a request to create a new formal
`Activity`:

```text
ActivityApplication
    -> SCHOOL_ADMIN review
    -> APPROVED
    -> create a new Activity
    -> persist createdActivityId
```

The accepted relationship remains `ActivityApplication -> Activity` by
`createdActivityId`. A school administrator may also create and manage a
same-school Activity directly without an application.

`ActivityApplication` is not:

- student enrollment;
- a participation record;
- an Activity target resource;
- a replacement for `ActivityParticipant`, `ActivityEnrollment` or
  `ParticipantScope`.

The historical state machine remains:

```text
DRAFT -> SUBMITTED -> APPROVED
                     -> REJECTED -> DRAFT
                     -> WITHDRAWN
```

Approval creates the Activity and records `createdActivityId`; rejection does
not create an Activity.

## 3. Runtime Responsibility

| Role | ActivityApplication responsibility | Current V1 status |
| --- | --- | --- |
| `SUPER_ADMIN` | No ordinary school application processing | Platform governance only |
| `SCHOOL_ADMIN` | Historical same-school review boundary | Legacy/frozen, not an active V1 creation path |
| `STUDENT` | Must not submit, list, detail or withdraw ActivityApplication | Not a student operation |
| `TEACHER` | Historical submitter only | Not a runtime role |

The retirement of `TEACHER` does not convert `ActivityApplication` into a
student feature. The historical application flow is frozen for V1 until a
future product decision assigns a valid runtime submitter or replaces the flow.

## 4. V1 Student Participation

V1 does not support student self-registration for an Activity.

The intended participation model is:

```text
SCHOOL_ADMIN
    -> configure Participant Scope for a same-school Activity
    -> include eligible students by supported scope
STUDENT
    -> view and participate in Activities allowed by Participant Scope
```

Supported scope targets are intended to include `individual`, `class` and
`grade`, subject to the actual domain and authorization implementation.

The existing `activity_participants` table is persistence evidence only. The
complete Participant Scope capability is currently:

`ACTIVITY_PARTICIPANT_SCOPE: NOT_IMPLEMENTED`

No new participant model is introduced by Stage 24.2.

## 5. Role Boundaries

| Role | Active V1 responsibilities |
| --- | --- |
| `SUPER_ADMIN` | School governance, platform project governance, platform ranking governance and audit/configuration |
| `SCHOOL_ADMIN` | Same-school Activity creation/management, future Participant Scope management, Score Write, own-school operations |
| `STUDENT` | Permitted Activity participation, approved score read, appeals, feedback and Ranking Read |
| `TEACHER` | Excluded; no runtime membership, workspace or endpoint |

`SCHOOL_ADMIN` direct Activity management is the active creation path for V1.
No V1 operation requires ActivityApplication to create an Activity.

## 6. Roadmap

| Stage | Scope | Status |
| --- | --- | --- |
| Stage 25 | Activity Participant Scope closure | Next implementation target |
| Stage 26 | `SCHOOL_ADMIN` Score Write closure | Not started |
| Stage 27 | Ranking calculation, version and publication | Not started |
| Stage 28 | ActivityResult closure | Not started |
| Stage 29 | Production readiness | Not started |
| Stage 30 | Real-data E2E and UI closure | Not started |

`ActivityApplication` is not the Stage 25 implementation target. The corrected
Stage 25 target is `Activity Participant Scope Closure`.

## 7. Preserved Decisions and Non-Goals

- Score Write remains `BACKEND_PARTIAL`, owned by `SCHOOL_ADMIN`, targeted to Stage 26.
- Ranking published snapshot read remains `COMPLETE_VERTICAL_SLICE`.
- Ranking production remains incomplete and targeted to Stage 27.
- Media remains `DEFERRED`.
- No Teacher role is restored.
- Stage 24.2 changes documentation only.
- No Stage25 local commit is modified, rebased, deleted or pushed by this stage.
