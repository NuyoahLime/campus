# Current Business Capability Inventory v1

> Status: Stage 13 audit baseline
> Baseline: `8da48bdd4cabe43b6096f8c3eeee537901533c81`
> Scope: repository evidence only; no production behavior is changed by this document

This inventory records what can be reused, what is incomplete, and what must remain
blocked. A domain class or controller alone is not evidence of a complete product
capability. The classification considers domain rules, application services, query
support, persistence, HTTP authorization, integration tests, frontend integration,
and UAT together.

## Legend

| Status | Meaning |
| --- | --- |
| `COMPLETE_VERTICAL_SLICE` | Domain, application, persistence, authorized API, frontend flow, and UAT exist for the stated scope. |
| `BACKEND_READY_FRONTEND_MISSING` | The backend contract is usable for the stated scope, but a required product UI is absent. |
| `BACKEND_PARTIAL` | Useful backend layers exist, but queries, lifecycle operations, authorization semantics, or API coverage are incomplete. |
| `DOMAIN_READY_API_MISSING` | Domain/persistence support exists without a usable product API. |
| `FRONTEND_SHELL_ONLY` | Navigation or workspace UI exists without the business interaction. |
| `LEGACY_CONFLICT` | Existing behavior or API shape conflicts with the current formal identity/business contract. |
| `DEFERRED_BY_IDENTITY_MODEL` | Correct execution requires the unresolved formal `TEACHER` identity. It must remain denied. |
| `NOT_IMPLEMENTED` | There is no reusable vertical capability beyond a schema/entity placeholder. |

Additional risk label:

| Label | Meaning |
| --- | --- |
| `PRODUCT_DECISION_REQUIRED` | The current three-role model cannot assign the operator safely. Product/identity authority must be decided before implementation. |
| `EXISTING_BACKEND_SEMANTIC_GAP` | Code is callable but does not enforce a frozen business precondition. |

## Audit Summary

| Item | Count |
| --- | ---: |
| Capability lines audited | 18 |
| `COMPLETE_VERTICAL_SLICE` | 2 |
| `BACKEND_READY_FRONTEND_MISSING` | 1 |
| `BACKEND_PARTIAL` | 9 |
| `DOMAIN_READY_API_MISSING` | 1 |
| `LEGACY_CONFLICT` | 1 |
| `DEFERRED_BY_IDENTITY_MODEL` | 3 |
| `NOT_IMPLEMENTED` | 1 |
| `PRODUCT_DECISION_REQUIRED` flags | 3 |

## Authority Baseline

The only formal authenticated identities are `SUPER_ADMIN`, `SCHOOL_ADMIN`, and
`STUDENT`. `TEACHER` and `REGISTERED_USER` are historical compatibility terms, not
roles that may be re-enabled. The governing source is
`docs/decision/current-identity-authorization-baseline-v1.3.md`.

## Auth & Identity

Overall status: `COMPLETE_VERTICAL_SLICE` for login, session restore, logout, role
dispatch, student registration state handling, and school-admin invitation activation.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | `User`, `SchoolMembership`, account and membership states | Ready | Historical enum values remain for compatibility | Reuse; do not revive historical roles |
| Application | login state resolution, registration, activation, membership and bootstrap services | Ready | Password recovery is backend-only | Reuse services and state rules |
| Query | authentication account/membership/login-state adapters | Ready | No general account-management read model | Reuse for authentication only |
| Persistence | user, membership, invitation, profile adapters | Ready | None for current auth scope | Reuse |
| HTTP/security | auth, CSRF, registration, resubmit, activation endpoints | Ready | None for current auth scope | Reuse cookie session and CSRF contract |
| Frontend | login, role dispatch, registration, resubmit, activation, session restore | Ready | Password recovery UI absent | Reuse shared auth API/store/forms |
| Tests/UAT | session, login-state, registration, resubmit, activation and browser UAT | Ready | None for accepted scope | Preserve regression suite |

## School Registration

Overall status: `BACKEND_READY_FRONTEND_MISSING`. Anonymous submission and the full
SUPER_ADMIN review lifecycle are supported by the backend; list/detail/review UI is
accepted. The anonymous school-registration submission UI is missing.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | `SchoolRegistration` state machine | Ready | Withdraw remains intentionally denied at HTTP boundary | Reuse |
| Application | submit, query, request supplement, approve, reject | Ready | No applicant-side supplement/resubmit flow | Reuse review transaction |
| Persistence | repository, lock-aware adapter, list/detail query adapter | Ready | None for accepted review scope | Reuse |
| HTTP | anonymous submit; SUPER_ADMIN list/detail/review | Ready | No applicant status/read contract | Reuse |
| Frontend | SUPER_ADMIN list/detail/review | Partial | Public submit and applicant follow-up UI missing | Extend without rewriting review UI |
| Tests/UAT | read/review authorization IT and Stage 12 browser UAT | Ready | Public submission UI UAT missing | Preserve |

## School Master

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | `School` lifecycle and status events | Ready | Domain transition alone cannot enforce administrator-count rule | Reuse state machine |
| Application | `SchoolApplicationService` | Partial | `activate()` does not require two active school admins or an operation reason | Reuse after semantic closure |
| Query | public NORMAL-school list | Partial | No SUPER_ADMIN all-status list or rich management detail query | Extend query side |
| Persistence | school repository/query adapter | Ready | No admin-count eligibility query attached to activation | Extend |
| HTTP | public list, SUPER_ADMIN detail/activate/disable | Partial | Missing suspend/restore/re-enable contract; activate has no reason body | Do not expose activate in UI yet |
| Frontend | public school select only | Missing | No school master list/detail/lifecycle UI | Build after read model |
| Tests/UAT | public school and authorization tests | Partial | No two-admin activation precondition test | Add in lifecycle stage |

### School lifecycle semantic gap

`SchoolApplicationService.activate()` currently authorizes SUPER_ADMIN, loads the
school, calls `School.activate()`, and saves. It does **not** verify the frozen rule:

```text
at least two NORMAL users with ACTIVE SCHOOL_ADMIN memberships
```

It also accepts no operation reason and does not explicitly record a lifecycle audit
entry. This is `EXISTING_BACKEND_SEMANTIC_GAP`. Stage 13 must not fix it, and a direct
frontend activate button must not be added before the gap is closed.

## School Admin Account

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | `SchoolAdminInvitation`, membership and user states | Ready | Account governance read concerns are absent | Reuse |
| Application | create, revoke, regenerate, activate invitation | Ready | No list/detail/account-state application query | Reuse commands |
| Persistence | invitation repository and lock-aware activation | Ready | Repository only supports command lookups | Add read adapter, do not replace |
| HTTP | create/revoke/regenerate and anonymous activation | Partial | No invitation list/detail or admin-account list/state API | Extend |
| Frontend | activation page | Partial | No SUPER_ADMIN provisioning or account-management UI | Build after read API |
| Tests/UAT | management, activation, concurrency and browser UAT | Ready for commands | No management read UAT | Extend |

Current reusable commands are create, revoke, regenerate, and activate. Missing
capabilities are invitation list, invitation detail, school-scoped invitation query,
school-admin account list, and account state management views/contracts.

## Student Identity

Overall status: `COMPLETE_VERTICAL_SLICE` for first registration, rejection/resubmit,
pending state, and same-school review.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | student application and membership state machines | Ready | None for accepted scope | Reuse |
| Application/query | registration, resubmit, review list/detail/approve/reject | Ready | No broader student account administration | Reuse |
| Persistence | application, profile, membership and user transaction | Ready | None for accepted scope | Reuse |
| HTTP/authz | anonymous registration/resubmit; same-school admin review | Ready | None for accepted scope | Reuse CurrentActor checks |
| Frontend | registration, rejected/resubmit, pending, review UI | Ready | No student profile management | Reuse |
| Tests/UAT | rollback, concurrency/scope and browser UAT | Ready | None for accepted scope | Preserve |

## Challenge Project

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | project state, category and score configuration | Ready | Update/unpublish operations are not exposed | Reuse |
| Application | create, find, publish | Partial | Edit, unpublish/archive and resource attachment absent | Extend |
| Query | public paged list | Partial | Detail response is only id/name/status; filters/categories absent | Extend |
| Persistence | repository and public query adapter | Ready | Attachment/resource model absent | Reuse |
| HTTP | public list/detail, SUPER_ADMIN create/publish | Partial | No edit, unpublish/archive, category management | Extend |
| Frontend | None | Missing | Public library/detail and SUPER_ADMIN management missing | Build from page 5-7/21 references |
| Tests/UAT | domain/service/controller/query tests | Partial | No frontend or full lifecycle UAT | Extend |

## Activity

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | rich activity execution/public lifecycle | Ready | Most transitions are not exposed | Reuse |
| Application | create/publish and public query | Partial | Full review, edit, cancel, result linkage incomplete at HTTP layer | Extend selectively |
| Persistence | repository and public list query | Ready | Management query/detail absent | Extend |
| HTTP | public list; SCHOOL_ADMIN create/publish | Partial | No detail or management list; lifecycle surface incomplete | Extend |
| Frontend | Disabled navigation item only | `FRONTEND_SHELL_ONLY` | No activity product flow | Build after operator decisions |
| Tests/UAT | domain/service/controller/query tests | Partial | No vertical browser UAT | Extend |

## Activity Application

Overall status: `DEFERRED_BY_IDENTITY_MODEL` and `PRODUCT_DECISION_REQUIRED`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain/persistence | application state machine and repository | Ready | Applicant role is historical Teacher flow | Reuse after decision |
| Application | submit/approve/reject/withdraw | Partial | Correct formal applicant identity unresolved | Reuse state machine |
| HTTP/authz | submit and withdraw are `denyAll`; admin review endpoints exist | Deferred | No legal current caller can submit | Keep denied |
| Frontend | None | Missing | Teacher workspace is obsolete business | Do not implement |
| Tests | unit/controller coverage | Partial | No valid three-role vertical flow | Preserve negative boundary |

## Score

Overall status: `DEFERRED_BY_IDENTITY_MODEL` and `PRODUCT_DECISION_REQUIRED`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | score-attempt state machine and value types | Ready | Operator ownership is unresolved | Reuse |
| Application | submit and review-oriented domain operations | Partial | `enteredBy`, student target and reviewer separation need a formal operator decision | Reuse after decision |
| Persistence | repository and JSON/value mapping | Ready | Read/query model absent | Extend later |
| HTTP/authz | score submission is `denyAll` | Deferred | Teacher/admin assignment unresolved | Keep denied |
| Frontend | None | Missing | Historical Teacher score-entry design cannot be used | Do not implement |
| Tests | domain, service, persistence and denial coverage | Partial | No valid vertical UAT | Preserve |

## Appeal

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | score appeal lifecycle | Ready | Ranking-appeal/product variants are not a complete query model | Reuse |
| Application | student submit/withdraw; school-admin process/reject; correction service | Partial | List/detail, resolution and escalation HTTP coverage incomplete | Extend |
| Persistence | repository and JSONB mapping | Ready | Query adapter absent | Extend |
| HTTP/authz | four mutation endpoints with self/same-school checks | Partial | No read endpoints | Reuse secured commands |
| Frontend | None | Missing | Student and admin appeal views absent | Build after queries |
| Tests | authorization and persistence coverage | Partial | No frontend UAT | Extend |

## Ranking

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | ranking definition and layer model | Ready | Version generation/publication is not represented as a complete service surface | Reuse |
| Application | create/enable/disable | Partial | No list/detail/version calculation/publication API | Extend |
| Persistence | definition repository | Ready | Read model and version repository surface absent | Extend |
| HTTP/authz | SCHOOL_ADMIN mutation endpoints | Partial | No queries or public rankings | Extend |
| Frontend | None | Missing | Student/admin ranking pages absent | Build after backend closure |
| Tests | domain/service/controller/persistence tests | Partial | No vertical UAT | Extend |

## L3 Authorization

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | L3 authorization lifecycle | Ready | Platform publication/version integration incomplete | Reuse |
| Application | submit/approve/withdraw | Partial | Query/detail and full review outcomes absent at HTTP layer | Extend |
| Persistence | repository | Ready | Read model absent | Extend |
| HTTP/authz | SCHOOL_ADMIN submit/withdraw; SUPER_ADMIN approve | Partial | No list/detail/reject/suspend UI contract | Extend |
| Frontend | None | Missing | No school/platform workflow | Build later |
| Tests | domain/service/controller/persistence tests | Partial | No vertical UAT | Extend |

## Media

Overall status: `DEFERRED_BY_IDENTITY_MODEL` and `PRODUCT_DECISION_REQUIRED`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | internal/public media lifecycle | Ready | Upload/review operator assignment unresolved | Reuse |
| Application | register, internal review/approve and public actions | Partial | Formal uploader/reviewer split unresolved | Reuse after decision |
| Persistence | repository | Ready | Query/media delivery contract absent | Extend |
| HTTP/authz | register/internal-review denied; SCHOOL_ADMIN internal-approve | Deferred | No legal current upload flow | Keep denied |
| Frontend | None | Missing | Teacher materials design is obsolete business | Do not implement |
| Tests | domain/service/controller/persistence tests | Partial | No valid vertical UAT | Preserve boundaries |

## Result

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | internal/public activity-result lifecycle | Ready | Most transitions are not exposed | Reuse |
| Application | create and publish-oriented service | Partial | Creation/read/review/publication contract incomplete | Extend |
| Persistence | repository | Ready | Query adapter absent | Extend |
| HTTP/authz | SCHOOL_ADMIN publish only | Partial | No create/read/platform review endpoints | Extend |
| Frontend | None | Missing | No result editor/public display | Build later |
| Tests | domain/service/controller/persistence tests | Partial | No vertical UAT | Extend |

## Feedback

Overall status: `BACKEND_PARTIAL`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain | feedback lifecycle | Ready | Platform escalation/notification read concerns incomplete | Reuse |
| Application | submit, process, resolve, close | Partial | List/detail queries absent | Reuse commands |
| Persistence | repository | Ready | Query adapter absent | Extend |
| HTTP/authz | student and school-admin mutation endpoints | Partial | No read endpoints | Extend |
| Frontend | None | Missing | Student/admin/platform feedback views absent | Build after queries |
| Tests | domain/service/controller/persistence tests | Partial | No vertical UAT | Extend |

## Audit / Platform

Overall status: `DOMAIN_READY_API_MISSING`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Command/persistence | `AuditRecordCommandPort`, adapter, entity, repository | Ready | No audit query model | Reuse write port |
| Application | Command adapter only | Partial | No platform audit service/query | Extend |
| HTTP | None | Missing | No authorized audit list/detail/export API | Build deliberately |
| Frontend | None | Missing | No audit center | Build only on real audit data |
| Tests/UAT | Indirect audit assertions | Partial | No audit product UAT | Add later |

## Notification

Overall status: `NOT_IMPLEMENTED`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Persistence | `NotificationEntity` | Placeholder | No repository/service/event handlers/API | Reassess schema before use |
| Domain/application | None | Missing | Notification rules are not implemented | Build from current specs |
| Frontend | None | Missing | No notification center | Do not show fake badge/count |

## Generic User Governance

Overall status: `LEGACY_CONFLICT`.

| Layer | Artifact | Status | Gap | Reuse in future |
| --- | --- | --- | --- | --- |
| Domain/application | `UserApplicationService` and account state operations | Partial | Generic create flow is not the formal student/admin onboarding model | Reuse state transitions selectively |
| HTTP | SUPER_ADMIN create/activate/disable/re-enable endpoints | Partial | No read API; broad generic-user semantics conflict with scoped account governance | Do not expose as generic UI |
| Frontend | None | Missing | School-admin/student account management needs explicit scoped contracts | Rebuild product boundary, not domain basics |
| Tests | authorization and provisioning coverage | Partial | No account-management UAT | Extend after read model |

## Existing Client Identity Risk

CurrentActor remediation has removed several spoofable operator fields, but capability
completion must continue to distinguish legitimate target IDs from actor identity.
The unresolved operator-sensitive lines are:

| Capability | Risk | Required outcome |
| --- | --- | --- |
| Activity application | Historical applicant/Teacher ownership | Product identity decision |
| Score entry | `studentId` is a target, while score operator/reviewer roles remain unresolved | Product identity and separation-of-duty decision |
| Media upload/review | Uploader and reviewer role split depends on Teacher model | Product identity decision |

## Overall Status

The repository is not an empty backend and must not be rebuilt from scratch. It has
strong domain and persistence foundations across all major business areas. Product
completion is concentrated in auth/student identity and the SUPER_ADMIN school
registration review. Most remaining modules need read models, explicit lifecycle
contracts, frontend product flows, and browser UAT rather than replacement aggregates.

## Actual Implementation Roadmap

### Stage 14: SUPER_ADMIN School and School-Admin Read/Provisioning Foundation

- Add an all-status SUPER_ADMIN school list and rich school detail read model.
- Add school-scoped invitation list/detail and school-admin account list.
- Productize create/revoke/regenerate invitation commands without exposing unsafe
  school activation.
- Build separate routes under School Governance; do not combine unrelated resources
  into one large tabbed page.

Reason: lifecycle decisions depend on seeing schools, invitations, and active
administrators together. The command foundations already exist, while the read side is
the blocking gap.

### Stage 15: School Lifecycle Governance

- Enforce at least two NORMAL users with ACTIVE SCHOOL_ADMIN memberships before NORMAL.
- Require reasons and audit records for activation, suspension, disable, restore, and
  re-enable operations.
- Add concurrency and browser authorization coverage before exposing lifecycle buttons.

Reason: direct activation is currently semantically unsafe. It must follow the Stage 14
read foundation rather than being bundled into it.

### Stage 16: Challenge Project Resource Library

- Complete project detail, edit, unpublish/archive, categories and resource attachment.
- Build public list/detail and SUPER_ADMIN management routes from the page 5-7/21
  design references.
- Keep Activity/Score/Media work deferred until their operator decisions are resolved.

## Remaining Gaps

1. School activation violates the frozen two-active-admin precondition.
2. School lifecycle reasons/auditing are not consistently represented by API contracts.
3. Invitation and school-admin account read models are absent.
4. ChallengeProject lacks edit/unpublish/archive/resources and all frontend views.
5. Activity management is a partial backend without a management query surface.
6. Activity application, score entry, and media upload remain identity-deferred.
7. Appeal, ranking, L3, result, and feedback have mutation-heavy APIs but no product
   query/read flows.
8. Audit has a write port only; notification is only an entity placeholder.
9. The Phase 8 endpoint matrix predates later school-registration endpoints and should
   be re-audited in a future authorization-documentation stage; its identity principles
   remain authoritative.
