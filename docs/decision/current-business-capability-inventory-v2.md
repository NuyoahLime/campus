# Current Business Capability Inventory v2

> L3 generation product seal state sync
> Baseline: `530b226f9216a8b5a737491031b42167cdf72c90`
> Evidence: current source, controllers, frontend routes, tests, accepted Stage26
> E2E, Phase4A-Phase4D L2 ranking closure, L3 authorization closure, L3
> generation closure, and PR #59 post-merge acceptance.

## 1. Completion Rule

`COMPLETE_VERTICAL_SLICE` requires the relevant Domain, Application,
Persistence, Query, authorized API, frontend, tests, and browser E2E for the
stated product scope. A controller, entity, or test-file count alone is not
completion evidence.

Current formal identity remains `SUPER_ADMIN`, `SCHOOL_ADMIN`, and `STUDENT`.
The ordinary Teacher role is removed. Teacher-dependent lines are denied or
deferred; their historical screens are not counted as missing frontend work to
be implemented.

## 2. Audit Summary

| Status | Count |
| --- | ---: |
| Capability lines audited | 23 |
| COMPLETE_VERTICAL_SLICE | 18 |
| BACKEND_PARTIAL | 1 |
| DOMAIN_READY_API_MISSING | 1 |
| DEFERRED_BY_IDENTITY_MODEL | 1 |
| LEGACY_FROZEN / DEFERRED_FOR_V1 | 1 |
| NOT_IMPLEMENTED | 1 |

The legacy generic-user governance endpoints are retained as an auxiliary risk
note, not a current product capability line. They must not be exposed as
generic account management UI.

## 3. Capability Matrix

| Capability | Current status | Reusable layers | Missing / blocker | Recommended stage |
| --- | --- | --- | --- | --- |
| Auth, session, registration, activation | COMPLETE_VERTICAL_SLICE | Domain, session application/query, persistence, CSRF API, auth UI, E2E | Password recovery UI is outside accepted scope | Preserve regression |
| School registration review | COMPLETE_VERTICAL_SLICE | State machine, locked review service, query, persistence, SUPER_ADMIN API/UI, E2E | Applicant-facing submission UI remains separate | Preserve |
| Public school registration intake | COMPLETE_VERTICAL_SLICE | Anonymous submit API, domain, persistence, tests, public form and E2E | Preserve regression | Preserve |
| School master and lifecycle governance | COMPLETE_VERTICAL_SLICE | School lifecycle, query, persistence, reasoned transitions, SUPER_ADMIN routes/UI, E2E | No new lifecycle behavior in the L2 seal stage | Preserve |
| School-admin invitation/account governance | COMPLETE_VERTICAL_SLICE | Invitation commands, read models, persistence, scoped API/UI, E2E | Do not expose generic user CRUD | Preserve |
| Student identity application | COMPLETE_VERTICAL_SLICE | Registration/resubmit/review/rollback, profile/membership persistence, API/UI, E2E | Student profile management is separate | Preserve |
| ChallengeProject resource library/governance | COMPLETE_VERTICAL_SLICE | Lifecycle, rule versions, public/governance queries, persistence, API/UI, E2E | Activity linkage/resources outside accepted scope | Preserve |
| Activity | COMPLETE_VERTICAL_SLICE | Domain, public read, school-admin management query/detail/create/edit/publish, persistence, API/UI, tests and E2E | ActivityApplication linkage is a separate line | Preserve |
| Activity Participant Scope | COMPLETE_VERTICAL_SLICE | Participant assignment, same-school authorization, API/UI, persistence, tests and E2E | Preserve scope and participant eligibility regression | Preserve |
| ActivityApplication | LEGACY_FROZEN / DEFERRED_FOR_V1 | Independent activity-creation application aggregate, persistence, review commands and negative API boundary | No current runtime role submits this legacy flow; historical Teacher responsibility is frozen | No V1 implementation |
| Score Write / Review | COMPLETE_VERTICAL_SLICE | SCHOOL_ADMIN score create/edit/submit/review, lifecycle, persistence, same-school authorization, tests and Stage26 E2E | Preserve Stage26 semantics | Preserve |
| Effective Score / Correction | COMPLETE_VERTICAL_SLICE | Authoritative effective-score selection, correction coordination, `APPROVED` + `is_current_effective=true`, tests and Stage26 E2E | Ranking must consume, not reselect, effective scores | Preserve |
| Score appeal | COMPLETE_VERTICAL_SLICE | Domain, self-scoped student API/UI, same-school school-admin API/UI, persistence, tests and E2E | Correction remains a score-write concern | Preserve |
| Ranking published snapshot read | COMPLETE_VERTICAL_SLICE | Published snapshot persistence, scoped read API, public/student/school-admin frontend and tests | Public read is L3/global plus non-L2; same-school reads cover L1/L2 | Preserve |
| L1 Ranking Production | COMPLETE_VERTICAL_SLICE | Definition, generation, immutable generated versions, preview, publication, read, management UI, tests and E2E | Preserve same-school L1 semantics | Preserve |
| L2 Ranking Production | COMPLETE_VERTICAL_SLICE | Policy baseline, definition, BEST_SCORE generation, same-rule-version guard, publication, same-school read, management UI, tests and E2E | L2 public visibility remains denied; SUPER_ADMIN production-view override is not implemented | L2 product seal |
| L3 authorization | COMPLETE_VERTICAL_SLICE | Domain, commands, persistence, scoped API/query, validation, SchoolAdmin auth UI, SuperAdmin auth UI, workflow lifecycle, tests and E2E | Manual Activity UUID selector remains deferred | L3 authorization product seal |
| L3 Generation | COMPLETE_VERTICAL_SLICE | Super-admin generation flow, candidate query, approved usable authorization consumption, BEST_SCORE reduction, immutable snapshot, tests and E2E | L3 publication and management frontend remain separate | L3 generation product seal |
| Media | DEFERRED_BY_IDENTITY_MODEL | Lifecycle domain, persistence, review commands | Uploader/reviewer/publication contract unresolved after Teacher removal | Later product decision |
| Activity result | BACKEND_PARTIAL | Domain, persistence, publish command/API | Create/read/review/public query and frontend absent | ActivityResult closure |
| Feedback | COMPLETE_VERTICAL_SLICE | Domain, self-scoped student API/UI, same-school school-admin API/UI, persistence, tests and E2E | Notifications are separate | Preserve |
| Audit / platform audit | DOMAIN_READY_API_MISSING | Audit command port, adapter, entity, repository | Authorized query/list/detail/export API and UI absent | Production readiness |
| Notification | NOT_IMPLEMENTED | Placeholder entity only | No rules, service, handlers, API, or UI | Reassess after product need |

## 4. L2 Ranking Product Seal

L2 ranking is now a complete same-school vertical slice:

1. `SCHOOL_ADMIN` creates one L2 `RankingDefinition` per school and
   ChallengeProject.
2. Generation consumes Stage26 authoritative effective scores only:
   `score_status = APPROVED` and `is_current_effective = true`.
3. L2 candidate selection uses `BEST_SCORE` across eligible ActivityProjects.
4. Candidates must share the same RuleVersion. Cross-rule-version generation is
   rejected.
5. Supported L2 filters are grade, class, and activity period.
6. Generated `RankingVersion` snapshots remain invisible until publication.
7. Publication switches a generated version to `PUBLISHED`, replaces the
   previous current published version, sets `current_version_id`, and records a
   server-derived `published_at`.
8. Ranking entries and score-source rows remain immutable publication
   snapshots.
9. `/school-admin/ranking-management` supports L1 and L2 create, generate,
   preview, reload, publish, read, and disable workflows.

The L2 physical invariant is preserved by
`uq_ranking_def_l2_school_project`: one school may have only one L2 definition
per ChallengeProject. Duplicate creation is a controlled `409` conflict with
code `L2_RANKING_DEFINITION_ALREADY_EXISTS`.

L2 activity-period filters reject `activityPeriodStart > activityPeriodEnd`
before persisting the definition. `start < end`, `start == end`, only-start,
and only-end filters remain valid.

## 5. Visibility Seal

| Actor / access path | L1 | L2 | L3 |
| --- | --- | --- | --- |
| Anonymous public read | Existing public/global behavior | DENY | ALLOW when implemented and published |
| Same-school student | ALLOW published same-school snapshot | ALLOW published same-school snapshot | Future L3 policy |
| Same-school school admin | ALLOW published same-school snapshot | ALLOW published same-school snapshot | Future L3 policy |
| Other school | DENY | DENY | Future L3 policy |
| Super Admin production-view override | NOT IMPLEMENTED | NOT IMPLEMENTED | Administrative capability only |

## 6. L3 Current State

L3 authorization and L3 generation are complete vertical slices. The ranking
chain remains out of scope here:

| L3 area | Status |
| --- | --- |
| L3 Authorization | COMPLETE_VERTICAL_SLICE |
| L3 Generation | COMPLETE_VERTICAL_SLICE |
| L3 Publication | NOT_IMPLEMENTED |
| L3 Management Frontend | NOT_IMPLEMENTED |

SchoolAdmin / SuperAdmin authorization UI and L3 generation are complete on
master. L3 ranking management frontend remains unimplemented and is not to be
conflated with authorization workflow closure.

## 7. Remaining Product Roadmap

1. L3 Publication.
2. L3 Ranking Management Frontend.
3. L3 Product Seal.
4. ActivityResult closure.
5. Production readiness.
6. Full real-data E2E / UI closure.

Media and Notification are not inserted into the current main production chain
until a separate product decision makes them part of that chain.

This inventory is a decision baseline, not permission to implement later
stages in the L2 product-seal change set.
