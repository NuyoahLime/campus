# Business Authorization Matrix

> Phase 1 Audit — read-only inventory. No code changes in this commit.

## Summary

| Metric | Count |
|--------|-------|
| Controllers | 40 |
| Endpoints | ~145 |
| Protected by `authenticated()` only | ~55 |
| Write endpoints missing role check | 14 |
| Trust request-body `actorId`/`reviewerId`/`handlerId`/`createdBy` | **6** |

---

## CRITICAL — Must Fix Before Public Registration

### 1. Request-body identity forgery (6 endpoints)

**Any authenticated user** can supply arbitrary `reviewerId`/`handlerId`/`createdBy`:

| # | Method | Path | Field |
|---|--------|------|-------|
| 1 | POST | `/api/v1/school-registrations/{id}/approve` | `reviewerId` |
| 2 | POST | `/api/v1/school-registrations/{id}/reject` | `reviewerId` |
| 3 | POST | `/api/v1/l3-authorizations/{id}/approve` | `reviewerId` |
| 4 | POST | `/api/v1/ranking-definitions` | `createdBy` |
| 5 | POST | `/api/v1/feedbacks/{id}/begin-processing` | `handlerId` (has role check but no identity verification) |
| 6 | POST | `/api/v1/score-appeals/{id}/begin-processing` | `handlerId` |

**Fix**: Remove these fields from request DTOs. Source identity from `currentActor.requireUserId()`.

### 2. No role check on critical platform endpoints

| Method | Path | Current Auth | Target |
|--------|------|-------------|--------|
| POST | `/api/v1/schools/{id}/activate` | `authenticated()` | `SUPER_ADMIN` |
| POST | `/api/v1/schools/{id}/disable` | `authenticated()` | `SUPER_ADMIN` |
| POST | `/api/v1/ranking-definitions/{id}/enable` | `authenticated()` | `SUPER_ADMIN` |
| POST | `/api/v1/ranking-definitions/{id}/disable` | `authenticated()` | `SUPER_ADMIN` |
| POST | `/api/v1/l3-authorizations` | `authenticated()` | `SUPER_ADMIN` |
| POST | `/api/v1/l3-authorizations/{id}/withdraw` | `authenticated()` | `SUPER_ADMIN` |
| POST | `/api/v1/activity-results/{id}/publish` | `authenticated()` | `SCHOOL_ADMIN` |

---

## HIGH — Missing Role Constraints

| Method | Path | Current Auth | Target |
|--------|------|-------------|--------|
| POST | `/api/v1/school-registrations/{id}/withdraw` | `authenticated()` | Owner or SUPER_ADMIN |
| POST | `/api/v1/school-registrations` | `authenticated()` | Owner |
| GET | `/api/v1/school-registrations` | `authenticated()` | Owner or SUPER_ADMIN |

---

## MEDIUM — School Scope Missing

Controllers with `@PreAuthorize("hasRole('SCHOOL_ADMIN')")` but no per-school scope check:

| Controller | Risk |
|-----------|------|
| `SchoolAdminActivityController` | School A admin can potentially access school B data |
| `SchoolAdminScoreReviewController` | Same — needs `@schoolAccess.canManage(#schoolId)` |
| `SchoolAdminScoreEntryController` | Same |
| `SchoolAdminRankingController` | Same |
| `SchoolAdminAccountController` | Uses `actorSchoolId()` correctly — ✅ |

---

## LOW — Authenticated Only (Read)

These can stay `authenticated()`:

| Method | Path |
|--------|------|
| GET | `/api/v1/auth/me` |
| GET | `/api/v1/public/**` |
| Various GET | Student/Teacher own-data endpoints (with service-layer ownership check) |

---

## Already Protected Correctly

| Controller | Protection |
|-----------|-----------|
| `UserController` | `@PreAuthorize("hasRole('SUPER_ADMIN')")` class-level |
| `AdminActivityReviewController` | `@PreAuthorize("hasRole('SUPER_ADMIN')")` class-level |
| `AdminApplicationReviewController` | `@PreAuthorize("hasRole('SUPER_ADMIN')")` class-level |
| `AdminSchoolAccountController` | `@PreAuthorize("hasRole('SUPER_ADMIN')")` method-level |
| `SchoolAdminScoreReviewController` | `@PreAuthorize("hasRole('SCHOOL_ADMIN')")` + `currentActor.requireUserId()` ✅ |
| `ChallengeProjectController` (write) | `@PreAuthorize("hasRole('SUPER_ADMIN')")` method-level |
| `MediaController` (review/approve/publish) | `@PreAuthorize` method-level |
| `FeedbackController` (process/resolve/close) | `@PreAuthorize` method-level |

---

## Fix Plan

### Phase 2 — Platform Admin Endpoint Closure
Fix all CRITICAL and HIGH items by adding `@PreAuthorize` and removing request-body actor params.

### Phase 3 — School Scope Authorization
Add `@schoolAccess.canManage(#schoolId)` to all SCHOOL_ADMIN endpoints.

### Phase 4 — Role Matrix Integration Tests
Tests that prove every endpoint returns correct 401/403/success.

### Phase 5 — Final Audit
Re-scan all controllers to verify zero gaps.
### Complete Endpoint Inventory

| # | Controller | Endpoints | Class @PreAuthorize | Issues |
|---|-----------|-----------|---------------------|--------|
| 1 | AccountActivationController | 1 | None | permitAll ✓ |
| 2 | UserController | 4 | SUPER_ADMIN | ✓ |
| 3 | AdminSchoolAccountController | 2 | None (method) | SUPER_ADMIN method-level ✓ |
| 4 | SchoolAdminAccountController | 2 | SCHOOL_ADMIN | ✓ |
| 5 | ActivityController | 1 | None | authenticated GET |
| 6 | PublicActivityController | 2 | None | permitAll ✓ |
| 7 | PublicChallengeProjectController | 2 | None | permitAll ✓ |
| 8 | AdminActivityReviewController | 6 | SUPER_ADMIN | ✓ |
| 9 | SchoolAdminActivityController | 16 | SCHOOL_ADMIN | school scope missing |
| 10 | SchoolAdminTeacherController | 1 | SCHOOL_ADMIN | ✓ |
| 11 | SchoolAdminParticipantController | 6 | SCHOOL_ADMIN | school scope missing |
| 12 | TeacherParticipantController | 1 | TEACHER | ✓ |
| 13 | StudentParticipantController | 4 | STUDENT | ✓ |
| 14 | ActivityApplicationController | 7 | None (method) | TEACHER method-level ✓ |
| 15 | TeacherApplicationSchoolController | 2 | TEACHER | ✓ |
| 16 | AdminApplicationReviewController | 6 | SUPER_ADMIN | ✓ |
| 17 | ChallengeProjectController | 4 | None (method) | SUPER_ADMIN on write ✓ |
| 18 | SchoolController | 4 | None | **CRITICAL**: activate/disable no role |
| 19 | SchoolRegistrationController | 4 | None | **CRITICAL**: reviewerId from body |
| 20 | MediaController | 6 | None (method) | hasAnyRole on review ✓ |
| 21 | ActivityResultController | 1 | None | **CRITICAL**: publish no role |
| 22 | FeedbackController | 7 | None (method) | **CRITICAL**: handlerId from body |
| 23 | ScoreAttemptController | 5 | None (method) | STUDENT/TEACHER method-level ✓ |
| 24 | ScoreAppealController | 6 | None | **CRITICAL**: handlerId from body |
| 25 | NotificationController | 4 | None | authenticated ✓ |
| 26 | StudentScoreController | 2 | STUDENT | ✓ |
| 27 | SchoolAdminScoreEntryController | 4 | SCHOOL_ADMIN | uses CurrentActor ✓ |
| 28 | SchoolAdminScoreReviewController | 4 | SCHOOL_ADMIN | uses CurrentActor ✓ |
| 29 | SchoolAdminScoreEntryOptionController | 2 | SCHOOL_ADMIN | ✓ |
| 30 | TeacherScoreEntryController | 5 | TEACHER | uses CurrentActor ✓ |
| 31 | TeacherResponsibleProjectController | 3 | TEACHER | ✓ |
| 32 | L3AuthorizationController | 3 | None | **CRITICAL**: reviewerId from body |
| 33 | RankingDefinitionController | 3 | None | **CRITICAL**: createdBy from body |
| 34 | RankingController | 8 | None (method) | SUPER_ADMIN/STUDENT on methods ✓ |
| 35 | SchoolAdminRankingController | 8 | SCHOOL_ADMIN | ✓ |
| 36 | StudentRankingController | 3 | STUDENT | ✓ |
| 37 | SchoolAdminAchievementController | 4 | SCHOOL_ADMIN | ✓ |
| 38 | StudentAchievementController | 2 | STUDENT | ✓ |
| 39 | AchievementRecordController | 6 | None (method) | SUPER_ADMIN/STUDENT on methods ✓ |
| 40 | OperationsDashboardController | 1 | None (method) | hasAnyRole ✓ |
