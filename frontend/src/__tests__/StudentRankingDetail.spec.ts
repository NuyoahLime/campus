import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '@/api/http';
import * as api from '@/api/student-ranking';
import type { StudentCurrentRankingDetail } from '@/types/student-ranking';
import StudentRankingDetail from '@/views/workbench/StudentRankingDetail.vue';

const { routerPush, routeParams } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  routeParams: {
    activityProjectId: '11111111-1111-4111-8111-111111111111',
  },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => ({ params: routeParams }),
}));

const PROJECT_ID = routeParams.activityProjectId;

let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function ranking(
  overrides: Partial<StudentCurrentRankingDetail> = {},
): StudentCurrentRankingDetail {
  return {
    activityProjectId: PROJECT_ID,
    activityId: 'activity-1',
    activityTitle: '校园跳绳赛',
    schoolName: '第一中学',
    projectId: 'project-1',
    projectName: '一分钟跳绳',
    scoreStorageType: 'INTEGER',
    scoreUnit: '次',
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    tiePolicy: 'COMPETITION',
    versionNumber: 3,
    publishedAt: '2026-07-30T08:00:00Z',
    totalRanked: 3,
    myRank: 2,
    myScoreDisplayValue: '188',
    entries: [
      {
        rankPosition: 1,
        studentDisplayName: 'Zed',
        scoreDisplayValue: '199',
        isCurrentStudent: false,
      },
      {
        rankPosition: 2,
        studentDisplayName: 'Amy',
        scoreDisplayValue: '188',
        isCurrentStudent: true,
      },
      {
        rankPosition: 3,
        studentDisplayName: 'Bob',
        scoreDisplayValue: '177',
        isCurrentStudent: false,
      },
    ],
    ...overrides,
  };
}

function cleanupOverlays() {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-tooltip__popper,.el-message',
    )
    .forEach(element => element.remove());
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(StudentRankingDetail, {
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

describe('StudentRankingDetail', () => {
  it('detailLoadsCurrentSnapshot', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      expect(api.fetchStudentCurrentRanking).toHaveBeenCalledWith(PROJECT_ID);
      expect(wrapper.text()).toContain('校园跳绳赛');
      expect(wrapper.text()).toContain('一分钟跳绳');
      expect(wrapper.text()).toContain('188');
    });
  });

  it('detailKeepsBackendEntryOrder', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      const text = wrapper.find('.el-table').text();
      expect(text.indexOf('Zed')).toBeLessThan(text.indexOf('Amy'));
      expect(text.indexOf('Amy')).toBeLessThan(text.indexOf('Bob'));
    });
  });

  it('ownEntryIsHighlighted', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      const rows = wrapper.findAll('.el-table__body-wrapper tbody tr');
      expect(rows).toHaveLength(3);
      expect(rows[0].classes()).not.toContain('current-student-row');
      expect(rows[1].classes()).toContain('current-student-row');
    });
  });

  it('ownEntryShowsMeTag', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      const rows = wrapper.findAll('.el-table__body-wrapper tbody tr');
      expect(rows[0].find('.me-tag').exists()).toBe(false);
      expect(rows[1].find('.me-tag').text()).toBe('我');
    });
  });

  it('detailDoesNotCompareNamesToFindSelf', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking({
      entries: [
        {
          rankPosition: 1,
          studentDisplayName: '同名学生',
          scoreDisplayValue: '199',
          isCurrentStudent: false,
        },
        {
          rankPosition: 2,
          studentDisplayName: '同名学生',
          scoreDisplayValue: '188',
          isCurrentStudent: true,
        },
      ],
    }));
    await withMounted(async wrapper => {
      const rows = wrapper.findAll('.el-table__body-wrapper tbody tr');
      expect(rows[0].classes()).not.toContain('current-student-row');
      expect(rows[0].find('.me-tag').exists()).toBe(false);
      expect(rows[1].classes()).toContain('current-student-row');
      expect(rows[1].find('.me-tag').exists()).toBe(true);
    });
  });

  it('detailShowsTiePolicy', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('允许并列（竞赛排名）');
    });
  });

  it('detailShowsVersionAndPublishedTime', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('V3');
      expect(wrapper.text()).toContain(
        new Date('2026-07-30T08:00:00Z').toLocaleString('zh-CN'),
      );
    });
  });

  it('notFoundShowsUnavailableMessage', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking').mockRejectedValue(
      new ApiError(404, 'Ranking not found'),
    );
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('排名尚未发布或已撤回');
      expect(wrapper.text()).not.toContain('其他学校');
    });
  });

  it('detailFailureShowsRetry', async () => {
    vi.spyOn(api, 'fetchStudentCurrentRanking')
      .mockRejectedValueOnce(new ApiError(500, 'failed'))
      .mockResolvedValue(ranking());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('排名详情加载失败');
      await wrapper.get('[data-testid="ranking-detail-retry"]').trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain('校园跳绳赛');
      expect(api.fetchStudentCurrentRanking).toHaveBeenCalledTimes(2);
    });
  });
});
