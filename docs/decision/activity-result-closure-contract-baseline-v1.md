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
| Planning labels `ActivityResult.create` as `INTERNAL` | interface-layer planning | Do not add a mechanical public create endpoint; creation is frozen as lazy first authorized SchoolAdmin save |
| Activity 1:0..1 invariant is clear, but creation timing was not explicit in the older planning document | ADR-004, Activity code and service | Freeze lazy creation on the first authorized SchoolAdmin save; reads never create rows |
| Content JSON shapes and defensive limits were not specified | 08 and V010 | Freeze the minimum V1 shapes and limits below as implementation limits, not historical source claims |
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
- Result creation is lazy on the first authorized same-school SchoolAdmin save;
  reads never create database rows.
- There is no anonymous or generic public ActivityResult create endpoint.
- A SchoolAdmin create/edit product entry is required even though the exact HTTP
  route is an implementation-slice detail.
- Public review and ordinary public publication remain separate commands.
- Public visibility is based on `currentPublicVersionId` plus takedown/anomaly
  protection, not on `result_public_status == PUBLIC` alone.
- An unapproved replacement candidate never replaces the old public version.
- Media is an optional reference boundary, not a reason to implement the
  Media product.
- ActivityResult implementation must not change Ranking, Score, Activity
  execution, ActivityApplication, Notification, Audit, or Teacher semantics.

The following implementation-blocking decisions are now frozen:

```text
RESULT_CREATION_TRIGGER =
LAZY_CREATE_ON_FIRST_AUTHORIZED_SCHOOL_ADMIN_SAVE

PUBLIC_CREATE_ENDPOINT_REQUIRED = NO
SCHOOL_ADMIN_CREATE_OR_EDIT_PRODUCT_ENTRY_REQUIRED = YES

ALLOWED_ACTIVITY_STATES_FOR_RESULT_CREATE =
PUBLISHED | IN_PROGRESS | ENDED

ALLOWED_ACTIVITY_STATES_FOR_RESULT_EDIT =
PUBLISHED | IN_PROGRESS | ENDED

CANCELLED_RESULT_CREATE = DENY
CANCELLED_RESULT_MUTATION = DENY_EXCEPT_PLATFORM_GOVERNANCE
ACTIVITY_CANCEL_EFFECT_ON_EXISTING_RESULT = PRESERVE

ACTIVITY_CREATE_STATE_MATRIX = FROZEN
ACTIVITY_EDIT_STATE_MATRIX = FROZEN
SUMMARY_TEXT_POLICY = FROZEN
SCORE_HIGHLIGHT_POLICY = FROZEN
MEDIA_REFS_LIMIT_POLICY = FROZEN
PUBLIC_POINTER_STATUS_MATRIX = FROZEN
CURRENT_CANDIDATE_VERSION_REQUIRED = YES
CURRENT_CANDIDATE_VERSION_BINDING = activity_results.currentCandidateVersionId
PUBLIC_VISIBILITY_BLOCK_REQUIRED = YES
PUBLIC_VISIBILITY_BLOCK = activity_results.publicVisibilityBlocked
REPLACEMENT_REVIEW_VISIBILITY = FROZEN
INTERNAL_WITHDRAW_REPLACEMENT_EFFECT = FROZEN
SUPERADMIN_TAKEDOWN_REPLACEMENT_EFFECT = FROZEN
FORMAT_EDIT_IMMUTABILITY_MODEL = FROZEN
RESULT_REVIEW_HISTORY_SCHEMA_CONTRACT = FROZEN
IMPLEMENTATION_BLOCKING_OPEN_PRODUCT_DECISIONS = NONE
```

The lazy-create contract is exact:

1. A same-school SchoolAdmin opens the result editor.
2. The read returns the existing ActivityResult or an empty editor model.
3. The first valid content save creates the ActivityResult and first
   ResultVersion in the same authorized application operation.
4. Later saves edit the existing aggregate and create candidate versions as
   required by the core-content rules.
5. Opening the editor, listing results, and reading detail never create or
   mutate a database row.

The Activity-state decision is also exact:

| Activity execution state | New result create | Result edit | Rationale |
| --- | --- | --- | --- |
| `DRAFT` | Deny | Deny | The Activity has not entered formal execution or display |
| `PUBLISHED` | Allow | Allow | Results may be prepared and maintained before execution |
| `IN_PROGRESS` | Allow | Allow | Results may be maintained during execution |
| `ENDED` | Allow | Allow | Frozen Activity rules explicitly preserve result work after ending |
| `CANCELLED` | Deny | Deny | Cancellation creates no new result fact; existing history is preserved |

`CANCELLED` does not delete an existing result, version, review record, or
format-edit record. Platform safety governance, including emergency takedown,
remains allowed for an existing cancelled-Activity result. Authorized reads
continue to follow the result visibility matrix.

`NON_BLOCKING_FUTURE_DECISIONS` may cover renderer-specific presentation
vocabulary, anomaly-recovery interaction copy, and operational retention
periods, but may not change the creation trigger, Activity-state matrix,
content validation, candidate visibility, format-edit persistence model, or
migration requirement.

## 6. Actor And Authorization Matrix

Runtime actors are limited to the following:

| Actor | Allowed responsibility |
| --- | --- |
| `SCHOOL_ADMIN` | Same-school result editing and internal lifecycle; submit public review; ordinary publication after platform approval; allowed anomaly handling |
| `SUPER_ADMIN` | Read pending public-review candidates; approve/reject public review; emergency platform takedown |
| `STUDENT` | Read only where the visibility and same-school rules allow; never manage or mutate results |
| Anonymous | Read only the currently authorized public version, with takedown/anomaly protection |
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

If the result has an active public pointer, meaning
`currentPublicVersionId != null` and the public snapshot is currently
readable, internal withdrawal must close public visibility in the same
transaction. This applies even when a replacement candidate has public
workflow status `PENDING_PUBLIC_REVIEW`, `PLATFORM_APPROVED`,
`PLATFORM_REJECTED`, or `NOT_SUBMITTED`.

The required cross-machine rule is:

```text
INTERNAL_WITHDRAW_WITH_ACTIVE_PUBLIC_POINTER =
ATOMIC_PLATFORM_TAKEDOWN
```

The transaction must:

- set internal status to `INTERNAL_WITHDRAWN`;
- set public workflow status to `PLATFORM_TAKEDOWN`;
- preserve `currentPublicVersionId` as historical evidence;
- invalidate any pending or approved candidate for ordinary publication;
- prevent that candidate from later being made public without a new legal
  review flow.

Restoring `DRAFT` must not restore public visibility.

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
| Platform takedown with reason | `SUPER_ADMIN` | Any currently visible result with an active public pointer, including replacement-review states -> `PLATFORM_TAKEDOWN` |
| Resolve allowed anomaly | Same-school `SCHOOL_ADMIN` | `ANOMALY_PENDING -> PUBLIC` only when no core content changed |

`SUPER_ADMIN_REVIEW_READ_RULE` is narrow: a SuperAdmin may read the candidate
needed to review a `PENDING_PUBLIC_REVIEW` result and its review context. This
does not grant arbitrary browsing of school-internal `DRAFT`,
`INTERNAL_PUBLISHED`, or `INTERNAL_WITHDRAWN` results.

`SUPER_ADMIN` approval must never directly transition a result to `PUBLIC`.

`SUPERADMIN_TAKEDOWN_WITH_REPLACEMENT_PENDING` is therefore frozen as:

```text
ALLOWED; close public visibility, preserve the old pointer and history,
invalidate the replacement candidate for ordinary publication
```

## 9. Result Content Contract

The authoritative V1 content is:

| Field | Required | Empty behavior | Maximum | Serialization and validation |
| --- | --- | --- | --- | --- |
| `title` | Yes | Trimmed value must be non-blank | 200 characters | Text; store the trimmed value; bounded by the V010 `varchar(200)` column |
| `summaryText` | Yes | Trimmed value must be non-blank | 10,000 characters | Text; store the trimmed value; do not silently truncate |
| `scoreHighlights` | No | `null` or `[]` means no highlights | At most 20 items; each trimmed item at most 200 characters | JSON array of strings; each item must be trimmed and non-blank; invalid payload is controlled `400` |
| `mediaRefs` | No | `null` or `[]` means no media | At most 20 UUIDs | JSON array of UUID strings; duplicate UUIDs are rejected with controlled `400`; same-school and same-Activity eligibility is required |

These are V1 defensive implementation limits, not retrospective claims about
the frozen source documents. The rules are:

- no silent truncation;
- invalid payload, wrong top-level shape, blank item, over-limit item, or
  over-limit array returns controlled `400`;
- `null` and `[]` are normalized consistently as no values;
- stored text and array items use their trimmed values;
- `scoreHighlights` cannot recalculate Ranking, reselect Effective Score,
  modify ScoreAttempt, or modify RankingVersion;
- `mediaRefs` stores IDs, not binary content snapshots;
- malformed JSON, invalid UUID references, or an unauthorized reference must
  fail closed;
- no field may accept a client-supplied actor identity as a substitute for
  SecurityContext authorization.

`SUMMARY_TEXT_POLICY = REQUIRED; TRIMMED_NON_BLANK; MAX_10000; NO_SILENT_TRUNCATION`.
`SCORE_HIGHLIGHT_POLICY = OPTIONAL; MAX_20_ITEMS; EACH_TRIMMED_NON_BLANK_MAX_200`.
`MEDIA_REFS_LIMIT_POLICY = OPTIONAL; MAX_20_UUIDS; DUPLICATES_REJECTED`.

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

`PUBLISHED_RESULT_VERSION_ROW = IMMUTABLE`. A published ResultVersion row must
not be updated directly, including `title`, `summary_text`,
`score_highlights`, `media_refs`, or `format_change_log`.

Format-only changes may keep the same public version only when they do not
change business meaning, review content, or the substance of public
disclosure. They must be stored in an independent append-only
`ResultFormatEditRecord` or equivalent explicit persistence structure. The
minimum record contract is:

- result ID;
- result version ID;
- editor user ID;
- created at;
- change summary;
- presentation-correction payload.

Internal and public reads may apply the latest valid format overlay for
presentation, but the immutable ResultVersion snapshot and the full edit
history remain queryable. Changes to `scoreHighlights` or `mediaRefs` are core
edits and must never be disguised as format-only edits.

```text
FORMAT_EDIT_MODEL =
APPEND_ONLY_FORMAT_EDIT_RECORD_OVER_IMMUTABLE_RESULT_VERSION
```

## 11. Version Pointer Semantics

ActivityResult has three distinct public-publication concepts. They must not
substitute for one another:

```text
ActivityResult.resultPublicStatus = current workflow/governance state
ResultReviewRecord = append-only history and exact candidate binding
currentPublicVersionId = currently exposed immutable public snapshot
```

The three pointers have separate meanings:

| Pointer | Meaning |
| --- | --- |
| `currentCandidateVersionId` | Current editable/review candidate ResultVersion; authoritative after draft save |
| `currentInternalVersionId` | Latest version published internally |
| `currentPublicVersionId` | Authoritative pointer to the currently public immutable ResultVersion |

`currentCandidateVersionId` is required additive V1 persistence. The later
migration must add a nullable UUID column with a same-result foreign key to
`result_versions`; V010 remains unchanged in this stage.

Candidate identity is never inferred from `MAX(version_number)`, timestamps,
UUID ordering, or the latest row. Editor reload reads
`currentCandidateVersionId` directly; candidate-bound workflow state and
append-only review/action history must agree, otherwise fail closed.

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
PUBLIC_BASE_VISIBILITY =
currentPublicVersionId != null AND publicVisibilityBlocked == false

PUBLIC_VISIBILITY_AUTHORITY =
PUBLIC_BASE_VISIBILITY + separately frozen anomaly/media protection

OLD_PUBLIC_VERSION_DURING_REVIEW = KEEP_VISIBLE
REJECTED_NEW_VERSION_DOES_NOT_REPLACE_OLD_PUBLIC_VERSION = YES
```

Public reads do not use `result_public_status == PUBLIC` as the only authority.
After successful `makePublic`, `currentCandidateVersionId` is set to `null`.
Takedown sets `publicVisibilityBlocked = true` while preserving the historical
public pointer; reset to `NOT_SUBMITTED` does not clear the block. Only a
successful new `makePublic` clears it.
The pointer/status/candidate matrix is:

The V1 matrix additionally freezes the persisted candidate and visibility
block columns:

| Status | `currentCandidateVersionId` | `currentInternalVersionId` | `currentPublicVersionId` | `publicVisibilityBlocked` | Public read |
| --- | --- | --- | --- | --- | --- |
| `NOT_SUBMITTED` | null | any | null | false | deny |
| `NOT_SUBMITTED` normal replacement | rejected/new candidate | any | old public | false | allow old public |
| `NOT_SUBMITTED` after takedown | null | any | historical | true | deny |
| `PENDING_PUBLIC_REVIEW` | exact submitted candidate | internal version | old public or null | false | old public only |
| `PLATFORM_APPROVED` | exact approved candidate | internal version | old public or null | false | old public only |
| `PLATFORM_REJECTED` replacement | rejected candidate | internal version | old public | false | allow old public |
| `PUBLIC` | null | public version | public version | false | allow current public |
| `ANOMALY_PENDING` | candidate or null | internal version | public version | policy block | anomaly protection applies |
| `PLATFORM_TAKEDOWN` | null | any | historical | true | deny |

| `result_public_status` | `currentPublicVersionId` | Candidate version | Anonymous/public read result | Management meaning |
| --- | --- | --- | --- | --- |
| `NOT_SUBMITTED` | `null` | None or draft-only | No public result | First publication has not started |
| `NOT_SUBMITTED` | Non-null | None or rejected/reset candidate | Old public version remains readable unless takedown protection applies | Replacement work has not started or was reset; public pointer remains authoritative |
| `PENDING_PUBLIC_REVIEW` | `null` | Candidate bound by submit record | No public result | First publication under review |
| `PENDING_PUBLIC_REVIEW` | Non-null | Replacement candidate bound by submit record | Old public version remains readable | Replacement under review |
| `PLATFORM_APPROVED` | `null` | Approved first-publication candidate | No public result until SchoolAdmin `makePublic` | Approval granted; ordinary publication pending |
| `PLATFORM_APPROVED` | Non-null | Approved replacement candidate | Old public version remains readable | Replacement approved; ordinary publication pending |
| `PLATFORM_REJECTED` | `null` | Rejected first-publication candidate | No public result | Rejected candidate preserved in history |
| `PLATFORM_REJECTED` | Non-null | Rejected replacement candidate | Old public version remains readable | Rejected replacement preserved; old public pointer authoritative |
| `PUBLIC` | Non-null | None | Current public version readable | Public version is live |
| `ANOMALY_PENDING` | Non-null | Optional repair candidate | Public result visibility follows anomaly protection; unsafe referenced material is hidden or protected | Published result needs remediation |
| `PLATFORM_TAKEDOWN` | Any historical pointer value | Any historical candidate | Public read denied | Public visibility closed; pointer/history preserved as evidence |

For first publication, `currentPublicVersionId = null` until SchoolAdmin
`makePublic`. For replacement publication, `currentPublicVersionId` remains the
old version until successful SchoolAdmin `makePublic` switches it to the new
approved candidate. Platform takedown does not physically clear
`currentPublicVersionId`; it overrides visibility.

## 12. Review History

`RESULT_REVIEW_HISTORY_REQUIRED = YES`.

`RESULT_REVIEW_HISTORY_CURRENTLY_IMPLEMENTED = NO`.

The status columns are not a substitute for review history. Each review
record must preserve at least:

- `id`;
- `result_id`;
- `result_version_id`;
- action or review outcome;
- `submitted_by` and `submitted_at`;
- `reviewer_id` and `reviewed_at`;
- reason/comment;
- `created_at`.

Records are append-only. A rejected candidate remains historically queryable,
while the prior public version remains authoritative for public reads.

The history contract must represent:

- `SUBMITTED`;
- `APPROVED`;
- `REJECTED`;
- `TAKEDOWN`, either in the same explicit result action-history table or an
  equally explicit takedown-history structure.

```text
RESULT_REVIEW_HISTORY_STORAGE =
NEW_ADDITIVE_SCHEMA_REQUIRED

RESULT_FORMAT_EDIT_HISTORY_STORAGE =
NEW_ADDITIVE_APPEND_ONLY_SCHEMA
```

The existing `audit_records` table cannot be counted as this history unless a
future implementation proves all required fields, candidate binding,
append-only behavior, and result-specific query semantics.

## 13. Visibility Matrix

Visibility is determined by actor scope, internal status where applicable,
`currentPublicVersionId`, and takedown/anomaly protection. `result_public_status
== PUBLIC` is not sufficient by itself in replacement-review scenarios.

| Actor / scope | `DRAFT` / no internal publication | `INTERNAL_PUBLISHED` | Active public pointer | `PLATFORM_TAKEDOWN` |
| --- | --- | --- | --- | --- |
| Same-school `SCHOOL_ADMIN` | Read/manage | Read/manage | Read/manage; ordinary lifecycle operations remain scoped | Read/manage historical state; may restart allowed flow |
| Other-school `SCHOOL_ADMIN` | Deny | Deny | Deny through school-scoped management | Deny |
| Same-school `STUDENT` | Deny | Read | Read current authorized public version when allowed | Deny public read; internal read depends on internal status only if still allowed |
| Other-school `STUDENT` | Deny | Deny | Deny | Deny |
| Anonymous | Deny | Deny | Read current authorized public version | Deny |
| `SUPER_ADMIN` | Deny for ordinary browsing | Deny for ordinary browsing | Platform governance read only as explicitly required | Platform governance read/action history only |

The SuperAdmin exception is only the pending-review candidate read required
for platform review. It is not a general production-view override.

Public list/detail queries must return only the currently authorized public
ResultVersion and must never leak draft, internal, rejected candidate, review
comments, or school internal metadata. During replacement review, anonymous
and public reads return the old public version. During `PLATFORM_TAKEDOWN`,
public reads are denied even if a historical public pointer remains stored.

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
- cancellation denies new result creation and denies content mutation, except
  for platform safety/takedown governance over already-visible results.

The Activity execution-state matrix is frozen as:

| Activity state | Create result | Edit result | Existing result/read effect |
| --- | --- | --- | --- |
| `DRAFT` | Deny | Deny | No result fact may be created before formal execution/display |
| `PUBLISHED` | Allow | Allow | Same-school SchoolAdmin may prepare or maintain result content |
| `IN_PROGRESS` | Allow | Allow | Same-school SchoolAdmin may maintain result content during execution |
| `ENDED` | Allow | Allow | Frozen spec explicitly allows continued result work |
| `CANCELLED` | Deny | Deny, except platform governance | Existing result/history preserved; reads follow result visibility; safety takedown allowed |

Creation trigger is frozen as:

```text
RESULT_CREATION_TRIGGER =
LAZY_CREATE_ON_FIRST_AUTHORIZED_SCHOOL_ADMIN_SAVE

ALLOWED_ACTIVITY_STATES_FOR_RESULT_CREATE =
PUBLISHED | IN_PROGRESS | ENDED

ALLOWED_ACTIVITY_STATES_FOR_RESULT_EDIT =
PUBLISHED | IN_PROGRESS | ENDED

CANCELLED_RESULT_CREATE = DENY
CANCELLED_RESULT_MUTATION = DENY_EXCEPT_PLATFORM_GOVERNANCE
```

The read path returns an existing ActivityResult or an empty editor model.
Only the first valid SchoolAdmin save creates the database row and first
ResultVersion. Activity creation does not pre-create an empty ActivityResult,
opening the page does not create a row, and anonymous/generic
`POST /activity-results` remains outside V1.

## 15. Media Dependency Boundary

`ACTIVITY_RESULT_REQUIRES_MEDIA_CLOSURE = NO`.

`MEDIA_DEPENDENCY_BLOCKER = NO`, provided that `mediaRefs` remains optional.

`MEDIA_REFS_V1_RULE`:

- no `mediaRefs` is valid and must support the complete ActivityResult
  lifecycle;
- provided references are UUIDs to existing Media records;
- at most 20 references are allowed;
- duplicate references are rejected consistently with controlled `400`;
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
ACTIVITY_RESULT_CLOSURE_MIGRATION_REQUIRED = YES
MIGRATION_REQUIRED = YES
RESULT_REVIEW_HISTORY_STORAGE = NEW_ADDITIVE_SCHEMA_REQUIRED
RESULT_FORMAT_EDIT_HISTORY_STORAGE = NEW_ADDITIVE_APPEND_ONLY_SCHEMA
```

No migration is created in this phase. The implementation phase must plan a
new additive migration because the current schema lacks:

1. candidate-bound public review history;
2. reviewer, decision, reason, and timestamp fields for result review;
3. append-only format-edit history independent of immutable ResultVersion
   rows;
4. durable takedown reason/history;
5. an explicit persistence structure that binds workflow actions to the exact
   candidate ResultVersion.

V010 must remain unchanged. The future migration number must be selected from
the then-current migration head.

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
| Create or obtain the one result | Same-school `SCHOOL_ADMIN` through lazy first-save trigger | Read returns existing result or empty editor; first valid save creates row/version; no anonymous create | `REQUIRED_V1` |
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
| Platform takedown | `SUPER_ADMIN` | Emergency takedown with reason for any active public pointer, including replacement-review states | `REQUIRED_V1` |
| Same-school read | Same-school `STUDENT` | Read authorized internal/public result | `REQUIRED_V1` |
| Public list/detail | Anonymous | Return authorized `currentPublicVersionId` with takedown/anomaly protection | `REQUIRED_V1` |
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
7. Candidate identity comes from candidate-bound review/action history, not
   `MAX(version_number)` or latest row.
8. A new candidate under review does not overwrite the current public version.
9. Public reads return the old public pointer during replacement review.
10. A rejected candidate preserves the current public version.
11. Successful replacement switches `currentPublicVersionId` atomically.
12. Internal withdrawal with any active public pointer causes atomic
    `PLATFORM_TAKEDOWN`, even during replacement review.
13. SuperAdmin emergency takedown closes public visibility even during
    replacement review.
14. Format-only correction uses append-only overlay/history and never updates
    the published ResultVersion row.
15. Cancelled Activity denies new result creation and content mutation while
    preserving existing result/history and allowing platform safety governance.
16. Stale ActivityResult update is rejected by optimistic locking.
17. Review history is append-only and candidate-bound.
18. Same-school and cross-school queries are isolated.
19. Public reads exclude candidates, drafts, review history, and internal
    fields.
20. Candidate/public pointer changes and status changes are transactionally
    consistent.
21. Draft save -> process/page reload reads `currentCandidateVersionId` and
    returns that exact ResultVersion; no latest-row inference is permitted.
22. Takedown -> `NOT_SUBMITTED` preserves `currentPublicVersionId`, keeps
    `publicVisibilityBlocked = true`, and denies anonymous and student reads.
23. A new candidate must complete internal publish -> submit -> approve before
    `makePublic`; reads remain denied until successful publication.
24. Successful replacement publication switches to the new candidate, clears
    the visibility block, sets `PUBLIC`, and clears the candidate pointer.

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
| E2E-7 | Public result internal withdrawal automatically produces platform takedown, including while a replacement candidate is pending |
| E2E-8 | Core edit with no existing public version creates a first-publication candidate and stays invisible until ordinary publication |
| E2E-9 | Core edit with an old public version creates a replacement candidate and leaves the old version visible during review |
| E2E-10 | SuperAdmin rejects replacement candidate and old public version remains authoritative |
| E2E-11 | SuperAdmin approves replacement and SchoolAdmin makes it public; public read switches to the new version |
| E2E-12 | SuperAdmin emergency takedown during replacement review closes public visibility |
| E2E-13 | Format-only correction overlays presentation without mutating the published ResultVersion row |
| E2E-14 | Cancelled Activity preserves existing result/history but denies new result creation and content mutation |
| E2E-15 | Page reload preserves authoritative result, version, review, and format-edit state |

The browser suite must explicitly prove that draft candidate recovery uses
`currentCandidateVersionId`, and that takedown followed by
`NOT_SUBMITTED` does not resurrect old public content. It must also prove that
only successful new `makePublic` clears `publicVisibilityBlocked`.

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
- automatic Activity-created empty ActivityResult rows;
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

| Use Case | Actor | Activity State | Internal State | Public Workflow State | `currentCandidateVersionId` Effect | `currentInternalVersionId` Effect | `currentPublicVersionId` Effect | `publicVisibilityBlocked` Effect | Review / Format Record Effect | Visibility Effect | API Requirement | Frontend Requirement | V1 Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Editor read before first save | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | No row | No row | No effect | No effect | No record | Empty editor model only; no public visibility | Read existing or empty model without side effects | SchoolAdmin editor entry | Required | Interface planning; lazy-create decision |
| First result save / lazy create | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | No row -> `DRAFT` | No row -> `NOT_SUBMITTED` | Remains null until internal publish | Remains null | Create first immutable ResultVersion; no review record | Same-school SchoolAdmin can read draft; Student/anonymous cannot read | Authorized create-or-edit save; no anonymous/generic create | Save action in SchoolAdmin editor | Required | ADR-004; V010; this contract |
| Core edit with no public version | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | Existing editable result | `NOT_SUBMITTED` | Preserve current internal pointer until publish | Remains null | Create new candidate ResultVersion | No anonymous public version exists | Authorized core edit command | Result editor | Required | 08 §6.3; ADR-004 |
| Publish internally | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | `DRAFT` -> `INTERNAL_PUBLISHED` | Preserve current workflow state | Set to internally published version | No change | Optional action history; no public review record | Same-school internal visibility opens | Internal publish command | Publish action | Required | 08 §6.5; ADR-004 |
| Submit first-publication candidate | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | `INTERNAL_PUBLISHED` | `NOT_SUBMITTED` -> `PENDING_PUBLIC_REVIEW` | No change | Remains null | Append candidate-bound `SUBMITTED` record | Anonymous/public read remains denied | Submit-review command | Submit action | Required | 08 §6.5 |
| Approve first-publication candidate | SuperAdmin | Any allowed governance context | `INTERNAL_PUBLISHED` | `PENDING_PUBLIC_REVIEW` -> `PLATFORM_APPROVED` | No change | Remains null | Append candidate-bound `APPROVED` record | Anonymous/public read remains denied until SchoolAdmin publishes | Approve command | Review queue/detail | Required | 08 §6.3/6.5 |
| Make first version public | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | `INTERNAL_PUBLISHED` | `PLATFORM_APPROVED` -> `PUBLIC` | No change | Set to approved candidate | Append ordinary publication action if modeled | Anonymous/public read returns the newly public version | Ordinary make-public command | Publish action | Required | 08 §6.2/6.3; ADR-004 |
| Core edit when old public version exists | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | Existing editable result | Reset/enter replacement workflow | Preserve or later update internal pointer per internal publish | Keep old public version | Create new candidate ResultVersion | Old public version remains readable | Authorized core edit command | Result editor | Required | 08 §6.3; ADR-004 |
| Submit replacement candidate | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | `INTERNAL_PUBLISHED` | `NOT_SUBMITTED` -> `PENDING_PUBLIC_REVIEW` | No change | Keep old public version | Append candidate-bound `SUBMITTED` record | Old public version remains readable | Submit-review command | Submit action | Required | 08 §6.5; this contract |
| Reject replacement candidate | SuperAdmin | Any allowed governance context | No change | `PENDING_PUBLIC_REVIEW` -> `PLATFORM_REJECTED` | No change | Keep old public version | Append candidate-bound `REJECTED` record with reason | Old public version remains authoritative | Reject command | Reject-with-reason action | Required | 08 §6.3/6.5; this contract |
| Approve replacement candidate | SuperAdmin | Any allowed governance context | No change | `PENDING_PUBLIC_REVIEW` -> `PLATFORM_APPROVED` | No change | Keep old public version until SchoolAdmin publish | Append candidate-bound `APPROVED` record | Old public version remains readable | Approve command | Review action | Required | 08 §6.3/6.5; this contract |
| Make replacement public | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | `INTERNAL_PUBLISHED` | `PLATFORM_APPROVED` -> `PUBLIC` | No change | Switch atomically to approved replacement | Append ordinary publication action if modeled | Public read switches to new version; old version remains immutable history | Ordinary make-public command | Publish action | Required | 08 §6.2/6.3; ADR-004 |
| Internal withdraw with active public pointer and candidate pending | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | `INTERNAL_PUBLISHED` -> `INTERNAL_WITHDRAWN` | Candidate state -> `PLATFORM_TAKEDOWN` | Preserve historical internal pointer | Preserve historical public pointer but close visibility | Append withdrawal/takedown action; invalidate pending/approved candidate | Public read denied despite stored pointer | Internal withdraw command with atomic takedown | Withdraw action | Required | ADR-004; this contract |
| SuperAdmin emergency takedown while replacement pending | SuperAdmin | Any allowed governance context | No internal change unless policy requires | Replacement workflow -> `PLATFORM_TAKEDOWN` | No change | Preserve historical public pointer but close visibility | Append takedown action with reason; invalidate pending/approved candidate | Public read denied despite stored pointer | Takedown command allowed for active public pointer | Takedown action | Required | 08 §6.3; this contract |
| Format-only correction over published version | Same-school SchoolAdmin | `PUBLISHED` / `IN_PROGRESS` / `ENDED` | No change | No workflow change | No change | No change | Append format-edit record; do not update published ResultVersion row | Public/internal read may apply latest valid overlay | Format-edit command | Format correction UI/history | Required | 08 §6.3; immutability decision |
| Cancelled Activity preservation / mutation denial | SchoolAdmin or SuperAdmin | `CANCELLED` | Preserve existing state | Preserve existing workflow unless platform takedown occurs | Preserve pointer | Preserve pointer | Preserve history; SuperAdmin takedown may append reasoned action | Existing reads follow result visibility; content mutation and new result creation denied | Deny create/edit; allow platform safety governance | Read-only state plus governance actions | Required | 04 §3; 11 lifecycle spec |
| Same-school result read | Student or SchoolAdmin | Any allowed by visibility | Current internal state | Current workflow state | No effect | No effect | No effect | Return only allowed internal/public version/content | Scoped list/detail query | Student/SchoolAdmin read UI | Required | Identity baseline; 08 |
| Public result read | Anonymous | Any | Any | Any non-takedown state with authorized public pointer | No effect | Read authoritative current public pointer | No effect | Return current public version; deny if pointer null or takedown override active | Public list/detail query | Public result UI | Required | 08 §6.4/6.5; this contract |
| Cross-school access | Non-governance actor | Any | Any | Any | No effect | No effect | No effect | Deny without existence/content leakage | Scoped denial contract | No visible management path | Required | Identity baseline; ADR-004 |

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
