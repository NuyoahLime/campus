import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '@/api/http';
import * as api from '@/api/student-achievement';
import type { StudentAchievementDetail as Detail } from '@/types/student-achievement';
import StudentAchievementDetail from '@/views/workbench/StudentAchievementDetail.vue';

const { routerPush, routeParams } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  routeParams: { recordId: '11111111-1111-4111-8111-111111111111' },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => ({ params: routeParams }),
}));

const CODE = 'a'.repeat(32);
const clipboardWrite = vi.fn();
let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function detail(overrides: Partial<Detail> = {}): Detail {
  return {
    recordId: routeParams.recordId,
    recordTitle: '校园跳绳赛 · 一分钟跳绳 · 第1名',
    schoolName: '第一中学',
    activityTitle: '校园跳绳赛',
    projectName: '一分钟跳绳',
    rankingVersionNumber: 2,
    rankPosition: 1,
    scoreDisplayValue: '188',
    scoreStorageType: 'INTEGER',
    verificationCode: CODE,
    status: 'ACTIVE',
    issuedAt: '2026-07-30T08:00:00Z',
    revokedAt: null,
    rankingVersionId: 'version-2',
    activityProjectId: 'project-1',
    revocationReason: null,
    ...overrides,
  };
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(StudentAchievementDetail, {
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
    document.body.querySelectorAll('.el-message').forEach(node => node.remove());
  }
}

beforeEach(() => {
  vi.restoreAllMocks();
  routerPush.mockReset();
  clipboardWrite.mockReset();
  clipboardWrite.mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWrite },
  });
  unhandledErrors = [];
  rejectionListener = event => unhandledErrors.push(event.reason);
  errorListener = event => unhandledErrors.push(event.error ?? event.message);
  window.addEventListener('unhandledrejection', rejectionListener);
  window.addEventListener('error', errorListener);
});

afterEach(() => {
  window.removeEventListener('unhandledrejection', rejectionListener);
  window.removeEventListener('error', errorListener);
  document.body.querySelectorAll('.el-message').forEach(node => node.remove());
  expect(unhandledErrors).toHaveLength(0);
});

describe('StudentAchievementDetail', () => {
  it('detailLoadsOwnRecord', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecord').mockResolvedValue(detail());
    await withMounted(async wrapper => {
      expect(api.fetchMyAchievementRecord).toHaveBeenCalledWith(
        routeParams.recordId,
      );
      expect(wrapper.text()).toContain('校园跳绳赛');
      expect(wrapper.text()).toContain('第1名');
      expect(wrapper.text()).toContain('该成就记录当前有效');
    });
  });

  it('detailShowsRevocationReason', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecord').mockResolvedValue(detail({
      status: 'REVOKED',
      revokedAt: '2026-07-31T08:00:00Z',
      revocationReason: '排名撤回',
    }));
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('该成就记录已撤销');
      expect(wrapper.text()).toContain('排名撤回');
    });
  });

  it('copyVerificationCodeWorks', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecord').mockResolvedValue(detail());
    await withMounted(async wrapper => {
      const copy = wrapper.findAll('button')
        .find(button => button.text().includes('复制验证码'));
      await copy?.trigger('click');
      expect(clipboardWrite).toHaveBeenCalledWith(CODE);
    });
  });

  it('verificationRouteUsesCode', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecord').mockResolvedValue(detail());
    await withMounted(async wrapper => {
      const open = wrapper.findAll('button')
        .find(button => button.text().includes('打开公开验真页'));
      await open?.trigger('click');
      expect(routerPush).toHaveBeenCalledWith(
        `/achievements/verify/${CODE}`,
      );
    });
  });

  it('otherStudentsRecordLooksNotFound', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecord').mockRejectedValue(
      new ApiError(404, 'not found'),
    );
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('未找到成就记录');
      expect(wrapper.text()).not.toContain('issuedBy');
    });
  });
});
