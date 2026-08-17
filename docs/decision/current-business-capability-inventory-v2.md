# Current Business Capability Inventory v2

> Stage 17 audit baseline
> Baseline: `4b83f8b5fab77bfb3aac6dbcb0ebd836267c83fe`
> Evidence: current source, controllers, frontend routes, tests, and accepted browser UAT

## 1. Completion Rule

`COMPLETE_VERTICAL_SLICE` requires all of Domain, Application, Persistence, Query,
authorized API, frontend, tests, and browser UAT for the stated scope. A controller,
entity, or test-file count alone is not completion evidence.

Current formal identity remains `SUPER_ADMIN`, `SCHOOL_ADMIN`, and `STUDENT`. The
ordinary Teacher role is removed. Teacher-dependent lines are denied or deferred;
their historical screens are not counted as missing frontend work to be implemented.

## 2. Audit Summary

| Status | Count |
| --- | ---: |
| Capability lines audited | 18 |
| COMPLETE_VERTICAL_SLICE | 6 |
| BACKEND_READY_FRONTEND_MISSING | 1 |
| BACKEND_PARTIAL | 6 |
| DOMAIN_READY_API_MISSING | 1 |
| DEFERRED_BY_IDENTITY_MODEL | 3 |
| PRODUCT_DECISION_REQUIRED flags | 3 |
| NOT_IMPLEMENTED | 1 |

The legacy generic-user governance endpoints are retained as an auxiliary risk note,
not a current product capability line. They must not be exposed as generic account
management UI.

## 3. Capability Matrix

| Capability | Current status | Reusable layers | Missing / blocker | Recommended stage |
| --- | --- | --- | --- | --- |
| Auth, session, registration, activation | COMPLETE_VERTICAL_SLICE | Domain, session application/query, persistence, CSRF API, auth UI, UAT | Password recovery UI is outside accepted scope | Preserve regression |
| School registration review | COMPLETE_VERTICAL_SLICE | State machine, locked review service, query, persistence, SUPER_ADMIN API/UI, browser UAT | Applicant-facing submission UI remains separate | Preserve |
| Public school registration intake | BACKEND_READY_FRONTEND_MISSING | Anonymous submit API, domain, persistence, tests | Public form and browser UAT absent | Future public intake stage |
| School master and lifecycle governance | COMPLETE_VERTICAL_SLICE | School lifecycle, query, persistence, reasoned transitions, SUPER_ADMIN routes/UI, UAT | No new lifecycle behavior in Stage 17 | Preserve |
| School-admin invitation/account governance | COMPLETE_VERTICAL_SLICE | Invitation commands, read models, persistence, scoped API/UI, UAT | Do not expose generic user CRUD | Preserve |
| Student identity application | COMPLETE_VERTICAL_SLICE | Registration/resubmit/review/rollback, profile/membership persistence, API/UI, UAT | Student profile management is separate | Preserve |
| ChallengeProject resource library/governance | COMPLETE_VERTICAL_SLICE | Lifecycle, rule versions, public/governance queries, persistence, API/UI, UAT | Activity linkage/resources outside accepted scope | Preserve Stage 16 |
| Activity | BACKEND_PARTIAL | Domain, create/publish application, persistence, public list API | Management query/detail/lifecycle and frontend absent | Stage 19 candidate |
| ActivityApplication | DEFERRED_BY_IDENTITY_MODEL | State machine, persistence, review commands, negative API boundary | Applicant identity is unresolved after Teacher removal | Product decision first |
| Score write | DEFERRED_BY_IDENTITY_MODEL | Value types, state/domain, persistence, denial tests | Operator and separation of duties unresolved | Product decision first |
| Score appeal | BACKEND_PARTIAL | Domain, mutation services, persistence, secured commands | Query/read API and frontend absent | Stage 21 candidate |
| Ranking | BACKEND_PARTIAL | Definition domain, commands, persistence, mutation API | Version/public query/calculation and frontend absent | Stage 22 candidate |
| L3 authorization | BACKEND_PARTIAL | Domain, commands, persistence, partial API | Query/detail/reject/suspend workflow absent | Later governance stage |
| Media | DEFERRED_BY_IDENTITY_MODEL | Lifecycle domain, persistence, review commands | Uploader/reviewer identity unresolved after Teacher removal | Product decision first |
| Activity result | BACKEND_PARTIAL | Domain, persistence, publish command/API | Create/read/review/public query and frontend absent | After Activity |
| Feedback | BACKEND_PARTIAL | Domain, mutation services, persistence, secured commands | Query/read model and frontend absent | Stage 21 candidate |
| Audit / platform audit | DOMAIN_READY_API_MISSING | Audit command port, adapter, entity, repository | Authorized query/list/detail/export API and UI absent | Stage 23 candidate |
| Notification | NOT_IMPLEMENTED | Placeholder entity only | No rules, service, handlers, API, or UI | Reassess after product need |

## 4. Stage 14-16 Reclassification

The following are upgraded from the v1 partial classifications for their accepted
scope: school master/lifecycle governance, school-admin invitation/account governance,
and ChallengeProject resource library/governance. This upgrade is limited to the
implemented read/write contracts and browser-tested workflows; it does not imply that
all historical screenshot features exist.

## 5. Identity-Blocked Lines

Activity application, score writing, and media upload remain `DEFERRED_BY_IDENTITY_MODEL`
and carry `PRODUCT_DECISION_REQUIRED`. The removed Teacher role cannot be recreated or
silently replaced with `SCHOOL_ADMIN`. Any future operator decision must explicitly
define target identity, school scope, review ownership, and separation of duties.

## 6. Roadmap After Stage 17

1. Stage 18: public home plus public activity read slice, only after real activity query
   and public data contracts are verified.
2. Stage 19: SCHOOL_ADMIN activity management read/create/publish slice, with explicit
   same-school authorization and no Teacher assumptions.
3. Stage 20: STUDENT self-scoped read experience for capabilities with complete query
   contracts.
4. Stage 21: appeal and feedback query/read workflows.
5. Stage 22: ranking read/version/publicization closure.
6. Stage 23: audit query and platform audit center.

This roadmap is a recommendation, not permission to begin the next stage in the Stage
17 change set.
