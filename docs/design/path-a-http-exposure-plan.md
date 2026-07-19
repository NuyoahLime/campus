# Path A HTTP Exposure Plan — Score Appeal Correction Interface

**Task:** `TASK-SCORE-APPEAL-PATH-A-APPLICATION-EXPOSE-PLAN`
**Mode:** `READ_ONLY_INTERFACE_SECURITY_AUDIT`
**Date:** 2026-07-16
**Status:** DRAFT — AWAITING REVIEW

---

## 1. Executive Summary

This document defines the HTTP exposure plan for ScoreAppeal Path A (score correction workflow), based on a full read-only audit of the current codebase. **No production code has been modified.**

The `ScoreAppealCorrectionService.correctAndResolve()` method is implemented and verified against real PostgreSQL, but has no HTTP endpoint. This plan defines the endpoint design, authorization model, error mapping, and prerequisite checklist required before the endpoint can be opened.

---

## 2. Current State Audit Findings

### 2.1 What Exists

| Layer | Artifact | Status |
|-------|----------|--------|
| Domain | `ScoreAppeal` (13-state machine) | COMPLETE |
| Domain | `AppealStatus` enum, events, exceptions | COMPLETE |
| Application | `ScoreAppealCorrectionService.correctAndResolve()` | COMPLETE, verified via IT |
| Application | `ScoreAppealRepository` port | COMPLETE |
| Persistence | `ScoreAppealEntity`, adapter, mapper | COMPLETE, upsert pattern |
| Interface | `ScoreAppealController` (`/api/v1/score-appeals`) | 4 endpoints exposed |
| Interface | `GlobalExceptionHandler` | COMPLETE, string-heuristic mapping |
| DB | `score_appeals` table (V008) | COMPLETE, `@Version` column |
| DB | `appeal_records` table (V008) | EXISTS, no Java entity (deferred) |

### 2.2 What Is Missing

| Gap | Severity | Description |
|-----|----------|-------------|
| Authentication | **BLOCKING** | `SecurityConfig` uses `anyRequest().denyAll()`. No `AuthenticationProvider`, no JWT/session auth, no `UserDetailsService`. |
| actorId sourcing | **BLOCKING** | `ScoreAppealCorrectionService` accepts `UUID actorId` as parameter. Controller must extract this from security context, not request body. |
| Authorization | **BLOCKING** | No role/permission model. `platformRole` is a free-form String (`SUPER_ADMIN` or null). `SchoolMembership.roleInSchool` is free-form (`STUDENT`, `TEACHER`, `SCHOOL_ADMIN`). No `@PreAuthorize` or authorization service exists. |
| 401/403 mapping | **BLOCKING** | `GlobalExceptionHandler` has no handlers for `AccessDeniedException` or `AuthenticationException`. |
| Request DTO | **MISSING** | No `CorrectAndResolveRequest` record exists. |
| Response DTO | **MISSING** | Current `ScoreAppealResponse(id, status)` is insufficient for Path A — caller needs new attempt ID and corrected value. |
| Concurrent safety | **CONCERN** | Two-thread concurrent correction test NOT performed. `@Version` provides DB-level protection but maps to 500 not 409. |
| Audit trail | **DEFERRED** | `appeal_records` table has no entity. Correction happens without audit record creation. |
| OpenAPI | **MISSING** | No `springdoc-openapi` dependency in POM. |
| Idempotency-Key | **NOT IMPLEMENTED** | Service relies on state-machine natural idempotency (appeal must be in PROCESSING; after correction it's RESOLVED). |

---

## 3. HTTP Endpoint Design

### 3.1 Method and Path

```http
POST /api/v1/score-appeals/{appealId}/correct-and-resolve
```

**Rationale:**
- Follows existing convention: all state transitions use `POST /api/v1/{resource}/{id}/{action}` (see 12 other controllers).
- `correct-and-resolve` is self-documenting — it describes the business action, not the internal state names.
- Avoids leaking internal state machine vocabulary (`accept-pending-correction`, `begin-score-correcting`) into the API.

**Alternative considered:**
```http
POST /api/admin/score-appeals/{appealId}/corrections
```
Rejected: The `/admin` prefix implies a separate admin API surface. The current project has no admin sub-path convention. Authorization should be handled by roles, not URL namespacing.

### 3.2 Request DTO

```java
public record CorrectAndResolveRequest(
    @NotNull ScoreValueInput correctedValue,
    @NotBlank @Size(max = 2000) String resolution
) {}
```

**`ScoreValueInput` design** (following `SubmitScoreRequest` discriminator pattern):

```java
public record ScoreValueInput(
    @NotBlank String scoreStorageType,  // "INTEGER" | "DECIMAL" | "DURATION" | "GRADE"
    Long integerValue,
    Double decimalValue,
    Long durationMs,
    String grade
) {}
```

Or, a flattened approach in `CorrectAndResolveRequest` itself:

```java
public record CorrectAndResolveRequest(
    @NotBlank String scoreStorageType,
    Long integerValue,
    Double decimalValue,
    Long durationMs,
    String grade,
    @NotBlank @Size(max = 2000) String resolution
) {}
```

**Fields the client MUST NOT submit** (enforced by DTO absence):
- `actorId` — sourced from `SecurityContext`
- `status` — determined by domain state machine
- `newAttemptId` — generated server-side
- `attemptNumber` — computed server-side
- `isCurrentEffective` — domain invariant
- `version` — JPA `@Version`, handled transparently
- `approvedAt` / `resolvedAt` — set by domain logic

**Validation rules:**
- `scoreStorageType` must match the original `ScoreAttempt.scoreStorageType` (validated in application service).
- Exactly one of `integerValue`/`decimalValue`/`durationMs`/`grade` must be non-null and must correspond to `scoreStorageType`.
- `resolution` is required (the appeal's resolution reason).

### 3.3 Response DTO

```java
public record CorrectionResponse(
    UUID appealId,
    String appealStatus,        // "RESOLVED"
    UUID originalAttemptId,      // the invalidated attempt
    UUID newAttemptId,           // the replacement attempt
    String newAttemptStatus,     // "APPROVED"
    ScoreValueResponse correctedValue,
    String resolution,
    Instant correctedAt
) {}
```

**`ScoreValueResponse`** (mirrors `ScoreValue` sealed hierarchy):

```java
public record ScoreValueResponse(
    String type,       // "INTEGER" | "DECIMAL" | "DURATION" | "GRADE"
    Long integerValue,
    Double decimalValue,
    Long durationMs,
    String grade
) {}
```

**Rationale:** The caller needs to confirm the full outcome: which attempt was replaced, what the new attempt ID is, the corrected value, and that the appeal is resolved. A bare `ScoreAppealResponse(id, status)` is insufficient.

**HTTP Status:** `200 OK`

### 3.4 Controller Method Signature (Illustrative)

```java
@PostMapping("/{appealId}/correct-and-resolve")
public ResponseEntity<CorrectionResponse> correctAndResolve(
        @PathVariable UUID appealId,
        @Valid @RequestBody CorrectAndResolveRequest request,
        Authentication authentication) {  // ← actorId from SecurityContext

    UUID actorId = extractActorId(authentication);
    ScoreValue correctedValue = mapToScoreValue(request);
    
    // Delegate to service
    correctionService.correctAndResolve(appealId, correctedValue, request.resolution(), actorId);
    
    // Build enriched response (requires querying the outcome)
    return ResponseEntity.ok(buildResponse(appealId));
}
```

---

## 4. Authentication Prerequisites

### 4.1 What Must Be Built First

The following do NOT exist in the codebase and are **hard blockers** for opening any authenticated endpoint:

| Component | Description | Priority |
|-----------|-------------|----------|
| `AuthenticationProvider` or `AuthenticationFilter` | Validates credentials, establishes `SecurityContext` | P0 |
| `UserDetailsService` or equivalent | Loads user identity for auth decisions | P0 |
| Token/session mechanism | JWT filter or Spring Session-based auth | P0 |
| `SecurityContext` integration | Makes `Authentication` available in controller methods | P0 |
| `actorId` extraction | Maps `Authentication` principal → `UUID actorId` | P0 |

### 4.2 actorId Extraction Pattern

Once authentication is in place, the controller extracts `actorId` from the `Authentication` object:

```java
private UUID extractActorId(Authentication auth) {
    // Option A: if principal is a UserDetails impl carrying the UserId
    if (auth.getPrincipal() instanceof CampusGuinnessUserDetails userDetails) {
        return userDetails.getUserId();
    }
    // Option B: if using JWT with "sub" claim = userId
    // Option C: if using Spring Session with a session attribute
    throw new AuthenticationException("Cannot resolve actor identity");
}
```

The `actorId` parameter in `ScoreAppealCorrectionService.correctAndResolve()` currently has the comment `TEMPORARY_EXPLICIT_ACTOR_ID`. This parameter is correct for the service signature — only the controller's source of the value must change.

---

## 5. Authorization Model

### 5.1 Available Identity Data

| Source | Field | Values Observed |
|--------|-------|-----------------|
| `User.platformRole` | `String` | `"SUPER_ADMIN"` or `null` |
| `SchoolMembership.roleInSchool` | `String` | `"STUDENT"`, `"TEACHER"`, `"SCHOOL_ADMIN"` |
| `SchoolMembership.schoolId` | `UUID` | References a School aggregate |
| `ScoreAppeal.schoolId` | `UUID` | The school that owns the appeal |
| `ScoreAppeal.studentId` | `UUID` | The student who submitted the appeal |

### 5.2 Proposed Authorization Rules

| Rule | Description | HTTP Status on Violation |
|------|-------------|--------------------------|
| **AUTH-01** | Actor must be authenticated | `401 Unauthorized` |
| **AUTH-02** | Actor's account must be `NORMAL` | `403 Forbidden` |
| **AUTH-03** | `SUPER_ADMIN` platform role → allowed to correct any appeal at any school | — |
| **AUTH-04** | Actor must have active `SchoolMembership` with `roleInSchool ∈ {SCHOOL_ADMIN, TEACHER}` at the school that owns the appeal (`appeal.schoolId`) | `403 Forbidden` |
| **AUTH-05** | Actor must not be the student who submitted the appeal (`appeal.studentId != actorId`) — prevents self-correction conflict of interest | `403 Forbidden` |

### 5.3 Authorization Check Location

Authorization should live in the **application layer**, not in the controller, to keep the domain and interface layers clean:

```java
// Proposed: ScoreAppealAuthorizationService (application layer)
public interface ScoreAppealAuthorizationService {
    void authorizeCorrection(UUID actorId, ScoreAppeal appeal) 
        throws AccessDeniedException;
}
```

This keeps the controller thin and allows authorization logic to be tested independently of HTTP.

### 5.4 Cross-School Access

**Decision:** NOT allowed in Path A.

A teacher at School A cannot process an appeal from School B. This matches the domain model where `SchoolMembership` ties a user to specific schools.

**SUPER_ADMIN** is the only cross-school role — this maps to the existing `User.platformRole = "SUPER_ADMIN"` pattern.

---

## 6. Error Mapping

### 6.1 Status Code Contract

| Scenario | HTTP Status | Error Code | Source Exception |
|----------|-------------|------------|------------------|
| Valid correction | `200 OK` | — | — |
| Request validation failure | `400 Bad Request` | `VALIDATION_FAILED` | `MethodArgumentNotValidException` |
| Malformed JSON | `400 Bad Request` | `MALFORMED_REQUEST` | `HttpMessageNotReadableException` |
| Wrong score type for original attempt | `400 Bad Request` | `SCORE_TYPE_MISMATCH` | `IllegalArgumentException` (custom message) |
| Not authenticated | `401 Unauthorized` | `UNAUTHORIZED` | `AuthenticationException` |
| Insufficient role / wrong school / self-correction | `403 Forbidden` | `FORBIDDEN` | `AccessDeniedException` |
| Appeal not found | `404 Not Found` | `NOT_FOUND` | `IllegalArgumentException` ("not found") |
| Appeal not in PROCESSING state | `409 Conflict` | `CONFLICT` | `InvalidAppealStateTransitionException` |
| Concurrent modification (stale version) | `409 Conflict` | `CONFLICT` | `OptimisticLockingFailureException` or `StaleObjectStateException` |
| ScoreAttempt not found (data inconsistency) | `500 Internal Server Error` | `INTERNAL_ERROR` | `IllegalArgumentException` (non-"not found" message) |
| Unexpected error | `500 Internal Server Error` | `INTERNAL_ERROR` | `RuntimeException` / `Exception` |

### 6.2 Required GlobalExceptionHandler Additions

The current `GlobalExceptionHandler` needs two new handlers:

```java
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiErrorResponse> handleUnauthorized(AuthenticationException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiErrorResponse.of("UNAUTHORIZED", "Authentication required", req.getRequestURI()));
}

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiErrorResponse.of("FORBIDDEN", "Access denied", req.getRequestURI()));
}
```

And for optimistic lock conflicts (currently falls through to 500):

```java
@ExceptionHandler(OptimisticLockingFailureException.class)
public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.of("CONFLICT", "Resource was modified by another request. Please retry.", req.getRequestURI()));
}
```

### 6.3 Domain Exception Classification Gap

Currently, `InvalidAppealStateTransitionException` (message: `"Cannot X from status Y"`) is caught by the generic `RuntimeException` handler via string matching (`msg.contains("Cannot") && msg.contains("status")`) → 409. This works but is fragile.

**Recommendation:** Add an explicit handler before opening more endpoints:

```java
@ExceptionHandler(InvalidAppealStateTransitionException.class)
public ResponseEntity<ApiErrorResponse> handleInvalidStateTransition(
        InvalidAppealStateTransitionException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.of("CONFLICT", ex.getMessage(), req.getRequestURI()));
}
```

This should be done for all domain exceptions (invalid state transitions from other aggregates too) — but scope it per the task authorization.

---

## 7. Idempotency and Repeat Requests

### 7.1 Current Behavior

The `ScoreAppealCorrectionService` provides **natural idempotency via state machine**:

1. First request: Appeal is `PROCESSING` → transitions through `ACCEPTED_PENDING_CORRECTION` → `SCORE_CORRECTING` → `RESOLVED`.
2. Second request (same appeal): Appeal is `RESOLVED` (terminal) → `correctAndResolve` fails at step 1 → throws `InvalidAppealStateTransitionException` → maps to `409 Conflict`.

### 7.2 Idempotency-Key Assessment

**An explicit `Idempotency-Key` header is NOT required for Path A** because:

- The state machine provides natural idempotency (terminal states reject further mutations).
- The correction is not a `POST /corrections` creating a sub-resource — it's a state transition on an existing resource.
- The client can safely retry a failed request: if the first request succeeded (status `RESOLVED`), the retry gets `409 Conflict` (safe to ignore); if the first request failed (network error, DB rollback), the retry succeeds.

### 7.3 Edge Case: Partial Success Before Network Failure

If the DB commit succeeds but the HTTP response is lost (network partition), the client will retry and get `409 Conflict`. The client should:
- Treat `409 Conflict` on retry as "the correction already happened."
- Query `GET /api/v1/score-appeals/{id}` to confirm the current state.

This is acceptable behavior and does not require an `Idempotency-Key`.

---

## 8. Concurrency and Optimistic Locking

### 8.1 Current Protection

- `ScoreAppealEntity` has `@Version private Integer version` → Hibernate optimistic locking.
- If two requests concurrently correct the same appeal, one will fail with `StaleObjectStateException` (wrapped as `OptimisticLockingFailureException` by Spring).

### 8.2 Gap

- The `GlobalExceptionHandler` does not catch `OptimisticLockingFailureException` → falls to the generic `RuntimeException` handler → returns `500 Internal Server Error` instead of `409 Conflict`.
- **Fix required:** Add the handler described in Section 6.2.

### 8.3 Two-Thread Concurrent Verification

```text
APPLICATION_PATH_A_CONCURRENT_SAFETY_VERIFIED = NO
```

This test has NOT been performed. It should be a prerequisite before opening the endpoint:

**Test scenario:**
1. Create a ScoreAppeal in PROCESSING state with version=N.
2. Thread A: Begin correction (reads version=N).
3. Thread B: Begin correction (reads version=N).
4. Thread A: Commits first → version becomes N+1.
5. Thread B: Attempts commit → `StaleObjectStateException` → rolls back → `OptimisticLockingFailureException` → maps to `409 Conflict`.

**Verdict:** This is a **soft gate** — the mechanism is already in place (JPA `@Version` + `saveAndFlush`). The verification test confirms it works end-to-end but is not a hard blocker for opening the endpoint (the `@Version` column has been verified in the existing IT tests).

---

## 9. Audit Trail

### 9.1 Current State

- `appeal_records` table exists (V008) with columns: `id`, `appeal_id`, `from_status`, `to_status`, `operator_id`, `comment`, `created_at`.
- **No Java entity exists** for this table (classified `IMMUTABLE_HISTORY_RECORD`, deferred per aggregate-model-matrix).
- `ScoreAppealCorrectionService` does NOT write audit records.

### 9.2 Decision: Audit Is a Separate Task

**Audit records should NOT be atomically committed with score correction in the initial Path A release.**

Rationale:
1. `appeal_records` is a deferred table — implementing it now expands scope.
2. The audit record is an `IMMUTABLE_HISTORY_RECORD` — it can be written in a separate transaction after the correction succeeds.
3. The correction itself is the critical path; audit is an observability concern.
4. If audit insertion fails, it should not roll back the score correction.

**Recommendation:** Open Path A without audit, then implement audit as a follow-up task (`TASK-SCORE-APPEAL-PATH-A-AUDIT`).

### 9.3 Future Audit Approach

```java
// Post-commit: publish domain event, async listener writes audit record
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onAppealResolved(ScoreAppealResolved event) {
    appealRecordRepository.save(new AppealRecord(
        event.appealId(), event.fromStatus(), event.toStatus(), 
        event.operatorId(), event.resolution()
    ));
}
```

This requires: `AppealRecord` entity, `AppealRecordRepository`, event enrichment (carrying `fromStatus`, `toStatus`, `operatorId`), and an event publication mechanism.

---

## 10. SecurityConfig Changes Required

The current `SecurityConfig`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .anyRequest().denyAll()
)
```

For Path A to be reachable, the security config must be updated. The minimal change depends on the authentication mechanism chosen:

### Scenario A: JWT Stateless

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .requestMatchers("/api/v1/score-appeals/{appealId}/correct-and-resolve")
        .hasAnyRole("SUPER_ADMIN", "SCHOOL_ADMIN", "TEACHER")
    .anyRequest().denyAll()
)
```

### Scenario B: Session-Based (Spring Session JDBC already configured)

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .requestMatchers("/api/v1/score-appeals/{appealId}/correct-and-resolve")
        .authenticated()
    .anyRequest().denyAll()
)
// + method-level @PreAuthorize for fine-grained school/resource checks
```

**Recommendation:** Use method-level authorization (`@PreAuthorize` or custom `@AuthorizeCorrection`) rather than URL-pattern role checks. URL patterns cannot express "actor must be a TEACHER at the school that owns this appeal" — that requires loading the appeal and checking the actor's relationship to it.

---

## 11. Implementation Sequence

The following tasks must be completed in order. Each task is independently verifiable.

```text
Phase 1: Authentication Foundation (BLOCKING)
├── TASK-AUTH-001: Implement Authentication mechanism
│   - Choose JWT vs session-based
│   - Implement AuthenticationProvider / UserDetailsService
│   - Implement SecurityFilter (JWT filter or login endpoint)
│   - Map authenticated principal → UUID actorId
│   - Update SecurityConfig to allow authenticated requests
│
├── TASK-AUTH-002: Add 401/403 exception handlers
│   - GlobalExceptionHandler entries for AuthenticationException, AccessDeniedException
│   - GlobalExceptionHandler entry for OptimisticLockingFailureException → 409
│   - Explicit handler for InvalidAppealStateTransitionException → 409
│
Phase 2: Authorization (BLOCKING)
├── TASK-AUTH-003: Implement ScoreAppeal authorization
│   - ScoreAppealAuthorizationService (role + school membership check)
│   - Self-correction prevention rule
│   - SUPER_ADMIN bypass rule
│
Phase 3: HTTP Endpoint (THIS TASK'S OUTPUT)
├── TASK-PATH-A-001: Implement Path A HTTP endpoint
│   - CorrectAndResolveRequest DTO + ScoreValueInput
│   - CorrectionResponse DTO + ScoreValueResponse
│   - Controller method in ScoreAppealController
│   - WebMvcTest for the new endpoint
│
Phase 4: Verification (SOFT GATE)
├── TASK-PATH-A-002: Concurrent correction verification
│   - Two-thread, two-transaction test
│   - Verify OptimisticLockingFailureException → 409 mapping
│
Phase 5: Observability (FOLLOW-UP)
├── TASK-PATH-A-003: Audit trail
│   - AppealRecord entity
│   - Domain event enrichment
│   - Async audit record persistence
│
Phase 6: Documentation (FOLLOW-UP)
└── TASK-PATH-A-004: OpenAPI contract
    - Add springdoc-openapi dependency
    - Annotate endpoint with @Operation
```

---

## 12. Open Decisions and Recommendations

### 12.1 Unresolved Questions

| # | Question | Recommendation | Rationale |
|---|----------|----------------|-----------|
| Q1 | JWT or session-based auth? | **Strawman: JWT stateless** | Project already has Spring Session JDBC configured (V015) but form login is disabled. The project appears to target a SPA frontend → JWT is the conventional choice. Final decision requires a dedicated auth design task. |
| Q2 | Should STUDENT role be allowed to correct? | **No** | Students submit appeals; teachers/school-admins adjudicate them. Self-service correction violates separation of concerns. |
| Q3 | Can a TEACHER at School A process appeals at School B? | **No (SUPER_ADMIN only)** | Cross-school access requires explicit platform-level authorization. |
| Q4 | Should audit be atomically committed with correction? | **No (deferred)** | Audit is observability, not business invariants. A failed audit write should not roll back the correction. |
| Q5 | Is concurrent safety verification a hard gate? | **Soft gate** | `@Version` provides proven protection. The verification test confirms, but does not change, behavior. |
| Q6 | Should we add `Idempotency-Key` header? | **Not for Path A** | State machine natural idempotency is sufficient. Revisit if Path B/C have different characteristics. |
| Q7 | What score value format should the request use? | **Discriminator: `scoreStorageType` + typed field** | Follows the `SubmitScoreRequest` pattern already established in `ScoreAttemptController`. The service validates type match against the original attempt. |
| Q8 | Should the endpoint use `/admin/` path prefix? | **No** | Project has no admin sub-path convention. Use roles for authorization, not URL namespacing. |

### 12.2 Risks

| Risk | Mitigation |
|------|------------|
| Authentication design changes the controller signature | Controller only depends on `Authentication` interface, which is stable |
| Role model formalization changes authorization logic | Encapsulate in `ScoreAppealAuthorizationService`, swap implementation |
| Audit requirement uncovered post-launch | Deferred table exists; add entity and async listener without changing correction logic |

---

## 13. Next Task

```text
NEXT_TASK = TASK-AUTH-001 (Authentication Foundation)
```

**Rationale:** Authentication is the single hard blocker for exposing any authenticated endpoint. Without it, the Path A endpoint cannot go live regardless of how complete the correction service and controller are.

The authentication design task should decide:
1. JWT vs session-based (JWT recommended for SPA frontend)
2. `UserDetailsService` implementation (loading from `UserEntity`)
3. `actorId` extraction contract
4. Migration of `platformRole` from free-form String to typed enum (if needed)
5. `SchoolMembership.roleInSchool` formalization

After authentication, the immediate follow-ups are:
```text
TASK-AUTH-002 → Exception handler additions (401, 403, 409 for optimistic lock)
TASK-AUTH-003 → ScoreAppeal authorization service
TASK-PATH-A-001 → HTTP endpoint implementation (this plan's implementation)
```

---

## 14. Audit Checklist Summary

| Item | Status |
|------|--------|
| ScoreAppealController | ✅ Audited |
| ScoreAppealCorrectionService | ✅ Audited |
| Command/DTO patterns | ✅ Audited (no Command in appeal module; records in interface) |
| User aggregate | ✅ Audited (4-state, SchoolMembership child entity) |
| Membership model | ✅ Audited (schoolId + roleInSchool as Strings) |
| Credential/auth domain classes | ✅ Audited (NONE — excluded from domain by design) |
| Spring Security config | ✅ Audited (denyAll, no auth mechanism) |
| Authentication user context | ✅ Audited (NONE — no SecurityContext integration) |
| ControllerAdvice | ✅ Audited (string-heuristic mapping, no 401/403 handlers) |
| Exception mapping | ✅ Audited (inventory of all handlers) |
| Audit tables/aggregates | ✅ Audited (appeal_records exists, deferred) |
| Existing Controller tests | ✅ Audited (WebMvcTest pattern, 7 test cases) |
| OpenAPI config | ✅ Audited (NONE — no dependency) |
| All 13 Controllers | ✅ Audited (consistent POST-for-action pattern) |
| Interface architecture test | ✅ Audited (InterfaceArchitectureTest, 5 checks) |

---

**Plan Status:** COMPLETE — AWAITING REVIEW
**No production code modified.**
