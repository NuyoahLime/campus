import { expect, request as playwrightRequest, test } from '@playwright/test';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { actors, apiRequest, loginApi, loginUi } from '../support/auth';
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

async function selectDefinition(page: Parameters<typeof loginUi>[0], name: string) {
  const item = page.locator('.ranking-definition-list button').filter({ hasText: name }).first();
  await expect(item).toBeVisible();
  await item.click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

test.describe('L1 ranking management frontend', () => {
  test.describe.configure({ mode: 'serial' });

  test('school admin creates, generates, previews, publishes, and refreshes a ranking', async ({ page, request }) => {
    await fixture();
    await loginUi(page, actors.schoolAdminB);
    await page.goto('/school-admin/ranking-management');
    await expect(page).toHaveURL(/\/school-admin\/ranking-management$/);

    const name = `E2E L1 Management ${Date.now()}`;
    await page.getByLabel(/^Name$/).fill(name);
    const createForm = page.locator('.ranking-create-form');
    await createForm.locator('select').first().selectOption('L1');
    await createForm.locator('select').nth(1).selectOption({ label: 'ACTIVITY_OTHER_SCHOOL' });
    await createForm.locator('select').nth(2).selectOption({ label: 'E2E Other School Project / Rule V1' });

    const createResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith('/api/v1/ranking-definitions')
    );
    await page.getByRole('button', { name: 'Create L1 Ranking' }).click();
    const createResult = await createResponse;
    expect(createResult.status()).toBe(201);
    const created = await createResult.json() as { id: string };
    await expect(page.getByText('RankingDefinition created.')).toBeVisible();
    await expect(page.getByRole('heading', { name })).toBeVisible();
    await expect(page.getByText('No generated version')).toBeVisible();

    const detail = page.locator('.ranking-management-detail');
    const generateResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/ranking-definitions/${created.id}/generate`)
    );
    await detail.getByRole('button', { name: 'Generate' }).click();
    const generated = await generateResponse;
    expect(generated.status()).toBe(200);
    await expect(detail).toContainText('GENERATED');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('1 entries');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('e2e-student-other-school');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('99');

    const publicBeforePublish = await apiRequest(request, 'GET', `/api/v1/public/rankings/${created.id}`);
    expect(publicBeforePublish.status()).toBe(404);

    await page.reload();
    await selectDefinition(page, name);
    await expect(detail).toContainText('GENERATED');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('1 entries');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('e2e-student-other-school');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('99');

    await detail.locator('.ranking-management-section')
      .filter({ hasText: 'GENERATED SNAPSHOT' })
      .getByRole('button', { name: 'Publish' })
      .click();
    const dialog = page.getByRole('dialog', { name: 'Confirm publication' });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('will become the current published ranking');
    const publishResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().includes(`/api/v1/ranking-definitions/${created.id}/versions/`)
      && response.url().endsWith('/publish')
    );
    await dialog.getByRole('button', { name: 'Publish' }).click();
    expect((await publishResponse).status()).toBe(200);

    await expect(page.getByText(/Published V/)).toBeVisible();
    await expect(page.locator('.ranking-management-summary')).toContainText('Current Published');
    await expect(page.locator('.ranking-management-section').last()).toContainText('PUBLISHED');
    await expect(page.locator('.ranking-management-section').last()).toContainText('e2e-student-other-school');

    const publicAfterPublish = await apiRequest(request, 'GET', `/api/v1/public/rankings/${created.id}`);
    expect(publicAfterPublish.status()).toBe(200);
    expect((await publicAfterPublish.json()).entries).toHaveLength(1);

    await detail.getByRole('button', { name: 'Disable' }).click();
    await expect(page.getByText('RankingDefinition disabled.')).toBeVisible();
    await expect(page.getByText('Disabled definitions cannot generate or publish rankings.')).toBeVisible();
    await page.reload();
    await selectDefinition(page, name);
    await expect(detail.getByRole('button', { name: 'Enable' })).toBeVisible();
    await expect(detail.getByRole('button', { name: 'Generate' })).toBeDisabled();
  });

  test('school admin completes the L2 ranking management workflow', async ({ page, request }) => {
    const ids = await fixture();
    expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);
    const firstScore = await createSubmittedAndApproved(
      request, ids.l2BestActivityProject, ids.studentA, 90, '2026-08-28T12:00:00Z'
    );
    expect(firstScore.currentEffective).toBe(true);
    const selectedScore = await createSubmittedAndApproved(
      request, ids.l2BestActivityProject, ids.studentA, 95, '2026-08-28T12:05:00Z'
    );
    expect(selectedScore.currentEffective).toBe(true);

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/ranking-management');
    await expect(page).toHaveURL(/\/school-admin\/ranking-management$/);

    const name = `E2E L2 Management ${Date.now()}`;
    await page.getByLabel(/^Name$/).fill(name);
    const createForm = page.locator('.ranking-create-form');
    await createForm.locator('select').first().selectOption('L2');
    await createForm.locator('select').nth(1).selectOption(ids.l2BestProject);
    await page.getByLabel(/^Grade Filter$/).fill('2026');
    await page.getByLabel(/^Class Filter$/).fill('E2E-A');

    const createResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith('/api/v1/ranking-definitions')
    );
    await page.getByRole('button', { name: 'Create L2 Ranking' }).click();
    const createResult = await createResponse;
    expect(createResult.status()).toBe(201);
    const created = await createResult.json() as { id: string };
    await expect(page.getByText('L2 RankingDefinition created.')).toBeVisible();
    await expect(page.getByRole('heading', { name })).toBeVisible();

    const detail = page.locator('.ranking-management-detail');
    await expect(detail).toContainText('L2');
    await expect(detail).toContainText('E2E L2 Best Project');
    await expect(detail).toContainText('Grade 2026');
    await expect(detail).toContainText('Class E2E-A');
    await expect(detail).toContainText('L2 SCOPE');
    await expect(detail).toContainText('BEST_SCORE');

    const generateResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/ranking-definitions/${created.id}/generate`)
    );
    await detail.getByRole('button', { name: 'Generate' }).click();
    const generated = await generateResponse;
    expect(generated.status()).toBe(200);
    await expect(detail).toContainText('GENERATED');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('1 entries');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('e2e-student-a');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('95');

    const publicBeforePublish = await apiRequest(request, 'GET', `/api/v1/public/rankings/${created.id}`);
    expect(publicBeforePublish.status()).toBe(404);

    await page.reload();
    await selectDefinition(page, name);
    await expect(detail).toContainText('L2 SCOPE');
    await expect(detail).toContainText('BEST_SCORE');
    await expect(detail).toContainText('Grade 2026');
    await expect(detail).toContainText('Class E2E-A');
    await expect(detail).toContainText('GENERATED');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('1 entries');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('e2e-student-a');
    await expect(detail.locator('.ranking-version-box').first()).toContainText('95');

    await detail.locator('.ranking-management-section')
      .filter({ hasText: 'GENERATED SNAPSHOT' })
      .getByRole('button', { name: 'Publish' })
      .click();
    const dialog = page.getByRole('dialog', { name: 'Confirm publication' });
    await expect(dialog).toBeVisible();
    const publishResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().includes(`/api/v1/ranking-definitions/${created.id}/versions/`)
      && response.url().endsWith('/publish')
    );
    await dialog.getByRole('button', { name: 'Publish' }).click();
    expect((await publishResponse).status()).toBe(200);

    await expect(page.getByText(/Published V/)).toBeVisible();
    await expect(detail.locator('.ranking-management-summary').first()).toContainText('Current Published');
    await expect(page.locator('.ranking-management-section').last()).toContainText('PUBLISHED');
    await expect(page.locator('.ranking-management-section').last()).toContainText('e2e-student-a');
    await expect(page.locator('.ranking-management-section').last()).toContainText('95');

    const publicAfterPublish = await apiRequest(request, 'GET', `/api/v1/public/rankings/${created.id}`);
    expect(publicAfterPublish.status()).toBe(404);

    const adminRead = await apiRequest(request, 'GET', `/api/v1/school-admin/rankings/${created.id}`);
    expect(adminRead.status()).toBe(200);
    const adminRanking = await adminRead.json() as { entries: Array<{ studentDisplayName: string; scoreDisplayValue: string }> };
    expect(adminRanking.entries).toHaveLength(1);
    expect(adminRanking.entries[0].studentDisplayName).toBe('e2e-student-a');
    expect(adminRanking.entries[0].scoreDisplayValue).toBe('95');

    const studentContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
    const otherSchoolContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
    try {
      expect((await loginApi(studentContext, actors.studentA)).status()).toBe(200);
      const studentRead = await apiRequest(studentContext, 'GET', `/api/v1/student/rankings/${created.id}`);
      expect(studentRead.status()).toBe(200);
      const studentRanking = await studentRead.json() as { entries: Array<{ studentDisplayName: string; scoreDisplayValue: string }> };
      expect(studentRanking.entries).toHaveLength(1);
      expect(studentRanking.entries[0].studentDisplayName).toBe('e2e-student-a');
      expect(studentRanking.entries[0].scoreDisplayValue).toBe('95');

      expect((await loginApi(otherSchoolContext, actors.studentOtherSchool)).status()).toBe(200);
      const crossSchoolRead = await apiRequest(otherSchoolContext, 'GET', `/api/v1/student/rankings/${created.id}`);
      expect([403, 404]).toContain(crossSchoolRead.status());
    } finally {
      await studentContext.dispose();
      await otherSchoolContext.dispose();
    }

    await page.reload();
    await selectDefinition(page, name);
    await expect(detail).toContainText('L2 SCOPE');
    await expect(detail.locator('.ranking-management-summary').first()).toContainText('Current Published');
    await expect(page.locator('.ranking-management-section').last()).toContainText('PUBLISHED');
    await expect(page.locator('.ranking-management-section').last()).toContainText('e2e-student-a');
    await expect(page.locator('.ranking-management-section').last()).toContainText('95');

    await detail.getByRole('button', { name: 'Disable' }).click();
    await expect(page.getByText('RankingDefinition disabled.')).toBeVisible();
    await expect(detail.getByRole('button', { name: 'Enable' })).toBeVisible();
    await expect(page.getByText('Disabled definitions cannot generate or publish rankings.')).toBeVisible();
    await expect(detail.getByRole('button', { name: 'Generate' })).toBeDisabled();
  });

  test('student route and backend management access are denied', async ({ page, request }) => {
    expect((await loginApi(request, actors.studentA)).status()).toBe(200);
    const backend = await apiRequest(request, 'GET', '/api/v1/school-admin/ranking-definitions');
    expect(backend.status()).toBe(403);

    await loginUi(page, actors.studentA);
    await page.goto('/school-admin/ranking-management');
    await expect(page).not.toHaveURL(/\/school-admin\/ranking-management$/);
    await expect(page).toHaveURL(/\/student(?:$|\/)/);
  });
});
