import { expect, test } from '@playwright/test';
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

async function queryDb(sql: string): Promise<string> {
  const runtime = JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime.json'),
    'utf8'
  )) as { dbContainer: string };
  return execFileSync('docker', [
    'exec', '-i', runtime.dbContainer, 'psql',
    '-At', '-U', 'postgres', '-d', 'campus_e2e', '-c', sql
  ], { encoding: 'utf8' }).trim();
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

async function createSubmittedAndApproved(
  request: Parameters<typeof apiRequest>[0],
  activityProjectId: string,
  studentId: string,
  integerValue: number,
  scoreBusinessTime: string
): Promise<{ scoreAttemptId: string }> {
  const created = await apiRequest(request, 'POST',
    `/api/v1/school-admin/activity-projects/${activityProjectId}/score-attempts`,
    { studentId, integerValue, scoreBusinessTime });
  expect(created.status()).toBe(201);
  const draft = await created.json() as { scoreAttemptId: string };
  expect((await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${draft.scoreAttemptId}/submit`)).status()).toBe(200);
  expect((await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${draft.scoreAttemptId}/approve`)).status()).toBe(200);
  return draft;
}

test.describe.configure({ mode: 'serial' });

test('L3 generation API persists the selected snapshot and opaque public identity', async ({ page, request }) => {
  const ids = await fixture();

  let authorizationId = '';
  let definitionId = '';
  let rankingVersionId = '';
  let scoreAttemptId = '';
  try {
    expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);
    const submitted = await createSubmittedAndApproved(
      request,
      ids.l2BestActivityProject,
      ids.studentA,
      95,
      '2026-08-28T12:00:00Z'
    );
    scoreAttemptId = submitted.scoreAttemptId;

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/l3-authorizations');
    await expect(page).toHaveURL(/\/school-admin\/l3-authorizations$/);
    await page.getByTestId('l3-project-select').selectOption({ label: 'E2E L2 Best Project' });
    await page.getByTestId('l3-grades').fill('2026');
    await page.getByTestId('l3-classes').fill('E2E-A');
    await page.getByTestId('l3-create-allow-school-name').uncheck();
    await page.getByTestId('l3-create-allow-student-name').check();
    const createResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith('/api/v1/school-admin/l3-authorizations')
    );
    await page.getByTestId('l3-create').click();
    const created = await createResponse;
    expect(created.status()).toBe(201);
    authorizationId = (await created.json() as { id: string }).id;

    const submitResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/school-admin/l3-authorizations/${authorizationId}/submit`)
    );
    await page.getByTestId('l3-submit').click();
    expect((await submitResponse).status()).toBe(200);

    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/l3-authorizations');
    await expect(page.getByTestId('l3-review-selected-status')).toHaveText('PENDING_REVIEW');
    await expect(page.locator('[data-testid="l3-review-detail"]')).toContainText(authorizationId);
    const approveResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/super-admin/l3-authorizations/${authorizationId}/approve`)
    );
    await page.getByTestId('l3-approve-comment').fill('approved for L3 generation E2E');
    await page.getByTestId('l3-approve').click();
    expect((await approveResponse).status()).toBe(200);

    expect((await loginApi(request, actors.superAdmin)).status()).toBe(200);
    const projectDetail = await apiRequest(request, 'GET', `/api/v1/challenge-projects/${ids.l2BestProject}`);
    expect(projectDetail.status()).toBe(200);
    const project = await projectDetail.json() as { currentRuleVersionId: string };
    const definitionResponse = await apiRequest(request, 'POST', '/api/v1/super-admin/ranking-definitions', {
      name: `L3 Generation E2E ${Date.now()}`,
      projectId: ids.l2BestProject,
      ruleVersionId: project.currentRuleVersionId
    });
    expect(definitionResponse.status()).toBe(201);
    definitionId = (await definitionResponse.json() as { id: string }).id;

    const generateResponse = await apiRequest(request, 'POST',
      `/api/v1/super-admin/ranking-definitions/${definitionId}/generate`);
    expect(generateResponse.status()).toBe(200);
    const generation = await generateResponse.json() as {
      rankingVersionId: string;
      versionNumber: number;
      entryCount: number;
      status: string;
    };
    rankingVersionId = generation.rankingVersionId;
    expect(generation.status).toBe('GENERATED');
    expect(generation.entryCount).toBe(1);

    expect(await queryDb(`
      SELECT version_status || '|' || COALESCE(published_at::text, 'NULL')
      FROM ranking_versions
      WHERE id = '${generation.rankingVersionId}'
    `)).toBe('GENERATED|NULL');
    expect(await queryDb(`
      SELECT COUNT(*)
      FROM ranking_entries
      WHERE version_id = '${generation.rankingVersionId}'
    `)).toBe('1');
    const generatedEntry = await queryDb(`
      SELECT student_display_name || '|' || COALESCE(school_name, 'NULL') || '|' || score_display_value
      FROM ranking_entries
      WHERE version_id = '${generation.rankingVersionId}'
      ORDER BY rank_position ASC, student_id ASC
      LIMIT 1
    `);
    expect(generatedEntry).toContain('|NULL|95');
    expect(generatedEntry.startsWith('\u9009\u624b-')).toBeTruthy();
    expect(generatedEntry).not.toContain(ids.studentA.replace(/-/g, ''));
    expect(await queryDb(`
      SELECT authorization_ids_snapshot::text
      FROM ranking_versions
      WHERE id = '${generation.rankingVersionId}'
    `)).toContain(authorizationId);
    expect(await queryDb(`
      SELECT COUNT(*)
      FROM ranking_versions
      WHERE definition_id = '${definitionId}' AND version_status = 'PUBLISHED'
    `)).toBe('0');
  } finally {
    if (rankingVersionId) {
      await execDb(`
        DELETE FROM ranking_entry_score_sources
        WHERE entry_id IN (
          SELECT id FROM ranking_entries WHERE version_id = '${rankingVersionId}'
        );
        DELETE FROM ranking_entries WHERE version_id = '${rankingVersionId}';
        DELETE FROM ranking_versions WHERE id = '${rankingVersionId}';
      `);
    }
    if (definitionId) {
      await execDb(`DELETE FROM ranking_definitions WHERE id = '${definitionId}';`);
    }
    if (authorizationId) {
      await execDb(`DELETE FROM l3_authorizations WHERE id = '${authorizationId}';`);
    }
    if (scoreAttemptId) {
      await execDb(`DELETE FROM score_review_records WHERE score_attempt_id = '${scoreAttemptId}';`);
      await execDb(`DELETE FROM score_attempts WHERE id = '${scoreAttemptId}';`);
    }
  }
});
