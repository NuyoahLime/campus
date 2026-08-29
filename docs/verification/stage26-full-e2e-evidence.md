# Stage26 Full E2E Evidence

This document describes the reproducible Full E2E runtime for the sealed Stage26 score business chain.

## Runtime

- Baseline: `5ffa7092a39d27458a205b1e63a838868a0f7ae1`
- Browser runner: Playwright with Chromium
- Local browser override: `STAGE26_CHROMIUM_PATH` may point to an already installed Chromium when the Playwright CDN is unavailable; CI uses the pinned Playwright browser install.
- Database: fresh PostgreSQL `18.4` Docker container per run
- Backend: Spring Boot on port `8080`
- Frontend: Vue/Vite on port `5173`
- API base: `/api/v1`
- Flyway: fresh migration through `V018`
- Node CI baseline: Node `20`
- Last verified local toolchain: Node `24.19.0`, npm `11.17.0`, Playwright `1.62.1`, Chromium `140.0.7339.16`

The runtime is started and stopped by `frontend/e2e/support/runtime.ts`. It registers the PostgreSQL container and each child PID immediately after creation, then uses independent best-effort cleanup for frontend, backend, and PostgreSQL if startup fails. Existing local validation artifacts are preserved.

## Evidence Classes

- `BROWSER`: real Chromium navigation, input, click, DOM assertion, session, and CSRF-backed product flow.
- `API_E2E`: real HTTP session and CSRF requests through the Vite proxy.
- `POSTGRES_INTEGRATION`: existing JUnit/Failsafe evidence for concurrency, correction, rollback, and persistence.
- `STATIC`: route, source, migration, and scope checks.

API and PostgreSQL evidence must not be reported as Browser evidence.

## Browser Scenarios

- School Admin login and activity navigation
- Participant visibility
- Draft creation and editing
- Submit, reject with reason, return-to-draft, resubmit, approve
- Review history, empty history, reviewer, reason, ordering, and close behavior
- Reloaded authoritative state
- Route transition clears history and lifecycle dialogs
- Stale lifecycle submit returns a real `409` and refreshes the visible status
- Unauthenticated School Admin route redirects to login
- Student current-score list and detail
- BEST selection through real create, submit, and approve operations: `10` becomes non-effective after `20` is approved
- LAST selection through real create, submit, and approve operations: attempt `#2 = 1` becomes effective even though attempt `#1 = 100` has a later business time
- Approved non-effective scores remain hidden from the Student UI
- Desktop and 390px mobile reject/history dialog reachability with no blocking page-level horizontal overflow

## API Scenarios

- ADMIN_DESIGNATED selection and stale CAS `409`
- Review-only approval leaves `currentEffective` false for ADMIN_DESIGNATED
- Cross-school detail, patch, submit, reject, approve, review-history, return-to-draft, and designation denial
- Anonymous, Student, Super Admin, inactive School Admin, ambiguous School Admin membership, and historical Teacher denial
- Non-participant score creation rejection

Teacher remains a historical negative fixture only. It is not granted a runtime authority.

## Deferred or Not Applicable Surfaces

- Correction has no product UI or dedicated external correction endpoint. Correction evidence remains existing PostgreSQL integration evidence.
- Ranking, ActivityResult, Media expansion, and Teacher runtime are outside Stage26 Full E2E implementation scope.

## Security and Logging

Browser and API tests use real login and server-managed `SESSION`/`XSRF-TOKEN` behavior. Test logs must not include passwords, cookies, CSRF values, or Authorization headers.
The E2E backend child process removes the host `DEBUG` environment variable and supplies an INFO-level `SPRING_APPLICATION_JSON` override, so uploaded runtime logs do not contain application debug request payloads.

## Artifacts

Playwright tracing is disabled (`trace: 'off'`) for authenticated Stage26 E2E. A deliberately failing authenticated browser test verifies that no trace, HAR, or storage-state artifact is generated.

CI uploads only an explicit allowlist: the Playwright HTML report and Stage26 JUnit XML. It does not upload `frontend/test-results/**`, runtime logs, fixture state, screenshots, videos, traces, HAR files, storage state, `.env`, or credentials. Before upload, `npm run e2e:artifact-guard` fails on trace/HAR/storage-state paths and scans the actual upload candidates for session, CSRF, cookie, Authorization, fixture-password, and database-password content.

## Local Execution

The local E2E command runs nine serial Playwright tests against a fresh containerized database.

- Browser tests: 5
- API E2E tests: 4
- Last verified local command: `STAGE26_CHROMIUM_PATH=<local Chromium> npm run e2e`
- Result: two consecutive runs, each 9 passed, 0 failed, 0 skipped, 0 retries
- Local repeatability total: 18/18 passed across two independent runs
- Remote Linux CI suite: 9 passed, 0 failed, 0 skipped, 0 retries
- Startup-failure cleanup: `npm run e2e:startup-cleanup` passed with no orphan backend, frontend, or PostgreSQL
- Trace guard: `npm run e2e:trace-guard` passed with 0 trace/HAR/storage-state artifacts
- Artifact guard: `npm run e2e:artifact-guard` passed
- Backend regression: `mvn clean verify` passed
- Frontend regression: `npm run build` passed
- Business validator: passed with 0 errors
