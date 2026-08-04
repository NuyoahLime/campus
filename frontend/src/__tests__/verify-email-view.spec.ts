import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import VerifyEmailView from '@/views/VerifyEmailView.vue';

const mockVerifyEmail = vi.hoisted(() => vi.fn());
const mockReplace = vi.hoisted(() => vi.fn());
const mockPush = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({ query: {} as Record<string, unknown> }));

vi.mock('@/api/registration', () => ({
  verifyEmail: (...args: unknown[]) => mockVerifyEmail(...args),
}));

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ replace: mockReplace, push: mockPush }),
}));

function mountVerify() {
  return mount(VerifyEmailView, {
    global: {
      plugins: [ElementPlus],
      stubs: { PublicLayout: { template: '<div><slot /></div>' } },
    },
  });
}

describe('VerifyEmailView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeState.query = {};
    mockReplace.mockResolvedValue(undefined);
    localStorage.clear();
    sessionStorage.clear();
  });

  it('verificationTokenIsReadOnceAndRemovedFromAddressBar', async () => {
    routeState.query = { token: 'raw-token' };
    mockVerifyEmail.mockResolvedValue(undefined);

    mountVerify();
    await flushPromises();

    expect(mockVerifyEmail).toHaveBeenCalledTimes(1);
    expect(mockVerifyEmail).toHaveBeenCalledWith('raw-token');
    expect(mockReplace).toHaveBeenCalledWith({ path: '/verify-email', query: {} });
  });

  it('verificationTokenIsNeverStored', async () => {
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
    routeState.query = { token: 'secret-token' };
    mockVerifyEmail.mockResolvedValue(undefined);

    mountVerify();
    await flushPromises();

    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();
    expect(storageSpy).not.toHaveBeenCalledWith(expect.stringContaining('token'), expect.anything());
  });

  it('validTokenShowsSuccess', async () => {
    routeState.query = { token: 'valid-token' };
    mockVerifyEmail.mockResolvedValue(undefined);

    const wrapper = mountVerify();
    await flushPromises();

    expect(wrapper.text()).toContain('邮箱验证成功');
    expect(wrapper.text()).toContain('前往登录');
  });

  it('invalidTokenShowsGenericFailure', async () => {
    routeState.query = { token: 'bad-token' };
    mockVerifyEmail.mockRejectedValue(new Error('used token'));

    const wrapper = mountVerify();
    await flushPromises();

    expect(wrapper.text()).toContain('验证链接无效或已过期');
    expect(wrapper.text()).not.toContain('used token');
  });

  it('missingTokenShowsGenericFailureWithoutApiCall', async () => {
    routeState.query = {};

    const wrapper = mountVerify();
    await flushPromises();

    expect(mockVerifyEmail).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('验证链接无效或已过期');
  });

  it('routeReplacementDoesNotCauseSecondApiCall', async () => {
    routeState.query = { token: 'one-shot-token' };
    mockVerifyEmail.mockResolvedValue(undefined);

    mountVerify();
    await flushPromises();
    await flushPromises();

    expect(mockVerifyEmail).toHaveBeenCalledTimes(1);
  });
});
