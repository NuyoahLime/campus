# Business Authorization Matrix (v2)

> Phase 1 Audit — read-only inventory. No code changes. 
> v2: corrected counts, explicit CRITICAL list, reconciled school scope, fixed registration policy.

## Summary

| Metric | Count |
|--------|-------|
| Controllers | 40 |
| HTTP Endpoints | 155 |
| `permitAll()` (SecurityConfig) | 11 |
| `authenticated()` only (catch-all `/api/**`) | 51 |
| — of which are WRITE (POST/PUT/PATCH/DELETE) | 14 |
| Endpoints with `@PreAuthorize` (class or method) | 73 |
| Request-body `reviewerId`/`handlerId`/`createdBy` fields | 6 |
| Endpoints also protected by SecurityConfig rule | 4 (`/api/v1/users/**`) |

---

## Authenticated-only Write Endpoints (14)

These 14 endpoints currently have NO `@PreAuthorize` and are only protected by `SecurityConfig`'s `.requestMatchers("/api/**").authenticated()`.

### Platform-level (require SUPER_ADMIN)

| # | Method | Path | Controller | Phase 2 fix |
|---|--------|------|-----------|-------------|
| 1 | POST | `/api/v1/schools/{id}/activate` | SchoolController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| 2 | POST | `/api/v1/schools/{id}/disable` | SchoolController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| 3 | POST | `/api/v1/ranking-definitions` | RankingDefinitionController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| 4 | POST | `/api/v1/ranking-definitions/{id}/enable` | RankingDefinitionController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| 5 | POST | `/api/v1/ranking-definitions/{id}/disable` | RankingDefinitionController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| 6 | POST | `/api/v1/l3-authorizations` | L3AuthorizationController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| 7 | POST | `/api/v1/l3-authorizations/{id}/approve` | L3AuthorizationController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` + remove `reviewerId` |
| 8 | POST | `/api/v1/l3-authorizations/{id}/withdraw` | L3AuthorizationController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` |

### Review endpoints (request-body identity forgery risk)

| # | Method | Path | Controller | Phase 2 fix |
|---|--------|------|-----------|-------------|
| 9 | POST | `/api/v1/school-registrations/{id}/approve` | SchoolRegistrationController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` + remove `reviewerId` |
| 10 | POST | `/api/v1/school-registrations/{id}/reject` | SchoolRegistrationController | `@PreAuthorize("hasRole('SUPER_ADMIN')")` + remove `reviewerId` |

### School-admin level

| # | Method | Path | Controller | Phase 2 fix |
|---|--------|------|-----------|-------------|
| 11 | POST | `/api/v1/activity-results/{id}/publish` | ActivityResultController | `@PreAuthorize("hasRole('SCHOOL_ADMIN')")` |

### Ownership-tracked (current: authenticated; future: owner+role)

| # | Method | Path | Controller | Phase 2 fix |
|---|--------|------|-----------|-------------|
| 12 | POST | `/api/v1/school-registrations` | SchoolRegistrationController | `authenticated()` — ownership not recorded (no `applicant_user_id` column) |
| 13 | POST | `/api/v1/school-registrations/{id}/withdraw` | SchoolRegistrationController | **Temp**: `SUPER_ADMIN` — withdrawn_by is string, not FK |

### Student/Teacher self-service (authenticated OK)

| # | Method | Path | Controller | Phase 2 fix |
|---|--------|------|-----------|-------------|
| 14 | POST | `/api/v1/feedbacks/{id}/begin-processing` | FeedbackController | Already `hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')` but takes `handlerId` from body |

Note: #14 already has a role check but still accepts `handlerId` from the request body — the field must be removed and sourced from `CurrentActor`.

---

## Request-Body Identity Fields (6)

These DTOs allow the caller to specify who performed the action:

| # | Endpoint | Field | Controller |
|---|----------|-------|-----------|
| 1 | `POST /api/v1/school-registrations/{id}/approve` | `reviewerId` | SchoolRegistrationController |
| 2 | `POST /api/v1/school-registrations/{id}/reject` | `reviewerId` | SchoolRegistrationController |
| 3 | `POST /api/v1/l3-authorizations/{id}/approve` | `reviewerId` | L3AuthorizationController |
| 4 | `POST /api/v1/ranking-definitions` | `createdBy` | RankingDefinitionController |
| 5 | `POST /api/v1/feedbacks/{id}/begin-processing` | `handlerId` | FeedbackController |
| 6 | `POST /api/v1/score-appeals/{id}/begin-processing` | `handlerId` | ScoreAppealController |

**Fix**: Remove these fields from DTOs. Source from `currentActor.requireUserId()`.

---

## School Scope Analysis

For every controller with `hasRole('SCHOOL_ADMIN')`, check three layers:

| Controller | Role OK? | Actor from SecurityContext? | School Scope? |
|-----------|----------|---------------------------|---------------|
| SchoolAdminActivityController | YES (`@PreAuthorize`) | Partial — `createdBy` from CurrentActor but schoolId from request | NO — no cross-school check |
| SchoolAdminParticipantController | YES (`@PreAuthorize`) | YES — studentId only | NO — no cross-school check |
| SchoolAdminScoreReviewController | YES (`@PreAuthorize`) | YES — `currentActor.requireUserId()` | NO |
| SchoolAdminScoreEntryController | YES (`@PreAuthorize`) | YES — `CurrentActor` | NO |
| SchoolAdminRankingController | YES (`@PreAuthorize`) | YES | NO |
| SchoolAdminAccountController | YES (`@PreAuthorize`) | YES — `actorSchoolId()` | YES ✅ |
| SchoolAdminAchievementController | YES (`@PreAuthorize`) | YES | NO |
| SchoolAdminTeacherController | YES (`@PreAuthorize`) | YES — queries by school | Partial |
| SchoolAdminScoreEntryOptionController | YES (`@PreAuthorize`) | YES | NO |

**School scope gaps: 8 controllers** (Phase 3).

---

## School Registration Special Case

The `school_registrations` table lacks an `applicant_user_id` column. Current fields:

- `contact_name`, `contact_phone`, `contact_email` — not authenticated
- `withdrawn_by VARCHAR(100)` — string, not FK
- `reviewed_by UUID` — set during review

Therefore ownership cannot be verified by the current schema.

### Phase 2 temporary policy

| Endpoint | Phase 2 protection | Blocked by |
|----------|--------------------|------------|
| `POST /school-registrations` | `authenticated()` | `applicant_user_id` missing |
| `POST /school-registrations/{id}/approve` | `hasRole('SUPER_ADMIN')` | — |
| `POST /school-registrations/{id}/reject` | `hasRole('SUPER_ADMIN')` | — |
| `POST /school-registrations/{id}/withdraw` | **Temporarily `hasRole('SUPER_ADMIN')`** | `applicant_user_id` missing |

Future: add `school_registrations.applicant_user_id UUID` → then allow applicant self-withdraw.

---

## Already Protected (reference)

| Controller | Mechanism |
|-----------|-----------|
| UserController | Class `@PreAuthorize("hasRole('SUPER_ADMIN')")` + SecurityConfig |
| AdminActivityReviewController | Class `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| AdminApplicationReviewController | Class `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| AdminSchoolAccountController | Method `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| StudentScoreController | Class `@PreAuthorize("hasRole('STUDENT')")` |
| StudentParticipantController | Class `@PreAuthorize("hasRole('STUDENT')")` |
| StudentRankingController | Class `@PreAuthorize("hasRole('STUDENT')")` |
| StudentAchievementController | Class `@PreAuthorize("hasRole('STUDENT')")` |
| TeacherParticipantController | Class `@PreAuthorize("hasRole('TEACHER')")` |
| TeacherScoreEntryController | Class `@PreAuthorize("hasRole('TEACHER')")` + CurrentActor |
| TeacherResponsibleProjectController | Class `@PreAuthorize("hasRole('TEACHER')")` |
| ActivityApplicationController | Method `@PreAuthorize("hasRole('TEACHER')")` |
| ChallengeProjectController (write) | Method `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| MediaController (review/approve/publish) | Method `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")` |
| FeedbackController (process/resolve/close) | Method `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")` |
| OperationsDashboardController | Method `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")` |
| SchoolAdminScoreReviewController | Class `@PreAuthorize("hasRole('SCHOOL_ADMIN')")` + `currentActor.requireUserId()` ✅ |
| RankingController | Method-level: `hasRole('SUPER_ADMIN')` / `hasRole('STUDENT')` |
| AchievementRecordController | Method-level: `hasRole('SUPER_ADMIN')` / `hasRole('STUDENT')` |
| ScoreAttemptController | Method-level: `hasRole('STUDENT')` / `hasRole('TEACHER')` |

---

## Phase 2 Implementation Scope

```text
PHASE-2-PLATFORM-ADMIN-ENDPOINT-CLOSURE

IN SCOPE:
1. School activate/disable → @PreAuthorize("hasRole('SUPER_ADMIN')")
2. Ranking definition create/enable/disable → @PreAuthorize("hasRole('SUPER_ADMIN')")
3. L3 authorization create/approve/withdraw → @PreAuthorize("hasRole('SUPER_ADMIN')")
4. School registration approve/reject → @PreAuthorize("hasRole('SUPER_ADMIN')")
5. School registration withdraw → TEMPORARILY @PreAuthorize("hasRole('SUPER_ADMIN')")
6. Activity result publish → @PreAuthorize("hasRole('SCHOOL_ADMIN')")
7. Remove reviewerId/handlerId/createdBy from all 6 DTOs
8. Resolve actors exclusively from CurrentActor
9. Add integration tests for all protected endpoints

OUT OF SCOPE:
1. SCHOOL_ADMIN cross-school isolation (Phase 3)
2. applicant_user_id database migration (needed before owner-based policies)
3. public self-registration
4. identity application workflow
5. SCHOOL_ADMIN role matrix tests (Phase 4)
```

---

## Fix Plan Summary

| Phase | Content |
|-------|---------|
| Phase 1 ✅ | Authorization inventory (this doc) |
| Phase 2 | Close platform admin endpoints + remove body actor params |
| Phase 3 | School-scope authorization (`@schoolAccess.canManage(#schoolId)`) |
| Phase 4 | Role matrix integration tests |
| Phase 5 | Final authorization audit |
