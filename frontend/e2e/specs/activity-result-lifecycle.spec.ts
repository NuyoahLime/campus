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
    await page.goto('/school-admin/activities');
    const activityRow = page.getByRole('row').filter({ hasText: 'ACTIVITY_LIFECYCLE' });
    await expect(activityRow).toBeVisible();
    await activityRow.getByRole('link', { name: '查看详情' }).click();
    await page.getByRole('link', { name: '活动成果' }).click();
    await expect(page).toHaveURL(new RegExp(`/school-admin/activities/${ids.activityLifecycle}/result$`));
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

    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/result`);
    await page.getByRole('button', { name: '编辑成果' }).click();
    await page.getByLabel('标题').fill('E2E Activity Result Replacement');
    await page.getByLabel('成果摘要').fill('Replacement after governance reset.');
    await page.getByRole('button', { name: '保存成果' }).click();
    await expect(page.getByText('成果草稿已保存。')).toBeVisible();
    await page.getByRole('button', { name: '内部发布' }).click();
    await page.getByRole('button', { name: '提交平台审核' }).click();
    await expect(page.locator('.activity-status').filter({ hasText: '平台审核中' })).toBeVisible();

    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/activity-results/reviews');
    const replacementReviewRow = page.getByRole('row').filter({ hasText: 'E2E Activity Result Replacement' });
    await expect(replacementReviewRow).toBeVisible();
    await replacementReviewRow.getByRole('link', { name: '查看详情' }).click();
    await page.getByRole('button', { name: '通过' }).click();
    await expect(page).toHaveURL(/\/super-admin\/activity-results\/reviews$/);

    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/result`);
    await page.getByRole('button', { name: '正式公开' }).click();
    await expect(page.locator('.activity-status').filter({ hasText: '已公开' })).toBeVisible();

    await page.context().clearCookies();
    await page.goto(`/activities/${ids.activityLifecycle}`);
    await expect(page.getByText('E2E Activity Result Replacement')).toBeVisible();
    await expect(page.getByText('E2E Activity Result', { exact: true })).toHaveCount(0);
  });

  test('student reaches assigned activity detail and reads the student result endpoint', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.studentA);
    await page.goto('/student/activities');
    const activityLink = page.getByRole('link', { name: /ACTIVITY_LIFECYCLE/ });
    await expect(activityLink).toBeVisible();
    const studentResultResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/v1/student/activities/${ids.activityLifecycle}/result`
      && response.request().method() === 'GET'
    );
    await activityLink.click();
    const response = await studentResultResponse;
    expect(response.ok()).toBeTruthy();
    await expect(page.getByRole('heading', { name: 'E2E Activity Result Replacement' })).toBeVisible();
    await expect(page.getByText('Replacement after governance reset.')).toBeVisible();
    await expect(page.getByText(/V\d+ · 校内成果/)).toBeVisible();
  });

  test('school admin edits exact public presentation and projections reload it', async ({ page }) => {
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/result`);
    await expect(page.locator('.result-format-section')).toBeVisible();

    const editorResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/v1/activities/${ids.activityLifecycle}/result`
      && response.request().method() === 'GET'
    );
    await page.reload();
    const editor = await (await editorResponse).json() as { resultId: string; currentPublicVersionId: string };
    const surface = page.locator('.result-format-surface');
    await expect(surface).toContainText('Replacement after governance reset.');
    await surface.evaluate(element => {
      const text = element.querySelector('span')?.firstChild;
      if (!text) throw new Error('format surface text node missing');
      const range = document.createRange();
      range.setStart(text, 0);
      range.setEnd(text, Math.min(11, text.textContent?.length ?? 0));
      const selection = window.getSelection();
      selection?.removeAllRanges();
      selection?.addRange(range);
    });
    await page.getByRole('button', { name: /加粗/ }).click();
    await page.getByLabel('调整原因').fill('Emphasize the published result lead.');
    await page.getByRole('button', { name: '保存展示样式' }).click();
    await expect(page.getByText('展示样式已保存，页面已重新读取服务端状态。')).toBeVisible();
    await expect(page.locator('.result-format-history-item')).toContainText('Emphasize the published result lead.');
    await expect(page.locator('.result-format-bold')).toContainText('Replacement');

    await page.context().clearCookies();
    await page.goto(`/activities/${ids.activityLifecycle}`);
    await expect(page.locator('.result-presentation-bold')).toContainText('Replacement');
    await expect(page.getByText('Replacement after governance reset.')).toBeVisible();

    await loginUi(page, actors.studentA);
    await page.goto(`/student/activities/${ids.activityLifecycle}`);
    await expect(page.locator('.result-presentation-bold')).toContainText('Replacement');
    const denied = await page.evaluate(async ({ resultId, versionId }) => {
      const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'include' });
      const csrf = await csrfResponse.json() as { headerName: string; token: string };
      const response = await fetch(`/api/v1/activity-results/${resultId}/versions/${versionId}/format-edits`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ reason: 'student must be denied', presentation: { paragraphs: [], emphasisRanges: [] } })
      });
      return response.status;
    }, editor);
    expect(denied).toBe(403);
  });
});
