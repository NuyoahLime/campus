import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import VerifyEmailPendingView from '@/views/VerifyEmailPendingView.vue';

const mockResendVerification = vi.hoisted(() => vi.fn());
const mockPush = vi.hoisted(() => vi.fn());

vi.mock('@/api/registration', () => ({
  resendVerification: (...args: unknown[]) => mockResendVerification(...args),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}));

function mountPending() {
  return mount(VerifyEmailPendingView, {
    global: {
      plugins: [ElementPlus],
      stubs: { PublicLayout: { template: '<div><slot /></div>' } },
    },
  });
}

describe('VerifyEmailPendingView', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('resendRequiresEmail', async () => {
    const wrapper = mountPending();

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(mockResendVerification).not.toHaveBeenCalled();
  });

  it('resendAlwaysShowsGenericMessageAndDoesNotClaimAccountExists', async () => {
    mockResendVerification.mockResolvedValue({
      message: 'If an unverified account exists, a verification email will be sent.',
    });
    const wrapper = mountPending();
    await wrapper.find('input').setValue('user@example.com');

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(mockResendVerification).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('If an unverified account exists');
    expect(wrapper.text()).not.toContain('该邮箱不存在');
    expect(wrapper.text()).not.toContain('已找到对应账号');
  });

  it('resendButtonHasLocalCooldown', async () => {
    vi.useFakeTimers();
    mockResendVerification.mockResolvedValue({
      message: 'If an unverified account exists, a verification email will be sent.',
    });
    const wrapper = mountPending();
    await wrapper.find('input').setValue('user@example.com');

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined();
    expect(wrapper.text()).toContain('60 秒后可重新发送');

    vi.advanceTimersByTime(60_000);
    await flushPromises();

    expect(wrapper.text()).toContain('重新发送验证邮件');
  });
});
