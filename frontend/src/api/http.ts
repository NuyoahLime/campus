import type { ApiErrorResponse, CsrfTokenResponse } from '../types/auth';

const API_PREFIX = '/api/v1';

export class ApiError extends Error {
  readonly status: number;
  readonly body: ApiErrorResponse | unknown;

  constructor(status: number, body: ApiErrorResponse | unknown) {
    super(`API request failed with status ${status}`);
    this.status = status;
    this.body = body;
  }

  get code(): string | undefined {
    if (this.body && typeof this.body === 'object' && 'code' in this.body) {
      return String((this.body as ApiErrorResponse).code);
    }
    return undefined;
  }
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function getCsrf(): Promise<CsrfTokenResponse> {
  const response = await fetch(`${API_PREFIX}/auth/csrf`, {
    method: 'GET',
    credentials: 'include'
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new ApiError(response.status, body);
  }
  return body as CsrfTokenResponse;
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);

  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await getCsrf();
    headers.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include'
  });
  const body = await readJson(response);

  if (!response.ok) {
    throw new ApiError(response.status, body);
  }

  return body as T;
}
