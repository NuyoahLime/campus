import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import LoginView from '@/views/LoginView.vue';
import { ApiError } from '@/api/http';
import type { AuthContextResponse } from '@/types/auth';

const mockLogin = vi.hoisted(() => vi.fn());
const mockReplace = vi.hoisted(() => vi.fn());
const mockPush = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({ query: {} as Record<string, unknown> }));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: mockLogin,
    defaultWorkspaceRoute: () => '/student',
  }),
}));

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ replace: mockReplace, push: mockPush }),
}));

vi.mock('@/router', () => ({
  safeRedirect: (raw: unknown, fallback: string) => (
    typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : fallback
  ),
}));

vi.mock('@/api/http', () => ({
  ApiError: class extends Error {
    constructor(
      public status: number,
      message: string,
      public code?: string,
    ) {
      super(message);
      this.name = 'ApiError';
    }
  },
}));

function registeredContext(): AuthContextResponse {
  return {
    userId: 'u',
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

function mountLogin() {
  return mount(LoginView, {
    global: {
      plugins: [ElementPlus],
      stubs: { PublicLayout: { template: '<div><slot /></div>' } },
    },
  });
}

async function fillLogin(wrapper: ReturnType<typeof mountLogin>) {
  const inputs = wrapper.findAll('input');
  await inputs[0].setValue('registered');
  await inputs[1].setValue('Example123!');
}

describe('LoginView registration states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeState.query = {};
  });

  it('verifiedQueryShowsSuccessBanner', () => {
    routeState.query = { verified: '1' };

    const wrapper = mountLogin();

    expect(wrapper.text()).toContain('邮箱验证成功，请登录。');
  });

  it('emailVerificationRequiredShowsVerificationPrompt', async () => {
    mockLogin.mockRejectedValue(
      new ApiError(403, 'Email verification is required.', 'EMAIL_VERIFICATION_REQUIRED'),
    );
    const wrapper = mountLogin();
    await fillLogin(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('该账号需要先完成邮箱验证。');
    expect(wrapper.text()).toContain('重新发送验证邮件');
  });

  it('registeredUserIgnoresStudentRedirectAfterLogin', async () => {
    routeState.query = { redirect: '/student' };
    mockLogin.mockResolvedValue(registeredContext());
    const wrapper = mountLogin();
    await fillLogin(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(mockReplace).toHaveBeenCalledWith('/onboarding');
  });

  it('registeredUserIgnoresAdminRedirectAfterLogin', async () => {
    routeState.query = { redirect: '/admin' };
    mockLogin.mockResolvedValue(registeredContext());
    const wrapper = mountLogin();
    await fillLogin(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(mockReplace).toHaveBeenCalledWith('/onboarding');
  });
});
