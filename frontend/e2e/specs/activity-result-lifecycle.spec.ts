import { expect, test } from '@playwright/test';
import { actors, loginUi } from '../support/auth';
import type { FixtureState } from '../support/fixture';
import { promises as fs } from 'node:fs';
import path from 'node:path';

async function fixture(): Promise<FixtureState> {
  return JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime/fixture-state.json'),
    'utf8'
  )) as FixtureState;
}

test.describe('ActivityResult product lifecycle', () => {
  test.describe.configure({ mode: 'serial' });

  test('school admin creates, publishes internally, and submits an exact result candidate', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/result`);
    await expect(page.getByText('该活动尚未创建活动成果')).toBeVisible();
    await page.getByRole('button', { name: '创建活动成果' }).click();
    await page.getByLabel('标题').fill('E2E Activity Result');
    await page.getByLabel('成果摘要').fill('A real persisted activity result.');
    await page.getByRole('button', { name: '保存成果' }).click();
    await expect(page.getByText('成果草稿已保存。')).toBeVisible();
    await page.getByRole('button', { name: '内部发布' }).click();
    await expect(page.locator('.activity-status').filter({ hasText: '已内部发布' })).toBeVisible();
    await page.getByRole('button', { name: '提交平台审核' }).click();
    await expect(page.locator('.activity-status').filter({ hasText: '平台审核中' })).toBeVisible();
  });

  test('super admin rejects, school admin corrects and resubmits, then approval makes it public', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/activity-results/reviews');
    await expect(page.getByText('E2E Activity Result')).toBeVisible();
    await page.getByRole('link', { name: '查看详情' }).first().click();
    await page.getByLabel('驳回原因').fill('Please add a clearer result summary.');
    await page.getByRole('button', { name: '驳回' }).click();
    await expect(page).toHaveURL(/\/super-admin\/activity-results\/reviews$/);

    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/result`);
    await expect(page.locator('.registration-review-notice').getByText('Please add a clearer result summary.')).toBeVisible();
    await page.getByRole('button', { name: '编辑成果' }).click();
    await page.getByLabel('成果摘要').fill('A corrected and persisted activity result.');
    await page.getByRole('button', { name: '保存成果' }).click();
    await page.getByRole('button', { name: '内部发布' }).click();
    await page.getByRole('button', { name: '提交平台审核' }).click();
    await expect(page.locator('.activity-status').filter({ hasText: '平台审核中' })).toBeVisible();

    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/activity-results/reviews');
    await page.getByRole('link', { name: '查看详情' }).first().click();
    await page.getByRole('button', { name: '通过' }).click();
    await expect(page).toHaveURL(/\/super-admin\/activity-results\/reviews$/);

    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/result`);
    await page.getByRole('button', { name: '正式公开' }).click();
    await expect(page.locator('.activity-status').filter({ hasText: '已公开' })).toBeVisible();
  });

  test('anonymous visibility and governance takedown/reset use authoritative discovery', async ({ page }) => {
    const ids = await fixture();
    await page.context().clearCookies();
    await page.goto(`/activities/${ids.activityLifecycle}`);
    await expect(page.getByText('E2E Activity Result')).toBeVisible();

    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/activity-results');
    const governanceRow = page.getByRole('row').filter({ hasText: 'ACTIVITY_LIFECYCLE' });
    await expect(governanceRow).toBeVisible();
    await governanceRow.getByRole('link', { name: '治理详情' }).click();
    const governanceUrl = page.url();
    await page.getByLabel('处理原因').fill('Governance verification.');
    await page.getByRole('button', { name: '下架成果' }).click();
    await expect(page.locator('.registration-review-panel').getByText('平台已下架')).toBeVisible();

    await page.context().clearCookies();
    await page.goto(`/activities/${ids.activityLifecycle}`);
    await expect(page.getByText('E2E Activity Result')).toHaveCount(0);

    await loginUi(page, actors.superAdmin);
    await page.goto(governanceUrl);
    await expect(page.getByRole('heading', { name: '成果治理详情' })).toBeVisible();
    await page.getByLabel('处理原因').fill('Restore public visibility after governance verification.');
    await page.getByRole('button', { name: '重置下架' }).click();
    await expect(page.locator('.registration-review-panel').getByText('未提交审核')).toBeVisible();

    await page.context().clearCookies();
    await page.goto(`/activities/${ids.activityLifecycle}`);
    await expect(page.getByText('E2E Activity Result')).toHaveCount(0);
  });
});
