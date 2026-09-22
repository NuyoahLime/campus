# ActivityResult Frontend Support Query Baseline v1

Status: proposed, ready for independent review

Authoritative master: `88db766d2b46ed43e77f0b15ab39a5f370d36332`

Authoritative frontend baseline: `docs/decision/activity-result-frontend-baseline-v1.md`

This baseline defines additive read-side support only. It does not change ActivityResult mutation semantics, role or authorization semantics, migrations, capability inventory, or product seal status.

## 1. Scope and gaps

`SUPPORT_QUERY_GAP_COUNT = 2`:

- Gap A: `SUPERADMIN_GOVERNANCE_DISCOVERY_READ`.
- Gap B: `SUPERADMIN_REVIEW_ACTIVITY_EXECUTION_STATUS`.

No frontend or backend implementation is authorized by this document. The product remains `ACTIVITY_RESULT_PRODUCT = NOT_SEALED`.

## 2. Governance discovery and detail

The additive governance endpoints are:

- `GET /api/v1/super-admin/activity-results/governance`
- `GET /api/v1/super-admin/activity-results/{id}/governance`

Both are SuperAdmin-only reads and require the existing platform governance authorization, with controller and application-service defense in depth. SchoolAdmin, Student, and anonymous callers are denied. Arbitrary SuperAdmin browsing is denied: governance scope is exactly `currentPublicVersionId != null`.

Never-public results are excluded. Results remain in governance scope when they retain a current public pointer, regardless of public workflow status: `PUBLIC`, `NOT_SUBMITTED`, `PENDING_PUBLIC_REVIEW`, `PLATFORM_APPROVED`, `PLATFORM_REJECTED`, `ANOMALY_PENDING`, or `PLATFORM_TAKEDOWN`.

`CANCELLED` Activities do not remove an in-scope result. Takedown and reset safety governance remain available on cancelled Activities; the ordinary Activity mutation gate does not apply to these platform actions.

### 2.1 Governance list contract

The list returns `PageResponse<GovernanceActivityResultSummary>` with `page` defaulting to `0`, `size` defaulting to `20`, and `size` constrained to `1..100`.

Filters are:

- `publicStatus`, validated against `ResultPublicStatus`;
- `blocked`, a boolean;
- `q`, trimmed and case-insensitive over school name or activity title.

Candidate title, reviewer name, raw UUID fragments, arbitrary JSON, and partial version searches are not list filters. Ordering is deterministic: `activity_results.updated_at DESC, activity_results.id DESC`.

`GovernanceActivityResultSummary` contains:

`resultId`, `schoolId`, `schoolName`, `activityId`, `activityTitle`, `activityExecutionStatus`, `internalStatus`, `publicStatus`, `publicVisibilityBlocked`, `currentPublicVersionId`, and `updatedAt`.

It does not contain summary text, highlights, media refs, candidate/internal projections, review history, or format payload. List rows discover state and open detail; mutations belong on detail.

The query scope is equivalent to:

```sql
FROM activity_results ar
JOIN activities a ON a.id = ar.activity_id AND a.school_id = ar.school_id
JOIN schools s ON s.id = ar.school_id
WHERE ar.current_public_version_id IS NOT NULL
```

The implementation must not narrow this to `publicStatus == PUBLIC`.

### 2.2 Governance detail contract

Detail is `GET /api/v1/super-admin/activity-results/{id}/governance` and returns `GovernanceActivityResultDetail` only when `currentPublicVersionId != null`. Never-public or out-of-scope resources return controlled not-found. It is not a SchoolAdmin management detail, pending-review detail, or public endpoint.

The detail contains `resultId`, `schoolId`, `schoolName`, `activityId`, `activityTitle`, `activityExecutionStatus`, `internalStatus`, `publicStatus`, `publicVisibilityBlocked`, `currentCandidateVersionId`, `currentInternalVersionId`, `currentPublicVersionId`, exact `currentPublicVersion`, governance history, and `updatedAt`.

`currentPublicVersion` must be loaded by the exact `currentPublicVersionId` pointer. `MAX(version_number)`, latest ordering, candidate fallback, and internal fallback are prohibited. The projection uses the immutable version plus safe effective presentation; raw HTML and `v-html` are prohibited.

If the pointer is missing or belongs to another result, the read fails closed with a controlled consistency error. It must not return another version or pretend that the result never existed.

### 2.3 Governance history and reload

`GovernanceActivityResultHistoryEntry` is append-only and contains only `TAKEDOWN` and `RESET` actions: `id`, `resultVersionId`, `action`, `reviewerId`, `reviewedAt`, `reason`, and `createdAt`. Order is `createdAt ASC, id ASC`. History covers all versions of the ActivityResult, not only the current public pointer.

After takedown, detail remains readable with the exact historical current public version, `publicStatus = PLATFORM_TAKEDOWN`, and `publicVisibilityBlocked = true`. After reset, detail remains readable and discoverable with `publicStatus = NOT_SUBMITTED`, `publicVisibilityBlocked = true`, the historical public pointer retained, and `currentCandidateVersionId = null`. Takedown and reset actions are unavailable in those respective terminal states, while SchoolAdmin later performs normal replacement recovery.

The browser contract is: list -> detail -> takedown -> detail reload -> list rediscovery -> detail -> reset -> detail reload. Local storage, manual UUID entry, transient router state, public-result scanning, and N+1 probes are prohibited.

Frontend affordance may show Takedown when the public pointer is non-null, unblocked, and status is not `PLATFORM_TAKEDOWN`; it may show Reset when status is `PLATFORM_TAKEDOWN`, blocked is true, public pointer is non-null, and candidate is null. Server authorization remains final.

## 3. Pending-review support query

`PendingResultReviewSummary` and `PendingResultReviewDetail` retain their existing candidate-bound fields and add, without removal or renaming:

- `schoolName`;
- `activityTitle`;
- `activityExecutionStatus`.

Both summary and detail must contain all three fields. The query joins `activity_results` to `activities` on both activity ID and school ID, and to `schools`, avoiding frontend N+1 activity or school requests.

The pending queue definition remains unchanged: `publicStatus = PENDING_PUBLIC_REVIEW` and the latest exact candidate review action is `SUBMITTED`, ordered by `submitted_at ASC, result_id ASC`, with page/size semantics `page >= 0`, `1..100`, default `0/20`.

The queue does not filter out `DRAFT` or `CANCELLED` Activity execution states. A pending review on a cancelled Activity remains in the queue and detail remains readable, but Approve and Reject are non-actionable. The same applies to anomalous pending reviews on draft Activities. Candidate and review history remain visible; the server mutation guard remains final.

Frontend Approve/Reject affordance requires public status `PENDING_PUBLIC_REVIEW` and Activity state `PUBLISHED`, `IN_PROGRESS`, or `ENDED`. `DRAFT` and `CANCELLED` disable or hide both actions. No new `reviewActionable`, `canApprove`, or `canReject` mutation field is required.

If cancellation occurs after a detail page was opened, stale approval or rejection is denied by the server; the frontend shows a controlled conflict, refetches authoritative detail, renders `CANCELLED`, and records no new decision history.

Review history remains candidate-bound and unchanged, including `SUBMITTED`, `APPROVED`, `REJECTED`, and other existing actions. This support query does not alter candidate binding, ordering, membership, or history schema.

## 4. Mutation and schema boundaries

Takedown, reset, submit, approve, reject, and make-public mutation contracts are unchanged. `NO_MUTATION_CONTRACT_CHANGE_REQUIRED = YES`.

No database migration is required. Existing fields in `activities`, `schools`, `activity_results`, `result_versions`, and `result_review_records` are sufficient. `DATABASE_MIGRATION_REQUIRED = NO`.

Read-side creation is absent: governance and review reads must not create ActivityResult, ResultVersion, or history rows, and must not update `updatedAt`.

## 5. Recommended implementation boundary

The future backend patch should use:

- `ActivityResultGovernanceQueryService`;
- `ActivityResultGovernanceQueryPort`;
- `ActivityResultGovernanceQueryAdapter`;
- `GovernanceActivityResultSummary`;
- `GovernanceActivityResultDetail`;
- `GovernanceActivityResultHistoryEntry`;
- the existing `SuperAdminActivityResultGovernanceController`, extended with GET routes;
- additive fields on `PendingResultReviewSummary` and `PendingResultReviewDetail`;
- the existing review query adapter.

The future implementation must include PostgreSQL integration and authorization tests, but this stage implements none of them.

## 6. Acceptance matrix

Governance query cases: GQ-01 never-public excluded; GQ-02 public included; GQ-03 replacement `NOT_SUBMITTED` with old public pointer included; GQ-04 replacement pending with old pointer included; GQ-05 takedown included; GQ-06 reset blocked included; GQ-07 cancelled Activity included; GQ-08 exact pointer detail; GQ-09 takedown reload; GQ-10 reset reload; GQ-11 TAKEDOWN/RESET-only history; GQ-12 history survives replacement; GQ-13 broken pointer fails closed; GQ-14 filters; GQ-15 deterministic pagination; GQ-16 non-SuperAdmin denied; GQ-17 reads create nothing.

Review support cases: RQ-01 summary school name; RQ-02 summary activity title; RQ-03 summary execution status; RQ-04 detail fields; RQ-05/06/07 allowed Activity states actionable; RQ-08 cancelled remains queued; RQ-09 cancelled detail readable; RQ-10 draft anomaly readable; RQ-11/12 cancelled approve/reject denied; RQ-13 ordering unchanged; RQ-14 exact candidate binding unchanged; RQ-15 non-SuperAdmin denied.

This support baseline unlocks FE-E2E-25 through FE-E2E-31 and subsequently FE-D and FE-E. Until implementation and review complete, `FE-D` and `FE-E` remain blocked by support query.

## 7. Frozen report values

```text
GOVERNANCE_LIST_ENDPOINT = GET /api/v1/super-admin/activity-results/governance
GOVERNANCE_DETAIL_ENDPOINT = GET /api/v1/super-admin/activity-results/{id}/governance
GOVERNANCE_DISCOVERY_SCOPE = CURRENT_PUBLIC_POINTER_NON_NULL
SUPERADMIN_ARBITRARY_RESULT_BROWSING = DENY
GOVERNANCE_EXACT_PUBLIC_POINTER = REQUIRED
GOVERNANCE_BROKEN_POINTER_POLICY = FAIL_CLOSED
GOVERNANCE_HISTORY_SCOPE = TAKEDOWN_RESET_ALL_VERSIONS
GOVERNANCE_LIST_FILTERS = publicStatus_blocked_q
GOVERNANCE_SORT = updatedAt_DESC_resultId_DESC
GOVERNANCE_READ_SIDE_CREATION = ABSENT
GOVERNANCE_AUTHORIZATION = SUPER_ADMIN_ONLY
TAKEDOWN_RELOAD_CONTRACT = DEFINED
RESET_RELOAD_CONTRACT = DEFINED
REVIEW_SUMMARY_ADD_SCHOOL_NAME = YES
REVIEW_SUMMARY_ADD_ACTIVITY_TITLE = YES
REVIEW_SUMMARY_ADD_ACTIVITY_EXECUTION_STATUS = YES
REVIEW_DETAIL_ADD_SCHOOL_NAME = YES
REVIEW_DETAIL_ADD_ACTIVITY_TITLE = YES
REVIEW_DETAIL_ADD_ACTIVITY_EXECUTION_STATUS = YES
REVIEW_PENDING_QUERY_EXECUTION_FILTER = NONE
CANCELLED_PENDING_REVIEW_IN_QUEUE = YES
CANCELLED_PENDING_REVIEW_DETAIL = READABLE
DRAFT_PENDING_REVIEW = READABLE_NON_ACTIONABLE
REVIEW_ACTIONABLE_ACTIVITY_STATES = PUBLISHED_IN_PROGRESS_ENDED
REVIEW_NON_ACTIONABLE_ACTIVITY_STATES = DRAFT_CANCELLED
REVIEW_N_PLUS_ONE_ACTIVITY_FETCH = PROHIBITED
REVIEW_N_PLUS_ONE_SCHOOL_FETCH = PROHIBITED
NO_MUTATION_CONTRACT_CHANGE_REQUIRED = YES
DATABASE_MIGRATION_REQUIRED = NO
GOVERNANCE_TEST_CASES = 17
REVIEW_SUPPORT_TEST_CASES = 15
BLOCKED_FE_E2E_UNLOCK_RANGE = 25_TO_31
ACTIVITY_RESULT_PRODUCT = NOT_SEALED
```
