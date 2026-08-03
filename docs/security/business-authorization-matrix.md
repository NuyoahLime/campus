# Business Authorization Matrix

> Phase 1 inventory + Phase 2 implementation result.
> Updated after Phase 2 authorization closure.

## Summary

| Metric | Count |
|--------|-------|
| Phase 1 baseline writes without `@PreAuthorize` | 17 |
| Phase 2 endpoints changed | 16 |
| Authenticated self-service unchanged | 1 (`POST /school-registrations`) |
| Request-body identity fields removed | 6 |
| Feedback handlerId (historical, already fixed) | excluded |
| SCHOOL_ADMIN cross-school gaps (Phase 3) | 8 |

## Phase 2 Role Distribution (16 endpoints)

| Role | Count | Endpoints |
|------|-------|-----------|
| `SUPER_ADMIN` only | 11 | schools ×2, ranking-definitions ×3, L3-authorizations ×3, school-registrations ×3 |
| `SCHOOL_ADMIN` only | 1 | activity-results publish |
| `SCHOOL_ADMIN` or `SUPER_ADMIN` | 2 | score-appeals begin-processing, reject |
| `STUDENT` only | 2 | score-appeals submit, withdraw |
| **Total** | **16** | |

## Removed Identity Fields (6)

| # | Endpoint | Field Removed | Now Sourced From |
|---|----------|--------------|-----------------|
| 1 | `POST /school-registrations/{id}/approve` | `reviewerId` | `CurrentActor.requireUserId()` |
| 2 | `POST /school-registrations/{id}/reject` | `reviewerId` | `CurrentActor.requireUserId()` |
| 3 | `POST /l3-authorizations/{id}/approve` | `reviewerId` | `CurrentActor.requireUserId()` |
| 4 | `POST /ranking-definitions` | `createdBy` | `CurrentActor.requireUserId()` |
| 5 | `POST /score-appeals/{id}/begin-processing` | `handlerId` | `CurrentActor.requireUserId()` |
| 6 | `POST /score-appeals` | `studentId` (and `schoolId`) | `CurrentActor.requireUserId()` + `ScoreAttempt.schoolId()` |

## School Registration Policy

| Endpoint | Phase 2 | Future |
|----------|---------|--------|
| `POST /school-registrations` | `authenticated()` — unchanged | `applicant_user_id` migration needed for ownership |
| `POST /school-registrations/{id}/withdraw` | temp `SUPER_ADMIN` | applicant self-withdraw after `applicant_user_id` |

## ScoreAppeal Submit — Phase 2 Completed

```text
studentId → from CurrentActor.requireUserId()
schoolId  → from ScoreAttempt.schoolId()
ownership → findByIdAndStudentId(scoreAttemptId, currentStudentId)
```

Phase 3: no additional submit school check required.

## School Scope Gaps (Phase 3)

| Controller | Role | Actor | Scope |
|-----------|------|-------|-------|
| SchoolAdminActivityController | ✅ | ✅ | ❌ |
| SchoolAdminParticipantController | ✅ | ✅ | ❌ |
| SchoolAdminScoreReviewController | ✅ | ✅ | ❌ |
| SchoolAdminScoreEntryController | ✅ | ✅ | ❌ |
| SchoolAdminRankingController | ✅ | ✅ | ❌ |
| SchoolAdminAchievementController | ✅ | ✅ | ❌ |
| SchoolAdminTeacherController | ✅ | ✅ | ❌ |
| SchoolAdminScoreEntryOptionController | ✅ | ✅ | ❌ |
| SchoolAdminAccountController | ✅ | ✅ | ✅ |

## Phase Status

| Phase | Status |
|-------|--------|
| Phase 1 | ✅ COMPLETED — inventory audit |
| Phase 2 | ✅ COMPLETED — 16 platform endpoints closed + 6 identity fields removed + 28 auth tests |
| Phase 3 | AWAITING APPROVAL — school-scope authorization |
| Phase 4 | NOT STARTED — role matrix integration tests |
| Phase 5 | NOT STARTED — final authorization audit |
