import http from './http';
import type { CsrfTokenResponse } from '@/types/auth';

let cachedToken: CsrfTokenResponse | null = null;
let fetchPromise: Promise<CsrfTokenResponse> | null = null;

export async function getCsrfToken(): Promise<CsrfTokenResponse> {
  if (cachedToken) return cachedToken;
  if (fetchPromise) return fetchPromise;

  fetchPromise = http
    .get<CsrfTokenResponse>('/v1/auth/csrf')
    .then((res) => {
      cachedToken = res.data;
      fetchPromise = null;
      return cachedToken;
    })
    .catch((err) => {
      fetchPromise = null;
      throw err;
    });

  return fetchPromise;
}

export function clearCsrfToken(): void {
  cachedToken = null;
}
