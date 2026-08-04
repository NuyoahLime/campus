import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import RegisterView from '@/views/RegisterView.vue';
import { ApiError } from '@/api/http';

const mockRegister = vi.hoisted(() => vi.fn());
const mockPush = vi.hoisted(() => vi.fn());
const mockLogin = vi.hoisted(() => vi.fn());

vi.mock('@/api/registration', () => ({
  register: (...args: unknown[]) => mockRegister(...args),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: mockLogin }),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
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

function mountRegister() {
  return mount(RegisterView, {
    global: {
      plugins: [ElementPlus],
      stubs: { PublicLayout: { template: '<div><slot /></div>' } },
    },
  });
}

async function fillValidForm(wrapper: ReturnType<typeof mountRegister>) {
  const inputs = wrapper.findAll('input');
  await inputs[0].setValue('public-user');
  await inputs[1].setValue('user@example.com');
  await inputs[2].setValue('Example123!');
  await inputs[3].setValue('Example123!');
}

describe('RegisterView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('registerPageHasNoRoleSelector', () => {
    const wrapper = mountRegister();

    expect(wrapper.text()).not.toContain('角色');
    expect(wrapper.text()).not.toContain('教师');
    expect(wrapper.text()).not.toContain('学校管理员');
    expect(wrapper.text()).not.toContain('平台角色');
    expect(wrapper.text()).not.toContain('账号状态');
  });

  it('passwordsUseNewPasswordAutocomplete', () => {
    const wrapper = mountRegister();
    const passwordInputs = wrapper.findAll('input[type="password"]');

    expect(passwordInputs).toHaveLength(2);
    expect(passwordInputs[0].attributes('autocomplete')).toBe('new-password');
    expect(passwordInputs[1].attributes('autocomplete')).toBe('new-password');
  });

  it('submitDisabledWhilePending', async () => {
    let resolveRegister: (value: unknown) => void = () => {};
    mockRegister.mockReturnValue(new Promise((resolve) => { resolveRegister = resolve; }));
    const wrapper = mountRegister();
    await fillValidForm(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined();
    resolveRegister({
      username: 'public-user',
      verificationRequired: true,
      nextAction: 'VERIFY_EMAIL',
    });
    await flushPromises();
  });

  it('successfulRegistrationDoesNotLoginAndNavigatesToPendingPage', async () => {
    mockRegister.mockResolvedValue({
      username: 'public-user',
      verificationRequired: true,
      nextAction: 'VERIFY_EMAIL',
    });
    const wrapper = mountRegister();
    await fillValidForm(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(mockRegister).toHaveBeenCalledTimes(1);
    expect(mockLogin).not.toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith({
      name: 'verify-email-pending',
      state: { username: 'public-user' },
    });
  });

  it('successfulRegistrationClearsPasswordFieldsAndDoesNotPersistPassword', async () => {
    const localSpy = vi.spyOn(Storage.prototype, 'setItem');
    mockRegister.mockResolvedValue({
      username: 'public-user',
      verificationRequired: true,
      nextAction: 'VERIFY_EMAIL',
    });
    const wrapper = mountRegister();
    await fillValidForm(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    const inputs = wrapper.findAll('input');
    expect((inputs[2].element as HTMLInputElement).value).toBe('');
    expect((inputs[3].element as HTMLInputElement).value).toBe('');
    expect(localStorage.getItem('password')).toBeNull();
    expect(sessionStorage.getItem('password')).toBeNull();
    expect(localSpy).not.toHaveBeenCalledWith(expect.stringContaining('password'), expect.anything());
  });

  it('genericConflictDoesNotRevealUsernameOrEmailExistence', async () => {
    mockRegister.mockRejectedValue(new ApiError(409, 'conflict', 'REGISTRATION_UNAVAILABLE'));
    const wrapper = mountRegister();
    await fillValidForm(wrapper);

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('当前注册信息不可用，请更换后重试');
    expect(wrapper.text()).not.toContain('用户名已存在');
    expect(wrapper.text()).not.toContain('邮箱已注册');
  });
});
