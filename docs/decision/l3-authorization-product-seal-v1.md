# L3 Authorization Product Seal v1

> Baseline: `9e782922b32a2a7863d539cfd7b16eba27756cfd`
> Scope: final product seal for completed L3 authorization policy,
> workflow, UI, and E2E evidence.

## 1. Seal Status

| Area | Status |
| --- | --- |
| L3 Authorization | COMPLETE_VERTICAL_SLICE |
| L3 Authorization Product Seal | READY_FOR_FINAL_ACCEPTANCE |

## 2. Product Status

L3 Authorization is a complete same-school vertical slice:

1. Policy.
2. Scope.
3. Privacy.
4. Create and edit.
5. Submit.
6. Approve and reject.
7. Return to draft and resubmit.
8. Withdraw.
9. Suspend and resume.
10. School lifecycle coordination.
11. Query.
12. SchoolAdmin UI.
13. SuperAdmin UI.
14. Tests.
15. Browser E2E.

## 3. Actor Responsibilities

`SCHOOL_ADMIN` is the data contributor and authorization owner.
`SUPER_ADMIN` is the reviewer and governance actor.

`SUPER_ADMIN` must not create an authorization on behalf of a school.

## 4. School Scope

School scope is server-derived:

```text
schoolId = authenticated SchoolAdmin unique active school
```

Clients may not decide `schoolId`, actor, reviewer, status, or school lifecycle
effects.

## 5. Scope Schema

The sealed v1 schema is:

```text
activityIds
activityPeriodStart
activityPeriodEnd
grades
classNames
```

Unknown keys are rejected. Raw unknown data is never silently discarded.

## 6. Privacy

`allowSchoolName` default = `false`
`allowStudentName` default = `false`

`allowStudentName` means masked or desensitized student display permission.
It does not permit raw legal-name publication.

## 7. Lifecycle

```text
DRAFT -> PENDING_REVIEW -> APPROVED
PENDING_REVIEW -> REJECTED -> DRAFT
APPROVED -> SUSPENDED -> APPROVED
WITHDRAWN = terminal
```

## 8. School Lifecycle Coordination

```text
School PAUSED -> APPROVED authorization = SUSPENDED
School restored to NORMAL -> authorization remains SUSPENDED
SUPER_ADMIN resume -> APPROVED
School DISABLED -> relevant non-terminal authorization = WITHDRAWN
```

## 9. Usable Authorization Contract

Future L3 generation must consume only usable authorizations:

```text
authorization.status = APPROVED
AND school.status = NORMAL
```

Generation must not reinterpret authorization state on its own.

## 10. Duplicate Policy

The active invariant is:

```text
schoolId + projectId + ruleVersionId + normalized dataScope
```

At most one non-WITHDRAWN active workflow may exist for the same key.

The invariant is enforced by V020:

```text
uq_l3_auth_active_school_project_rule_scope
```

Sequential and concurrent duplicates both return:

```text
409
L3_AUTHORIZATION_ALREADY_EXISTS
```

WITHDRAWN history is preserved.

## 11. Deferred Minor

```text
DEFERRED_MINOR_1 = Activity scope selector currently requires manual UUID entry.
```

This is a management UX debt, not an authorization safety debt.

## 12. Explicitly Out Of Scope

This stage does not implement:

```text
L3 Ranking Generation
L3 Ranking Publication
L3 Ranking Management Frontend
ActivityResult
Media
Notification
```
