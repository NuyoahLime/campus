import { expect, request as playwrightRequest, test } from '@playwright/test';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { actors, apiRequest, loginApi } from '../support/auth';
import type { FixtureState } from '../support/fixture';

async function fixture(): Promise<FixtureState> {
  return JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime/fixture-state.json'),
    'utf8'
  )) as FixtureState;
}

test.describe.configure({ mode: 'serial' });

test('admin designated API succeeds, leaves approval selection separate, and enforces CAS', async ({ request }) => {
  const ids = await fixture();
  const login = await loginApi(request, actors.schoolAdminA);
  expect(login.status()).toBe(200);

  const detail = await apiRequest(request, 'GET',
    `/api/v1/school-admin/score-attempts/${ids.designatedFirstAttempt}`);
  expect(detail.status()).toBe(200);
  expect((await detail.json()).currentEffective).toBe(false);

  const first = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${ids.designatedFirstAttempt}/designate-effective`,
    { expectedCurrentEffectiveAttemptId: null });
  expect(first.status()).toBe(200);
  expect((await first.json()).currentEffective).toBe(true);

  const stale = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${ids.designatedSecondAttempt}/designate-effective`,
    { expectedCurrentEffectiveAttemptId: null });
  expect(stale.status()).toBe(409);

  const pending = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`);
  expect(pending.status()).toBe(200);
  const pendingBody = await pending.json();
  expect(pendingBody.status).toBe('APPROVED');
  expect(pendingBody.currentEffective).toBe(false);
});

test('cross-school and role security use real sessions', async ({ request }) => {
  const ids = await fixture();
  const admin = await loginApi(request, actors.schoolAdminA);
  expect(admin.status()).toBe(200);

  const crossRead = await apiRequest(request, 'GET',
    `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}`);
  expect(crossRead.status()).toBe(404);
  const crossMutation = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/submit`);
  expect(crossMutation.status()).toBe(403);

  const studentContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  try {
    expect((await (await loginApi(studentContext, actors.studentA)).status())).toBe(200);
    expect((await apiRequest(studentContext, 'POST',
      `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`)).status()).toBe(403);
  } finally {
    await studentContext.dispose();
  }

  const superContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  try {
    expect((await (await loginApi(superContext, actors.superAdmin)).status())).toBe(200);
    expect((await apiRequest(superContext, 'POST',
      `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`)).status()).toBe(403);
  } finally {
    await superContext.dispose();
  }

  const teacherContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  try {
    const teacherLogin = await loginApi(teacherContext, actors.teacher);
    expect(teacherLogin.status()).toBe(403);
  } finally {
    await teacherContext.dispose();
  }

  const anonymousContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  try {
    const anonymous = await apiRequest(anonymousContext, 'POST',
      `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`
    );
    expect(anonymous.status()).toBe(401);
  } finally {
    await anonymousContext.dispose();
  }
});

test('participant eligibility rejects non-participant score creation', async ({ request }) => {
  const ids = await fixture();
  expect((await (await loginApi(request, actors.schoolAdminA)).status())).toBe(200);
  const response = await apiRequest(request, 'POST',
    `/api/v1/school-admin/activity-projects/${ids.lifecycleActivityProject}/score-attempts`,
    {
      studentId: '20000000-0000-0000-0000-000000000004',
      integerValue: 3,
      scoreBusinessTime: '2026-08-27T00:00:00Z'
    });
  expect(response.status()).toBe(409);
  expect((await response.json()).code).toBe('SCORE_STUDENT_NOT_PARTICIPANT');
});
