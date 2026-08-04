import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AuthContextResponse } from '@/types/auth';

const authState = vi.hoisted(() => ({
  initialized: true,
  authenticated: false,
  user: null as AuthContextResponse | null,
  restoreSession: vi.fn(),
  defaultWorkspaceRoute: vi.fn(() => '/student'),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}));

function registeredUser(): AuthContextResponse {
  return {
    userId: 'u1',
    username: 'registered',
    accountStatus: 'NORMAL',
    platformRole: 'REGISTERED_USER',
    roles: ['REGISTERED_USER'],
    schoolMemberships: [],
    primaryRole: 'REGISTERED_USER',
    primarySchoolId: null,
    onboardingRequired: true,
  };
}

function formalStudent(): AuthContextResponse {
  return {
    userId: 'u2',
    username: 'student',
    accountStatus: 'NORMAL',
    platformRole: null,
    roles: ['STUDENT'],
    schoolMemberships: [{ schoolId: 's1', roleInSchool: 'STUDENT' }],
    primaryRole: 'STUDENT',
    primarySchoolId: 's1',
    onboardingRequired: false,
  };
}

describe('router registration onboarding gates', () => {
  beforeEach(async () => {
    Object.defineProperty(window, 'scrollTo', {
      value: vi.fn(),
      writable: true,
    });

    authState.initialized = true;
    authState.authenticated = false;
    authState.user = null;
    authState.defaultWorkspaceRoute.mockReturnValue('/student');
    vi.clearAllMocks();

    const router = (await import('@/router')).default;
    await router.push('/');
    await router.isReady();
  });

  it('guestCanOpenRegister', async () => {
    const router = (await import('@/router')).default;

    await router.push('/register');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/register');
  });

  it('guestCanOpenVerifyEmail', async () => {
    const router = (await import('@/router')).default;

    await router.push('/verify-email?token=abc');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/verify-email');
  });

  it('guestCannotOpenOnboarding', async () => {
    const router = (await import('@/router')).default;

    await router.push('/onboarding');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/login');
    expect(router.currentRoute.value.query.redirect).toBe('/onboarding');
  });

  it('registeredUserCanOpenOnboarding', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/onboarding');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/onboarding');
  });

  it('registeredUserCannotOpenStudentWorkspace', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/student');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/forbidden');
  });

  it('registeredUserCannotOpenTeacherWorkspace', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/teacher');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/forbidden');
  });

  it('registeredUserCannotOpenSchoolAdminWorkspace', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/school-admin');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/forbidden');
  });

  it('registeredUserCannotOpenAdminWorkspace', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/admin');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/forbidden');
  });

  it('formalStudentIsRedirectedAwayFromOnboarding', async () => {
    authState.authenticated = true;
    authState.user = formalStudent();
    authState.defaultWorkspaceRoute.mockReturnValue('/student');
    const router = (await import('@/router')).default;

    await router.push('/onboarding');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/student');
  });

  it('registeredUserIgnoresStudentRedirect', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/login?redirect=/student');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/onboarding');
  });

  it('registeredUserIgnoresAdminRedirect', async () => {
    authState.authenticated = true;
    authState.user = registeredUser();
    const router = (await import('@/router')).default;

    await router.push('/login?redirect=/admin');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/onboarding');
  });

  it('safeRedirectRejectsOpenRedirects', async () => {
    const { safeRedirect } = await import('@/router');

    expect(safeRedirect('/student', '/fallback')).toBe('/student');
    expect(safeRedirect('//example.com', '/fallback')).toBe('/fallback');
    expect(safeRedirect('https://example.com', '/fallback')).toBe('/fallback');
    expect(safeRedirect('javascript:alert(1)', '/fallback')).toBe('/fallback');
  });
});
