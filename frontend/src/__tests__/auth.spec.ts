import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAuthStore } from '@/stores/auth';

// Mock API modules
const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  ApiError: class extends Error {
    constructor(public status: number, message: string) {
      super(message);
      this.name = 'ApiError';
    }
  },
}));

import { login, fetchMe } from '@/api/auth';

vi.mock('@/api/csrf', () => ({
  getCsrfToken: vi.fn().mockResolvedValue({ headerName: 'X-XSRF-TOKEN', token: 'test-token' }),
  clearCsrfToken: vi.fn(),
}));

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  mockGet.mockReset();
  mockPost.mockReset();
});

// ── login tests ──

describe('login', () => {
  it('includes CSRF header in login request', async () => {
    mockPost.mockResolvedValue({
      data: { userId: 'u1', username: 'test', accountStatus: 'NORMAL', platformRole: null, roles: ['STUDENT'], schoolMemberships: [] },
    });

    await login({ username: 'test', password: 'pass' });

    expect(mockPost).toHaveBeenCalledWith(
      '/v1/auth/login',
      { username: 'test', password: 'pass' },
      expect.objectContaining({
        headers: { 'X-XSRF-TOKEN': 'test-token' },
      }),
    );
  });

  it('returns auth context on success', async () => {
    const context = { userId: 'u1', username: 'test', accountStatus: 'NORMAL', platformRole: 'SUPER_ADMIN', roles: ['SUPER_ADMIN'], schoolMemberships: [] };
    mockPost.mockResolvedValue({ data: context });

    const result = await login({ username: 'test', password: 'pass' });
    expect(result.userId).toBe('u1');
    expect(result.roles).toContain('SUPER_ADMIN');
  });
});

// ── fetchMe tests ──

describe('fetchMe', () => {
  it('returns context on success', async () => {
    mockGet.mockResolvedValue({
      data: { userId: 'u1', username: 'test', accountStatus: 'NORMAL', platformRole: null, roles: ['TEACHER'], schoolMemberships: [{ schoolId: 's1', roleInSchool: 'TEACHER' }] },
    });

    const result = await fetchMe();
    expect(result.roles).toContain('TEACHER');
    expect(result.schoolMemberships).toHaveLength(1);
  });
});

// ── auth store tests ──

describe('useAuthStore', () => {
  function mockMeResponse(roles: string[] = ['STUDENT']) {
    mockGet.mockResolvedValue({
      data: { userId: 'u1', username: 'test', accountStatus: 'NORMAL', platformRole: null, roles, schoolMemberships: [] },
    });
  }

  class MockApiError extends Error {
    constructor(public status: number, message: string) {
      super(message);
      this.name = 'ApiError';
    }
  }

  function mockMeReject(status: number) {
    mockGet.mockRejectedValue(new MockApiError(status, 'error'));
  }

  it('starts as uninitialized guest', () => {
    const store = useAuthStore();
    expect(store.initialized).toBe(false);
    expect(store.authenticated).toBe(false);
  });

  it('restoreSession sets user on success', async () => {
    mockMeResponse(['STUDENT']);
    const store = useAuthStore();
    await store.restoreSession();
    expect(store.authenticated).toBe(true);
    expect(store.user?.roles).toContain('STUDENT');
  });

  it('restoreSession stays guest on 401', async () => {
    mockMeReject(401);
    const store = useAuthStore();
    await store.restoreSession();
    expect(store.authenticated).toBe(false);
    expect(store.initialized).toBe(true);
  });

  it('hasRole checks single role', async () => {
    mockMeResponse(['TEACHER', 'STUDENT']);
    const store = useAuthStore();
    await store.restoreSession();
    expect(store.hasRole('TEACHER')).toBe(true);
    expect(store.hasRole('SUPER_ADMIN')).toBe(false);
  });

  it('hasAnyRole checks multiple roles', async () => {
    mockMeResponse(['STUDENT']);
    const store = useAuthStore();
    await store.restoreSession();
    expect(store.hasAnyRole(['STUDENT', 'TEACHER'])).toBe(true);
    expect(store.hasAnyRole(['SUPER_ADMIN'])).toBe(false);
  });

  it('defaultWorkspaceRoute returns correct path', async () => {
    mockGet.mockResolvedValue({ data: { userId:'u', username:'t', accountStatus:'NORMAL', platformRole:null, roles:['TEACHER'], schoolMemberships:[], primaryRole:'TEACHER', primarySchoolId:null } });
    await useAuthStore().restoreSession();
    expect(useAuthStore().defaultWorkspaceRoute()).toBe('/teacher');
  });

  it('defaultWorkspaceRoute uses primaryRole', async () => {
    mockGet.mockResolvedValue({ data: { userId:'u', username:'t', accountStatus:'NORMAL', platformRole:null, roles:['STUDENT','SUPER_ADMIN'], schoolMemberships:[], primaryRole:'STUDENT', primarySchoolId:null } });
    await useAuthStore().restoreSession();
    expect(useAuthStore().defaultWorkspaceRoute()).toBe('/student');
  });

  it('logout clears user', async () => {
    mockMeResponse(['STUDENT']);
    mockPost.mockResolvedValue({});
    const store = useAuthStore();
    await store.restoreSession();
    expect(store.authenticated).toBe(true);
    await store.logout();
    expect(store.authenticated).toBe(false);
  });

  it('login sets user and marks initialized', async () => {
    mockPost.mockResolvedValue({
      data: { userId: 'u1', username: 't', accountStatus: 'NORMAL', platformRole: null, roles: ['STUDENT'], schoolMemberships: [] },
    });
    const store = useAuthStore();
    await store.login('t', 'p');
    expect(store.authenticated).toBe(true);
    expect(store.initialized).toBe(true);
  });
});
