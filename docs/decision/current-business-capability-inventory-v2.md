# Current Business Capability Inventory v2

> Stage 24 refresh
> Baseline: `d11d47fcd2600ab02056baa934184b20ec2a5b72`
> Evidence: current source, controllers, frontend routes, tests, and accepted browser UAT through Stage 23

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
| COMPLETE_VERTICAL_SLICE | 11 |
| BACKEND_READY_FRONTEND_MISSING | 0 |
| BACKEND_PARTIAL | 4 |
| DOMAIN_READY_API_MISSING | 1 |
| DEFERRED_BY_IDENTITY_MODEL | 1 |
| PRODUCT_DECISION_REQUIRED flags | 0 |
| NOT_IMPLEMENTED | 1 |

The legacy generic-user governance endpoints are retained as an auxiliary risk note,
not a current product capability line. They must not be exposed as generic account
management UI.

## 3. Capability Matrix

| Capability | Current status | Reusable layers | Missing / blocker | Recommended stage |
| --- | --- | --- | --- | --- |
| Auth, session, registration, activation | COMPLETE_VERTICAL_SLICE | Domain, session application/query, persistence, CSRF API, auth UI, UAT | Password recovery UI is outside accepted scope | Preserve regression |
| School registration review | COMPLETE_VERTICAL_SLICE | State machine, locked review service, query, persistence, SUPER_ADMIN API/UI, browser UAT | Applicant-facing submission UI remains separate | Preserve |
| Public school registration intake | COMPLETE_VERTICAL_SLICE | Anonymous submit API, domain, persistence, tests, public form and browser UAT | Preserve regression | Preserve Stage 22 |
| School master and lifecycle governance | COMPLETE_VERTICAL_SLICE | School lifecycle, query, persistence, reasoned transitions, SUPER_ADMIN routes/UI, UAT | No new lifecycle behavior in Stage 17 | Preserve |
| School-admin invitation/account governance | COMPLETE_VERTICAL_SLICE | Invitation commands, read models, persistence, scoped API/UI, UAT | Do not expose generic user CRUD | Preserve |
| Student identity application | COMPLETE_VERTICAL_SLICE | Registration/resubmit/review/rollback, profile/membership persistence, API/UI, UAT | Student profile management is separate | Preserve |
| ChallengeProject resource library/governance | COMPLETE_VERTICAL_SLICE | Lifecycle, rule versions, public/governance queries, persistence, API/UI, UAT | Activity linkage/resources outside accepted scope | Preserve Stage 16 |
| Activity | COMPLETE_VERTICAL_SLICE | Domain, public read, school-admin management query/detail/create/edit/publish, persistence, API/UI, tests and UAT | ActivityApplication linkage is a separate line | Preserve Stage 18-19 |
| ActivityApplication | BACKEND_PARTIAL | State machine, persistence, review commands, negative API boundary | Student query/self-scope/withdraw and workflow API missing | Stage 25 |
| Score write | DEFERRED_BY_IDENTITY_MODEL | Value types, state/domain, persistence, denial tests | School-admin operator, confirmation and correction workflow missing | Stage 26 |
| Score appeal | COMPLETE_VERTICAL_SLICE | Domain, self-scoped student API/UI, same-school school-admin API/UI, persistence, tests and UAT | Correction remains a score-write concern | Preserve Stage 21 |
| Ranking | COMPLETE_VERTICAL_SLICE | Published snapshot persistence, scoped read API, public/student/school-admin frontend and tests | Generation/publication absent; public L3 filtering follow-up | Stage 27 |
| L3 authorization | BACKEND_PARTIAL | Domain, commands, persistence, partial API | Full query/detail/reject/suspend workflow absent | Later governance stage |
| Media | DEFERRED_BY_IDENTITY_MODEL | Lifecycle domain, persistence, review commands | Uploader/reviewer/publication contract unresolved after Teacher removal | Later product decision |
| Activity result | BACKEND_PARTIAL | Domain, persistence, publish command/API | Create/read/review/public query and frontend absent | Stage 28 |
| Feedback | COMPLETE_VERTICAL_SLICE | Domain, self-scoped student API/UI, same-school school-admin API/UI, persistence, tests and UAT | Notifications are separate | Preserve Stage 21 |
| Audit / platform audit | DOMAIN_READY_API_MISSING | Audit command port, adapter, entity, repository | Authorized query/list/detail/export API and UI absent | Stage 29 |
| Notification | NOT_IMPLEMENTED | Placeholder entity only | No rules, service, handlers, API, or UI | Reassess after product need |

## 4. Stage 14-16 Reclassification

The following are upgraded from the v1 partial classifications for their accepted
scope: school master/lifecycle governance, school-admin invitation/account governance,
and ChallengeProject resource library/governance. This upgrade is limited to the
implemented read/write contracts and browser-tested workflows; it does not imply that
all historical screenshot features exist.

## 5. Deferred and Partial Lines

ActivityApplication, Score Write, L3 ranking production and ActivityResult remain
partial implementation lines. Stage 24 has now defined their target identity,
school scope, review ownership and separation-of-duties contract. Media upload and
publication remain `DEFERRED_BY_IDENTITY_MODEL` because its uploader, school reviewer
and platform reviewer model is still unresolved. The removed Teacher role cannot be
recreated or silently replaced with `SCHOOL_ADMIN`.

## 6. Roadmap After Stage 23

1. Stage 18: public home plus public activity read slice. SEALED.
2. Stage 19: SCHOOL_ADMIN activity management read/create/publish slice. SEALED.
3. Stage 20: STUDENT self-scoped score read experience. SEALED.
4. Stage 21: appeal and feedback workflows. SEALED.
5. Stage 22: public school registration frontend. SEALED.
6. Stage 23: published ranking snapshot read/publicization. SEALED.
7. Stage 24: three-role operational responsibility baseline. Current documentation stage.
8. Stage 25: Student ActivityApplication closure.
9. Stage 26: SCHOOL_ADMIN Score Write closure.
10. Stage 27: Ranking calculation, version and publication.
11. Stage 28: ActivityResult closure.
12. Stage 29: Production readiness.
13. Stage 30: full real-data E2E and UI closure.

This roadmap is a decision baseline, not permission to implement later stages in the
Stage 24 change set.

## 7. Stage 23 Read Boundary

Ranking read is a complete vertical slice for public, student and school-admin
clients. It reads enabled definitions with published current versions and does not
calculate rankings during reads. Ranking generation and publication remain absent.

The current public query is broader than the final L3-only publication rule and must
be narrowed in the Ranking production stage. This is a documented follow-up, not a
reason to change Stage 24 production code.
