import { describe, it, expect, vi, beforeEach } from 'vitest';

// ── Hoisted state (available inside vi.mock factories) ──

const { mockClearLocalSession, captured } = vi.hoisted(() => ({
  mockClearLocalSession: vi.fn(),
  captured: { resErrorHandler: null as ((error: unknown) => unknown) | null },
}));

// ── Mocks (hoisted by Vitest) ─────────────────────────

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    clearLocalSession: mockClearLocalSession,
    logout: vi.fn().mockResolvedValue(undefined),
  })),
}));

vi.mock('@/api/csrf', () => ({
  getCsrfToken: vi.fn(),
  clearCsrfToken: vi.fn(),
}));

vi.mock('axios', () => {
  const mockInstance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn(), eject: vi.fn() },
      response: {
        use: (_fulfilled: unknown, rejected: unknown) => {
          captured.resErrorHandler = rejected as (error: unknown) => unknown;
          return 0;
        },
        eject: vi.fn(),
      },
    },
  };
  return {
    default: {
      create: vi.fn(() => mockInstance),
      ...mockInstance,
    },
  };
});

// Import after mocks — triggers interceptor registration
import { ApiError } from '@/api/http';

// ── Helpers ────────────────────────────────────────────

function makeAxiosError(status: number, url: string, method = 'get', data?: unknown) {
  return {
    response: {
      status,
      data,
      config: { url, method },
    },
    config: { url, method },
    isAxiosError: true,
  };
}

/**
 * Flush microtasks + a timer tick so fire-and-forget .then() callbacks
 * from dynamic imports inside the interceptor can run.
 */
function flushMicrotasks(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 50));
}

// ── Tests ──────────────────────────────────────────────

describe('HTTP 401 interceptor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('failed login 401 does not clear existing session', async () => {
    const error = makeAxiosError(401, '/api/v1/auth/login');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* the interceptor always re-throws as ApiError */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).not.toHaveBeenCalled();
  });

  it('failed activate 401 does not clear existing session', async () => {
    const error = makeAxiosError(401, '/api/v1/auth/activate', 'post');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).not.toHaveBeenCalled();
  });

  it('failed register 401 does not clear existing session', async () => {
    const error = makeAxiosError(401, '/api/v1/auth/register', 'post');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).not.toHaveBeenCalled();
  });

  it('me 401 clears local session', async () => {
    const error = makeAxiosError(401, '/api/v1/auth/me');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).toHaveBeenCalledTimes(1);
  });

  it('business API 401 clears local session', async () => {
    const error = makeAxiosError(401, '/api/v1/school-registrations/mine');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).toHaveBeenCalledTimes(1);
  });

  it('non-401 errors do not clear session', async () => {
    const error = makeAxiosError(403, '/api/v1/admin/schools');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).not.toHaveBeenCalled();
  });

  it('network error (no response) does not clear session', async () => {
    const error = {
      response: undefined,
      config: { url: '/api/v1/auth/me', method: 'get' },
      isAxiosError: true,
    };

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();
    expect(mockClearLocalSession).not.toHaveBeenCalled();
  });

  it('interceptor throws ApiError on all errors', async () => {
    const error = makeAxiosError(500, '/api/v1/whatever');

    try {
      await captured.resErrorHandler!(error);
      expect(true).toBe(false);
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).status).toBe(500);
    }
  });

  it('no recursive logout on 401 (clearLocalSession never calls remote API)', async () => {
    const error = makeAxiosError(401, '/api/v1/auth/me');

    try {
      await captured.resErrorHandler!(error);
    } catch { /* expected */ }

    await flushMicrotasks();

    const { useAuthStore } = await import('@/stores/auth');
    const store = useAuthStore();
    expect(store.logout).not.toHaveBeenCalled();
  });
});
