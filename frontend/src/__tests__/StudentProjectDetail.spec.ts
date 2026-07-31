import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import * as api from '@/api/student-project';
import type { StudentProjectDetail as StudentProjectDetailType } from '@/types/student-project';
import StudentProjectDetail from '@/views/workbench/StudentProjectDetail.vue';

const { routerPush, routeParams } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  routeParams: {
    activityProjectId: '11111111-1111-4111-8111-111111111111',
  },
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams }),
}));

const PROJECT_ID = '22222222-2222-4222-8222-222222222222';

function detail(): StudentProjectDetailType {
  return {
    activityProjectId: routeParams.activityProjectId,
    activityId: 'activity-1',
    activityTitle: '校园跳绳赛',
    projectId: PROJECT_ID,
    projectName: '一分钟跳绳',
    category: '体能',
    scoreStorageType: 'INTEGER',
    comparisonDirection: 'HIGHER_BETTER',
    scoreUnit: '次',
    attemptCount: 1,
    latestAttemptId: 'attempt-1',
    latestAttemptStatus: 'APPROVED',
    latestScoreDisplay: '188',
    hasApprovedScore: true,
    assignedAt: '2026-07-30T07:00:00Z',
    activityDescription: '活动说明',
    activityStartTime: '2026-07-30T07:00:00Z',
    activityEndTime: '2026-07-30T09:00:00Z',
    location: '体育馆',
    projectDescription: '项目说明',
    rulesText: '规则',
    venueRequirements: '室内',
    equipmentRequirements: '跳绳',
    effectiveScoreRule: 'BEST',
    allowTie: true,
    decimalPlaces: 0,
    gradeOrder: null,
  };
}

function cleanupOverlays() {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-tooltip__popper,.el-message',
    )
    .forEach(element => element.remove());
}

beforeEach(() => {
  vi.restoreAllMocks();
  routerPush.mockReset();
});

afterEach(() => {
  cleanupOverlays();
});

describe('StudentProjectDetail', () => {
  it('projectDetailRankingButtonUsesActivityProjectId', async () => {
    vi.spyOn(api, 'fetchMyProjectById').mockResolvedValue(detail());
    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(StudentProjectDetail, {
      attachTo: host,
      global: {
        plugins: [ElementPlus],
        mocks: {
          $router: { push: routerPush },
        },
      },
    });
    await flushPromises();
    try {
      await wrapper.get('[data-testid="project-ranking-button"]')
        .trigger('click');
      expect(routerPush).toHaveBeenCalledWith(
        `/student/rankings/${routeParams.activityProjectId}`,
      );
      expect(routerPush).not.toHaveBeenCalledWith(
        `/student/rankings/${PROJECT_ID}`,
      );
    } finally {
      await nextTick();
      await flushPromises();
      wrapper.unmount();
      host.remove();
      cleanupOverlays();
    }
  });
});
