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

type ScoreAttempt = {
  scoreAttemptId: string;
  status: string;
  currentEffective: boolean;
  integerValue: number | null;
  attemptNumber: number;
};

async function createSubmittedAndApproved(
  request: Parameters<typeof apiRequest>[0],
  activityProjectId: string,
  studentId: string,
  integerValue: number,
  scoreBusinessTime: string
): Promise<ScoreAttempt> {
  const created = await apiRequest(request, 'POST',
    `/api/v1/school-admin/activity-projects/${activityProjectId}/score-attempts`,
    { studentId, integerValue, scoreBusinessTime });
  expect(created.status()).toBe(201);
  const draft = await created.json() as ScoreAttempt;
  expect(draft.status).toBe('DRAFT');

  const submitted = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${draft.scoreAttemptId}/submit`);
  expect(submitted.status()).toBe(200);
  expect((await submitted.json() as ScoreAttempt).status).toBe('PENDING_REVIEW');

  const approved = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${draft.scoreAttemptId}/approve`);
  expect(approved.status()).toBe(200);
  return approved.json() as Promise<ScoreAttempt>;
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

test('BEST and LAST use real lifecycle approval and attempt-number selection', async ({ request }) => {
  const ids = await fixture();
  expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);

  const bestFirst = await createSubmittedAndApproved(
    request, ids.bestActivityProject, ids.studentA, 10, '2026-08-27T12:00:00Z'
  );
  expect(bestFirst.currentEffective).toBe(true);
  const bestSecond = await createSubmittedAndApproved(
    request, ids.bestActivityProject, ids.studentA, 20, '2026-08-27T12:05:00Z'
  );
  expect(bestSecond.currentEffective).toBe(true);

  const bestFirstDetail = await apiRequest(request, 'GET',
    `/api/v1/school-admin/score-attempts/${bestFirst.scoreAttemptId}`);
  expect(bestFirstDetail.status()).toBe(200);
  expect((await bestFirstDetail.json() as ScoreAttempt).currentEffective).toBe(false);

  const lastFirst = await createSubmittedAndApproved(
    request, ids.lastActivityProject, ids.studentA, 100, '2026-08-27T12:10:00Z'
  );
  const lastSecond = await createSubmittedAndApproved(
    request, ids.lastActivityProject, ids.studentA, 1, '2026-08-27T12:00:00Z'
  );
  expect(lastFirst.attemptNumber).toBe(1);
  expect(lastSecond.attemptNumber).toBe(2);
  expect(lastSecond.currentEffective).toBe(true);

  const lastFirstDetail = await apiRequest(request, 'GET',
    `/api/v1/school-admin/score-attempts/${lastFirst.scoreAttemptId}`);
  expect(lastFirstDetail.status()).toBe(200);
  expect((await lastFirstDetail.json() as ScoreAttempt).currentEffective).toBe(false);
});

test('cross-school and role security use real sessions', async ({ request }) => {
  const ids = await fixture();
  const admin = await loginApi(request, actors.schoolAdminA);
  expect(admin.status()).toBe(200);

  const crossRead = await apiRequest(request, 'GET',
    `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}`);
  expect(crossRead.status()).toBe(404);
  const crossCalls = [
    apiRequest(request, 'PATCH', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}`,
      { integerValue: 98, scoreBusinessTime: '2026-08-27T00:00:00Z' }),
    apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/submit`),
    apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/approve`),
    apiRequest(request, 'GET', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/reviews`),
    apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/designate-effective`,
      { expectedCurrentEffectiveAttemptId: null }),
    apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/reject`,
      { reason: 'Cross-school attempt' }),
    apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${ids.otherSchoolAttempt}/return-to-draft`)
  ];
  for (const response of await Promise.all(crossCalls)) {
    expect([403, 404]).toContain(response.status());
  }

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

  const inactiveContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  try {
    const inactiveLogin = await loginApi(inactiveContext, actors.inactiveSchoolAdmin);
    if (inactiveLogin.status() === 200) {
      expect((await apiRequest(inactiveContext, 'POST',
        `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`)).status()).toBe(403);
    } else {
      expect(inactiveLogin.status()).toBe(403);
    }
  } finally {
    await inactiveContext.dispose();
  }

  const ambiguousContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  try {
    const ambiguousLogin = await loginApi(ambiguousContext, actors.ambiguousSchoolAdmin);
    expect([200, 403]).toContain(ambiguousLogin.status());
    if (ambiguousLogin.status() === 200) {
      expect((await apiRequest(ambiguousContext, 'POST',
        `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`)).status()).toBe(403);
    }
  } finally {
    await ambiguousContext.dispose();
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
