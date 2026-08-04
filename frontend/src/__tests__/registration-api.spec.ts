import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  register,
  resendVerification,
  verifyEmail,
  type RegisterPayload,
} from '@/api/registration';

const mockPost = vi.hoisted(() => vi.fn());
const mockConsoleLog = vi.hoisted(() => vi.fn());

vi.mock('@/api/http', () => ({
  default: {
    post: (...args: unknown[]) => mockPost(...args),
  },
}));

vi.mock('@/api/csrf', () => ({
  getCsrfToken: vi.fn(),
  clearCsrfToken: vi.fn(),
}));

describe('registration api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'log').mockImplementation(mockConsoleLog);
  });

  it('registerSendsExactExpectedPayload', async () => {
    const payload: RegisterPayload = {
      username: 'public-user',
      email: 'user@example.com',
      password: 'Example123!',
      confirmPassword: 'Example123!',
    };
    mockPost.mockResolvedValue({
      data: {
        username: 'public-user',
        verificationRequired: true,
        nextAction: 'VERIFY_EMAIL',
      },
    });

    await register(payload);

    expect(mockPost).toHaveBeenCalledTimes(1);
    expect(mockPost).toHaveBeenCalledWith('/v1/auth/register', {
      username: 'public-user',
      email: 'user@example.com',
      password: 'Example123!',
      confirmPassword: 'Example123!',
    });
  });

  it('registerDoesNotSendRoleOrAccountStatus', async () => {
    mockPost.mockResolvedValue({
      data: {
        username: 'public-user',
        verificationRequired: true,
        nextAction: 'VERIFY_EMAIL',
      },
    });

    await register({
      username: 'public-user',
      email: 'user@example.com',
      password: 'Example123!',
      confirmPassword: 'Example123!',
    });

    const body = mockPost.mock.calls[0][1] as Record<string, unknown>;
    expect(body).not.toHaveProperty('role');
    expect(body).not.toHaveProperty('platformRole');
    expect(body).not.toHaveProperty('accountStatus');
  });

  it('registerUsesExistingCsrfFlow', async () => {
    const csrf = await import('@/api/csrf');
    mockPost.mockResolvedValue({
      data: {
        username: 'public-user',
        verificationRequired: true,
        nextAction: 'VERIFY_EMAIL',
      },
    });

    await register({
      username: 'public-user',
      email: 'user@example.com',
      password: 'Example123!',
      confirmPassword: 'Example123!',
    });

    expect(csrf.getCsrfToken).not.toHaveBeenCalled();
    expect(mockPost).toHaveBeenCalledTimes(1);
  });

  it('verifyEmailPostsOnlyToken', async () => {
    mockPost.mockResolvedValue({ data: undefined });

    await verifyEmail('raw-token');

    expect(mockPost).toHaveBeenCalledTimes(1);
    expect(mockPost).toHaveBeenCalledWith('/v1/auth/verify-email', {
      token: 'raw-token',
    });
  });

  it('resendVerificationPostsEmail', async () => {
    mockPost.mockResolvedValue({ data: { message: 'generic' } });

    await resendVerification('user@example.com');

    expect(mockPost).toHaveBeenCalledTimes(1);
    expect(mockPost).toHaveBeenCalledWith('/v1/auth/resend-verification', {
      email: 'user@example.com',
    });
  });

  it('apiDoesNotLogPasswordOrToken', async () => {
    mockPost.mockResolvedValue({ data: { message: 'generic' } });

    await verifyEmail('secret-token');
    await resendVerification('user@example.com');

    expect(mockConsoleLog).not.toHaveBeenCalled();
  });
});
