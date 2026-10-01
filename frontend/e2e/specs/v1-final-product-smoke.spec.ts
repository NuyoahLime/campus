import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { actors, apiRequest, loginApi, loginUi } from '../support/auth';
import type { FixtureState } from '../support/fixture';

let smokeProjectName = '';
let smokeProjectId = '';

async function fixture(): Promise<FixtureState> {
  return JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime/fixture-state.json'),
    'utf8'
  )) as FixtureState;
}

function diagnostics(page: Page) {
  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  const serverErrors: string[] = [];
  page.on('console', message => {
    if (message.type() === 'error' && !/status of (401|403|404) \(/.test(message.text())) {
      consoleErrors.push(message.text());
    }
  });
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('response', response => {
    if (response.status() >= 500) serverErrors.push(`${response.status()} ${response.url()}`);
  });
  return () => {
    expect(consoleErrors, `console.error: ${consoleErrors.join('\n')}`).toEqual([]);
    expect(pageErrors, `pageerror: ${pageErrors.join('\n')}`).toEqual([]);
    expect(serverErrors, `server responses: ${serverErrors.join('\n')}`).toEqual([]);
  };
}

async function logout(page: Page) {
  const button = page.getByRole('button', { name: /退出登录/ });
  if (await button.count()) {
    await button.click();
    await expect(page).toHaveURL(/\/login(?:$|\?)/);
  }
}

async function loginApiInNewContext(username: string): Promise<APIRequestContext> {
  const context = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  const response = await loginApi(context, username);
  expect(response.status()).toBe(200);
  return context;
}

test.describe('V1 final product smoke', () => {
  test.describe.configure({ mode: 'serial' });
  test.setTimeout(120_000);

  test('public portal and protected session routes have no fatal browser errors', async ({ page, request }) => {
    const check = diagnostics(page);
    const ids = await fixture();

    for (const route of [
      '/', '/projects', `/projects/${ids.lifecycleProject}`, '/activities',
      `/activities/${ids.activityLifecycle}`, '/rankings', '/school-registration', '/login', '/register'
    ]) {
      await page.context().clearCookies();
      const response = await page.goto(route);
      expect(response?.status()).toBeLessThan(500);
      await expect(page.locator('body')).not.toBeEmpty();
    }

    await page.goto(`/school-admin/activities/${ids.activityLifecycle}`);
    await expect(page).toHaveURL(/\/login(?:$|\?)/);
    expect((await request.get('/api/v1/activities/management')).status()).toBe(401);
    check();
  });

  test('all supported roles reach their workspace and representative permissions are enforced', async ({ page }) => {
    const check = diagnostics(page);
    const ids = await fixture();

    await loginUi(page, actors.superAdmin);
    await expect(page).toHaveURL(/\/super-admin$/);
    for (const link of [
      '/super-admin/school-registrations', '/super-admin/schools', '/super-admin/projects',
      '/super-admin/activity-results/reviews', '/super-admin/activity-results',
      '/super-admin/l3-authorizations', '/super-admin/ranking-management'
    ]) await expect(page.locator(`.workspace-nav-item[href="${link}"]`)).toBeVisible();
    await logout(page);

    await page.locator('input[autocomplete="username"]').fill(actors.schoolAdminA);
    await page.locator('input[autocomplete="current-password"]').fill('definitely-wrong');
    const failedLogin = page.waitForResponse(response =>
      response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST'
    );
    await page.locator('button[type="submit"]').click();
    expect((await failedLogin).status()).toBe(401);
    await expect(page).toHaveURL(/\/login(?:$|\?)/);
    await page.goto('/school-admin');
    await expect(page).toHaveURL(/\/login(?:$|\?)/);

    await loginUi(page, actors.schoolAdminA);
    await expect(page).toHaveURL(/\/school-admin$/);
    for (const link of [
      '/school-admin/student-applications', '/school-admin/activities', '/school-admin/appeals',
      '/school-admin/feedback', '/school-admin/l3-authorizations', '/school-admin/ranking-management',
      '/school-admin/rankings'
    ]) await expect(page.locator(`.workspace-nav-item[href="${link}"]`)).toBeVisible();
    await logout(page);

    await loginUi(page, actors.studentA);
    await expect(page).toHaveURL(/\/student$/);
    for (const link of ['/student/activities', '/student/scores', '/student/rankings', '/student/appeals', '/student/feedback']) {
      await expect(page.locator(`.workspace-nav-item[href="${link}"]`)).toBeVisible();
    }
    await page.goto('/school-admin/activities');
    await expect(page).toHaveURL(/\/student(?:$|\/)/);

    const anonymous = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
    const student = await loginApiInNewContext(actors.studentA);
    const schoolAdmin = await loginApiInNewContext(actors.schoolAdminA);
    const otherSchoolAdmin = await loginApiInNewContext(actors.schoolAdminB);
    const superAdmin = await loginApiInNewContext(actors.superAdmin);
    try {
      expect((await anonymous.get('/api/v1/activities/management')).status()).toBe(401);
      expect((await apiRequest(student, 'GET', '/api/v1/schools/governance')).status()).toBe(403);
      expect((await apiRequest(student, 'GET', '/api/v1/activities/management')).status()).toBe(403);
      expect((await apiRequest(schoolAdmin, 'GET', '/api/v1/schools/governance')).status()).toBe(403);
      expect([403, 404]).toContain((await apiRequest(otherSchoolAdmin, 'GET', `/api/v1/activities/management/${ids.activityLifecycle}`)).status());
      expect((await apiRequest(superAdmin, 'POST', `/api/v1/school-admin/score-attempts/${ids.apiPendingAttempt}/approve`)).status()).toBe(403);
    } finally {
      await Promise.all([anonymous.dispose(), student.dispose(), schoolAdmin.dispose(), otherSchoolAdmin.dispose(), superAdmin.dispose()]);
    }
    check();
  });

  test('student registration is reviewed through the school workspace before login', async ({ page }) => {
    const check = diagnostics(page);
    const username = `smoke-student-${Date.now()}`;
    await page.context().clearCookies();
    await page.goto('/register');
    await page.locator('input[autocomplete="username"]').fill(username);
    await page.locator('input[autocomplete="new-password"]').nth(0).fill('SmokeStudentPass1!');
    await page.locator('input[autocomplete="new-password"]').nth(1).fill('SmokeStudentPass1!');
    await page.getByText('真实姓名').locator('..').locator('input').fill('Smoke Student');
    await page.locator('select').selectOption({ label: 'E2E School A' });
    await page.getByText('学号').locator('..').locator('input').fill(`SMOKE-${Date.now()}`);
    await page.getByText('年级').locator('..').locator('input').fill('2026');
    await page.getByText('班级').locator('..').locator('input').fill('Smoke Class');
    const approvedRegistration = page.waitForResponse(response =>
      response.url().endsWith('/api/v1/auth/student/register') && response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: '提交注册申请' }).click();
    expect((await approvedRegistration).status()).toBe(201);
    await expect(page.locator('.submitted-summary')).toContainText(username);

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/student-applications');
    const row = page.getByRole('row').filter({ hasText: username });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: '批准' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认批准' }).click();
    await expect(page.getByText('已批准学生身份申请')).toBeVisible();

    await page.context().clearCookies();
    await page.goto('/login');
    await page.locator('input[autocomplete="username"]').fill(username);
    await page.locator('input[autocomplete="current-password"]').fill('SmokeStudentPass1!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/student$/);

    const rejectedUsername = `smoke-rejected-${Date.now()}`;
    await page.context().clearCookies();
    await page.goto('/register');
    await page.locator('input[autocomplete="username"]').fill(rejectedUsername);
    await page.locator('input[autocomplete="new-password"]').nth(0).fill('SmokeRejectedPass1!');
    await page.locator('input[autocomplete="new-password"]').nth(1).fill('SmokeRejectedPass1!');
    await page.getByText('真实姓名').locator('..').locator('input').fill('Rejected Smoke Student');
    await page.locator('select').selectOption({ label: 'E2E School A' });
    await page.getByText('学号').locator('..').locator('input').fill(`REJECT-${Date.now()}`);
    await page.getByText('年级').locator('..').locator('input').fill('2026');
    await page.getByText('班级').locator('..').locator('input').fill('Rejected Class');
    const rejectedRegistration = page.waitForResponse(response =>
      response.url().endsWith('/api/v1/auth/student/register') && response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: '提交注册申请' }).click();
    expect((await rejectedRegistration).status()).toBe(201);
    await expect(page.locator('.submitted-summary')).toContainText(rejectedUsername);

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/student-applications');
    const rejectedRow = page.getByRole('row').filter({ hasText: rejectedUsername });
    await expect(rejectedRow).toBeVisible();
    await rejectedRow.getByRole('button', { name: '拒绝' }).click();
    await page.getByRole('dialog').getByLabel('拒绝原因').fill('Final smoke rejection path');
    await page.getByRole('dialog').getByRole('button', { name: '确认拒绝' }).click();
    await expect(page.getByText('已拒绝学生身份申请')).toBeVisible();

    await page.context().clearCookies();
    await page.goto('/login');
    await page.locator('input[autocomplete="username"]').fill(rejectedUsername);
    await page.locator('input[autocomplete="current-password"]').fill('SmokeRejectedPass1!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/student\/application\/rejected/);
    await expect(page.getByRole('heading', { name: '申请未通过审核' })).toBeVisible();
    check();
  });

  test('school registration, school lifecycle, and admin invitation remain reachable', async ({ page }) => {
    const check = diagnostics(page);
    const suffix = Date.now();
    const schoolName = `Smoke School ${suffix}`;

    await page.context().clearCookies();
    await page.goto('/school-registration');
    await page.getByLabel('学校名称').fill(schoolName);
    await page.getByLabel('统一识别类型').fill('USCC');
    await page.getByLabel('统一识别编码').fill(`SMOKE-${suffix}`);
    await page.getByLabel('学校类型').fill('UNIVERSITY');
    await page.getByLabel('所属地区').fill('Smoke Region');
    await page.getByLabel('学校地址').fill('Smoke Address');
    await page.getByLabel('联系人姓名').fill('Smoke Contact');
    await page.getByLabel('联系电话').fill('13800000000');
    await page.getByLabel('联系邮箱').fill(`smoke-${suffix}@example.test`);
    await page.getByRole('button', { name: '提交入驻申请' }).click();
    await expect(page.getByRole('heading', { name: '申请已提交' })).toBeVisible();

    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/school-registrations');
    const registration = page.getByRole('row').filter({ hasText: schoolName });
    await expect(registration).toBeVisible();
    await registration.getByRole('link', { name: '查看详情' }).click();
    await page.getByRole('button', { name: '审核通过' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认通过' }).click();
    await expect(page.getByText('审核已通过')).toBeVisible();
    await page.goto('/super-admin/schools');
    const schoolRow = page.getByRole('row').filter({ hasText: schoolName });
    await expect(schoolRow).toBeVisible();

    const ids = await fixture();
    await page.goto(`/super-admin/schools/${ids.schoolA}`);
    await expect(page.getByRole('heading', { name: 'E2E School A' })).toBeVisible();
    await page.getByRole('button', { name: '暂停学校' }).click();
    await page.getByRole('dialog').getByLabel('操作原因').fill('Final smoke lifecycle check');
    await page.getByRole('dialog').getByRole('button', { name: '确认暂停' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('[aria-labelledby="school-basic-title"]')).toContainText('已暂停');
    await page.getByRole('button', { name: '恢复学校' }).click();
    await page.getByRole('dialog').getByLabel('操作原因').fill('Final smoke lifecycle restore');
    await page.getByRole('dialog').getByRole('button', { name: '确认恢复' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('[aria-labelledby="school-basic-title"]')).toContainText('正常');

    await page.goto(`/super-admin/schools/${ids.schoolA}/admins`);
    const invitedUsername = `smoke-admin-${suffix}`;
    await page.getByRole('button', { name: '创建邀请' }).click();
    await page.getByRole('dialog').getByLabel('用户名').fill(invitedUsername);
    await page.getByRole('dialog').getByRole('button', { name: '创建邀请' }).click();
    const code = await page.locator('[aria-label="一次性邀请码"]').innerText();
    await page.getByRole('button', { name: '我已安全保存' }).click();

    await page.context().clearCookies();
    await page.goto('/school-admin/activate');
    await page.locator('input[autocomplete="username"]').fill(invitedUsername);
    await page.locator('input[autocomplete="one-time-code"]').fill(code);
    await page.locator('input[autocomplete="new-password"]').nth(0).fill('SmokeAdminPass1!');
    await page.locator('input[autocomplete="new-password"]').nth(1).fill('SmokeAdminPass1!');
    await page.getByRole('button', { name: '激活账号' }).click();
    await expect(page.getByRole('heading', { name: '账号激活成功' })).toBeVisible();
    await page.getByRole('button', { name: '前往登录' }).click();
    await page.locator('input[autocomplete="username"]').fill(invitedUsername);
    await page.locator('input[autocomplete="current-password"]').fill('SmokeAdminPass1!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/school-admin$/);
    check();
  });

  test('challenge project is created, published, and visible in the public library', async ({ page }) => {
    const check = diagnostics(page);
    smokeProjectName = `V1 Smoke Project ${Date.now()}`;
    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/projects/new');
    await page.getByLabel('项目名称').fill(smokeProjectName);
    await page.getByLabel('分类').fill('SPORTS');
    await page.getByLabel('项目说明').fill('Final product smoke project.');
    await page.getByLabel('规则正文').fill('Complete the challenge under supervised conditions.');
    await page.getByLabel('成绩单位').fill('points');
    await page.getByRole('button', { name: '保存项目' }).click();
    await expect(page).toHaveURL(/\/super-admin\/projects\/[0-9a-f-]+$/);
    smokeProjectId = page.url().split('/').at(-1)!;
    await expect(page.getByRole('button', { name: '上架项目' })).toBeVisible();
    await page.getByRole('button', { name: '上架项目' }).click();
    await page.getByRole('dialog').getByLabel('操作原因').fill('Final smoke publication');
    await page.getByRole('dialog').getByRole('button', { name: '确认' }).click();
    await expect(page.locator('.project-status')).toHaveAttribute('data-status', 'PUBLISHED');

    await page.context().clearCookies();
    await page.goto('/projects');
    await page.getByPlaceholder('项目名称或说明').fill(smokeProjectName);
    await page.getByRole('button', { name: '查询' }).click();
    const projectLink = page.locator(`a[href="/projects/${smokeProjectId}"]`);
    await expect(projectLink).toBeVisible();
    await projectLink.click();
    await expect(page.getByRole('heading', { name: smokeProjectName })).toBeVisible();
    check();
  });

  test('activity publishes, participant assignment round-trips, and cancellation is authoritative', async ({ page }) => {
    const check = diagnostics(page);
    expect(smokeProjectId).not.toBe('');
    const activityName = `V1 Smoke Activity ${Date.now()}`;

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/activities/new');
    await page.getByLabel('活动名称').fill(activityName);
    await page.getByLabel('挑战项目').selectOption(smokeProjectId);
    await page.getByLabel('描述').fill('Final product smoke activity.');
    await page.getByRole('button', { name: '保存活动' }).click();
    await expect(page).toHaveURL(/\/school-admin\/activities\/[0-9a-f-]+$/);
    const activityId = page.url().split('/').at(-1)!;
    await expect(page.getByRole('button', { name: '发布活动' })).toBeVisible();
    await page.getByRole('button', { name: '发布活动' }).click();
    await expect(page.getByText('活动已发布。')).toBeVisible();

    await page.goto(`/school-admin/activities/${activityId}/participants`);
    const candidate = page.locator('.participant-section').filter({ hasText: '添加学生' })
      .getByRole('row').filter({ hasText: 'E2E-STUDENT-B' });
    await expect(candidate).toBeVisible();
    await candidate.getByRole('button', { name: '添加到活动' }).click();
    await expect(page.getByText('学生已加入本次活动。')).toBeVisible();

    await loginUi(page, actors.studentB);
    await page.goto('/student/activities');
    await expect(page.locator(`a[href="/student/activities/${activityId}"]`)).toBeVisible();

    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${activityId}/participants`);
    const assigned = page.locator('.participant-section').filter({ hasText: '已分配学生' })
      .getByRole('row').filter({ hasText: 'E2E-STUDENT-B' });
    await assigned.getByRole('button', { name: '移除' }).click();
    await expect(page.getByText('学生已从本次活动移除。')).toBeVisible();
    await page.goto(`/school-admin/activities/${activityId}`);
    await page.getByRole('button', { name: '取消活动' }).click();
    await expect(page.getByText('活动已取消。')).toBeVisible();

    await loginUi(page, actors.studentB);
    await page.goto('/student/activities');
    await expect(page.locator(`a[href="/student/activities/${activityId}"]`)).toHaveCount(0);
    check();
  });

  test('activity participants and ActivityResult are reachable from role-specific pages', async ({ page }) => {
    const check = diagnostics(page);
    const ids = await fixture();
    await loginUi(page, actors.schoolAdminA);
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}`);
    await expect(page.getByRole('link', { name: '配置参与学生' })).toBeVisible();
    await page.getByRole('link', { name: '活动成果' }).click();
    await expect(page).toHaveURL(new RegExp(`/school-admin/activities/${ids.activityLifecycle}/result$`));
    await expect(page.locator('body')).toContainText('活动成果');
    await page.goto(`/school-admin/activities/${ids.activityLifecycle}/participants`);
    await expect(page.getByRole('heading', { name: '添加学生' })).toBeVisible();
    await expect(page.locator('.participant-table').filter({ hasText: 'E2E-STUDENT-A' })).toBeVisible();

    await page.context().clearCookies();
    await page.goto(`/activities/${ids.activityLifecycle}`);
    await expect(page).toHaveURL(new RegExp(`/activities/${ids.activityLifecycle}$`));
    await expect(page.locator('body')).toContainText('ACTIVITY_LIFECYCLE');
    await loginUi(page, actors.studentA);
    await page.goto('/student/activities');
    await expect(page.getByRole('link', { name: /ACTIVITY_LIFECYCLE/ })).toBeVisible();
    await page.getByRole('link', { name: /ACTIVITY_LIFECYCLE/ }).click();
    await expect(page).toHaveURL(new RegExp(`/student/activities/${ids.activityLifecycle}$`));
    await expect(page.getByRole('heading', { name: 'ACTIVITY_LIFECYCLE' })).toBeVisible();
    await loginUi(page, actors.superAdmin);
    await page.goto('/super-admin/activity-results/reviews');
    await expect(page.getByRole('heading', { name: '成果审核' })).toBeVisible();
    check();
  });

  test('score, appeal, and feedback journeys complete through real UI', async ({ page, request }) => {
    const check = diagnostics(page);
    const ids = await fixture();
    expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);
    const created = await apiRequest(request, 'POST', `/api/v1/school-admin/activity-projects/${ids.lifecycleActivityProject}/score-attempts`, {
      studentId: ids.studentA,
      integerValue: 88,
      scoreBusinessTime: new Date().toISOString()
    });
    expect(created.status()).toBe(201);
    const scoreAttempt = await created.json() as { scoreAttemptId: string };
    expect((await apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${scoreAttempt.scoreAttemptId}/submit`)).status()).toBe(200);
    expect((await apiRequest(request, 'POST', `/api/v1/school-admin/score-attempts/${scoreAttempt.scoreAttemptId}/approve`)).status()).toBe(200);

    await loginUi(page, actors.studentA);
    await page.goto('/student/scores');
    const scoreLink = page.locator(`a[href="/student/scores/${scoreAttempt.scoreAttemptId}"]`);
    await expect(scoreLink).toBeVisible();
    await scoreLink.click();
    await page.getByRole('link', { name: '发起申诉' }).click();
    await page.getByLabel('申诉原因').fill('Final smoke appeal reason');
    await page.getByRole('button', { name: '提交申诉' }).click();
    await expect(page).toHaveURL(/\/student\/appeals\/[0-9a-f-]+$/);
    const appealUrl = page.url();
    const appealId = appealUrl.split('/').at(-1)!;

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/appeals');
    await expect(page.locator(`a[href="/school-admin/appeals/${appealId}"]`)).toBeVisible();
    await page.locator(`a[href="/school-admin/appeals/${appealId}"]`).click();
    await page.getByRole('button', { name: '开始处理' }).click();
    await page.getByPlaceholder('驳回说明').fill('Final smoke appeal resolution');
    await page.getByRole('button', { name: '驳回申诉' }).click();
    await expect(page.getByText('申诉已驳回')).toBeVisible();

    await loginUi(page, actors.studentA);
    await page.goto(appealUrl);
    await expect(page.getByText('Final smoke appeal resolution')).toBeVisible();
    await page.goto('/student/feedback/new');
    await page.getByLabel('反馈说明').fill('Final smoke feedback');
    await page.getByRole('button', { name: '提交反馈' }).click();
    await expect(page).toHaveURL(/\/student\/feedback\/[0-9a-f-]+$/);
    const feedbackUrl = page.url();
    const feedbackId = feedbackUrl.split('/').at(-1)!;

    await loginUi(page, actors.schoolAdminA);
    await page.goto('/school-admin/feedback');
    await expect(page.locator(`a[href="/school-admin/feedback/${feedbackId}"]`)).toBeVisible();
    await page.locator(`a[href="/school-admin/feedback/${feedbackId}"]`).click();
    await page.getByRole('button', { name: '开始处理' }).click();
    await page.getByPlaceholder('回复内容').fill('Final smoke feedback reply');
    await page.getByRole('button', { name: '回复并完成' }).click();
    await expect(page.getByText('反馈已回复')).toBeVisible();

    await loginUi(page, actors.studentA);
    await page.goto(feedbackUrl);
    await expect(page.getByText('Final smoke feedback reply')).toBeVisible();
    check();
  });
});
