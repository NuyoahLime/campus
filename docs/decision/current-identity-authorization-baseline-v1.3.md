# Current Identity and Authorization Baseline v1.3

> **Status**: Phase 8 baseline freeze, pending manual review.
> **Baseline master**: `278c85599d2abd9d97a0ab1a3b5f49afd2f207ba`
> **Scope**: Documentation and authorization contract only.
> **Code rule**: No production code, test code, migration, dependency, workflow, Docker or frontend change is allowed in this phase.

This document is the current highest-priority incremental contract for identity, authentication and authorization. When older business specifications, design notes or planning documents conflict with this file, this file wins for the current implementation line.

Phase 8 does not implement authorization code. It freezes the target contract and records the gaps that must be closed by later code tasks.

## Audit Snapshot

The current codebase was re-audited from `origin/master` at `278c85599d2abd9d97a0ab1a3b5f49afd2f207ba`.

| Item | Current count | Notes |
| --- | ---: | --- |
| Controller mappings | 54 | `@(Get|Post|Put|Patch|Delete)Mapping` under `src/main/java` |
| REST controllers | 19 | Excludes `GlobalExceptionHandler` because it is `@RestControllerAdvice` |
| Business controllers with `@PreAuthorize` | 2 | `UserController`, `SchoolAdminInvitationController` |
| Matrix rows | 58 | 54 controller mappings plus logout, OPTIONS, actuator health and actuator info |
| Declared DTOs with the named client identity fields | 13 | Uses the requested field set: `actorId`, `reviewerId`, `createdBy`, `studentId`, `submitterId`, `applicantId`, `uploaderId`; 12 are HTTP `@RequestBody` DTOs and 1 is an unused legacy declaration |
| Active HTTP `@RequestBody` DTOs with the named fields | 12 | `ActivateSchoolRequest` is not bound to a controller request body and remains the single `UNUSED` legacy DTO |

`SecurityConfig` currently permits actuator health/info, `OPTIONS /**`, CSRF token fetch, login, student registration, and school-admin activation. It gates `/api/v1/users/**` with `ROLE_SUPER_ADMIN`, requires authentication for the remaining `/api/**`, and denies everything else.

## Current Formal Identities

The current formal authenticated identities are exactly:

```text
SUPER_ADMIN
STUDENT
SCHOOL_ADMIN
```

Rules:

- `SUPER_ADMIN` is platform governance identity. It does not belong to a school and must not have any ACTIVE school membership.
- An ordinary authenticated user must have exactly one ACTIVE school membership.
- A `STUDENT` membership maps only to `ROLE_STUDENT`.
- A `SCHOOL_ADMIN` membership maps only to `ROLE_SCHOOL_ADMIN`.
- Multi-role sessions and multi-school sessions are not allowed.
- `SUPER_ADMIN` must not be treated as an implicit school operator for ordinary in-school business mutation.

## Historical Identities

The current code and data model may still contain these historical compatibility values:

```text
TEACHER
REGISTERED_USER
```

Rules:

- `TEACHER` may exist only as a database compatibility value, historical documentation term, reconstitution value, or negative test input.
- The current version must not generate `ROLE_TEACHER`.
- A user whose only ACTIVE membership is `TEACHER` must be denied login with `ACCOUNT_ROLE_NOT_READY`.
- `REGISTERED_USER` is not a current formal platform identity.
- Do not restore ordinary public registration, email verification, resend-verification, onboarding authorization, or user-selected formal roles.
- Do not modify migrations `V001` through `V016` and do not delete historical compatibility constraints from the database.

Any flow that requires a formal `TEACHER` identity is marked:

```text
DEFERRED_BY_CURRENT_IDENTITY_MODEL
```

Do not silently transfer every teacher responsibility to `SCHOOL_ADMIN`. Only operations already explicitly allowed to school administrators by the source specification may remain in the school-admin authority set.

## Registration and Activation Model

Current formal student flow:

```text
Anonymous student identity application
-> PENDING_ACTIVATION account
-> school administrator review
-> NORMAL account + ACTIVE STUDENT membership
```

Current formal school-admin flow:

```text
SUPER_ADMIN creates school-admin invitation
-> PENDING_ACTIVATION account
-> invitation-code activation
-> NORMAL account + ACTIVE SCHOOL_ADMIN membership
```

Explicitly prohibited from revival:

```text
/api/v1/auth/register old ordinary registration
REGISTERED_USER formal authorization
email verification
resend-verification
onboarding authorization
client-selected formal role
```

## Login Contract

The current login contract is:

```text
Password is verified before business-state disclosure.

Unknown user or wrong password:
401 AUTHENTICATION_FAILED

Five consecutive failures:
lock for about 10 minutes

Correct password during lock:
401 ACCOUNT_LOCKED

No formal identity:
403 IDENTITY_NOT_ASSIGNED

Conflicting identity:
403 IDENTITY_AMBIGUOUS

Historical or unsupported role:
403 ACCOUNT_ROLE_NOT_READY
```

Session and response rules:

- Successful login rotates the servlet session id before persisting the `SecurityContext`.
- CSRF is cookie-backed and the token endpoint returns the current header name, parameter name and token for SPA use.
- Login response must include only the authenticated identity needed by the client and must not expose password hash, invitation code hash, raw credential material or internal audit-only fields.
- Locked or denied login must not create an authenticated session.

## Target Authorization Principles

| Target subject | Scope |
| --- | --- |
| `ANONYMOUS` | Public information, CSRF, login, student identity application, and school-admin invitation activation only |
| `STUDENT` | Self data, same-school published content, and self appeal or feedback flows only |
| `SCHOOL_ADMIN` | Same-school administration only; cross-school access must be denied |
| `SUPER_ADMIN` | Platform governance operations; no default authority to mutate ordinary school-internal activity, score or appeal data |
| `DEFERRED / DENY` | Endpoints whose correct authority depends on a formal `TEACHER` identity |

Cross-school behavior should be `403` when the caller is authenticated but not allowed, or anti-enumeration `404` when revealing the resource existence would leak another school's data. The specific response must be chosen per endpoint in the implementation phase.

## Endpoint Authorization Matrix

Legend for current rule:

- `permitAll`: currently anonymous through `SecurityConfig`.
- `authenticated`: currently any authenticated formal identity can reach the controller unless service code blocks it.
- `ROLE_SUPER_ADMIN`: currently guarded by URL rule or `@PreAuthorize`.
- `service CurrentActor`: current controller has no method annotation, but application service derives actor from `SecurityContext`.
- `DEFERRED_BY_CURRENT_IDENTITY_MODEL`: target authority depends on unresolved teacher responsibilities.

| # | HTTP Method | Path | Current access rule | Target subject | Data scope | Operator source | schoolId source | Cross-school behavior | Current gap | Future batch |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | GET | `/actuator/health` | permitAll | ANONYMOUS | PUBLIC | N/A | N/A | N/A | None | AUTHZ-08-ACTUATOR |
| 2 | GET | `/actuator/info` | permitAll | ANONYMOUS | PUBLIC | N/A | N/A | N/A | None | AUTHZ-08-ACTUATOR |
| 3 | OPTIONS | `/**` | permitAll | ANONYMOUS | PUBLIC | N/A | N/A | N/A | CORS preflight only | AUTHZ-08-SECURITY |
| 4 | GET | `/api/v1/auth/csrf` | permitAll | ANONYMOUS | PUBLIC | N/A | N/A | N/A | None | AUTHZ-08-AUTH |
| 5 | POST | `/api/v1/auth/login` | permitAll | ANONYMOUS | SELF | N/A | Principal after success | N/A | None | AUTHZ-08-AUTH |
| 6 | POST | `/api/v1/auth/logout` | Spring Security logout filter with CSRF | AUTHENTICATED_USER | SELF | SecurityContext | Principal | N/A | Not a controller mapping; document as configured endpoint | AUTHZ-08-AUTH |
| 7 | GET | `/api/v1/auth/me` | authenticated | AUTHENTICATED_USER | SELF | SecurityContext | Principal | N/A | None | AUTHZ-08-AUTH |
| 8 | POST | `/api/v1/auth/student/register` | permitAll | ANONYMOUS | PUBLIC | N/A | Legitimate request target school | N/A | Needs anonymous school list contract | AUTHZ-08-SCHOOL-LIST |
| 9 | POST | `/api/v1/auth/school-admin/activate` | permitAll | ANONYMOUS | SELF | N/A | Invitation target school | N/A | None | AUTHZ-08-AUTH |
| 10 | POST | `/api/v1/users` | ROLE_SUPER_ADMIN | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | N/A | N/A | None | AUTHZ-08-USER |
| 11 | POST | `/api/v1/users/{id}/activate` | ROLE_SUPER_ADMIN | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target user/account | N/A | Must preserve no school-internal override | AUTHZ-08-USER |
| 12 | POST | `/api/v1/users/{id}/disable` | ROLE_SUPER_ADMIN | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target user/account | N/A | Must preserve emergency/platform boundary | AUTHZ-08-USER |
| 13 | POST | `/api/v1/users/{id}/re-enable` | ROLE_SUPER_ADMIN | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target user/account | N/A | Must preserve platform boundary | AUTHZ-08-USER |
| 14 | POST | `/api/v1/school-admin-invitations` | ROLE_SUPER_ADMIN + CurrentActor in service | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Legitimate invitation target school | N/A | None | AUTHZ-08-INVITATION |
| 15 | POST | `/api/v1/school-admin-invitations/{invitationId}/revoke` | ROLE_SUPER_ADMIN + CurrentActor in service | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target invitation | N/A | None | AUTHZ-08-INVITATION |
| 16 | POST | `/api/v1/school-admin-invitations/{invitationId}/regenerate` | ROLE_SUPER_ADMIN + CurrentActor in service | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target invitation | N/A | None | AUTHZ-08-INVITATION |
| 17 | GET | `/api/v1/schools/{schoolId}/student-identity-applications` | authenticated + service CurrentActor same-school admin | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext | Path and membership must match | 403 or anti-enumeration 404 | Controller lacks method annotation; service owns check | AUTHZ-08-STUDENT-REVIEW |
| 18 | GET | `/api/v1/schools/{schoolId}/student-identity-applications/{applicationId}` | authenticated + service CurrentActor same-school admin | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext | Path, resource and membership must match | 403 or anti-enumeration 404 | Controller lacks method annotation; service owns check | AUTHZ-08-STUDENT-REVIEW |
| 19 | POST | `/api/v1/schools/{schoolId}/student-identity-applications/{applicationId}/approve` | authenticated + service CurrentActor same-school admin | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext | Path, resource and membership must match | 403 | Controller lacks method annotation; service owns check | AUTHZ-08-STUDENT-REVIEW |
| 20 | POST | `/api/v1/schools/{schoolId}/student-identity-applications/{applicationId}/reject` | authenticated + service CurrentActor same-school admin | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext | Path, resource and membership must match | 403 | Controller lacks method annotation; service owns check | AUTHZ-08-STUDENT-REVIEW |
| 21 | GET | `/api/v1/schools` | authenticated | ANONYMOUS | PUBLIC | N/A | N/A | N/A | Must be opened only for exact GET collection path with minimal fields | AUTHZ-08-SCHOOL-LIST |
| 22 | GET | `/api/v1/schools/{id}` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target school | N/A | Target role must be decided; do not open anonymously | AUTHZ-08-SCHOOL-MGMT |
| 23 | POST | `/api/v1/schools/{id}/activate` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target school | N/A | Legacy `ActivateSchoolRequest.actorId` exists but method currently has no body | AUTHZ-08-SCHOOL-MGMT |
| 24 | POST | `/api/v1/schools/{id}/disable` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext | Target school | N/A | Management authority not yet explicit | AUTHZ-08-SCHOOL-MGMT |
| 25 | POST | `/api/v1/school-registrations` | authenticated | ANONYMOUS | PUBLIC | N/A | Legitimate submitted school data | N/A | Current code requires auth although school admission was public in older specs | AUTHZ-08-SCHOOL-REG |
| 26 | POST | `/api/v1/school-registrations/{id}/approve` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | Request body reviewerId | Target registration | N/A | `reviewerId` is client-forgeable | AUTHZ-08-SCHOOL-REG |
| 27 | POST | `/api/v1/school-registrations/{id}/reject` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | Request body reviewerId | Target registration | N/A | `reviewerId` is client-forgeable | AUTHZ-08-SCHOOL-REG |
| 28 | POST | `/api/v1/school-registrations/{id}/withdraw` | authenticated | NEEDS_PRODUCT_DECISION | PUBLIC | N/A | Target registration | N/A | Submitter identity not modeled in request contract | AUTHZ-08-SCHOOL-REG |
| 29 | GET | `/api/v1/challenge-projects` | authenticated | ANONYMOUS | PUBLIC | N/A | N/A | N/A | Public read currently requires login | AUTHZ-08-PUBLIC-READ |
| 30 | POST | `/api/v1/challenge-projects` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext target | N/A | N/A | Target authority not explicit | AUTHZ-08-PROJECT |
| 31 | GET | `/api/v1/challenge-projects/{id}` | authenticated | ANONYMOUS | PUBLIC | N/A | N/A | N/A | Public read currently requires login | AUTHZ-08-PUBLIC-READ |
| 32 | POST | `/api/v1/challenge-projects/{id}/publish` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | SecurityContext target | N/A | N/A | Target authority not explicit | AUTHZ-08-PROJECT |
| 33 | GET | `/api/v1/activities` | authenticated | ANONYMOUS | PUBLIC | N/A | Result schoolId | N/A | Public read currently requires login | AUTHZ-08-PUBLIC-READ |
| 34 | POST | `/api/v1/activities` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | Request body createdBy | Request body schoolId | 403 | `createdBy` and school scope are client-forgeable | AUTHZ-08-ACTIVITY |
| 35 | POST | `/api/v1/activities/{id}/publish` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target activity | 403 | Same-school check absent at controller boundary | AUTHZ-08-ACTIVITY |
| 36 | POST | `/api/v1/activity-applications` | authenticated | DEFERRED / DENY | SAME_SCHOOL | Request body applicantId | Request body schoolId | 403 | Depends on formal TEACHER flow; `applicantId` forgeable | AUTHZ-08-DEFERRED-TEACHER |
| 37 | POST | `/api/v1/activity-applications/{id}/approve` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | Request body reviewerId | Target application | 403 | `reviewerId` is client-forgeable | AUTHZ-08-ACTIVITY-APPLICATION |
| 38 | POST | `/api/v1/activity-applications/{id}/reject` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | Request body reviewerId | Target application | 403 | `reviewerId` is client-forgeable | AUTHZ-08-ACTIVITY-APPLICATION |
| 39 | POST | `/api/v1/activity-applications/{id}/withdraw` | authenticated | DEFERRED / DENY | SAME_SCHOOL | SecurityContext target | Target application | 403 | Depends on formal TEACHER applicant model | AUTHZ-08-DEFERRED-TEACHER |
| 40 | POST | `/api/v1/activity-results/{id}/publish` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target activity result | 403 | Same-school check absent at controller boundary | AUTHZ-08-RESULT |
| 41 | POST | `/api/v1/score-attempts` | authenticated | DEFERRED / DENY | SAME_SCHOOL | Request body studentId and enteredBy | Request body schoolId | 403 | Score entry depends on teacher/admin decision; identity fields forgeable | AUTHZ-08-DEFERRED-TEACHER |
| 42 | POST | `/api/v1/score-appeals` | authenticated | STUDENT | SELF | Request body studentId | Request body schoolId and score attempt target | 403 or 404 | `studentId` is client-forgeable | AUTHZ-08-APPEAL |
| 43 | POST | `/api/v1/score-appeals/{id}/begin-processing` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | Request body handlerId | Target appeal | 403 | `handlerId` is client-forgeable; teacher path deferred | AUTHZ-08-APPEAL |
| 44 | POST | `/api/v1/score-appeals/{id}/reject` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target appeal | 403 | Same-school and non-self rules not explicit at controller boundary | AUTHZ-08-APPEAL |
| 45 | POST | `/api/v1/score-appeals/{id}/withdraw` | authenticated | STUDENT | SELF | SecurityContext target | Target appeal | 403 or 404 | Self ownership check not explicit at controller boundary | AUTHZ-08-APPEAL |
| 46 | POST | `/api/v1/ranking-definitions` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | Request body createdBy | Request body schoolId | 403 | `createdBy` and school scope are client-forgeable | AUTHZ-08-RANKING |
| 47 | POST | `/api/v1/ranking-definitions/{id}/enable` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target ranking definition | 403 | Same-school check absent at controller boundary | AUTHZ-08-RANKING |
| 48 | POST | `/api/v1/ranking-definitions/{id}/disable` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target ranking definition | 403 | Same-school check absent at controller boundary | AUTHZ-08-RANKING |
| 49 | POST | `/api/v1/l3-authorizations` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Request body schoolId and project target | 403 | Same-school submission rule not explicit | AUTHZ-08-L3 |
| 50 | POST | `/api/v1/l3-authorizations/{id}/approve` | authenticated | SUPER_ADMIN | PLATFORM_GOVERNANCE | Request body reviewerId | Target authorization | N/A | `reviewerId` is client-forgeable | AUTHZ-08-L3 |
| 51 | POST | `/api/v1/l3-authorizations/{id}/withdraw` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target authorization | 403 | Ownership/same-school rule not explicit | AUTHZ-08-L3 |
| 52 | POST | `/api/v1/media` | authenticated | DEFERRED / DENY | SAME_SCHOOL | Request body uploaderId | Request body schoolId and activity target | 403 | Upload role depends on teacher/admin decision; `uploaderId` forgeable | AUTHZ-08-DEFERRED-TEACHER |
| 53 | POST | `/api/v1/media/{id}/internal-review` | authenticated | DEFERRED / DENY | SAME_SCHOOL | SecurityContext target | Target media | 403 | Depends on teacher/admin media flow | AUTHZ-08-DEFERRED-TEACHER |
| 54 | POST | `/api/v1/media/{id}/internal-approve` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target media | 403 | Same-school and non-self upload review not explicit | AUTHZ-08-MEDIA |
| 55 | POST | `/api/v1/feedbacks` | authenticated | STUDENT | SELF | Request body submitterId | Request body schoolId | 403 | `submitterId` is client-forgeable; teacher submitter deferred | AUTHZ-08-FEEDBACK |
| 56 | POST | `/api/v1/feedbacks/{id}/begin-processing` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | Request body handlerId | Target feedback | 403 | `handlerId` is client-forgeable | AUTHZ-08-FEEDBACK |
| 57 | POST | `/api/v1/feedbacks/{id}/resolve` | authenticated | SCHOOL_ADMIN | SAME_SCHOOL | SecurityContext target | Target feedback | 403 | Same-school rule not explicit | AUTHZ-08-FEEDBACK |
| 58 | POST | `/api/v1/feedbacks/{id}/close` | authenticated | STUDENT | SELF | SecurityContext target | Target feedback | 403 or 404 | Self ownership rule not explicit | AUTHZ-08-FEEDBACK |

## Anonymous School List Target Contract

The next code phase that touches school query authorization must implement this precise contract:

```http
GET /api/v1/schools
```

Target rules:

- Allow anonymous access to this exact GET collection path.
- Return only `NORMAL` schools.
- Return only the fields needed for student registration choice:
  - `id`
  - `name`
  - `schoolType`
  - `region`
- Preserve existing pagination boundaries.
- Do not return address, contact name, contact phone, contact email, internal code, unified code, or other internal fields.
- Do not open anonymous `GET /api/v1/schools/{id}`.
- Do not relax school activation, disablement, or other management endpoints.

This phase records the contract only. It does not modify `SecurityConfig` or `SchoolController`.

## Client Identity Field Ledger

The following table records the 13 declared DTOs that contain one of the explicitly requested client identity fields: `actorId`, `reviewerId`, `createdBy`, `studentId`, `submitterId`, `applicantId`, `uploaderId`. Twelve are active HTTP `@RequestBody` DTOs; `ActivateSchoolRequest` is an unused legacy declaration and is not currently bound to an endpoint.

| # | Request DTO | Field | Classification | Required follow-up |
| ---: | --- | --- | --- | --- |
| 1 | `ActivateSchoolRequest` | `actorId` | UNUSED | Remove unused legacy DTO or wire it only after actor comes from `CurrentActor`; current controller does not consume it |
| 2 | `CreateActivityRequest` | `createdBy` | MUST_REMOVE | Derive creator from `CurrentActor`; school scope from authenticated school admin membership |
| 3 | `CreateRankingDefinitionRequest` | `createdBy` | MUST_REMOVE | Derive creator from `CurrentActor`; school scope from principal or target resource |
| 4 | `ApproveActivityApplicationRequest` | `reviewerId` | MUST_REMOVE | Derive reviewer from `CurrentActor` |
| 5 | `RejectActivityApplicationRequest` | `reviewerId` | MUST_REMOVE | Derive reviewer from `CurrentActor` |
| 6 | `ApproveSchoolRegistrationRequest` | `reviewerId` | MUST_REMOVE | Derive reviewer from `CurrentActor`; `schoolId` is target created school input and needs separate validation |
| 7 | `RejectSchoolRegistrationRequest` | `reviewerId` | MUST_REMOVE | Derive reviewer from `CurrentActor` |
| 8 | `ApproveL3AuthorizationRequest` | `reviewerId` | MUST_REMOVE | Derive reviewer from `CurrentActor` |
| 9 | `SubmitScoreRequest` | `studentId` | LEGITIMATE_TARGET_ID | Target student id, but caller authority and same-school scope must be checked from principal/resource |
| 10 | `SubmitScoreAppealRequest` | `studentId` | MUST_REMOVE | Student appeal submitter must be the authenticated student; score attempt may identify the target |
| 11 | `SubmitFeedbackRequest` | `submitterId` | MUST_REMOVE | Feedback submitter must be the authenticated user |
| 12 | `SubmitActivityApplicationRequest` | `applicantId` | MUST_REMOVE | Activity applicant must be the authenticated actor after the teacher/student/admin product decision |
| 13 | `RegisterMediaRequest` | `uploaderId` | MUST_REMOVE | Media uploader must come from `CurrentActor`; school and activity scope must be resource-validated |

Same-family fields observed outside the requested field set:

- `BeginProcessingRequest.handlerId` in feedback and score appeal is client-supplied operator identity and should be removed in the same cleanup wave as `reviewerId`.
- `SubmitScoreRequest.enteredBy` is client-supplied operator identity and should be removed or derived from `CurrentActor`.

General rule:

- Current actor id must come from `CurrentActor`.
- School-admin school scope should come from the authenticated principal's ACTIVE `SCHOOL_ADMIN` membership.
- For existing resources, `schoolId` should be derived by loading the target resource.
- Anonymous student registration `schoolId` is a legitimate target school input, not actor identity.
- Clients must not declare that they are reviewer, creator, uploader, handler, score owner, or applicant.

## Legacy Specification Sync

The following legacy documents are intentionally retained as historical evidence, but affected sections must be read through this baseline:

- `CLAUDE.md`
- `docs/business-spec/01-用户与权限规格.md`
- `docs/business-spec/02-学校入驻与账号管理规格.md`
- `docs/business-spec/04-活动管理规格.md`
- `docs/business-spec/05-成绩管理规格.md`
- `docs/business-spec/07-成绩申诉规格.md`
- `docs/business-spec/08-素材与活动成果规格.md`
- `docs/business-spec/09-反馈与通知规格.md`
- `docs/business-spec/10-平台管理规格.md`
- `docs/business-spec/11-生命周期与数据保留规格.md`
- `docs/design/authentication-authorization-foundation-plan.md`
- `docs/design/path-a-http-exposure-plan.md`
- `docs/interface/interface-layer-planning.md`

Required interpretation:

- Historical `TEACHER` requirements remain as original business evidence, not as current authenticated role support.
- "Student V1 does not support self-registration" is replaced by the student identity-application registration model.
- Temporary explicit `actorId` and similar client operator fields are security debt.
- Old JWT, `TASK-AUTH-FOUNDATION-001`, `denyAll` and role-mapping plans are historical planning artifacts unless restated in this document.

## Unresolved Teacher Responsibilities

These flows remain unresolved under the current three-role identity model and must not be silently assigned to `SCHOOL_ADMIN`:

| Flow | Current status | Notes |
| --- | --- | --- |
| Teacher submits activity application | DEFERRED_BY_CURRENT_IDENTITY_MODEL | Existing endpoint has `applicantId`; formal teacher identity is not available |
| Responsible teacher assignment | DEFERRED_BY_CURRENT_IDENTITY_MODEL | Historical product requirement; no current formal login role |
| Teacher score entry for responsible projects | DEFERRED_BY_CURRENT_IDENTITY_MODEL | Existing score submit endpoint has client `studentId` and `enteredBy` debt |
| Teacher media upload and internal review participation | DEFERRED_BY_CURRENT_IDENTITY_MODEL | Upload endpoint has client `uploaderId`; authority split remains unresolved |
| Teacher feedback submitter or processor role | DEFERRED_BY_CURRENT_IDENTITY_MODEL | Feedback supports client `submitterId` and `handlerId`-style debt |
| Teacher project favorites or teacher workspace | DEFERRED_BY_CURRENT_IDENTITY_MODEL | Historical UI/spec flow, no current formal role |

## Stage 24 Current-Baseline Addendum

The older audit snapshot above is retained as historical evidence. For current
runtime decisions, the following Stage 24 baseline supersedes its stale baseline
SHA and unresolved Teacher wording:

- Current master: `d11d47fcd2600ab02056baa934184b20ec2a5b72`
- Runtime roles: `SUPER_ADMIN`, `SCHOOL_ADMIN`, `STUDENT`
- `TEACHER`: `NOT_A_RUNTIME_ROLE`
- `ActivityApplication` student operations are self-scoped; school-admin review is
  same-school; SuperAdmin does not perform school application operations.
- Score write is a future same-school SchoolAdmin responsibility; student is
  read-only and SuperAdmin is not the ordinary school score operator.
- Ranking read is a published-snapshot read slice. Ranking generation and
  publication are future production responsibilities.
- Stage 23 public ranking read currently needs a later L3 visibility tightening;
  Stage 24 does not change the API.

The authoritative operational contract is:

`docs/decision/current-three-role-operational-responsibility-baseline-v1.0.md`

Historical migrations, enum values, fixtures and negative tests containing
`TEACHER` remain compatibility-only and must not be removed in this stage.

## Validation Expectations

For the Phase 8 PR:

- `python scripts/validate_business_specs.py` must pass.
- `git diff --check` must pass.
- The diff against `origin/master` must not include `src/`, `pom.xml`, `docker-compose.yml`, `frontend/`, `.github/workflows/`, or `src/main/resources/db/migration/`.
- `mvn clean verify` should preserve:
  - Surefire: 314 tests, 0 failures, 0 errors, 0 skipped.
  - Failsafe: 101 tests, 0 failures, 0 errors, 0 skipped.
- CI must be green before this Draft PR can be considered ready for manual review.

## Final Phase 8 State

```text
阶段 8：授权契约与规格基线已形成，等待人工复核。
```
