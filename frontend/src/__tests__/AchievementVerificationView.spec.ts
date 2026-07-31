import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '@/api/http';
import * as api from '@/api/student-achievement';
import type { PublicAchievementVerification } from '@/types/student-achievement';
import AchievementVerificationView from '@/views/AchievementVerificationView.vue';

const { routerReplace, routeParams } = vi.hoisted(() => ({
  routerReplace: vi.fn(),
  routeParams: { verificationCode: '' },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace }),
  useRoute: () => ({ params: routeParams }),
}));

const CODE = 'a'.repeat(32);
let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function verification(
  overrides: Partial<PublicAchievementVerification> = {},
): PublicAchievementVerification {
  return {
    valid: true,
    status: 'ACTIVE',
    recordTitle: '校园跳绳赛 · 一分钟跳绳 · 第1名',
    schoolName: '第一中学',
    activityTitle: '校园跳绳赛',
    projectName: '一分钟跳绳',
    rankingVersionNumber: 2,
    rankPosition: 1,
    scoreDisplayValue: '188',
    scoreStorageType: 'INTEGER',
    issuedAt: '2026-07-30T08:00:00Z',
    revokedAt: null,
    ...overrides,
  };
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(AchievementVerificationView, {
    attachTo: host,
    global: { plugins: [ElementPlus] },
  });
  await flushPromises();
  try {
    await run(wrapper);
  } finally {
    await nextTick();
    wrapper.unmount();
    host.remove();
  }
}

beforeEach(() => {
  vi.restoreAllMocks();
  routerReplace.mockReset();
  routeParams.verificationCode = '';
  unhandledErrors = [];
  rejectionListener = event => unhandledErrors.push(event.reason);
  errorListener = event => unhandledErrors.push(event.error ?? event.message);
  window.addEventListener('unhandledrejection', rejectionListener);
  window.addEventListener('error', errorListener);
});

afterEach(() => {
  window.removeEventListener('unhandledrejection', rejectionListener);
  window.removeEventListener('error', errorListener);
  expect(unhandledErrors).toHaveLength(0);
});

describe('AchievementVerificationView', () => {
  it('activeVerificationShowsValid', async () => {
    routeParams.verificationCode = CODE;
    vi.spyOn(api, 'verifyAchievementRecord')
      .mockResolvedValue(verification());
    await withMounted(async wrapper => {
      expect(api.verifyAchievementRecord).toHaveBeenCalledWith(CODE);
      expect(wrapper.text()).toContain('成就记录有效');
      expect(wrapper.text()).toContain('第一中学');
    });
  });

  it('revokedVerificationShowsInvalid', async () => {
    routeParams.verificationCode = CODE;
    vi.spyOn(api, 'verifyAchievementRecord').mockResolvedValue(verification({
      valid: false,
      status: 'REVOKED',
      revokedAt: '2026-07-31T08:00:00Z',
    }));
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('成就记录已撤销');
      expect(wrapper.text()).toContain(
        new Date('2026-07-31T08:00:00Z')
          .toLocaleString('zh-CN', { hour12: false }),
      );
    });
  });

  it('unknownVerificationShowsNotFound', async () => {
    routeParams.verificationCode = CODE;
    vi.spyOn(api, 'verifyAchievementRecord').mockRejectedValue(
      new ApiError(404, 'not found'),
    );
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('未找到对应成就记录');
    });
  });

  it('publicViewDoesNotShowStudentIdentity', async () => {
    routeParams.verificationCode = CODE;
    vi.spyOn(api, 'verifyAchievementRecord').mockResolvedValue({
      ...verification(),
      studentDisplayName: 'Secret Student',
      studentId: 'secret-id',
    } as PublicAchievementVerification);
    await withMounted(async wrapper => {
      expect(wrapper.text()).not.toContain('Secret Student');
      expect(wrapper.text()).not.toContain('secret-id');
    });
  });

  it('uppercaseCodeIsNormalized', async () => {
    routeParams.verificationCode = CODE.toUpperCase();
    vi.spyOn(api, 'verifyAchievementRecord')
      .mockResolvedValue(verification());
    await withMounted(async () => {
      expect(api.verifyAchievementRecord).toHaveBeenCalledWith(CODE);
      expect(routerReplace).toHaveBeenCalledWith(
        `/achievements/verify/${CODE}`,
      );
    });
  });

  it('malformedCodeUsesSameNotFoundStateWithoutApiCall', async () => {
    routeParams.verificationCode = 'NOT-A-CODE';
    vi.spyOn(api, 'verifyAchievementRecord')
      .mockResolvedValue(verification());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('未找到对应成就记录');
      expect(api.verifyAchievementRecord).not.toHaveBeenCalled();
    });
  });
});
