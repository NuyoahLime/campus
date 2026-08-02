# Business Authorization Matrix (v3)

> Phase 1 Audit — read-only inventory. No code changes.
> v3: removed already-fixed Feedback handlerId, added ScoreAppealController gaps, split counts.

## Summary

| Metric | Count |
|--------|-------|
| Controllers | 40 |
| HTTP Endpoints | 155 |
| All write endpoints without `@PreAuthorize` | 17 |
| Phase-2 high-risk write endpoints | 17 |
| Request-body actor/identity fields | 7 (6 reviewerId/handlerId/createdBy + 1 studentId) |
| SCHOOL_ADMIN scope gaps (cross-school) | 8 |

---

## Feedback — Already Protected

`POST /api/v1/feedbacks/{id}/begin-processing`:

- `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")` ✅
- `currentActor.requireUserId()` → not from request body ✅
- Removed from CRITICAL list in v3.

---

## Phase 2 High-Risk Write Endpoints (17)

### Platform-level (SUPER_ADMIN) — 8

| # | Method | Path | Controller |
|---|--------|------|-----------|
| 1 | POST | `/api/v1/schools/{id}/activate` | SchoolController |
| 2 | POST | `/api/v1/schools/{id}/disable` | SchoolController |
| 3 | POST | `/api/v1/ranking-definitions` | RankingDefinitionController |
| 4 | POST | `/api/v1/ranking-definitions/{id}/enable` | RankingDefinitionController |
| 5 | POST | `/api/v1/ranking-definitions/{id}/disable` | RankingDefinitionController |
| 6 | POST | `/api/v1/l3-authorizations` | L3AuthorizationController |
| 7 | POST | `/api/v1/l3-authorizations/{id}/approve` | L3AuthorizationController |
| 8 | POST | `/api/v1/l3-authorizations/{id}/withdraw` | L3AuthorizationController |

### Review (SUPER_ADMIN + remove body actor) — 2

| # | Method | Path | Controller |
|---|--------|------|-----------|
| 9 | POST | `/api/v1/school-registrations/{id}/approve` | SchoolRegistrationController |
| 10 | POST | `/api/v1/school-registrations/{id}/reject` | SchoolRegistrationController |

### School-admin — 1

| # | Method | Path | Controller |
|---|--------|------|-----------|
| 11 | POST | `/api/v1/activity-results/{id}/publish` | ActivityResultController |

### School registration (temp SUPER_ADMIN) — 2

| # | Method | Path | Controller |
|---|--------|------|-----------|
| 12 | POST | `/api/v1/school-registrations` | SchoolRegistrationController |
| 13 | POST | `/api/v1/school-registrations/{id}/withdraw` | SchoolRegistrationController |

### Score Appeals — 4 (NEW in v3)

| # | Method | Path | Controller | Current risk |
|---|--------|------|-----------|-------------|
| 14 | POST | `/api/v1/score-appeals` | ScoreAppealController | Takes `studentId` from body — can submit as anyone |
| 15 | POST | `/api/v1/score-appeals/{id}/begin-processing` | ScoreAppealController | Takes `handlerId` from body |
| 16 | POST | `/api/v1/score-appeals/{id}/reject` | ScoreAppealController | Any authenticated user can reject |
| 17 | POST | `/api/v1/score-appeals/{id}/withdraw` | ScoreAppealController | No ownership check |

---

## Request-Body Identity Fields (7)

| # | Endpoint | Field | Risk |
|---|----------|-------|------|
| 1 | `POST /school-registrations/{id}/approve` | `reviewerId` | Approve as anyone |
| 2 | `POST /school-registrations/{id}/reject` | `reviewerId` | Reject as anyone |
| 3 | `POST /l3-authorizations/{id}/approve` | `reviewerId` | Approve as anyone |
| 4 | `POST /ranking-definitions` | `createdBy` | Create as anyone |
| 5 | `POST /feedbacks/{id}/begin-processing` | ~~`handlerId`~~ → **ALREADY FIXED** (`CurrentActor`) |
| 6 | `POST /score-appeals/{id}/begin-processing` | `handlerId` | Process as anyone |
| 7 | `POST /score-appeals` | `studentId` | Submit appeal as someone else |

**Phase 2 fix**: Remove all 7 from DTOs. Source from `currentActor.requireUserId()`.

---

## ScoreAppeal Target Policies

| Endpoint | Phase 2 target | Phase 3 addition |
|----------|---------------|-----------------|
| `POST /score-appeals` | `hasRole('STUDENT')`, student from `CurrentActor` | Validate `scoreAttempt.schoolId == request school` |
| `POST /score-appeals/{id}/begin-processing` | `hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')`, handler from `CurrentActor` | `appeal.schoolId == admin.schoolId` |
| `POST /score-appeals/{id}/reject` | `hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')` | Cross-school check |
| `POST /score-appeals/{id}/withdraw` | `hasRole('STUDENT')`, appeal owned by current user | — |

---

## School Scope Gaps (Phase 3)

| Controller | Role | Actor | Scope |
|-----------|------|-------|-------|
| SchoolAdminActivityController | ✅ | ✅ | ❌ |
| SchoolAdminParticipantController | ✅ | ✅ | ❌ |
| SchoolAdminScoreReviewController | ✅ | ✅ (`CurrentActor`) | ❌ |
| SchoolAdminScoreEntryController | ✅ | ✅ | ❌ |
| SchoolAdminRankingController | ✅ | ✅ | ❌ |
| SchoolAdminAchievementController | ✅ | ✅ | ❌ |
| SchoolAdminTeacherController | ✅ | ✅ | Partial |
| SchoolAdminScoreEntryOptionController | ✅ | ✅ | ❌ |
| SchoolAdminAccountController | ✅ | ✅ | ✅ (`actorSchoolId()`) |

8 cross-school gaps.

---

## Phase 2 Scope

```text
PHASE-2-PLATFORM-ADMIN-ENDPOINT-CLOSURE

IN SCOPE:
1-8:   School/ranking/L3 → SUPER_ADMIN
9-10:  School reg approve/reject → SUPER_ADMIN + remove reviewerId
11:    Activity result publish → SCHOOL_ADMIN
12-13: School reg submit/withdraw → temp SUPER_ADMIN
14:    Score appeal submit → STUDENT, remove studentId, validate ownership
15:    Score appeal begin-processing → SCHOOL_ADMIN|SUPER_ADMIN, remove handlerId
16:    Score appeal reject → SCHOOL_ADMIN|SUPER_ADMIN
17:    Score appeal withdraw → STUDENT + ownership
      Integration tests for all 17 endpoints

OUT OF SCOPE:
      Cross-school isolation (Phase 3)
      applicant_user_id migration
      Public self-registration
```

---

## Fix Plan

| Phase | Status |
|-------|--------|
| Phase 1 ✅ | Authorization inventory (this doc) |
| Phase 2 | Close 17 platform write endpoints + remove body actors + tests |
| Phase 3 | School-scope authorization |
| Phase 4 | Role matrix integration tests |
| Phase 5 | Final audit |
