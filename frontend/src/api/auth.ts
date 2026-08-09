import { apiRequest } from './http';
import type { CurrentUser, LoginRequest } from '../types/auth';

export async function login(request: LoginRequest): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function getMe(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/auth/me');
}

export async function logout(): Promise<void> {
  await apiRequest<null>('/auth/logout', {
    method: 'POST'
  });
}
