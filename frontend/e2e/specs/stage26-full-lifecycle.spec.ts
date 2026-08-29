import { expect, request as playwrightRequest, test, type Page } from '@playwright/test';
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

function savedScores(page: Page) {
  return page.locator('table.score-table').last();
}

function scoreRow(page: Page, text: string) {
  return savedScores(page).locator('tbody tr').filter({ hasText: text }).first();
}

async function saveDraft(page: Page, value: string): Promise<{ scoreAttemptId: string }> {
  const form = page.locator('.score-editor-form');
  const submit = form.locator('button[type="submit"]');
  const input = form.locator('input[type="number"]');
  await input.fill(value);
  await expect(input).toHaveValue(value);
  const responsePromise = page.waitForResponse(response =>
    /\/api\/v1\/(?:school-admin\/)?(?:activity-projects|score-attempts)\//.test(response.url())
    && ['POST', 'PATCH'].includes(response.request().method())
  );
  await submit.click();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  await expect(submit).toBeEnabled();
  return response.json() as Promise<{ scoreAttemptId: string }>;
}

async function confirmLifecycle(page: Page) {
  await page.locator('.score-lifecycle-modal .project-modal-actions button').last().click();
}

test.describe('Stage26 full lifecycle browser evidence', () => {
  test.describe.configure({ mode: 'serial' });

  test('admin navigates, creates, edits, submits, approves, and reloads authoritative state', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await expect(page).toHaveURL(/\/school-admin$/);

    await page.goto('/school-admin/activities');
    const activityRow = page.locator('table.activity-admin-table tbody tr')
      .filter({ hasText: 'ACTIVITY_LIFECYCLE' }).first();
    await expect(activityRow).toBeVisible();
    await activityRow.getByRole('link').first().click();

    await page.locator(`a[href="/school-admin/activities/${ids.activityLifecycle}/participants"]`).click();
    await expect(page.locator('.participant-table tbody tr')
      .filter({ hasText: 'E2E-STUDENT-A' }).first()).toBeVisible();

    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/scores`);
    const candidateRow = page.locator('table.score-table').first().locator('tbody tr')
      .filter({ hasText: 'E2E-STUDENT-A' }).filter({ hasText: 'E2E Lifecycle Project' }).first();
    await expect(candidateRow).toBeVisible();
    await candidateRow.locator('button').first().click();
    await saveDraft(page, '9');

    let row = scoreRow(page, 'E2E Lifecycle Project');
    await expect(row.locator('[data-status="DRAFT"]')).toBeVisible();
    await expect(row).toContainText('9');
    const editLoad = page.waitForResponse(response =>
      response.request().method() === 'GET'
      && /\/api\/v1\/school-admin\/score-attempts\/[^/]+$/.test(response.url())
    );
    await row.locator('button').nth(1).click();
    await editLoad;
    await expect(page.locator('.score-editor-form input[type="number"]')).toHaveValue('9');
    await saveDraft(page, '11');
    await page.reload();
    row = scoreRow(page, 'E2E Lifecycle Project');
    await expect(row).toContainText('11');

    await row.locator('button').nth(2).click();
    await expect(page.locator('.score-lifecycle-modal')).toBeVisible();
    await confirmLifecycle(page);
    await expect(page.locator('[data-status="PENDING_REVIEW"]')).toBeVisible();
    await page.reload();
    row = scoreRow(page, 'E2E Lifecycle Project');
    await expect(row.locator('[data-status="PENDING_REVIEW"]')).toBeVisible();

    await row.locator('button').nth(1).click();
    await confirmLifecycle(page);
    await expect(page.locator('[data-status="APPROVED"]')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('最终成绩');
    await expect(page.locator('body')).not.toContainText('排名成绩');
    await page.reload();
    await expect(scoreRow(page, 'E2E Lifecycle Project')
      .locator('[data-status="APPROVED"]')).toBeVisible();
  });

  test('admin can reject, return, resubmit, approve, and inspect review history', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/scores`);

    const candidateRow = page.locator('table.score-table').first().locator('tbody tr')
      .filter({ hasText: 'E2E-STUDENT-A' }).filter({ hasText: 'E2E Lifecycle Project' }).first();
    await candidateRow.locator('button').first().click();
    await saveDraft(page, '13');
    let row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '#2' }).first();

    await row.locator('button').nth(2).click();
    await confirmLifecycle(page);
    row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '#2' }).first();
    await expect(row.locator('[data-status="PENDING_REVIEW"]')).toBeVisible();

    await row.locator('button').nth(2).click();
    await page.locator('.score-reject-field textarea').fill('Needs evidence');
    await confirmLifecycle(page);
    await expect(page.locator('[data-status="REJECTED"]')).toBeVisible();

    row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '#2' }).first();
    await row.locator('button').first().click();
    const history = page.locator('.score-review-history-modal');
    await expect(history.locator('.score-review-entry')).toHaveCount(1);
    await expect(history.locator('[data-status="REJECTED"]')).toBeVisible();
    await expect(history).toContainText('e2e-admin-a');
    await expect(history).toContainText('Needs evidence');
    const rejectedTime = await history.locator('time').getAttribute('datetime');
    expect(rejectedTime).not.toBeNull();
    expect(Number.isNaN(Date.parse(rejectedTime!))).toBeFalsy();
    await history.locator('.project-modal-actions button').click();

    await row.locator('button').nth(1).click();
    await confirmLifecycle(page);
    await expect(page.locator('[data-status="DRAFT"]')).toBeVisible();
    row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '#2' }).first();
    await row.locator('button').nth(2).click();
    await confirmLifecycle(page);
    row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '#2' }).first();
    await row.locator('button').nth(1).click();
    await confirmLifecycle(page);
    await expect(page.locator('[data-status="APPROVED"]')).toBeVisible();

    row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '#2' }).first();
    await row.locator('button').first().click();
    await expect(page.locator('.score-review-history-modal .score-review-entry')).toHaveCount(2);
    const entries = page.locator('.score-review-history-modal .score-review-entry');
    await expect(entries.nth(0)).toContainText('Needs evidence');
    await expect(entries.nth(1)).toContainText('e2e-admin-a');
    await expect(entries.nth(0).locator('[data-status="REJECTED"]')).toBeVisible();
    await expect(entries.nth(1).locator('[data-status="APPROVED"]')).toBeVisible();
    const approvedTime = await entries.nth(1).locator('time').getAttribute('datetime');
    expect(approvedTime).not.toBeNull();
    expect(Number.isNaN(Date.parse(approvedTime!))).toBeFalsy();
    await page.locator('.score-review-history-modal .project-modal-actions button').click();
  });

  test('empty history and route changes clear stale dialog state', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/scores`);
    const candidateRow = page.locator('table.score-table').first().locator('tbody tr')
      .filter({ hasText: 'E2E Empty History Project' }).first();
    await candidateRow.locator('button').first().click();
    await saveDraft(page, '4');

    const row = scoreRow(page, 'E2E Empty History Project');
    await row.locator('button').first().click();
    const history = page.locator('.score-review-history-modal');
    await expect(history.locator('.score-review-entry')).toHaveCount(0);
    await expect(history.locator('.score-review-history-state')).toBeVisible();
    await history.locator('.project-modal-actions button').click();

    await row.locator('button').first().click();
    await expect(page.locator('.score-review-history-modal')).toBeVisible();
    await page.goto(`/school-admin/activities/${ids.activityBest}/scores`);
    await expect(page.locator('.score-review-history-modal')).toHaveCount(0);
    await expect(page.locator('.score-lifecycle-modal')).toHaveCount(0);
  });

  test('stale submit reports a real conflict and refreshes the authoritative state', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/scores`);

    const candidateRow = page.locator('table.score-table').first().locator('tbody tr')
      .filter({ hasText: 'E2E-STUDENT-A' }).filter({ hasText: 'E2E Lifecycle Project' }).first();
    await candidateRow.locator('button').first().click();
    const created = await saveDraft(page, '17');
    const row = scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '17' }).first();
    await expect(row.locator('[data-status="DRAFT"]')).toBeVisible();
    await row.locator('button').nth(2).click();
    await expect(page.locator('.score-lifecycle-modal')).toBeVisible();

    const concurrentAdmin = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
    try {
      expect((await loginApi(concurrentAdmin, actors.schoolAdminA)).status()).toBe(200);
      expect((await apiRequest(concurrentAdmin, 'POST',
        `/api/v1/school-admin/score-attempts/${created.scoreAttemptId}/submit`)).status()).toBe(200);
    } finally {
      await concurrentAdmin.dispose();
    }

    const staleSubmit = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/score-attempts/${created.scoreAttemptId}/submit`)
    );
    await confirmLifecycle(page);
    expect((await staleSubmit).status()).toBe(409);
    await expect(scoreRow(page, 'E2E Lifecycle Project').filter({ hasText: '17' }).first()
      .locator('[data-status="PENDING_REVIEW"]')).toBeVisible();
  });

  test('student sees only current effective scores and mobile review controls remain reachable', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.studentA);
    await page.goto('/student/scores');
    const best = page.locator('table.student-score-table tbody tr')
      .filter({ hasText: 'E2E Best Project' }).first();
    const last = page.locator('table.student-score-table tbody tr')
      .filter({ hasText: 'E2E Last Project' }).first();
    await expect(best).toContainText('20 points');
    await expect(best).not.toContainText('10 points');
    await expect(last).toContainText('1 points');
    await expect(last).not.toContainText('100 points');
    await best.getByRole('link').click();
    await expect(page).toHaveURL(/\/student\/scores\//);
    await expect(page.locator('body')).toContainText('E2E Best Project');

    await page.setViewportSize({ width: 390, height: 844 });
    await page.context().clearCookies();
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/scores`);
    await expect(page).toHaveURL(/\/login(?:$|\?)/);
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/scores`);
    const candidate = page.locator('table.score-table').first().locator('tbody tr')
      .filter({ hasText: 'E2E Empty History Project' }).first();
    const create = candidate.locator('button').first();
    await expect(create).toBeVisible();
    const tableWrap = create.locator('xpath=ancestor::div[contains(@class, "project-admin-table-wrap")]');
    await tableWrap.evaluate(element => { element.scrollLeft = element.scrollWidth; });
    const box = await create.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(390);

    await create.click();
    await saveDraft(page, '8');
    let mobileRow = scoreRow(page, 'E2E Empty History Project').filter({ hasText: '8' }).first();
    await mobileRow.locator('button.primary-button').click();
    const lifecycle = page.getByRole('dialog');
    await expect(lifecycle).toBeVisible();
    await expect(lifecycle.getByRole('button').last()).toBeVisible();
    await lifecycle.getByRole('button').last().click();
    mobileRow = scoreRow(page, 'E2E Empty History Project').filter({ hasText: '8' }).first();
    await expect(mobileRow.locator('[data-status="PENDING_REVIEW"]')).toBeVisible();

    await mobileRow.locator('button.danger-outline').click();
    await expect(lifecycle).toBeVisible();
    const rejectInput = lifecycle.locator('textarea');
    await expect(rejectInput).toBeVisible();
    await rejectInput.fill('Mobile review reason');
    await expect(lifecycle.getByRole('button').last()).toBeVisible();
    await lifecycle.getByRole('button').last().click();
    await expect(lifecycle).toHaveCount(0);

    mobileRow = scoreRow(page, 'E2E Empty History Project').filter({ hasText: '8' }).first();
    await expect(mobileRow.locator('[data-status="REJECTED"]')).toBeVisible();
    await mobileRow.locator('button.secondary-button').first().click();
    const history = page.getByRole('dialog');
    await expect(history).toBeVisible();
    await expect(history).toContainText('Mobile review reason');
    await history.getByRole('button').click();
    await expect(history).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBeTruthy();
  });
});
