import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import OnboardingView from '@/views/OnboardingView.vue';

const mockLogout = vi.hoisted(() => vi.fn());
const mockPush = vi.hoisted(() => vi.fn());

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { username: 'registered-user', primaryRole: 'REGISTERED_USER' },
    logout: mockLogout,
  }),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}));

function mountOnboarding() {
  return mount(OnboardingView, {
    global: {
      plugins: [ElementPlus],
      stubs: { PublicLayout: { template: '<div><slot /></div>' } },
    },
  });
}

describe('OnboardingView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLogout.mockResolvedValue(undefined);
  });

  it('onboardingShowsExactlyTwoApplicationPaths', () => {
    const wrapper = mountOnboarding();

    expect(wrapper.text()).toContain('申请加入学校');
    expect(wrapper.text()).toContain('申请学校入驻');
    expect(wrapper.findAll('.path-card')).toHaveLength(2);
  });

  it('onboardingHasNoTeacherApplication', () => {
    const wrapper = mountOnboarding();

    expect(wrapper.text()).not.toContain('教师申请');
    expect(wrapper.text()).not.toContain('申请成为教师');
  });

  it('fakeSubmissionBehaviorIsDisabled', () => {
    const wrapper = mountOnboarding();

    const disabledButtons = wrapper.findAll('button[disabled]');
    expect(disabledButtons.length).toBeGreaterThanOrEqual(2);
    expect(wrapper.text()).toContain('下一阶段开放');
  });

  it('registeredUserCanLogout', async () => {
    const wrapper = mountOnboarding();

    await wrapper.findAll('button').at(-1)!.trigger('click');
    await flushPromises();

    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(mockPush).toHaveBeenCalledWith('/login');
  });
});
