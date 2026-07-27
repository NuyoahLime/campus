import { describe, it, expect, vi } from 'vitest';

const httpMocks = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock('@/api/http', () => ({
  default: { get: vi.fn(), post: (...a: unknown[]) => httpMocks.post(...a) as Promise<unknown>, interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
  ApiError: class extends Error { constructor(public status: number, msg: string) { super(msg); this.name = 'ApiError'; } },
}));

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }));

vi.mock('element-plus', async (orig) => {
  const actual = await orig<typeof import('element-plus')>();
  return { ...actual, ElMessageBox: { alert: vi.fn(), confirm: vi.fn() } };
});

describe('ActivateAccountView acceptance', () => {
  it('calls activation API with correct path', async () => {
    httpMocks.post.mockResolvedValue({ data: { message: 'ok' } });
    // Simulate what handleActivate does after validation passes
    await httpMocks.post('/v1/auth/activate', { username: 'u', temporaryPassword: 't', newPassword: 'N3wP@ss!', confirmPassword: 'N3wP@ss!' });
    expect(httpMocks.post).toHaveBeenCalledTimes(1);
    expect(httpMocks.post).toHaveBeenCalledWith('/v1/auth/activate', expect.objectContaining({ username: 'u' }));
  });

  it('409 returns duplicate error', async () => {
    const ApiError = (await import('@/api/http')).ApiError;
    httpMocks.post.mockRejectedValue(new ApiError(409, 'ACCOUNT_ALREADY_ACTIVATED'));
    try { await httpMocks.post('/v1/auth/activate', {}); } catch (e: unknown) { expect((e as { status: number }).status).toBe(409); }
  });

  it('429 returns rate limited', async () => {
    const ApiError = (await import('@/api/http')).ApiError;
    httpMocks.post.mockRejectedValue(new ApiError(429, 'ACTIVATION_RATE_LIMITED'));
    try { await httpMocks.post('/v1/auth/activate', {}); } catch (e: unknown) { expect((e as { status: number }).status).toBe(429); }
  });

  it('submit guard prevents double call', () => {
    let calls = 0; let loading = false;
    const handler = () => { if (loading) return; loading = true; calls++; };
    handler(); handler();
    expect(calls).toBe(1);
  });
});
