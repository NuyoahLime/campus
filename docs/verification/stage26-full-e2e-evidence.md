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

The runtime is started and stopped by `frontend/e2e/support/runtime.ts`. It removes only its own named PostgreSQL container and child processes. Existing local validation artifacts are preserved.

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
- Approved non-effective score remains hidden
- BEST representative visibility
- Desktop and 390px mobile action reachability

## API Scenarios

- ADMIN_DESIGNATED selection and stale CAS `409`
- Review-only approval leaves `currentEffective` false for ADMIN_DESIGNATED
- Cross-school read/mutation denial
- Anonymous, Student, Super Admin, and historical Teacher denial
- Non-participant score creation rejection

Teacher remains a historical negative fixture only. It is not granted a runtime authority.

## Deferred or Not Applicable Surfaces

- Correction has no product UI or dedicated external correction endpoint. Correction evidence remains existing PostgreSQL integration evidence.
- Ranking, ActivityResult, Media expansion, and Teacher runtime are outside Stage26 Full E2E implementation scope.

## Security and Logging

Browser and API tests use real login and server-managed `SESSION`/`XSRF-TOKEN` behavior. Test logs must not include passwords, cookies, CSRF values, or Authorization headers.
The E2E backend child process removes the host `DEBUG` environment variable and supplies an INFO-level `SPRING_APPLICATION_JSON` override, so uploaded runtime logs do not contain application debug request payloads.

## Artifacts

Playwright HTML/JUnit reports, failure screenshots, traces, videos, runtime logs, and fixture state are written below ignored `frontend/playwright-report` and `frontend/test-results` paths. CI uploads the report paths with a 14-day retention period.

## Local Execution

The local E2E command runs eight serial Playwright tests against a fresh containerized database.

- Browser tests: 5
- API E2E tests: 3
- Last verified local command: `STAGE26_CHROMIUM_PATH=<local Chromium> npm run e2e`
- Result: 8 passed, 0 failed, 0 retries
- Backend regression: `mvn clean verify` passed
- Frontend regression: `npm run build` passed
- Business validator: passed with 0 errors
