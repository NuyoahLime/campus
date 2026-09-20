# ActivityResult Format-only Edit Baseline v1

Status: `CONTRACT_BASELINE_READY_FOR_INDEPENDENT_REVIEW`

This document extends the sealed ActivityResult closure contract. It does not
redefine ResultVersion lifecycle, review lifecycle, D2 governance, or read and
visibility authority.

## Boundary

`CORE_EDIT` changes business facts, meaning, review content, score meaning,
media references, or the identity of the reviewed object. It creates a new
immutable ResultVersion.

`FORMAT_ONLY_EDIT` changes only machine-verifiable presentation metadata over
one already-authorized ResultVersion. It does not insert, delete, replace, or
reorder base text and does not change review or visibility state.

The following are core edits: title changes, spelling or character changes,
scoreHighlights changes, and mediaRefs add/remove/replace/reorder. Title,
scoreHighlights, and mediaRefs are never format-only fields. Summary formatting
is allowed only through the structured payload below; arbitrary summary text
replacement is a core edit.

## Target Eligibility

```text
FORMAT_EDIT_ELIGIBLE_TARGET =
  exact currentInternalVersionId OR exact currentPublicVersionId
```

The target must belong to the requested ActivityResult and same-school
authorization must pass. Activity state must be allowed, and ANOMALY_PENDING,
PLATFORM_TAKEDOWN, blocked historical-only public targets, and non-current
historical versions are denied.

Candidate identity alone never grants eligibility:

```text
CANDIDATE_ONLY_FORMAT_EDIT =
  versionId == currentCandidateVersionId
  AND versionId != currentInternalVersionId
  AND versionId != currentPublicVersionId
```

Candidate-only targets are denied. A candidate is eligible only when it is also
the exact current internal or public pointer and is not review-bound.

Review-bound target:

```text
REVIEW_BOUND_TARGET =
  versionId == currentCandidateVersionId
  AND publicStatus IN (
    PENDING_PUBLIC_REVIEW,
    PLATFORM_APPROVED,
    PLATFORM_REJECTED
  )
```

`FORMAT_EDIT_REVIEW_BOUND_TARGET = DENY`.

The resulting matrix is:

| State | Exact internal/public target | Candidate-only target | Old exact public target |
|---|---:|---:|---:|
| `NOT_SUBMITTED` | Allow | Deny | Allow if exact public pointer |
| `PENDING_PUBLIC_REVIEW` | Deny when it is the review-bound candidate | Deny | Allow |
| `PLATFORM_APPROVED` | Deny when it is the review-bound candidate | Deny | Allow |
| `PLATFORM_REJECTED` | Deny when it is the review-bound candidate | Deny | Allow |
| `PUBLIC` | Allow exact current pointer | Deny | N/A |
| `PLATFORM_TAKEDOWN` | Deny | Deny | Deny |
| `ANOMALY_PENDING` | Deny | Deny | Deny |

First-publication review-bound V1 is denied while `publicStatus` is
`PENDING_PUBLIC_REVIEW` or `PLATFORM_APPROVED`; it becomes eligible only after
the public workflow has produced an exact public pointer. A rejected candidate
is corrected through core edit to a new version, never by overlaying the
rejected review snapshot.

## Immutable Base and Payload

```text
FORMAT_BASE_TEXT = immutable ResultVersion.summaryText
FORMAT_OFFSET_UNIT = Unicode code point index
FORMAT_RANGE_CONVENTION = [startInclusive, endExclusive)
FORMAT_OVERLAY_CHAINING = NO
PLAIN_TEXT_INVARIANT = renderedPlainText == base summaryText
```

The payload may not insert, delete, replace, or reorder text. Removing all
presentation metadata must reproduce the immutable base summary text exactly.
Unknown keys are rejected with controlled `400`; null collections normalize to
empty arrays; the server canonicalizes ordering before persistence.

V1 payload is a structured object, not an arbitrary JSON blob:

```json
{
  "paragraphs": [{"start": 0, "end": 18, "style": "NORMAL"}],
  "emphasisRanges": [{"start": 3, "end": 8, "style": "BOLD"}]
}
```

`paragraphs` are ordered, contiguous, non-overlapping ranges covering the full
base text. V1 paragraph style is `NORMAL`; `BULLET`, `ORDERED`, `HEADING_1`,
and `HEADING_2` remain reserved for a later product decision unless a consuming
UI requires them. `emphasisRanges` support `BOLD` and `ITALIC`; ranges must be
within one paragraph. Same-style overlap and duplicate exact ranges are
rejected. `MAX_PARAGRAPHS = 200` and `MAX_EMPHASIS_RANGES = 200`.

## Persistence and History

Each format record is a complete immutable full-state overlay, not a delta:

```text
ResultFormatEditRecord(
  id, resultId, resultVersionId, revision, payload,
  reason, editedBy, editedAt
)
```

History is append-only. Existing records are never updated or deleted. The
current authority is an explicit head, never `latest`, `MAX(revision)`,
created-at ordering, or UUID ordering:

```text
result_format_heads(
  resultVersionId PRIMARY KEY,
  resultId,
  currentFormatEditRecordId,
  version,
  updatedAt
)
```

The head and record require database-enforced same-result/version integrity.
Head advancement uses compare-and-set optimistic locking. Append and head
advance are one transaction; a failed CAS rolls back the record, so there is
no orphan format record. A missing head row means no overlay and is valid.
A null current record pointer in an existing head row is invalid.

`reason` is required, trimmed, non-blank, and at most 2000 characters.

## Reads and Lifecycle

Reads first authorize the exact ResultVersion, then resolve that version's
format head, then apply its overlay. Public and Student DTOs expose effective
presentation metadata but no format history, editor, reason, or revision.
Management exposes base text, effective presentation metadata, current format
record ID/revision, and the exact-version history endpoint.

Broken heads fail closed: Public and Student reads return not-found/invisible;
Management returns a controlled `409`. Takedown and reset preserve format
history but do not restore visibility. A new post-reset V2 never inherits V1's
overlay. Format edit never grants visibility and never changes any status,
pointer, review binding, visibility block, or publication timestamp.

Actor policy: same-school active SchoolAdmin only. SuperAdmin, Student, and
Anonymous cannot perform ordinary format edit. Activity `PUBLISHED`,
`IN_PROGRESS`, and `ENDED` permit eligible targets; `DRAFT`, `CANCELLED`,
`ANOMALY_PENDING`, and `PLATFORM_TAKEDOWN` deny ordinary format edit.

## API and Migration

```text
POST /api/v1/activity-results/{resultId}/versions/{versionId}/format-edits
GET  /api/v1/school-admin/activity-results/{resultId}/versions/{versionId}/format-history
```

Request fields are `reason` and `presentation` only. IDs, actor, school,
revision, and timestamps are path- or server-derived. Response fields are
`formatEditId`, `resultId`, `resultVersionId`, `revision`, `presentation`,
`reason`, and `editedAt`.

`NEW_MIGRATION_REQUIRED = YES`, with additive tables
`result_format_edit_records` and `result_format_heads`. ResultVersion remains
immutable; ResultReviewRecord and generic audit records are not reused as
authoritative format history.

## Required Test Matrix

The implementation baseline must cover FMT-01 through FMT-55, including:

- immutable version and pointer/status/timestamp invariants;
- candidate-only, review-bound, old-public, first-publication, takedown,
  reset, anomaly, cancelled, and historical-target gates;
- exact-version overlay isolation and public/student/management projection;
- unknown keys, Unicode code-point bounds, plain-text invariant, paragraph and
  emphasis validation, canonical persistence, and payload limits;
- append-only history, explicit head authority, same-result constraints,
  concurrent first edit, CAS conflict, orphan rollback, missing head, and
  wrong-version head failure behavior.

```text
FORMAT_TEST_MATRIX_TOTAL = 55_OR_MORE
FORMAT_TEST_MATRIX_READY = YES
```

This baseline is ready for independent review. No implementation, migration,
test, frontend, D3, or capability-inventory change is included in this stage.
