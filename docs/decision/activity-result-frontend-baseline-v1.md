# ActivityResult Frontend Baseline v1

Status: proposed, ready for independent review

Authoritative backend baseline: `578aef87bc2c443dcb1037e4bded18724dbbe502`

The backend correctness patch has merged and been resealed at this baseline. Ordinary ActivityResult mutations are allowed only when the Activity execution state is `PUBLISHED`, `IN_PROGRESS`, or `ENDED`; `DRAFT` and `CANCELLED` deny ordinary mutations. Platform takedown and reset remain the separate safety-governance exception for cancelled Activities.

Scope: product and frontend contract only; no implementation is authorized by this document.

## 1. Purpose and boundaries

This baseline defines the ActivityResult frontend information architecture, routes, API mapping, user-visible state, action availability, formatting interaction, and browser acceptance matrix after backend mainline closure.

The authenticated roles remain exactly `SUPER_ADMIN`, `SCHOOL_ADMIN`, and `STUDENT`. Anonymous access is a public audience, not a role. `TEACHER` must not be introduced or revived, and historical teacher behavior must not be reassigned implicitly.

Runtime screens must use real API data. Fake results, highlights, versions, review records, users, or statistics are prohibited. This baseline does not authorize frontend code, backend code, migrations, capability-inventory changes, D3, Media closure, or Notification work.

## 2. Existing frontend foundation

Implementation must extend the current Vue 3.5, TypeScript, Vite 7, Pinia, Vue Router, and Playwright application. It must reuse:

- `WorkspaceShell.vue` for authenticated workspaces and `PublicShell.vue` for public pages;
- the existing auth store, session restoration, role homes, and router `requiredAuthority` guards;
- `apiRequest()` and its cookie credentials and CSRF-token flow for every ActivityResult API call;
- existing design tokens and workspace, form, table, detail, state, button, and dialog styles.

There must be no second fetch wrapper, JWT/localStorage authentication, parallel layout system, new role system, fake dashboard, or direct `fetch` call for ActivityResult APIs.

Recommended implementation boundaries are `frontend/src/api/activityResult.ts`, `frontend/src/types/activityResult.ts`, and `frontend/src/utils/activityResultLabels.ts`. Types must remain audience-specific (`PublicActivityResult`, `StudentActivityResult`, `ManagementActivityResult`, and `PendingResultReview`), rather than one large optional-field DTO.

## 3. Frozen API inventory

No endpoint may be invented by frontend implementation.

| Audience | Method | Endpoint | Purpose |
| --- | --- | --- | --- |
| SchoolAdmin | GET | `/api/v1/school-admin/activity-results` | Result management list with `page`, `size`, `internalStatus`, `publicStatus`, and `q` |
| SchoolAdmin | GET | `/api/v1/activities/{activityId}/result` | Activity-scoped editor read; returns an empty editor model when no result exists |
| SchoolAdmin | PUT | `/api/v1/activities/{activityId}/result` | Lazy first save or immutable core-content version save |
| SchoolAdmin | GET | `/api/v1/school-admin/activities/{activityId}/result/history` | Lifecycle and review history |
| SchoolAdmin | POST | `/api/v1/activity-results/{id}/publish` | Publish current candidate internally |
| SchoolAdmin | POST | `/api/v1/activity-results/{id}/withdraw` | Withdraw internal publication |
| SchoolAdmin | POST | `/api/v1/activity-results/{id}/return-to-draft` | Return an internally withdrawn result to draft |
| SchoolAdmin | POST | `/api/v1/activity-results/{id}/submit-public-review` | Submit the exact candidate for public review |
| SchoolAdmin | POST | `/api/v1/activity-results/{id}/make-public` | Publish the exact approved candidate publicly |
| SchoolAdmin | POST | `/api/v1/activity-results/{resultId}/versions/{versionId}/format-edits` | Append a format-only edit for an exact immutable version |
| SchoolAdmin | GET | `/api/v1/school-admin/activity-results/{resultId}/versions/{versionId}/format-history` | Read format history for an exact version |
| Student | GET | `/api/v1/student/activities/{activityId}/result` | Read the server-authorized student projection |
| Public | GET | `/api/v1/public/activities/{activityId}/result` | Read the public projection |
| SuperAdmin | GET | `/api/v1/super-admin/activity-results/public-reviews` | List pending public reviews |
| SuperAdmin | GET | `/api/v1/super-admin/activity-results/{id}/public-review` | Read pending review detail |
| SuperAdmin | POST | `/api/v1/super-admin/activity-results/{id}/approve-public-review` | Approve the exact reviewed candidate |
| SuperAdmin | POST | `/api/v1/super-admin/activity-results/{id}/reject-public-review` | Reject the exact reviewed candidate with reason |
| SuperAdmin | POST | `/api/v1/super-admin/activity-results/{id}/takedown` | Block an exact current public version with reason |
| SuperAdmin | POST | `/api/v1/super-admin/activity-results/{id}/reset-takedown` | Reset takedown workflow state with reason without restoring visibility |

## 4. Public and Student information architecture

Public ActivityResult is a section of the existing `/activities/:id` page, after activity and challenge/project information. There is no `/results/:id` route. Activity detail and result are independent reads: a result `404` while activity detail is `200` leaves the activity page usable and renders a normal empty state such as "No activity result has been published yet." It must not be presented as a missing activity.

The public result section shows only `title`, `summaryText`, `scoreHighlights`, `publishedPubliclyAt`, and `presentation`. `versionNumber` may appear as low-emphasis provenance. Candidate/internal/public version IDs, format edit IDs or revisions, reasons, editors, review history, and internal workflow statuses are not public UI.

Student ActivityResult is likewise a section of `/student/activities/:id`; there is no `/student/results/:id`. A result `404` does not turn the activity detail into a `404`. It renders a normal unavailable state because internal publication, takedown, anomaly, or another server-side authority may control visibility.

The Student response's `visibilitySource` is localized centrally: `INTERNAL` means "Internal result" and `PUBLIC` means "Public result". Raw enum values are not product copy, and the source is explanatory only; the server remains the visibility authority.

## 5. SchoolAdmin information architecture

Add "Activity Results" to `schoolAdminNavigation`, ordered after Activities and before Appeals.

- `/school-admin/activity-results` is the result list. It uses the real list filters and shows at least activity title, localized internal status, localized public status, visibility-blocked state, and updated time. Its primary row action opens the activity-scoped result page. It never exposes a school selector.
- `/school-admin/activities/:activityId/result` is the dedicated editor, lifecycle, projection, and history page. The existing `/school-admin/activities/:id` detail provides a "Manage result" entry to it; the full result editor is not embedded in the activity basic-information form.

`GET /activities/{activityId}/result` returning `200` with `resultId = null` is the valid "not created" editor state. Mounting or reading this page must never send a create or save request. Only the user's explicit "Save result" action invokes `PUT`, which performs lazy creation.

### 5.1 Editor and media policy

The core editor changes `title`, `summaryText`, and `scoreHighlights`. It is visually and behaviorally separate from format-only editing. The UI must warn that changing core text can create a new result version and restart publication workflow.

There is no raw media UUID input. `MEDIA_REF_MUTATION_UI = DEFERRED` until a real ActivityResult media selector exists. A core save must preserve the `mediaRefs` obtained from the authoritative editor model; it must not silently send `[]` and delete existing references. If preservation cannot be guaranteed, core save is disabled and reports the controlled limitation.

### 5.2 Management projections

The detail presents three independent projections in product language:

- Candidate version (pending work);
- Internal published version;
- Current public version.

Each existing projection may show version number, title, summary, score highlights, relevant publication timestamps, and presentation. UUIDs are not primary content. A replacement state such as public V1 with candidate/internal V2 must remain visibly distinct so users understand that anonymous and Student audiences can see different versions.

All labels for `internalStatus`, `publicStatus`, `publicVisibilityBlocked`, and `visibilitySource` are centralized. Individual pages must not define divergent status strings.

### 5.3 SchoolAdmin action matrix

The matrix is a frontend affordance contract, not authorization. Activity execution state, pointers, review binding, and block state refine it; the server remains final authority. Unsupported actions are hidden when impossible and disabled with a concise reason when the surrounding workflow context is useful.

`ACTIVITY_RESULT_ORDINARY_ACTION_STATE_GATE = PUBLISHED | IN_PROGRESS | ENDED` applies to save core content, publish internal, withdraw internal, return to draft, submit public review, approve public review, reject public review, make public, and format-only edit. `DRAFT` and `CANCELLED` deny these ordinary actions. Takedown and reset are platform safety-governance actions, not ordinary mutations, and remain allowed on cancelled Activities.

| Action | Show/enable when | Disable or hide when |
| --- | --- | --- |
| Save core content | Allowed Activity state; result absent or not internally withdrawn; public status is not `PENDING_PUBLIC_REVIEW`, `PLATFORM_APPROVED`, or `PLATFORM_TAKEDOWN` | Activity `DRAFT` or `CANCELLED`; internal `INTERNAL_WITHDRAWN`; review-bound/takedown public status |
| Publish internal | Allowed Activity state; result exists; internal `DRAFT`; candidate pointer exists | Activity `DRAFT`/`CANCELLED`, no result/candidate, or internal is not `DRAFT` |
| Withdraw internal | Allowed Activity state; internal `INTERNAL_PUBLISHED` | Activity `DRAFT`/`CANCELLED`, or any other internal status |
| Return to draft | Allowed Activity state; internal `INTERNAL_WITHDRAWN` | Activity `DRAFT`/`CANCELLED`, or any other internal status; action never clears a public block or restores public visibility |
| Submit public review | Allowed Activity state; internal `INTERNAL_PUBLISHED`; public `NOT_SUBMITTED`; non-null candidate and internal pointers identify the same exact version | Activity `DRAFT`/`CANCELLED`, missing/mismatched pointers, another public status, or another internal status. `publicVisibilityBlocked` is not a blocker |
| Make public | Allowed Activity state; public `PLATFORM_APPROVED`; non-null candidate and internal pointers identify the same exact approved version; server approval binding remains authoritative | Activity `DRAFT`/`CANCELLED`, or any other status/pointer relation. `publicVisibilityBlocked` is not a blocker |
| Format-only edit | Activity is `PUBLISHED`, `IN_PROGRESS`, or `ENDED`; public status is neither `ANOMALY_PENDING` nor `PLATFORM_TAKEDOWN`; target is either the exact current internal version while internal is `INTERNAL_PUBLISHED`, or the exact current public version while visibility is not blocked; target is not candidate-only or a review-bound candidate | Activity `DRAFT`/`CANCELLED`; governance-blocked status; candidate-only, review-bound candidate, historical non-current, blocked public, or withdrawn-internal-only target |
| View result history | Existing result and history read is authorized | No result; controlled `403`/scoped `404` |
| View format history | Existing exact result/version | Missing result/version; controlled `403`/scoped `404` |

Once a candidate becomes bound to public review, format-only editing is disabled for that candidate. The old visible public version remains independently eligible when it is still the exact current public version and is not blocked. A backend `409` is final concurrency and state protection, not the primary interaction.

### 5.4 Post-takedown recovery

`publicVisibilityBlocked` guards visibility of the old public pointer. It is not a global ActivityResult workflow lock and does not block a replacement candidate from normal review and publication.

For core save specifically, `publicStatus == PLATFORM_TAKEDOWN` is a deny condition because reset is required first and reset leaves `currentCandidateVersionId == null`. Do not use `publicVisibilityBlocked == true` as a core-save blocker. The reset-first path is the only recovery path for a takedown result.

The frozen recovery path is:

1. `PLATFORM_TAKEDOWN` is reset by SuperAdmin to `NOT_SUBMITTED`; `publicVisibilityBlocked = true`, the historical `currentPublicVersionId` remains V1, and `currentCandidateVersionId = null`.
2. SchoolAdmin saves replacement V2; internal becomes `DRAFT`, public remains `NOT_SUBMITTED`, and the old-public block remains true.
3. SchoolAdmin publishes V2 internally and submits V2 for review while the old-public block remains true.
4. SuperAdmin approves V2 while the block remains true.
5. SchoolAdmin makes V2 public while the block remains true. The server atomically sets `currentPublicVersionId = V2`, clears `currentCandidateVersionId`, sets public status to `PUBLIC`, and sets `publicVisibilityBlocked = false`.

| Recovery state | Submit review | Make public |
| --- | --- | --- |
| Internal `INTERNAL_PUBLISHED`; public `NOT_SUBMITTED`; candidate/internal V2; historical public V1; blocked `true` | Enabled | Not yet; approval is required |
| Public `PLATFORM_APPROVED`; candidate/internal V2; historical public V1; blocked `true` | Not applicable | Enabled |

An implementation equivalent to `if (publicVisibilityBlocked) { disable submit; disable makePublic; }` is prohibited. The block affects old public visibility, public-version format eligibility, public reads, and Student visibility according to server authority; it does not lock the whole workflow.

The same prohibition applies globally: `if (publicVisibilityBlocked) disable save/submit/approve/makePublic` is invalid. After reset, save V2, internal publish V2, submit V2, approve V2, and make public V2 remain available while blocked; successful Make Public changes the public pointer to V2 and clears the block.

### 5.5 Exact format-target eligibility

`FORMAT_EDIT_ACTIVITY_STATE_GATE = PUBLISHED | IN_PROGRESS | ENDED`. Activity `DRAFT` and `CANCELLED` deny formatting. Public status `ANOMALY_PENDING` or `PLATFORM_TAKEDOWN` also denies formatting regardless of retained pointers.

After those gates, an exact target is eligible only when either:

- it equals `currentInternalVersionId` and `internalStatus == INTERNAL_PUBLISHED`; or
- it equals `currentPublicVersionId` and `publicVisibilityBlocked == false`.

A target is nevertheless denied when it is candidate-only, meaning it equals `currentCandidateVersionId` but neither current internal nor current public; or when it is the current candidate bound to `PENDING_PUBLIC_REVIEW`, `PLATFORM_APPROVED`, or `PLATFORM_REJECTED`. A target that is neither current internal nor current public is historical non-current and denied. `INTERNAL_WITHDRAWN` does not grant authority through a retained internal pointer, unless that same exact target independently qualifies as the visible current public version.

| Scenario | Exact target | Frontend eligibility |
| --- | --- | --- |
| Candidate-only V2 | V2 | Deny |
| First-public pending V1 | V1 | Deny: review-bound candidate |
| First-public approved V1 | V1 | Deny: review-bound candidate |
| Public V1 | V1 | Allow when it is the unblocked exact current public version |
| Replacement draft | V2 | Deny: candidate-only |
| Replacement draft | Old V1 | Allow when it remains the unblocked exact current public version |
| Replacement internal | V2 | Allow as exact current internal while `INTERNAL_PUBLISHED` |
| Replacement internal | Old V1 | Allow when it remains the unblocked exact current public version |
| Replacement pending | V2 | Deny: review-bound candidate |
| Replacement pending | Old V1 | Allow when it remains the unblocked exact current public version |
| Replacement approved | V2 | Deny: review-bound candidate |
| Replacement approved | Old V1 | Allow when it remains the unblocked exact current public version |
| Replacement rejected | V2 | Deny: review-bound candidate |
| Replacement rejected | Old V1 | Allow when it remains the unblocked exact current public version |
| Takedown historical public | Old V1 | Deny: `PLATFORM_TAKEDOWN` and blocked |
| Reset blocked old public | Old V1 | Deny: blocked historical public |
| Historical non-current | Historical version | Deny |
| Internal withdrawn only | Retained internal version | Deny unless the same target independently qualifies as visible current public |

## 6. Format-only editor and renderer

The format editor uses the immutable exact `ResultVersion.summaryText` as a read-only text base. It may edit only `BOLD`, `ITALIC`, paragraph boundaries, and presentation metadata permitted by the sealed schema. V1 paragraph style is `NORMAL` only. It must not offer headings, bullets, ordered lists, underline, font size/color, HTML editing, Markdown replacement, or text mutation.

The interaction consists of a read-only summary selection surface, Bold and Italic controls, a format preview, required reason where the API requires it, and explicit save. Browser UTF-16 offsets must be translated to Unicode code-point offsets using `Array.from(summaryText)` or an equivalent code-point-safe algorithm. Emoji and other non-BMP selections require dedicated tests.

A shared `ActivityResultPresentation` renderer is used by Public, Student, and management previews. It accepts `summaryText` and `presentation` and constructs Vue DOM nodes; `v-html`, persisted HTML, and unsafe markup interpretation are forbidden. Missing presentation renders plain text with preserved line breaks (`white-space: pre-wrap` or equivalent).

SchoolAdmin history remains two explicit views:

- lifecycle/review history: action, version, occurrence time, reason, and actor from the activity-scoped history API;
- format history: revision, edit time, reason, and optional presentation preview from the exact version API.

Editor UUIDs are not the default human-facing identity.

## 7. SuperAdmin review

Add "Result Review" to `superAdminNavigation`.

- `/super-admin/activity-results/reviews` reads the pending queue and shows only real `PENDING_PUBLIC_REVIEW` entries.
- `/super-admin/activity-results/:resultId/review` reads pending review detail and shows exact candidate title, summary, highlights, version, useful school/activity context identifiers, submitted time, and review history.

Approve and Reject act on the loaded exact candidate. Reject requires a bounded modal and reason. Approval is not publication: after approval the UI states that SchoolAdmin must still perform Make Public, and anonymous visibility must not be implied.

Approve and Reject are allowed only for public status `PENDING_PUBLIC_REVIEW` and Activity execution state `PUBLISHED`, `IN_PROGRESS`, or `ENDED`. If an Activity becomes `CANCELLED` while pending, the candidate and review history remain readable, but Approve and Reject are unavailable; platform safety governance is separate and does not delete history.

`SUPERADMIN_REVIEW_ACTIVITY_STATE_READ = GAP` and `SUPERADMIN_REVIEW_ACTIVITY_STATE_AFFORDANCE = BLOCKED_BY_SUPPORT_QUERY`: the current `PendingResultReviewSummary` and `PendingResultReviewDetail` do not expose `activityExecutionStatus`, while backend decisions enforce it. Do not infer Activity state from `publicStatus` or use the SchoolAdmin management endpoint for SuperAdmin. Candidate future fields are `PendingResultReviewSummary.activityExecutionStatus` and `PendingResultReviewDetail.activityExecutionStatus`; the final shape is deferred to the Support Query Baseline.

## 8. SuperAdmin governance read-gap audit

`SUPERADMIN_GOVERNANCE_DISCOVERY_READ = GAP`.

Code evidence at the authoritative baseline:

- `SuperAdminActivityResultGovernanceController` exposes only `POST /{id}/takedown` and `POST /{id}/reset-takedown`; it has no governance list or detail read.
- `SuperAdminActivityResultReviewController` exposes reads only for the pending queue and pending detail.
- `ActivityResultReviewQueryService.pendingDetail()` delegates to `findPendingDetail()` and returns not-found outside pending review.
- A taken-down result is therefore not guaranteed to remain discoverable or readable after reload, so the required `discover -> read -> takedown -> reload -> read takedown state -> reset -> reload` chain does not exist.

Consequently `SUPERADMIN_GOVERNANCE_UI = BLOCKED_BY_READ_CONTRACT`. Implementation must not bridge this gap using localStorage, transient page state, manual UUID entry, scanning public activities, N+1 public-result probes, or hiding reload behavior. The minimum next stage is `ACTIVITY_RESULT_FRONTEND_SUPPORT_QUERY_BASELINE`, defining additive governance discovery/detail reads before governance UI work.

### 8.1 Support-query gaps

| Gap | Evidence | Required support |
| --- | --- | --- |
| Governance discovery/read | Governance exposes takedown/reset mutations but no durable general list/detail reload path | Additive governance discovery/detail query contract |
| SuperAdmin review Activity execution status | Pending review summary/detail omit `activityExecutionStatus` although approve/reject enforce it | Additive review support query field contract |

`SUPPORT_QUERY_GAP_COUNT = 2`. `NO MUTATION CONTRACT CHANGE REQUIRED`. Both gaps are addressed together in `ACTIVITY_RESULT_FRONTEND_SUPPORT_QUERY_BASELINE`.

### 8.2 SchoolAdmin Activity status source

Do not add `executionStatus` to the ActivityResult DTO. SchoolAdmin loads Activity context and execution status independently from `GET /api/v1/activities/management/{activityId}` and loads result state, pointers, and content from `GET /api/v1/activities/{activityId}/result`. Separate loading and error handling is required. `SCHOOL_ADMIN_ACTIVITY_STATE_SUPPORT_QUERY = NOT_REQUIRED`.

## 9. Page state and mutation contracts

Every new page or embedded result section defines `LOADING`, `EMPTY`, `ERROR`, and `SUCCESS`. Management surfaces additionally handle:

- `403`: permission state and safe role-home navigation;
- `404`: scoped not found without revealing another school's resource existence;
- `409`: lifecycle/concurrency conflict with a prompt to reload authoritative state.

After every save, publish, withdraw, return-to-draft, submit, approve, reject, make-public, format edit, takedown, or reset, the page re-fetches its authoritative read model. It must not simulate success by patching local status fields.

Every mutation has pending, success, and controlled-failure states and disables all conflicting triggers while pending. Double clicks and duplicate publish, submit, approve, or make-public requests must be prevented.

Withdraw Internal, Reject Review, Takedown, and Reset Takedown require confirmation. Reject, Takedown, and Reset collect a reason aligned with the API contract. Use the existing bounded dialog/modal visual language, never `window.prompt()`.

## 10. Responsive and accessibility baseline

Acceptance viewports are 1440, 1366, 1024, and 375 CSS pixels. There must be no horizontal page overflow, oversized decorative cards, fake KPI dashboard, or overflowing mobile action toolbar. Management lists use dense tables on desktop and bounded cards or responsive rows on narrow screens. Controls retain visible focus, dialogs manage focus and labels, status is not color-only, and loading/error updates use suitable live-region semantics.

## 11. Browser E2E acceptance matrix

The following cases use real API behavior and verify both visible UI and relevant network/state outcomes:

| ID | Acceptance |
| --- | --- |
| FE-E2E-01 | SchoolAdmin opens an activity without a result; editor shows the empty model and no read-side creation occurs. |
| FE-E2E-02 | First explicit save creates V1. |
| FE-E2E-03 | Reload restores the exact candidate pointer and content. |
| FE-E2E-04 | Publish internal V1; authorized Student sees V1. |
| FE-E2E-05 | Format V1; Student sees formatted V1. |
| FE-E2E-06 | Submit public review; candidate format action is disabled. |
| FE-E2E-07 | SuperAdmin pending queue and detail show the exact candidate. |
| FE-E2E-08 | SuperAdmin approves; public result remains unavailable. |
| FE-E2E-09 | SchoolAdmin makes the approved candidate public; anonymous audience sees V1. |
| FE-E2E-10 | SchoolAdmin core edit creates V2; anonymous audience remains on V1. |
| FE-E2E-11 | Before V2 internal publication, Student visibility follows server authority. |
| FE-E2E-12 | Internal publish V2; Student sees V2 while anonymous audience sees V1. |
| FE-E2E-13 | Format V2; only V2 presentation changes. |
| FE-E2E-14 | Replacement review pending; anonymous sees V1 and Student sees V2. |
| FE-E2E-15 | Replacement V2 rejected; old public V1 remains visible. |
| FE-E2E-16 | Corrected V3 is a new version and does not reuse V2 review binding. |
| FE-E2E-17 | Replacement approved and made public; public switches to exactly the approved version. |
| FE-E2E-18 | Public result `404` leaves public activity detail usable with a normal result empty state. |
| FE-E2E-19 | Student result `404` leaves Student activity detail usable with a normal unavailable state. |
| FE-E2E-20 | Cross-school SchoolAdmin receives scoped `404` without resource disclosure. |
| FE-E2E-21 | Student cannot enter SchoolAdmin result routes. |
| FE-E2E-22 | SuperAdmin cannot perform ordinary SchoolAdmin result editing. |
| FE-E2E-23 | Emoji/non-BMP format selection maps to exact Unicode code points. |
| FE-E2E-24 | Reload preserves and re-renders format presentation. |
| FE-E2E-25 | Conditional: SuperAdmin takedown makes the public result unavailable. |
| FE-E2E-26 | Conditional: governance reload can still read the taken-down result and state. |
| FE-E2E-27 | Conditional: reset takedown leaves public visibility blocked. |
| FE-E2E-28 | Conditional: reload can still read reset state. |
| FE-E2E-29 | Conditional: a future approved publication restores visibility through the normal publication flow. |
| FE-E2E-30 | Pending review -> Activity is cancelled -> reload detail; candidate/history remain readable and Approve/Reject are disabled or unavailable. |
| FE-E2E-31 | Stale Approve/Reject after cancellation is denied by the server; the UI shows a controlled conflict, refreshes authoritative state, and records no APPROVED/REJECTED history. |

FE-E2E-01 through FE-E2E-24 are approved baseline cases. `FE-E2E-25..31 = BLOCKED_BY_FRONTEND_SUPPORT_QUERY`; they become executable only after stable governance discovery/detail and review Activity-state contracts close the documented gaps. `FE-E2E_TOTAL = 31`.

`POST_TAKEDOWN_RECOVERY_CONTRACT = APPROVED`. Once governance queries make the conditional cases executable, the browser flow must verify: reset preserves `publicVisibilityBlocked = true`; a new SchoolAdmin candidate can be saved, published internally, and submitted while blocked; SuperAdmin approval preserves the block; and SchoolAdmin Make Public succeeds while blocked, atomically clears the block, and exposes the exact newly approved version. The blocked flag must never be used as a Submit or Make Public UI blocker.

## 12. Implementation slices

Implementation must remain split into reviewable slices:

- **FE-A:** audience-specific types, ActivityResult API client, centralized labels, and safe shared presentation renderer.
- **FE-B:** Public and Student projections integrated into their existing activity detail pages.
- **FE-C:** SchoolAdmin list, dedicated editor, projection views, lifecycle actions, lifecycle/review history, and format editor/history.
- **FE-D:** SuperAdmin pending review queue, detail, approve, and reject; partially blocked by the review Activity-state support query.
- **FE-E:** SuperAdmin takedown/reset governance; blocked by the governance read contract and review support-query contract.

`FE-D = PARTIALLY_BLOCKED_BY_SUPPORT_QUERY`. `FE-E = BLOCKED_BY_SUPPORT_QUERY`. No slice should combine all audiences into one large pull request. After Review V3, the next stage is `ACTIVITY_RESULT_FRONTEND_SUPPORT_QUERY_BASELINE`, which addresses both gaps together.

## 13. Frozen decisions

| Decision | Value |
| --- | --- |
| Public route | Integrate result into `/activities/:id` |
| Student route | Integrate result into `/student/activities/:id` |
| SchoolAdmin list | `/school-admin/activity-results` |
| SchoolAdmin detail | `/school-admin/activities/:activityId/result` |
| SuperAdmin review list | `/super-admin/activity-results/reviews` |
| SuperAdmin review detail | `/super-admin/activity-results/:resultId/review` |
| Read-side creation | Prohibited |
| `READ_SIDE_CREATION` | `ABSENT` by contract |
| Core/format editor separation | Required |
| Raw media UUID UI | Prohibited |
| Media-ref mutation UI | Deferred; existing refs preserved |
| Renderer | Shared safe Vue DOM renderer; no `v-html` |
| Governance discovery/read | Gap |
| Governance UI | Blocked by read contract |
| Runtime data | Real API data only |
