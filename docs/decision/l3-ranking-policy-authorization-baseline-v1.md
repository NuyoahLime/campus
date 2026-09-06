# L3 Ranking Policy Authorization Baseline v1

> Baseline: `3b34ffd398874b25829bd90bb2f5271ec9db9576`
> Scope: L3 data authorization policy and workflow closure before L3 ranking
> generation. This document does not authorize L3 ranking generation or
> publication in this stage.

## 1. L3 Positioning

L3 is the platform-wide public ranking layer.

| Responsibility | v1 owner |
| --- | --- |
| Ranking operator | `SUPER_ADMIN` |
| Data contributor | `SCHOOL_ADMIN` through L3 data authorization |
| School data scope | Server-derived from the authenticated school admin |

`SUPER_ADMIN` must not create school data authorization on behalf of a school.
School data authorization is always contributed by the school itself.

## 2. Server-Derived School Scope

School admins create L3 authorizations for their unique active school-admin
membership only:

```text
schoolId = authenticated SCHOOL_ADMIN unique school
```

Clients cannot choose `schoolId`, `actorId`, status, reviewer, timestamps, or
school lifecycle effects.

## 3. Scope Contract

The v1 authorization scope is a deterministic JSON object with these keys:

```text
activityIds
activityPeriodStart
activityPeriodEnd
grades
classNames
```

All keys are optional. `activityIds`, `grades`, and `classNames` are normalized
as sorted unique arrays. Blank grade/class values are removed. Activity period
values are ISO-8601 instants.

The backend rejects:

```text
activityPeriodStart > activityPeriodEnd
activityIds that do not belong to the submitting school
activityIds whose ActivityProject does not match the selected ChallengeProject
activityIds whose ActivityProject ruleVersion does not match the selected RuleVersion
project/ruleVersion mismatches
```

The backend validates and normalizes this JSON before persistence. Raw client
JSON is not stored without validation.

## 4. ChallengeProject And RuleVersion Integrity

Creation, edit, approval, and resume paths must validate:

```text
ChallengeProject exists
ChallengeProject is PUBLISHED
RuleVersion exists
RuleVersion belongs to ChallengeProject
Scope still belongs to the school and selected project/ruleVersion
```

Frontend selection is not a security boundary.

## 5. State Machine

L3 authorization keeps the ADR-005 lifecycle:

```text
DRAFT -> PENDING_REVIEW
PENDING_REVIEW -> APPROVED
PENDING_REVIEW -> REJECTED
REJECTED -> DRAFT
APPROVED -> SUSPENDED
SUSPENDED -> APPROVED
WITHDRAWN = terminal
```

School admins can create, list, read, edit own `DRAFT`, submit own `DRAFT`,
return own `REJECTED` to draft, and withdraw allowed own-school authorizations.

Super admins can list the review queue, read details, approve or reject
`PENDING_REVIEW`, and resume `SUSPENDED` authorizations after the school is
back to normal.

`projectId` and `ruleVersionId` are immutable after authorization creation. A
school creates a new authorization to use a different project or RuleVersion.

## 6. Approval Safety

Super-admin approval revalidates the authorization at review time. Approval is
fail-closed if:

```text
authorization is not PENDING_REVIEW
school is not NORMAL
project/ruleVersion is no longer valid
scope no longer belongs to the authorization school
```

Reject reason is required. Approval comment remains optional.

## 7. School Lifecycle Coordination

School lifecycle coordination is mandatory:

```text
NORMAL -> PAUSED:
  APPROVED L3 authorizations -> SUSPENDED

PAUSED -> NORMAL:
  SUSPENDED L3 authorizations remain SUSPENDED
  SUPER_ADMIN resume is required

any status -> DISABLED:
  non-terminal L3 authorizations -> WITHDRAWN
```

Manual school-admin withdrawal and forced school-disable withdrawal are separate
domain operations.

## 8. Fail-Closed Consumption Rule

Future L3 generation must consume only usable authorizations:

```text
authorization.status = APPROVED
AND school.status = NORMAL
```

Generation must not rely only on `L3Authorization.isUsable()` while ignoring the
current school lifecycle state.

## 9. Privacy Flags

`allowSchoolName` means the school permits its school name to appear in future
L3 public ranking output.

`allowStudentName` means the school permits use of desensitized student display
names. It does not permit publishing raw student legal names. Future L3 ranking
generation must still apply public privacy masking.

When the client omits these flags, the backend defaults them to `false` and the
UI should treat that as the fail-closed baseline. There is no implicit privacy
consent in this v1 policy.

## 10. Duplicate And Historical Policy

`WITHDRAWN` is terminal audit history. A school may create a new authorization
after withdrawal.

During active workflow, v1 allows at most one non-withdrawn authorization for
the same:

```text
schoolId + projectId + ruleVersionId + normalized dataScope
```

This invariant is enforced by the database with a partial unique index.

## 11. Explicitly Out Of Scope

This stage does not implement:

```text
L3 RankingDefinition
L3 Ranking Generation
L3 Ranking Publication
L3 Ranking Management Frontend
RankingGenerationCalculator changes
L1/L2 ranking behavior changes
Score lifecycle changes
Effective Score or Correction changes
ActivityResult
Media
Notification
Teacher runtime
```
