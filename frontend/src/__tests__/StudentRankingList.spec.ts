import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import * as api from '@/api/student-ranking';
import type { StudentRankingProjectItem } from '@/types/student-ranking';
import StudentRankingList from '@/views/workbench/StudentRankingList.vue';

const { routerPush } = vi.hoisted(() => ({
  routerPush: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
}));

const PROJECT_ID = '11111111-1111-4111-8111-111111111111';

let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function project(
  overrides: Partial<StudentRankingProjectItem> = {},
): StudentRankingProjectItem {
  return {
    activityProjectId: PROJECT_ID,
    activityId: 'activity-1',
    activityTitle: '校园跳绳赛',
    schoolId: 'school-1',
    schoolName: '第一中学',
    executionStatus: 'ENDED',
    projectId: 'project-1',
    projectName: '一分钟跳绳',
    scoreStorageType: 'INTEGER',
    scoreUnit: '次',
    comparisonDirection: 'HIGHER_BETTER',
    rankingAvailability: 'CURRENT',
    currentVersionNumber: 2,
    publishedAt: '2026-07-30T08:00:00Z',
    totalRanked: 21,
    myRank: 3,
    myScoreDisplayValue: '188',
    ...overrides,
  };
}

function page(
  items: StudentRankingProjectItem[] = [project()],
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

function mockList(
  items: StudentRankingProjectItem[] = [project()],
  total = items.length,
) {
  return vi
    .spyOn(api, 'fetchStudentRankingProjects')
    .mockResolvedValue(page(items, total));
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
  const wrapper = mount(StudentRankingList, {
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
    await nextTick();
    await flushPromises();
    host.remove();
    cleanupOverlays();
  }
}

async function chooseOption(selectTestId: string, label: string) {
  const select = document.body.querySelector<HTMLElement>(
    `[data-testid="${selectTestId}"] .el-select__wrapper`,
  );
  expect(select).not.toBeNull();
  select?.click();
  await nextTick();
  await flushPromises();
  const option = Array.from(
    document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
  ).find(element => element.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
}

beforeEach(() => {
  vi.restoreAllMocks();
  routerPush.mockReset();
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

describe('StudentRankingList', () => {
  it('rankingProjectListLoads', async () => {
    mockList();
    await withMounted(async wrapper => {
      expect(api.fetchStudentRankingProjects).toHaveBeenCalledWith(
        { executionStatus: '', rankingAvailability: '', keyword: '' },
        0,
        20,
      );
      expect(wrapper.text()).toContain('校园跳绳赛');
      expect(wrapper.text()).toContain('第一中学');
    });
  });

  it('activityStatusFilterWorks', async () => {
    mockList();
    await withMounted(async wrapper => {
      await chooseOption('execution-status-filter', '已结束');
      await wrapper.get('[data-testid="ranking-search"]').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        { executionStatus: 'ENDED', rankingAvailability: '', keyword: '' },
        0,
        20,
      );
    });
  });

  it('rankingAvailabilityFilterWorks', async () => {
    mockList();
    await withMounted(async wrapper => {
      await chooseOption('ranking-availability-filter', '排名已撤回');
      await wrapper.get('[data-testid="ranking-search"]').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        { executionStatus: '', rankingAvailability: 'WITHDRAWN', keyword: '' },
        0,
        20,
      );
    });
  });

  it('keywordIsTrimmed', async () => {
    mockList();
    await withMounted(async wrapper => {
      await wrapper.get('[data-testid="ranking-keyword"]')
        .setValue('  跳绳  ');
      await wrapper.get('[data-testid="ranking-search"]').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        { executionStatus: '', rankingAvailability: '', keyword: '跳绳' },
        0,
        20,
      );
    });
  });

  it('searchResetsToFirstPage', async () => {
    mockList([project()], 41);
    await withMounted(async wrapper => {
      await wrapper.get('.btn-next').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
      );
      await wrapper.get('[data-testid="ranking-search"]').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
      );
    });
  });

  it('resetClearsFilters', async () => {
    mockList();
    await withMounted(async wrapper => {
      await chooseOption('execution-status-filter', '已结束');
      await wrapper.get('[data-testid="ranking-keyword"]')
        .setValue('跳绳');
      await wrapper.get('[data-testid="ranking-reset"]').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        { executionStatus: '', rankingAvailability: '', keyword: '' },
        0,
        20,
      );
    });
  });

  it('paginationWorks', async () => {
    mockList([project()], 41);
    await withMounted(async wrapper => {
      await wrapper.get('.btn-next').trigger('click');
      await flushPromises();
      expect(api.fetchStudentRankingProjects).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
      );
    });
  });

  it('currentRankingShowsViewAction', async () => {
    mockList();
    await withMounted(async wrapper => {
      await wrapper.get(`[data-testid="view-ranking-${PROJECT_ID}"]`)
        .trigger('click');
      expect(routerPush).toHaveBeenCalledWith(
        `/student/rankings/${PROJECT_ID}`,
      );
    });
  });

  it('unpublishedRankingShowsUnavailableState', async () => {
    mockList([project({ rankingAvailability: 'NOT_PUBLISHED' })]);
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('尚未发布');
      expect(wrapper.find(`[data-testid="view-ranking-${PROJECT_ID}"]`).exists())
        .toBe(false);
    });
  });

  it('withdrawnRankingShowsWithdrawnState', async () => {
    mockList([project({ rankingAvailability: 'WITHDRAWN' })]);
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('排名已撤回');
      expect(wrapper.find(`[data-testid="view-ranking-${PROJECT_ID}"]`).exists())
        .toBe(false);
    });
  });

  it('disabledRankingShowsDisabledState', async () => {
    mockList([
      project({
        rankingAvailability: 'DISABLED',
        comparisonDirection: 'NO_RANKING',
      }),
    ]);
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('该项目不参与排名');
      expect(wrapper.find(`[data-testid="view-ranking-${PROJECT_ID}"]`).exists())
        .toBe(false);
    });
  });

  it('unrankedStudentShowsNotRanked', async () => {
    mockList([project({ myRank: null, myScoreDisplayValue: null })]);
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('暂未上榜');
    });
  });

  it('listFailureShowsRetry', async () => {
    vi.spyOn(api, 'fetchStudentRankingProjects')
      .mockRejectedValueOnce(new Error('failed'))
      .mockResolvedValue(page());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('排名项目加载失败');
      await wrapper.get('[data-testid="ranking-list-retry"]').trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain('校园跳绳赛');
      expect(api.fetchStudentRankingProjects).toHaveBeenCalledTimes(2);
    });
  });
});
