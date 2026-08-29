import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { actors, E2E_PASSWORD } from './fixture';

export async function loginUi(page: Page, username: string) {
  await page.goto('/login');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(E2E_PASSWORD);
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST'
  );
  await page.locator('button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  await expect(page).not.toHaveURL(/\/login(?:$|\?)/);
}

export async function expectSchoolAdminWorkspace(page: Page) {
  await expect(page).toHaveURL(/\/school-admin(?:$|\/)/);
}

async function csrf(context: APIRequestContext) {
  const response = await context.get('/api/v1/auth/csrf');
  expect(response.ok()).toBeTruthy();
  return response.json() as Promise<{ headerName: string; token: string }>;
}

export async function loginApi(context: APIRequestContext, username: string) {
  const token = await csrf(context);
  return context.post('/api/v1/auth/login', {
    headers: { [token.headerName]: token.token },
    data: { username, password: E2E_PASSWORD }
  });
}

export async function apiRequest(
  context: APIRequestContext,
  method: 'GET' | 'POST' | 'PATCH',
  url: string,
  data?: unknown
) {
  const options: Parameters<APIRequestContext['fetch']>[1] = { method, data };
  if (method !== 'GET') {
    const token = await csrf(context);
    options.headers = { [token.headerName]: token.token };
  }
  return context.fetch(url, options);
}

export { actors };
