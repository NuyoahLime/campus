import http from './http';
import { getCsrfToken, clearCsrfToken } from './csrf';
import type { AuthContextResponse, LoginRequest } from '@/types/auth';

export async function fetchCsrf(): Promise<{ headerName: string; token: string }> {
  const csrf = await getCsrfToken();
  return { headerName: csrf.headerName, token: csrf.token };
}

export async function login(req: LoginRequest): Promise<AuthContextResponse> {
  const csrf = await getCsrfToken();
  const response = await http.post<AuthContextResponse>('/v1/auth/login', req, {
    headers: { [csrf.headerName]: csrf.token },
  });
  clearCsrfToken();
  return response.data;
}

export async function fetchMe(): Promise<AuthContextResponse> {
  const response = await http.get<AuthContextResponse>('/v1/auth/me');
  return response.data;
}

export async function logout(): Promise<void> {
  const csrf = await getCsrfToken();
  await http.post('/v1/auth/logout', null, {
    headers: { [csrf.headerName]: csrf.token },
  });
  clearCsrfToken();
}
