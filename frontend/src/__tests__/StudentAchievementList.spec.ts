import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import * as api from '@/api/student-achievement';
import type { StudentAchievementItem } from '@/types/student-achievement';
import StudentAchievementList from '@/views/workbench/StudentAchievementList.vue';

const { routerPush } = vi.hoisted(() => ({ routerPush: vi.fn() }));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
}));

const RECORD_ID = '11111111-1111-4111-8111-111111111111';
const CODE = 'a'.repeat(32);
const clipboardWrite = vi.fn();
let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function record(
  overrides: Partial<StudentAchievementItem> = {},
): StudentAchievementItem {
  return {
    recordId: RECORD_ID,
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
    ...overrides,
  };
}

function page(
  items: StudentAchievementItem[] = [record()],
  totalElements = items.length,
) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
    hasNext: totalElements > 20,
  };
}

function cleanupOverlays() {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-select__popper,.el-tooltip__popper,.el-message',
    )
    .forEach(element => element.remove());
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(StudentAchievementList, {
    attachTo: host,
    global: { plugins: [ElementPlus] },
  });
  await flushPromises();
  try {
    await run(wrapper);
  } finally {
    await nextTick();
    await flushPromises();
    wrapper.unmount();
    host.remove();
    cleanupOverlays();
  }
}

async function chooseStatus(label: string) {
  const select = document.body.querySelector<HTMLElement>(
    '[data-testid="achievement-status-filter"] .el-select__wrapper',
  );
  select?.click();
  await nextTick();
  const option = Array.from(
    document.body.querySelectorAll<HTMLElement>(
      '.el-select-dropdown__item',
    ),
  ).find(element => element.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await flushPromises();
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
  cleanupOverlays();
  expect(unhandledErrors).toHaveLength(0);
});

describe('StudentAchievementList', () => {
  it('studentAchievementsLoad', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords').mockResolvedValue(page());
    await withMounted(async wrapper => {
      expect(api.fetchMyAchievementRecords).toHaveBeenCalledWith(
        { status: '', keyword: '' },
        0,
        20,
      );
      expect(wrapper.text()).toContain('校园跳绳赛');
      expect(wrapper.text()).toContain('第一中学');
    });
  });

  it('statusFilterWorks', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords').mockResolvedValue(page());
    await withMounted(async wrapper => {
      await chooseStatus('已撤销');
      await wrapper.get('[data-testid="achievement-search"]').trigger('click');
      await flushPromises();
      expect(api.fetchMyAchievementRecords).toHaveBeenLastCalledWith(
        { status: 'REVOKED', keyword: '' },
        0,
        20,
      );
    });
  });

  it('keywordIsTrimmed', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords').mockResolvedValue(page());
    await withMounted(async wrapper => {
      await wrapper.get('[data-testid="achievement-keyword"]')
        .setValue('  跳绳  ');
      await wrapper.get('[data-testid="achievement-search"]').trigger('click');
      await flushPromises();
      expect(api.fetchMyAchievementRecords).toHaveBeenLastCalledWith(
        { status: '', keyword: '跳绳' },
        0,
        20,
      );
    });
  });

  it('paginationWorks', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords')
      .mockResolvedValue(page([record()], 41));
    await withMounted(async wrapper => {
      await wrapper.get('.btn-next').trigger('click');
      await flushPromises();
      expect(api.fetchMyAchievementRecords).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
      );
    });
  });

  it('listFailureShowsRetry', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords')
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(page());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('成就记录加载失败');
      await wrapper.get('[data-testid="achievement-list-retry"]')
        .trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain('校园跳绳赛');
    });
  });

  it('activeAndRevokedRecordsShowTheirStatus', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords').mockResolvedValue(page([
      record(),
      record({
        recordId: 'record-2',
        status: 'REVOKED',
        revokedAt: '2026-07-31T08:00:00Z',
      }),
    ]));
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('有效');
      expect(wrapper.text()).toContain('已撤销');
    });
  });

  it('detailCopyAndVerificationActionsWork', async () => {
    vi.spyOn(api, 'fetchMyAchievementRecords').mockResolvedValue(page());
    await withMounted(async wrapper => {
      const buttons = wrapper.findAll('.el-table__body-wrapper button');
      await buttons[0].trigger('click');
      expect(routerPush).toHaveBeenCalledWith(
        `/student/achievements/${RECORD_ID}`,
      );
      await buttons[1].trigger('click');
      expect(clipboardWrite).toHaveBeenCalledWith(CODE);
      await buttons[2].trigger('click');
      expect(routerPush).toHaveBeenCalledWith(
        `/achievements/verify/${CODE}`,
      );
    });
  });
});
