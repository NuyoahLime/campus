# ActivityResult Closure Contract Baseline v1

> Status: CONTRACT_BASELINE_READY_FOR_INDEPENDENT_REVIEW
>
> This document freezes the V1 ActivityResult closure contract before
> implementation. It is a documentation-only decision record. It does not
> implement ResultVersion, ActivityResult APIs, migrations, frontend, review
> history, authorization changes, or optimistic-locking fixes.

## 1. Baseline And Authority

| Item | Value |
| --- | --- |
| Repository | `NuyoahLime/campus` |
| Baseline master | `4e686391ffbeeb614bd6a729adfbd7875bf87eb9` |
| Baseline verification | `origin/master` and local `master` matched; worktree clean |
| Current capability status | `Activity result = BACKEND_PARTIAL` |
| Product sequence | ActivityResult closure -> Production Readiness -> Full real-data E2E / UI closure -> V1 Product Seal |
| Formal runtime roles | `SUPER_ADMIN`, `SCHOOL_ADMIN`, `STUDENT` |
| Teacher status | `NOT_A_RUNTIME_ROLE` |

Authority order for this contract:

1. `docs/business-spec/08-素材与活动成果规格.md`
2. `docs/business-spec/04-活动管理规格.md`
3. `docs/adr/ADR-004-activity-result-boundary.md`
4. `docs/decision/业务决策记录-v1.2.md`
5. `docs/decision/current-identity-authorization-baseline-v1.3.md`
6. `docs/decision/current-three-role-operational-responsibility-baseline-v1.1.md`
7. `docs/interface/interface-layer-planning.md`
8. Current source, schema, tests, and accepted capability inventory evidence

No later accepted decision was found that removes ResultVersion or review
history from V1. The source comment that says ResultVersion is deferred is
therefore an implementation note, not a change to the frozen business
contract.

## 2. Current Implementation State

The current master contains an independent `ActivityResult` aggregate root
with:

- `schoolId` and `activityId`;
- `result_internal_status` and `result_public_status`;
- `currentInternalVersionId` and `currentPublicVersionId`;
- the internal and public state transition guards listed below;
- domain events for internal publication, internal withdrawal, public review
  submission, platform approval, public publication, and platform takedown;
- a repository port, JPA entity, mapper, repository adapter, and one
  application service;
- one exposed endpoint:
  `POST /api/v1/activity-results/{id}/publish`.

The current application service exposes only:

- `create(UUID schoolId, UUID activityId)`;
- `publishInternal(UUID id)`.

The current `ActivityResultController` exposes only internal publication. There
is no current ActivityResult list/detail query, content edit command, public
review command, public read query, or frontend route.

The current persistence model is intentionally incomplete:

- V010 creates `activity_results` and `result_versions`;
- `ActivityResultEntity` maps the result row, but no `ResultVersionEntity`
  exists;
- `ActivityResultPersistenceMapper` maps statuses and version pointer UUIDs,
  but does not map a ResultVersion or the JPA version field;
- no result review-history table, entity, repository, application service, or
  query adapter was found;
- existing generic audit records are not treated as result review history
  without a field-by-field contract match.

ResultVersion implementation status is:

| Layer | Status |
| --- | --- |
| Schema | `PRESENT_ONLY`: V010 defines `result_versions` and its foreign keys |
| Domain | `NOT_IMPLEMENTED` |
| Persistence mapping | `NOT_IMPLEMENTED` |
| Application | `NOT_IMPLEMENTED` |
| API | `NOT_IMPLEMENTED` |
| Frontend | `NOT_IMPLEMENTED` |

The current capability inventory remains unchanged:

| Inventory metric | Baseline value |
| --- | ---: |
| Capability lines audited | 24 |
| `COMPLETE_VERTICAL_SLICE` | 19 |
| `BACKEND_PARTIAL` | 1 |
| `DOMAIN_READY_API_MISSING` | 1 |
| `DEFERRED_BY_IDENTITY_MODEL` | 1 |
| `LEGACY_FROZEN / DEFERRED_FOR_V1` | 1 |
| `NOT_IMPLEMENTED` | 1 |

## 3. Frozen Business Semantics

ActivityResult is an independent aggregate root. Its publication is not an
Activity execution state. The V1 relationship is:

```text
Activity 1 : 0..1 ActivityResult
```

The two ActivityResult state machines are independent:

```text
Internal:
DRAFT -> INTERNAL_PUBLISHED -> INTERNAL_WITHDRAWN -> DRAFT

Public:
NOT_SUBMITTED -> PENDING_PUBLIC_REVIEW -> PLATFORM_APPROVED -> PUBLIC
                              -> PLATFORM_REJECTED -> NOT_SUBMITTED
PUBLIC -> ANOMALY_PENDING -> PUBLIC
PUBLIC -> ANOMALY_PENDING -> NOT_SUBMITTED
PUBLIC -> ANOMALY_PENDING -> PLATFORM_TAKEDOWN
PUBLIC -> PLATFORM_TAKEDOWN -> NOT_SUBMITTED
```

The following are frozen invariants:

- only `INTERNAL_PUBLISHED` may submit for public review;
- platform approval does not publish the result;
- `SCHOOL_ADMIN` performs ordinary public publication after approval;
- `SUPER_ADMIN` may approve, reject, and force takedown, but may not perform
  ordinary publication;
- withdrawing an internally published result that is `PUBLIC` or
  `ANOMALY_PENDING` atomically changes public status to
  `PLATFORM_TAKEDOWN`;
- returning internal status to `DRAFT` does not restore public visibility;
- core content changes require a new immutable ResultVersion and public
  re-review;
- format-only changes may keep the public status but require a durable edit
  record;
- Activity cancellation does not delete existing results, content, scores,
  materials, or review records.

## 4. Contract Conflicts

| Conflict | Evidence | Contract treatment |
| --- | --- | --- |
| Source comment says ResultVersion is deferred | `ActivityResult.java` | Frozen business spec and ADR take precedence; ResultVersion is required for V1 closure |
| `result_versions` table exists without implementation | V010 | Schema existence is not feature completion |
| Status fields exist without review records | 08 §6.3/6.5 and current source | Review history remains a required implementation gap |
| Planning labels `ActivityResult.create` as `INTERNAL` | interface-layer planning | Do not add a mechanical public create endpoint; creation trigger needs explicit product resolution |
| Activity 1:0..1 invariant is clear, but creation timing is not | ADR-004, Activity code and service | Keep cardinality frozen; record trigger as `OPEN_PRODUCT_DECISION` |
| Content JSON shapes are not specified | 08 and V010 | Define only the minimum compatible implementation shape below; unresolved limits remain explicit |
| `@Version` is present only on the entity | entity and mapper audit | Record optimistic-lock round-trip as `BROKEN`; fix is outside this phase |

## 5. V1 Scope Decision

`ActivityResult` becomes `COMPLETE_VERTICAL_SLICE` only when the complete
contract is implemented and verified across domain, ResultVersion, content,
review history, persistence, locking, queries, authorization, API, frontend,
unit tests, PostgreSQL integration tests, controller/security tests, browser
E2E, and the business-spec validator.

The current phase freezes the following decisions:

- ResultVersion is required for V1 closure.
- Review history is required for V1 closure.
- Result creation remains an internal, authorized product flow rather than an
  anonymous or generic public CRUD endpoint.
- Public review and ordinary public publication remain separate commands.
- Public visibility is based on the current public ResultVersion, while an
  unapproved candidate does not replace the old public version.
- Media is an optional reference boundary, not a reason to implement the
  Media product.
- ActivityResult implementation must not change Ranking, Score, Activity
  execution, ActivityApplication, Notification, Audit, or Teacher semantics.

The following remain explicit `OPEN_PRODUCT_DECISION` items:

- whether creation is automatic with Activity creation, lazy on first
  authorized result editing, or an explicit SchoolAdmin result-entry action;
- whether result editing is allowed while the Activity is `PUBLISHED` or
  `IN_PROGRESS`, beyond the explicit `ENDED` allowance;
- whether a result may be newly created for an Activity in `CANCELLED` state;
- the maximum length and whitespace policy for `summaryText`;
- the final structured shape and item limits for score highlights;
- the retention and field-level shape of format-edit records.

`PUBLIC_CREATE_ENDPOINT_REQUIRED = NO` for anonymous or generic public REST
creation. A same-school SchoolAdmin product entry is still required after the
creation-trigger decision is accepted.

## 6. Actor And Authorization Matrix

Runtime actors are limited to the following:

| Actor | Allowed responsibility |
| --- | --- |
| `SCHOOL_ADMIN` | Same-school result editing and internal lifecycle; submit public review; ordinary publication after platform approval; allowed anomaly handling |
| `SUPER_ADMIN` | Read pending public-review candidates; approve/reject public review; emergency platform takedown |
| `STUDENT` | Read only where the visibility rules allow; never manage or mutate results |
| Anonymous | Read only `PUBLIC` results |
| Teacher | No runtime identity, workspace, or ActivityResult responsibility |

The target resource school must be derived and checked server-side. Client
identity or school fields must not widen the authorization scope.

## 7. Internal Lifecycle

The internal lifecycle is:

```text
DRAFT -> INTERNAL_PUBLISHED -> INTERNAL_WITHDRAWN -> DRAFT
```

`SCHOOL_ADMIN` of the ActivityResult school owns these operations:

| Operation | Before | After | Atomicity |
| --- | --- | --- | --- |
| `publishInternal` | `DRAFT` | `INTERNAL_PUBLISHED` | One aggregate transaction |
| `withdrawInternal` | `INTERNAL_PUBLISHED` | `INTERNAL_WITHDRAWN` | One aggregate transaction |
| `returnToDraft` | `INTERNAL_WITHDRAWN` | `DRAFT` | One aggregate transaction |

If the result is `PUBLIC` or `ANOMALY_PENDING` when internal withdrawal occurs,
the same transaction must also set public status to
`PLATFORM_TAKEDOWN`. Restoring `DRAFT` must not restore public status.

## 8. Public Review Lifecycle

The public lifecycle is:

```text
NOT_SUBMITTED
    -> PENDING_PUBLIC_REVIEW
    -> PLATFORM_APPROVED
    -> PUBLIC

PENDING_PUBLIC_REVIEW -> PLATFORM_REJECTED -> NOT_SUBMITTED
PUBLIC -> ANOMALY_PENDING -> PUBLIC / NOT_SUBMITTED / PLATFORM_TAKEDOWN
PUBLIC -> PLATFORM_TAKEDOWN -> NOT_SUBMITTED
```

The required responsibility split is:

| Operation | Actor | Required transition |
| --- | --- | --- |
| Submit for public review | Same-school `SCHOOL_ADMIN` | `NOT_SUBMITTED -> PENDING_PUBLIC_REVIEW` |
| Approve | `SUPER_ADMIN` | `PENDING_PUBLIC_REVIEW -> PLATFORM_APPROVED` |
| Reject with reason | `SUPER_ADMIN` | `PENDING_PUBLIC_REVIEW -> PLATFORM_REJECTED` |
| Make public | Same-school `SCHOOL_ADMIN` | `PLATFORM_APPROVED -> PUBLIC` |
| Platform takedown with reason | `SUPER_ADMIN` | `PUBLIC` or `ANOMALY_PENDING -> PLATFORM_TAKEDOWN` |
| Resolve allowed anomaly | Same-school `SCHOOL_ADMIN` | `ANOMALY_PENDING -> PUBLIC` only when no core content changed |

`SUPER_ADMIN_REVIEW_READ_RULE` is narrow: a SuperAdmin may read the candidate
needed to review a `PENDING_PUBLIC_REVIEW` result and its review context. This
does not grant arbitrary browsing of school-internal `DRAFT`,
`INTERNAL_PUBLISHED`, or `INTERNAL_WITHDRAWN` results.

`SUPER_ADMIN` approval must never directly transition a result to `PUBLIC`.

## 9. Result Content Contract

The authoritative V1 content is:

| Field | Required | Empty behavior | Maximum | Serialization and validation |
| --- | --- | --- | --- | --- |
| `title` | Yes | Must be a non-blank title | 200 characters | Text; bounded by the V010 `varchar(200)` column |
| `summaryText` | Yes | Must be present; exact blank-string policy is `OPEN_PRODUCT_DECISION` | No frozen application maximum; V010 uses `text` | Text; do not silently truncate |
| `scoreHighlights` | No | `null` or `[]` means no highlights | Item/length limit is `OPEN_PRODUCT_DECISION` | V1 minimum shape: JSON array of display strings; reject non-array or non-string items; never use it to recalculate scores |
| `mediaRefs` | No | `null` or `[]` means no media | Item limit is `OPEN_PRODUCT_DECISION` | V1 minimum shape: JSON array of UUID reference strings; validate references without uploading or copying binary content |

The minimum JSON decisions above are intentionally narrow and
backward-compatible with `jsonb`. They are display/reference payloads only:

- `scoreHighlights` cannot recalculate Ranking, reselect Effective Score,
  modify ScoreAttempt, or modify RankingVersion;
- `mediaRefs` stores IDs, not binary content snapshots;
- malformed JSON, wrong top-level shape, invalid UUID references, or an
  unauthorized reference must fail closed;
- no field may accept a client-supplied actor identity as a substitute for
  SecurityContext authorization.

## 10. ResultVersion Contract

`RESULT_VERSION_REQUIRED_FOR_V1_CLOSURE = YES`.

One ActivityResult may own multiple immutable ResultVersions. A ResultVersion
contains the V010 fields:

```text
id
result_id
version_number
title
summary_text
score_highlights
media_refs
is_core_content_modified
format_change_log
published_internally_at
published_publicly_at
created_at
```

Version numbers are monotonically increasing per `result_id`, starting at the
first version created for that result. A version is never updated after it has
been published. A new version is required when any core content changes:

- title;
- summary text;
- score highlights;
- referenced media.

Format-only changes may use the existing version only when they do not change
business meaning, review content, or the substance of public disclosure. Such
changes must produce a durable format-edit record.

## 11. Version Pointer Semantics

The two pointers have separate meanings:

| Pointer | Meaning |
| --- | --- |
| `currentInternalVersionId` | Version currently used for the internal published result |
| `currentPublicVersionId` | Version currently exposed by public result reads |

Required rules:

1. Creating or editing a draft creates or updates a candidate ResultVersion
   according to the ResultVersion rules; it does not change the public pointer.
2. Internal publication points `currentInternalVersionId` to the version
   published internally.
3. Public review is bound to the exact candidate version submitted by the
   SchoolAdmin.
4. Platform approval is bound to that same candidate version.
5. `makePublic` switches `currentPublicVersionId` to the approved candidate in
   the same transaction as the public status transition.
6. While a new candidate is under review, the old
   `currentPublicVersionId` remains in place and remains visible.
7. Rejecting a candidate never replaces or mutates the old public pointer.
8. A core edit of a public result creates a new candidate and requires public
   re-review; it must not mutate the published version.
9. A format-only edit may keep the current public pointer, but its edit record
   must remain queryable.
10. A published ResultVersion is immutable.

Therefore:

```text
OLD_PUBLIC_VERSION_DURING_REVIEW = KEEP_VISIBLE
REJECTED_NEW_VERSION_DOES_NOT_REPLACE_OLD_PUBLIC_VERSION = YES
```

## 12. Review History

`RESULT_REVIEW_HISTORY_REQUIRED = YES`.

`RESULT_REVIEW_HISTORY_CURRENTLY_IMPLEMENTED = NO`.

The status columns are not a substitute for review history. Each review
record must preserve at least:

- result ID;
- candidate ResultVersion ID;
- review level;
- submitted by and submitted at;
- reviewer ID and reviewed at;
- result (`APPROVED` or `REJECTED`);
- review reason/comment;
- any required takedown reason or platform action reference.

Records are append-only. A rejected candidate remains historically queryable,
while the prior public version remains authoritative for public reads.

The existing `audit_records` table cannot be counted as this history unless a
future implementation proves all required fields, candidate binding,
append-only behavior, and result-specific query semantics.

## 13. Visibility Matrix

Visibility is determined by both actor scope and the result's state:

| Actor / scope | `DRAFT` | `INTERNAL_PUBLISHED` | `PUBLIC` |
| --- | --- | --- | --- |
| Same-school `SCHOOL_ADMIN` | Read/manage | Read/manage | Read/manage; ordinary lifecycle operations remain scoped |
| Other-school `SCHOOL_ADMIN` | Deny | Deny | Deny through school-scoped management |
| Same-school `STUDENT` | Deny | Read | Read |
| Other-school `STUDENT` | Deny | Deny | Deny |
| Anonymous | Deny | Deny | Read |
| `SUPER_ADMIN` | Deny for ordinary browsing | Deny for ordinary browsing | Platform governance read as explicitly required |

The SuperAdmin exception is only the pending-review candidate read required
for platform review. It is not a general production-view override.

Public list/detail queries must return only public result content and must
never leak draft, internal, rejected candidate, review comments, or school
internal metadata.

## 14. Activity Relationship

The V1 relationship is `Activity 1 : 0..1 ActivityResult`. V010 enforces:

- `UNIQUE(activity_id)`;
- `FOREIGN KEY (activity_id, school_id) REFERENCES activities(id, school_id)`;
- `FOREIGN KEY (school_id) REFERENCES schools(id)`.

Activity execution state and ActivityResult state are independent:

- `ENDED` activities explicitly continue to allow result editing and
  publication-related work without changing Activity execution status;
- Activity cancellation preserves existing results, versions, materials,
  scores, and review records;
- cancellation does not delete a result or automatically change its public
  status;
- whether cancellation permits creation of a new result or further result
  mutation is `OPEN_PRODUCT_DECISION`, except that preservation is mandatory.

Creation trigger remains:

```text
RESULT_CREATION_TRIGGER = OPEN_PRODUCT_DECISION
```

The current planning evidence makes creation an internal use case. The next
implementation phase must choose one authorized product entry:

- Activity creation creates the one result automatically;
- SchoolAdmin first opens result editing and the system lazily creates it;
- SchoolAdmin explicitly creates the result through a product flow.

No choice is implied by the current service method alone.

## 15. Media Dependency Boundary

`ACTIVITY_RESULT_REQUIRES_MEDIA_CLOSURE = NO`.

`MEDIA_DEPENDENCY_BLOCKER = NO`, provided that `mediaRefs` remains optional.

`MEDIA_REFS_V1_RULE`:

- no `mediaRefs` is valid and must support the complete ActivityResult
  lifecycle;
- provided references are UUIDs to existing Media records;
- a reference must belong to the same Activity and school;
- a new result may reference only Media that is internally approved and
  usable under the current Media contract;
- media upload, binary storage, Media review, public Media publication, and
  Media authorization closure are separate capabilities;
- a Media status change may cause the specified result anomaly workflow, but
  that integration is a later implementation slice.

This boundary does not revive the Teacher role or turn Media into an implicit
dependency of the ActivityResult product.

## 16. Persistence And Migration Decision

Current schema evidence:

- `activity_results` exists with both status fields, both current-version
  pointers, timestamps, `version`, the one-result-per-activity unique
  constraint, and same-school foreign-key invariant;
- `result_versions` exists with the required immutable-snapshot columns and
  cycle-safe foreign keys;
- no result review-history schema exists;
- no JPA or repository mapping exists for `result_versions`;
- no candidate-version review binding or format-edit history is persisted.

```text
ACTIVITY_RESULT_CLOSURE_MIGRATION_REQUIRED = CONDITIONAL
```

No migration is created in this phase. A future implementation may need a new
additive migration if review history, candidate metadata, takedown reason,
format-edit records, or stronger database immutability constraints cannot be
represented by the existing V010 schema. V010 must remain unchanged.

## 17. Optimistic Locking Finding

`ActivityResultEntity` declares:

```java
@Version
private int version;
```

However:

- `ActivityResult` has no domain version field;
- `ActivityResultPersistenceMapper.toEntity` does not copy a domain version;
- `ActivityResultPersistenceMapper.toDomain` does not restore a version;
- mapper and repository tests do not assert version round-trip;
- the adapter maps an aggregate to a fresh entity on save.

Therefore:

```text
ACTIVITY_RESULT_OPTIMISTIC_LOCK_VERSION_ROUND_TRIP = BROKEN
```

This is a required follow-up for correctness closure. It is intentionally not
fixed here.

## 18. Required API Set

The final API must be derived from the use-case contract, not mechanical CRUD.
The minimum required set is:

| Use case | Actor/scope | API requirement | V1 status |
| --- | --- | --- | --- |
| Create or obtain the one result | Same-school `SCHOOL_ADMIN` through resolved creation trigger | Authorized product entry; no anonymous create | `REQUIRED_V1`, trigger `OPEN_PRODUCT_DECISION` |
| List school results | Same-school `SCHOOL_ADMIN` | Scoped management list | `REQUIRED_V1` |
| Read school result detail | Same-school `SCHOOL_ADMIN` | Scoped detail including candidate/version state | `REQUIRED_V1` |
| Edit draft/core content | Same-school `SCHOOL_ADMIN` | Creates immutable candidate version when required | `REQUIRED_V1` |
| Format-only edit | Same-school `SCHOOL_ADMIN` | Preserves public status and appends edit record | `REQUIRED_V1` |
| Publish internally | Same-school `SCHOOL_ADMIN` | `DRAFT -> INTERNAL_PUBLISHED`, pointer update | `REQUIRED_V1` |
| Withdraw internally | Same-school `SCHOOL_ADMIN` | Atomic public takedown when applicable | `REQUIRED_V1` |
| Return to draft | Same-school `SCHOOL_ADMIN` | `INTERNAL_WITHDRAWN -> DRAFT` | `REQUIRED_V1` |
| Submit public review | Same-school `SCHOOL_ADMIN` | Bind exact candidate version | `REQUIRED_V1` |
| Pending review list/detail | `SUPER_ADMIN` | Candidate-only governance read | `REQUIRED_V1` |
| Approve public review | `SUPER_ADMIN` | Candidate -> `PLATFORM_APPROVED` | `REQUIRED_V1` |
| Reject public review | `SUPER_ADMIN` | Candidate rejection with reason | `REQUIRED_V1` |
| Make public | Same-school `SCHOOL_ADMIN` | Approved candidate becomes current public version | `REQUIRED_V1` |
| Resolve allowed anomaly | Same-school `SCHOOL_ADMIN` | Restore only without core change | `REQUIRED_V1` |
| Platform takedown | `SUPER_ADMIN` | Emergency takedown with reason | `REQUIRED_V1` |
| Same-school read | Same-school `STUDENT` | Read authorized internal/public result | `REQUIRED_V1` |
| Public list/detail | Anonymous | Return only `PUBLIC` current version | `REQUIRED_V1` |
| Cross-school read/mutation | Any non-governance actor | Deny; no data leakage | `REQUIRED_V1` |

The existing `POST /api/v1/activity-results/{id}/publish` is only a partial
internal-publication endpoint and must not be treated as the completed API
contract.

## 19. Required Frontend Set

`SCHOOL_ADMIN_RESULT_MANAGEMENT_UI = REQUIRED`.

The SchoolAdmin UI must provide:

- result list and authoritative detail;
- content editing with title, summary, score highlights, optional media
  references, and version state;
- internal publish, withdraw, and return-to-draft;
- public-review submission;
- approved-candidate ordinary public publication;
- anomaly handling and reset actions allowed by the state machine;
- reload-safe state hydration and no cross-school leakage.

`SUPER_ADMIN_RESULT_REVIEW_UI = REQUIRED`: pending candidate list/detail,
approve, reject-with-reason, and emergency takedown-with-reason.

`PUBLIC_RESULT_READ_UI = REQUIRED`: public result list/detail based on the
current public version.

`STUDENT_SAME_SCHOOL_RESULT_READ_UI = REQUIRED`: authorized same-school
result read without mutation controls.

No Teacher workspace, result editor, or result review path may be added.

## 20. PostgreSQL Integration Requirements

The implementation must verify at least:

1. Result creation and persistence round-trip.
2. One Activity has at most one ActivityResult.
3. Same-school ActivityResult foreign-key invariant.
4. ResultVersion immutable sequencing per result.
5. `currentInternalVersionId` points to the internally published version.
6. `currentPublicVersionId` points to the publicly visible version.
7. A new candidate under review does not overwrite the current public version.
8. A rejected candidate preserves the current public version.
9. Internal withdrawal from `PUBLIC` atomically causes
   `PLATFORM_TAKEDOWN`.
10. Stale ActivityResult update is rejected by optimistic locking.
11. Review history is append-only and candidate-bound.
12. Same-school and cross-school queries are isolated.
13. Public reads exclude candidates, drafts, review history, and internal
    fields.
14. Candidate/public pointer changes and status changes are transactionally
    consistent.

## 21. Browser E2E Requirements

The final browser acceptance must cover:

| E2E | Scenario |
| --- | --- |
| E2E-1 | SchoolAdmin edits a result, internally publishes it, reloads, and sees authoritative state |
| E2E-2 | SchoolAdmin submits public review, SuperAdmin approves, SchoolAdmin makes public, anonymous user reads it |
| E2E-3 | SuperAdmin rejects, SchoolAdmin corrects, and resubmits |
| E2E-4 | Cross-school read and mutation denial / IDOR protection |
| E2E-5 | Student cannot manage ActivityResult |
| E2E-6 | SuperAdmin cannot perform ordinary SchoolAdmin publication |
| E2E-7 | Public result internal withdrawal automatically produces platform takedown |
| E2E-8 | Core edit creates a new version, requires re-review, and leaves the old public version visible during review |
| E2E-9 | Successful replacement switches public read to the approved and published version |
| E2E-10 | Page reload preserves authoritative result, version, and review state |

E2E must also prove no public response leaks rejected candidate content,
review comments, or internal-only fields.

## 22. Explicit Non-Goals

This contract does not authorize:

- Teacher revival;
- Media upload or Media review closure;
- Notification implementation;
- Audit Center implementation;
- Ranking semantic changes;
- Score correction or Effective Score redesign;
- ActivityApplication revival;
- Production Readiness implementation;
- generic user-management expansion;
- Activity execution state changes;
- automatic ResultVersion creation until the trigger decision is accepted;
- migration changes to V010 in this phase.

## 23. Implementation Slices

Implementation is intentionally deferred. The proposed order is:

| Slice | Scope |
| --- | --- |
| A | ActivityResult and ResultVersion domain/persistence correctness |
| B | SchoolAdmin content and internal lifecycle |
| C | SuperAdmin public review lifecycle |
| D | Public and same-school queries plus visibility |
| E | SchoolAdmin, SuperAdmin, Student, and public frontend vertical slices |
| F | PostgreSQL, authorization, and browser E2E |
| G | Correctness closure: pointers, candidate isolation, history, locking, concurrency |
| H | Final acceptance and ActivityResult Product Seal |

Every slice must preserve the contract in the use-case table below.

## 24. Acceptance Gate

The following table is the authoritative V1 use-case contract. Future
implementation must not diverge from it without a new accepted decision.

| Use Case | Actor | Scope | Precondition | State Before | State After | Version Effect | Visibility Effect | API Requirement | Frontend Requirement | V1 Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create/obtain result | SchoolAdmin | Same school; one per Activity | Resolved creation trigger; Activity relationship valid | No result | `DRAFT` / `NOT_SUBMITTED` | Create first candidate or obtain existing draft | No public visibility | Authorized product entry; no anonymous create | SchoolAdmin entry point | Required; trigger open | ADR-004; V010; interface planning |
| Edit core content | SchoolAdmin | Same school | Draft/editable result; content valid | Existing candidate or current result | Candidate version; public status reset as required | New immutable ResultVersion | Old public version remains visible during review | Authorized edit command | Result editor | Required | 08 §6.3; ADR-004 |
| Edit format only | SchoolAdmin | Same school | No semantic change | Existing version | Same public state | Edit record; version may remain same | Visibility preserved | Authorized format-edit command | Format edit with audit display | Required | 08 §6.3; ADR-004 |
| Publish internally | SchoolAdmin | Same school | Valid draft candidate | `DRAFT` | `INTERNAL_PUBLISHED` | Set `currentInternalVersionId` | Same-school internal visibility opens | Internal publish command | Publish action | Required | 08 §6.5; ADR-004 |
| Withdraw internally | SchoolAdmin | Same school | Internally published result | `INTERNAL_PUBLISHED` | `INTERNAL_WITHDRAWN`; public takedown if public | Preserve versions; no public restore | Internal visibility closes; public takedown is atomic | Internal withdraw command | Withdraw action | Required | 08 §6.5; ADR-004 |
| Return to draft | SchoolAdmin | Same school | Internally withdrawn result | `INTERNAL_WITHDRAWN` | `DRAFT` | No automatic public pointer restore | Public status stays non-restored | Return-to-draft command | Return action | Required | ADR-004 |
| Submit public review | SchoolAdmin | Same school | Internal published; candidate valid | `NOT_SUBMITTED` | `PENDING_PUBLIC_REVIEW` | Bind candidate version | Old public version remains visible | Submit-review command | Submit action | Required | 08 §6.5 |
| Review candidate | SuperAdmin | Platform governance; candidate only | Pending candidate | `PENDING_PUBLIC_REVIEW` | Approved or rejected | Append review record bound to candidate | No direct public publish | Approve/reject commands | Review queue/detail | Required | 08 §6.3/6.5; identity baseline |
| Make public | SchoolAdmin | Same school | Platform-approved candidate | `PLATFORM_APPROVED` | `PUBLIC` | Switch `currentPublicVersionId` atomically | Candidate becomes public | Ordinary publish command | Publish action | Required | 08 §6.2/6.3; ADR-004 |
| Resolve anomaly | SchoolAdmin | Same school | Anomaly resolved; no core change | `ANOMALY_PENDING` | `PUBLIC` or reset path | No new version unless content changes | Public visibility restores only when permitted | Anomaly resolution command | Anomaly workflow | Required | 08 §6.3 |
| Platform takedown | SuperAdmin | Platform governance | Public or anomaly result; reason required | `PUBLIC` / `ANOMALY_PENDING` | `PLATFORM_TAKEDOWN` | Preserve history and public version | Public visibility closes | Takedown command | Takedown action | Required | 08 §6.3/6.5 |
| Same-school result read | Student or SchoolAdmin | Same school | Authorized state | Internal/public result | No state change | No version change | Return only allowed version/content | Scoped list/detail query | Student/SchoolAdmin read UI | Required | identity baseline; 08 |
| Public result read | Anonymous | Public only | `PUBLIC` current version | `PUBLIC` | No state change | Return `currentPublicVersionId` | Public list/detail only | Public list/detail query | Public result UI | Required | 08 §6.4/6.5 |
| Cross-school access | Non-governance actor | Other school | Any result state | Any | Denied | No effect | No existence or content leakage | Scoped denial contract | No visible management path | Required | identity baseline; ADR-004 |

The acceptance gate is not passed until all of the following are true:

- ActivityResult remains an independent aggregate with the `1 : 0..1`
  Activity relationship;
- ResultVersion is implemented as an immutable, candidate-bound snapshot;
- content and JSON validation are explicit and fail closed;
- review history is durable, append-only, and queryable;
- both version pointers are transactionally correct;
- SchoolAdmin and SuperAdmin responsibilities remain separate;
- same-school, cross-school, student, anonymous, and pending-review visibility
  are tested;
- optimistic-lock version round-trip is repaired and tested;
- required API and frontend flows exist without Teacher or generic-account
  expansion;
- PostgreSQL integration and browser E2E requirements pass;
- `python scripts/validate_business_specs.py` reports zero errors;
- the capability inventory is updated only in a separate accepted stage after
  the complete vertical slice is proven.
