import { expect, test } from '@playwright/test';
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

test.describe('L1 ranking management frontend', () => {
  test.describe.configure({ mode: 'serial' });

  test('school admin creates, generates, previews, publishes, and refreshes a ranking', async ({ page, request }) => {
    await fixture();
    await loginUi(page, actors.schoolAdminB);
    await page.goto('/school-admin/ranking-management');
    await expect(page).toHaveURL(/\/school-admin\/ranking-management$/);
    await expect(page.getByRole('heading', { name: 'Ranking Management' })).toBeVisible();

    const name = `E2E L1 Management ${Date.now()}`;
    await page.getByLabel(/^Name$/).fill(name);
    const createForm = page.locator('.ranking-create-form');
    await createForm.locator('select').first().selectOption({ label: 'ACTIVITY_OTHER_SCHOOL' });
    await expect(createForm.locator('select').nth(1).locator('option', {
      hasText: 'E2E Other School Project / Rule V1'
    })).toBeAttached();
    await createForm.locator('select').nth(1).selectOption({ label: 'E2E Other School Project / Rule V1' });

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
    await expect(page.getByRole('heading', { name })).toBeVisible();
    await expect(page.locator('.ranking-version-box').first()).toContainText('GENERATED');
    await expect(page.locator('.ranking-version-box').first()).toContainText('e2e-student-other-school');

    await page.locator('.ranking-management-section').first().getByRole('button', { name: 'Publish' }).click();
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
    await expect(page.locator('.ranking-management-summary')).toContainText('Disabled');
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
