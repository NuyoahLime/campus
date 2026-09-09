import { expect, request as playwrightRequest, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
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

async function execDb(sql: string): Promise<void> {
  const runtime = JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime.json'),
    'utf8'
  )) as { dbContainer: string };
  execFileSync('docker', [
    'exec', '-i', runtime.dbContainer, 'psql',
    '-U', 'postgres', '-d', 'campus_e2e', '-c', sql
  ], { encoding: 'utf8' });
}

async function createApprovedAuthorization(
  request: Parameters<typeof apiRequest>[0],
  projectId: string,
  ruleVersionId: string
): Promise<string> {
  const created = await apiRequest(request, 'POST', '/api/v1/school-admin/l3-authorizations', {
    projectId,
    ruleVersionId,
    dataScope: {},
    allowSchoolName: false,
    allowStudentName: true
  });
  expect(created.status()).toBe(201);
  const id = (await created.json() as { id: string }).id;
  expect((await apiRequest(request, 'POST', `/api/v1/school-admin/l3-authorizations/${id}/submit`)).status())
    .toBe(200);
  return id;
}

async function createApprovedScore(
  request: Parameters<typeof apiRequest>[0],
  activityProjectId: string,
  studentId: string
): Promise<string> {
  const created = await apiRequest(request, 'POST',
    `/api/v1/school-admin/activity-projects/${activityProjectId}/score-attempts`,
    { studentId, integerValue: 9876, scoreBusinessTime: '2026-08-29T12:00:00Z' });
  expect(created.status()).toBe(201);
  const id = (await created.json() as { scoreAttemptId: string }).scoreAttemptId;
  expect((await apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${id}/submit`)).status())
    .toBe(200);
  expect((await apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${id}/approve`)).status())
    .toBe(200);
  return id;
}

test.describe('L3 ranking management frontend', () => {
  test.describe.configure({ mode: 'serial' });

  test('super admin creates, generates, publishes, reloads, disables, and enables an L3 ranking', async ({
    page,
    request
  }) => {
    const ids = await fixture();
    const superAdminContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });

    let authorizationId = '';
    let scoreAttemptId = '';
    let definitionId = '';

    try {
      expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);
      const projectResponse = await apiRequest(
        request,
        'GET',
        `/api/v1/challenge-projects/${ids.l2BestProject}`
      );
      expect(projectResponse.status()).toBe(200);
      const project = await projectResponse.json() as { currentRuleVersionId: string };
      authorizationId = await createApprovedAuthorization(request, ids.l2BestProject, project.currentRuleVersionId);
      scoreAttemptId = await createApprovedScore(request, ids.l2BestActivityProject, ids.studentA);

      expect((await loginApi(superAdminContext, actors.superAdmin)).status()).toBe(200);
      expect((await apiRequest(
        superAdminContext,
        'POST',
        `/api/v1/super-admin/l3-authorizations/${authorizationId}/approve`,
        { comment: 'approved for L3 management E2E' }
      )).status()).toBe(200);

      await loginUi(page, actors.superAdmin);
      await page.goto('/super-admin/ranking-management');
      await expect(page).toHaveURL(/\/super-admin\/ranking-management$/);

      const name = `E2E L3 Management ${Date.now()}`;
      await page.getByTestId('l3-ranking-name').fill(name);
      await page.getByTestId('l3-ranking-project').selectOption(ids.l2BestProject);
      await expect(page.getByTestId('l3-ranking-rule-version')).toBeEnabled();
      await page.getByTestId('l3-ranking-rule-version').selectOption(project.currentRuleVersionId);

      const createResponse = page.waitForResponse(response =>
        response.request().method() === 'POST'
        && response.url().endsWith('/api/v1/super-admin/ranking-definitions')
      );
      await page.getByTestId('l3-ranking-create').click();
      expect((await createResponse).status()).toBe(201);
      await expect(page.getByRole('heading', { name })).toBeVisible();
      definitionId = (await (await apiRequest(
        superAdminContext,
        'GET',
        '/api/v1/super-admin/ranking-definitions'
      )).json() as { items: Array<{ id: string; name: string }> }).items.find(item => item.name === name)!.id;

      const detail = page.locator('.ranking-management-detail');
      await expect(detail).toContainText('Platform');
      await expect(detail).toContainText('E2E L2 Best Project');
      await expect(detail).toContainText('RuleVersion');

      const generateResponse = page.waitForResponse(response =>
        response.request().method() === 'POST'
        && response.url().endsWith(`/api/v1/super-admin/ranking-definitions/${definitionId}/generate`)
      );
      await page.getByTestId('l3-ranking-generate').click();
      expect((await generateResponse).status()).toBe(200);
      await expect(detail).toContainText('GENERATED');
      await expect(detail.locator('.ranking-version-box').first()).toContainText('1 entries');
      await expect(detail.locator('.ranking-version-box').first()).toContainText('9876');
      await expect(detail.locator('.ranking-version-box').first()).not.toContainText(ids.studentA);

      expect((await apiRequest(request, 'GET', `/api/v1/public/rankings/${definitionId}`)).status()).toBe(404);

      await page.getByTestId('l3-ranking-publish').click();
      const dialog = page.getByRole('dialog', { name: 'Confirm publication' });
      await expect(dialog).toBeVisible();
      const publishResponse = page.waitForResponse(response =>
        response.request().method() === 'POST'
        && response.url().includes(`/api/v1/super-admin/ranking-definitions/${definitionId}/versions/`)
        && response.url().endsWith('/publish')
      );
      await dialog.getByTestId('l3-ranking-publish-confirm').click();
      expect((await publishResponse).status()).toBe(200);
      await expect(page.getByTestId('l3-ranking-action-message')).toContainText('Published V');
      await expect(detail).toContainText('Current Published');
      await expect(detail).toContainText('PUBLISHED');

      const publicAfterPublish = await apiRequest(request, 'GET', `/api/v1/public/rankings/${definitionId}`);
      expect(publicAfterPublish.status()).toBe(200);
      const publicDetail = await publicAfterPublish.json() as {
        entries: Array<{ studentDisplayName: string; schoolName: string | null }>;
      };
      expect(publicDetail.entries).toHaveLength(1);
      expect(publicDetail.entries[0].schoolName).toBeNull();
      expect(publicDetail.entries[0].studentDisplayName).not.toContain(ids.studentA);

      await page.reload();
      await expect(page.getByRole('heading', { name })).toBeVisible();
      await expect(page.locator('.ranking-management-detail')).toContainText('PUBLISHED');

      await page.getByTestId('l3-ranking-toggle').click();
      await expect(page.getByTestId('l3-ranking-action-message')).toContainText('disabled');
      await expect(page.getByTestId('l3-ranking-generate')).toBeDisabled();
      expect((await apiRequest(request, 'GET', `/api/v1/public/rankings/${definitionId}`)).status()).toBe(404);

      await page.getByTestId('l3-ranking-toggle').click();
      await expect(page.getByTestId('l3-ranking-action-message')).toContainText('enabled');
      expect((await apiRequest(request, 'GET', `/api/v1/public/rankings/${definitionId}`)).status()).toBe(200);
    } finally {
      if (definitionId) {
        await execDb(`UPDATE ranking_definitions SET current_version_id = NULL WHERE id = '${definitionId}';`);
        await execDb(`
          DELETE FROM ranking_entry_score_sources
          WHERE entry_id IN (
            SELECT id FROM ranking_entries
            WHERE version_id IN (SELECT id FROM ranking_versions WHERE definition_id = '${definitionId}')
          );
          DELETE FROM ranking_entries
          WHERE version_id IN (SELECT id FROM ranking_versions WHERE definition_id = '${definitionId}');
          DELETE FROM ranking_versions WHERE definition_id = '${definitionId}';
          DELETE FROM ranking_definitions WHERE id = '${definitionId}';
        `);
      }
      if (authorizationId) {
        await execDb(`DELETE FROM l3_authorizations WHERE id = '${authorizationId}';`);
      }
      if (scoreAttemptId) {
        await execDb(`DELETE FROM score_review_records WHERE score_attempt_id = '${scoreAttemptId}';`);
        await execDb(`DELETE FROM score_attempts WHERE id = '${scoreAttemptId}';`);
      }
      await superAdminContext.dispose();
    }
  });

  test('student and school admin cannot access L3 ranking management', async ({ page, request }) => {
    expect((await loginApi(request, actors.studentA)).status()).toBe(200);
    expect((await apiRequest(request, 'GET', '/api/v1/super-admin/ranking-definitions')).status()).toBe(403);
    await loginUi(page, actors.studentA);
    await page.goto('/super-admin/ranking-management');
    await expect(page).not.toHaveURL(/\/super-admin\/ranking-management$/);

    expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);
    expect((await apiRequest(request, 'GET', '/api/v1/super-admin/ranking-definitions')).status()).toBe(403);
  });
});
