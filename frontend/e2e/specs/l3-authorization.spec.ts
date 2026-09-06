import { expect, test } from '@playwright/test';
import { actors, loginUi } from '../support/auth';

async function loginAs(page: Parameters<typeof loginUi>[0], username: string) {
  await page.context().clearCookies();
  await loginUi(page, username);
}

async function createDraft(page: Parameters<typeof loginUi>[0], grade: string, className: string) {
  await loginAs(page, actors.schoolAdminA);
  await page.goto('/school-admin/l3-authorizations');
  await expect(page).toHaveURL(/\/school-admin\/l3-authorizations$/);
  await page.getByTestId('l3-project-select').selectOption({ label: 'E2E L2 Best Project' });
  await page.getByTestId('l3-grades').fill(grade);
  await page.getByTestId('l3-classes').fill(className);
  await page.getByTestId('l3-create-allow-school-name').uncheck();
  await page.getByTestId('l3-create-allow-student-name').check();
  await expect(page.getByTestId('l3-create')).toBeEnabled();

  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST'
    && response.url().endsWith('/api/v1/school-admin/l3-authorizations')
  );
  await page.getByTestId('l3-create').click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const body = await response.json() as { id: string };
  await expect(page.getByTestId('l3-selected-status')).toHaveText('DRAFT');
  return body.id;
}

async function submitSelected(page: Parameters<typeof loginUi>[0], id: string) {
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST'
    && response.url().endsWith(`/api/v1/school-admin/l3-authorizations/${id}/submit`)
  );
  await page.getByTestId('l3-submit').click();
  expect((await responsePromise).status()).toBe(200);
  await expect(page.getByTestId('l3-selected-status')).toHaveText('PENDING_REVIEW');
}

async function openReview(page: Parameters<typeof loginUi>[0], id: string) {
  await loginAs(page, actors.superAdmin);
  await page.goto('/super-admin/l3-authorizations');
  await expect(page).toHaveURL(/\/super-admin\/l3-authorizations$/);
  await expect(page.getByTestId('l3-review-selected-status')).toHaveText('PENDING_REVIEW');
  await expect(page.locator('[data-testid="l3-review-detail"]')).toContainText(id);
}

test.describe('L3 data authorization frontend', () => {
  test.describe.configure({ mode: 'serial' });

  test('school admin submits and super admin approves an L3 authorization', async ({ page }) => {
    const id = await createDraft(page, `G-${Date.now()}`, 'E2E-A');
    await submitSelected(page, id);

    await openReview(page, id);
    await page.getByTestId('l3-approve-comment').fill('approved for E2E');
    const approveResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/super-admin/l3-authorizations/${id}/approve`)
    );
    await page.getByTestId('l3-approve').click();
    expect((await approveResponse).status()).toBe(200);
  await expect(page.getByTestId('l3-review-selected-status')).toHaveText('APPROVED');

    await loginAs(page, actors.schoolAdminA);
    await page.goto('/school-admin/l3-authorizations');
    await expect(page.locator('[data-testid="l3-school-detail"]')).toContainText('APPROVED');
  });

  test('school admin returns a rejected L3 authorization to draft, edits, resubmits, and gets approval', async ({ page }) => {
    const grade = `R-${Date.now()}`;
    const id = await createDraft(page, grade, 'E2E-B');
    await submitSelected(page, id);

    await openReview(page, id);
    await page.getByTestId('l3-reject-input').fill('narrow the class scope');
    const rejectResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/super-admin/l3-authorizations/${id}/reject`)
    );
    await page.getByTestId('l3-reject').click();
    expect((await rejectResponse).status()).toBe(200);
    await expect(page.getByTestId('l3-review-selected-status')).toHaveText('REJECTED');

    await loginAs(page, actors.schoolAdminA);
    await page.goto('/school-admin/l3-authorizations');
    await expect(page.getByTestId('l3-selected-status')).toHaveText('REJECTED');
    await expect(page.getByTestId('l3-edit-grades')).toHaveValue(grade);
    await expect(page.getByTestId('l3-edit-classes')).toHaveValue('E2E-B');
    await expect(page.getByTestId('l3-edit-allow-school-name')).not.toBeChecked();
    await expect(page.getByTestId('l3-edit-allow-student-name')).toBeChecked();
    await expect(page.getByTestId('l3-reject-reason')).toContainText('narrow the class scope');
    const returnResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/school-admin/l3-authorizations/${id}/return-to-draft`)
    );
    await page.getByTestId('l3-return').click();
    expect((await returnResponse).status()).toBe(200);
    await expect(page.getByTestId('l3-selected-status')).toHaveText('DRAFT');
    await expect(page.getByTestId('l3-edit-grades')).toHaveValue(grade);
    await expect(page.getByTestId('l3-edit-classes')).toHaveValue('E2E-B');
    await expect(page.getByTestId('l3-edit-allow-school-name')).not.toBeChecked();
    await expect(page.getByTestId('l3-edit-allow-student-name')).toBeChecked();

    await page.getByTestId('l3-edit-classes').fill('E2E-A');
    const editResponse = page.waitForResponse(response =>
      response.request().method() === 'PUT'
      && response.url().endsWith(`/api/v1/school-admin/l3-authorizations/${id}`)
    );
    await page.getByTestId('l3-edit').click();
    expect((await editResponse).status()).toBe(200);
    await expect(page.getByTestId('l3-edit-grades')).toHaveValue(grade);
    await expect(page.getByTestId('l3-edit-classes')).toHaveValue('E2E-A');
    await expect(page.getByTestId('l3-edit-allow-school-name')).not.toBeChecked();
    await expect(page.getByTestId('l3-edit-allow-student-name')).toBeChecked();
    await submitSelected(page, id);

    await openReview(page, id);
    const approveResponse = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && response.url().endsWith(`/api/v1/super-admin/l3-authorizations/${id}/approve`)
    );
    await page.getByTestId('l3-approve').click();
    expect((await approveResponse).status()).toBe(200);
    await expect(page.getByTestId('l3-review-selected-status')).toHaveText('APPROVED');
  });

  test('management auxiliary project load failure does not clear existing authorization state', async ({ page }) => {
    const id = await createDraft(page, `A-${Date.now()}`, 'E2E-C');
    await page.route('**/api/v1/challenge-projects**', (route) => route.abort());
    await loginAs(page, actors.schoolAdminA);
    await page.goto('/school-admin/l3-authorizations');
    await expect(page.getByTestId('l3-project-list-error')).toBeVisible();
    await expect(page.getByTestId('l3-selected-status')).toHaveText('DRAFT');
    await expect(page.locator('[data-testid="l3-school-detail"]')).toContainText(id);
  });
});
