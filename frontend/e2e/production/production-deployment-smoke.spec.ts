import { expect, test, type Page } from '@playwright/test';

const adminUsername = process.env.PRODUCTION_ADMIN_USERNAME;
const adminPassword = process.env.PRODUCTION_ADMIN_PASSWORD;
let schoolName = '';
let schoolId = '';
let schoolAdminUsername = '';
let schoolAdminPassword = '';
let studentUsername = '';
let studentPassword = '';
let projectName = '';
let projectId = '';

async function login(page: Page, username: string, password: string, destination: RegExp) {
  await page.goto('/login');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  const loginResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST'
  );
  await page.locator('button[type="submit"]').click();
  const response = await loginResponse;
  expect(response.status()).toBe(200);
  const setCookie = await response.headerValue('set-cookie');
  expect(setCookie).toContain('SESSION=');
  expect(setCookie).toMatch(/Secure/i);
  expect(setCookie).toMatch(/HttpOnly/i);
  expect(setCookie).toMatch(/SameSite=Lax/i);
  await expect(page).toHaveURL(destination);
}

async function logout(page: Page) {
  await page.getByRole('button', { name: /退出登录/ }).click();
  await expect(page).toHaveURL(/\/login(?:$|\?)/);
}

test.describe('production deployment smoke', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(() => {
    expect(adminUsername, 'PRODUCTION_ADMIN_USERNAME must be set').toBeTruthy();
    expect(adminPassword, 'PRODUCTION_ADMIN_PASSWORD must be set').toBeTruthy();
  });

  test('HTTPS, API proxy, and SPA fallback serve the production build', async ({ page, request }) => {
    for (const path of ['/', '/projects', '/activities', '/rankings', '/student', '/school-admin/activities', '/super-admin/schools']) {
      const response = await page.goto(path);
      expect(response?.status()).toBeLessThan(500);
      await expect(page.locator('body')).not.toBeEmpty();
    }
    const health = await request.get('/api/v1/auth/csrf');
    expect(health.status()).toBe(200);
  });

  test('bootstrap administrator can log in, mutate through CSRF, and log out', async ({ page }) => {
    const suffix = Date.now();
    schoolName = `Production Smoke School ${suffix}`;
    schoolAdminUsername = `production-admin-${suffix}`;
    schoolAdminPassword = 'ProductionSchoolAdmin1!';
    const secondAdminUsername = `production-admin-2-${suffix}`;
    const secondAdminPassword = 'ProductionSchoolAdmin2!';

    await login(page, adminUsername!, adminPassword!, /\/super-admin$/);
    await page.goto('/school-registration');
    await page.getByLabel('学校名称').fill(schoolName);
    await page.getByLabel('统一识别类型').fill('USCC');
    await page.getByLabel('统一识别编码').fill(`PROD-${suffix}`);
    await page.getByLabel('学校类型').fill('UNIVERSITY');
    await page.getByLabel('所属地区').fill('Production Smoke Region');
    await page.getByLabel('学校地址').fill('Production Smoke Address');
    await page.getByLabel('联系人姓名').fill('Production Smoke Contact');
    await page.getByLabel('联系电话').fill('13800000000');
    await page.getByLabel('联系邮箱').fill(`production-${suffix}@example.test`);
    await page.getByRole('button', { name: '提交入驻申请' }).click();
    await expect(page.getByRole('heading', { name: '申请已提交' })).toBeVisible();

    await page.goto('/super-admin/school-registrations');
    const row = page.getByRole('row').filter({ hasText: schoolName });
    await row.getByRole('link', { name: '查看详情' }).click();
    await page.getByRole('button', { name: '审核通过' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认通过' }).click();
    await expect(page.getByText('审核已通过')).toBeVisible();

    await page.goto('/super-admin/schools');
    const schoolRow = page.getByRole('row').filter({ hasText: schoolName });
    const detailLink = schoolRow.getByRole('link', { name: '查看详情' });
    const detailPath = await detailLink.getAttribute('href');
    expect(detailPath).toBeTruthy();
    schoolId = detailPath!.split('/').at(-1)!;
    await page.goto(`/super-admin/schools/${schoolId}/admins`);
    await page.getByRole('button', { name: '创建邀请' }).click();
    await page.getByRole('dialog').getByLabel('用户名').fill(schoolAdminUsername);
    await page.getByRole('dialog').getByRole('button', { name: '创建邀请' }).click();
    const activationCode = await page.locator('[aria-label="一次性邀请码"]').innerText();
    await page.getByRole('button', { name: '我已安全保存' }).click();
    await page.getByRole('button', { name: '创建邀请' }).click();
    await page.getByRole('dialog').getByLabel('用户名').fill(secondAdminUsername);
    await page.getByRole('dialog').getByRole('button', { name: '创建邀请' }).click();
    const secondActivationCode = await page.locator('[aria-label="一次性邀请码"]').innerText();
    await page.getByRole('button', { name: '我已安全保存' }).click();
    await logout(page);

    await page.goto('/school-admin/activate');
    await page.locator('input[autocomplete="username"]').fill(schoolAdminUsername);
    await page.locator('input[autocomplete="one-time-code"]').fill(activationCode);
    await page.locator('input[autocomplete="new-password"]').nth(0).fill(schoolAdminPassword);
    await page.locator('input[autocomplete="new-password"]').nth(1).fill(schoolAdminPassword);
    await page.getByRole('button', { name: '激活账号' }).click();
    await expect(page.getByRole('heading', { name: '账号激活成功' })).toBeVisible();

    await page.goto('/school-admin/activate');
    await page.locator('input[autocomplete="username"]').fill(secondAdminUsername);
    await page.locator('input[autocomplete="one-time-code"]').fill(secondActivationCode);
    await page.locator('input[autocomplete="new-password"]').nth(0).fill(secondAdminPassword);
    await page.locator('input[autocomplete="new-password"]').nth(1).fill(secondAdminPassword);
    await page.getByRole('button', { name: '激活账号' }).click();
    await expect(page.getByRole('heading', { name: '账号激活成功' })).toBeVisible();

    await login(page, schoolAdminUsername, schoolAdminPassword, /\/school-admin$/);
    await expect(page.locator('body')).toContainText(schoolId);
    await logout(page);

    await login(page, adminUsername!, adminPassword!, /\/super-admin$/);
    await page.goto(`/super-admin/schools/${schoolId}`);
    await page.getByRole('button', { name: '启用学校' }).click();
    await page.getByRole('dialog').getByLabel('操作原因').fill('Production deployment readiness');
    await page.getByRole('dialog').getByRole('button', { name: '确认启用' }).click();
    await expect(page.locator('[aria-labelledby="school-basic-title"]')).toContainText('正常');
    await logout(page);
  });

  test('student registration is approved by the production school administrator', async ({ page }) => {
    const suffix = Date.now();
    studentUsername = `production-student-${suffix}`;
    studentPassword = 'ProductionStudent1!';

    await page.goto('/register');
    await page.locator('input[autocomplete="username"]').fill(studentUsername);
    await page.locator('input[autocomplete="new-password"]').nth(0).fill(studentPassword);
    await page.locator('input[autocomplete="new-password"]').nth(1).fill(studentPassword);
    await page.getByText('真实姓名').locator('..').locator('input').fill('Production Smoke Student');
    await page.locator('select').selectOption({ label: schoolName });
    await page.getByText('学号').locator('..').locator('input').fill(`PROD-STUDENT-${suffix}`);
    await page.getByText('年级').locator('..').locator('input').fill('2026');
    await page.getByText('班级').locator('..').locator('input').fill('Production Smoke Class');
    await page.getByRole('button', { name: '提交注册申请' }).click();
    await expect(page.locator('.submitted-summary')).toContainText(studentUsername);

    await login(page, schoolAdminUsername, schoolAdminPassword, /\/school-admin$/);
    await page.goto('/school-admin/student-applications');
    const row = page.getByRole('row').filter({ hasText: studentUsername });
    await row.getByRole('button', { name: '批准' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认批准' }).click();
    await expect(page.getByText('已批准学生身份申请')).toBeVisible();
    await logout(page);

    await login(page, studentUsername, studentPassword, /\/student$/);
    await logout(page);
  });

  test('production project and activity mutations survive authoritative reloads', async ({ page }) => {
    projectName = `Production Smoke Project ${Date.now()}`;
    await login(page, adminUsername!, adminPassword!, /\/super-admin$/);
    await page.goto('/super-admin/projects/new');
    await page.getByLabel('项目名称').fill(projectName);
    await page.getByLabel('分类').fill('SPORTS');
    await page.getByLabel('项目说明').fill('Production deployment smoke project.');
    await page.getByLabel('规则正文').fill('Production deployment smoke rules.');
    await page.getByLabel('成绩单位').fill('points');
    await page.getByRole('button', { name: '保存项目' }).click();
    await expect(page).toHaveURL(/\/super-admin\/projects\/[0-9a-f-]+$/);
    projectId = page.url().split('/').at(-1)!;
    await page.getByRole('button', { name: '上架项目' }).click();
    await page.getByRole('dialog').getByLabel('操作原因').fill('Production deployment smoke');
    await page.getByRole('dialog').getByRole('button', { name: '确认' }).click();
    await expect(page.locator('.project-status')).toHaveAttribute('data-status', 'PUBLISHED');
    await logout(page);

    await login(page, schoolAdminUsername, schoolAdminPassword, /\/school-admin$/);
    await page.goto('/school-admin/activities/new');
    await page.getByLabel('活动名称').fill('Production Smoke Activity');
    await page.getByLabel('挑战项目').selectOption(projectId);
    await page.getByLabel('描述').fill('Production deployment smoke activity.');
    await page.getByRole('button', { name: '保存活动' }).click();
    await expect(page).toHaveURL(/\/school-admin\/activities\/[0-9a-f-]+$/);
    await page.reload();
    await expect(page.getByRole('button', { name: '发布活动' })).toBeVisible();
    await page.getByRole('button', { name: '发布活动' }).click();
    await expect(page.getByText('活动已发布。')).toBeVisible();
    await logout(page);

    await page.goto('/projects');
    await page.getByPlaceholder('项目名称或说明').fill(projectName);
    await page.getByRole('button', { name: '查询' }).click();
    await expect(page.locator(`a[href="/projects/${projectId}"]`)).toBeVisible();
  });
});
