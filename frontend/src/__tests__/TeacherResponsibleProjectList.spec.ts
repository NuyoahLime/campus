import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import ElementPlus from 'element-plus';
import TeacherResponsibleProjectList from '@/views/workbench/TeacherResponsibleProjectList.vue';
import * as projectApi from '@/api/teacher-responsible-project';
import { ApiError } from '@/api/http';
import type { TeacherResponsibleProjectItem } from '@/types/teacher-responsible-project';

let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function project(
  overrides: Partial<TeacherResponsibleProjectItem> = {},
): TeacherResponsibleProjectItem {
  return {
    activityProjectId: '11111111-1111-4111-8111-111111111111',
    activityId: '22222222-2222-4222-8222-222222222222',
    activityTitle: '春季校运会',
    schoolId: '33333333-3333-4333-8333-333333333333',
    schoolName: '第一中学',
    executionStatus: 'IN_PROGRESS',
    startTime: '2026-07-30T08:00:00Z',
    endTime: '2026-07-30T10:00:00Z',
    location: '田径场',
    projectId: '44444444-4444-4444-8444-444444444444',
    projectName: '跳绳',
    category: 'SPORT',
    scoreStorageType: 'INTEGER',
    scoreUnit: '次',
    decimalPlaces: 0,
    gradeOrder: null,
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    participantCount: 20,
    enteredAttemptCount: 8,
    pendingReviewCount: 2,
    rejectedCount: 1,
    ...overrides,
  };
}

function page(items: TeacherResponsibleProjectItem[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false,
  };
}

function cleanupOverlays(): void {
  document.body
    .querySelectorAll('.el-overlay,.el-popper-container,.el-select__popper')
    .forEach((element) => element.remove());
}

async function withMounted(
  run: (
    wrapper: ReturnType<typeof mount>,
    router: ReturnType<typeof createRouter>,
  ) => Promise<void>,
): Promise<void> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/teacher/responsible', component: TeacherResponsibleProjectList },
      { path: '/teacher/responsible/:id', component: { template: '<div />' } },
    ],
  });
  await router.push('/teacher/responsible');
  await router.isReady();
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(TeacherResponsibleProjectList, {
    attachTo: host,
    global: { plugins: [router, ElementPlus] },
  });
  await flushPromises();
  try {
    await run(wrapper, router);
  } finally {
    wrapper.unmount();
    await nextTick();
    await flushPromises();
    host.remove();
    cleanupOverlays();
  }
}

async function chooseStatus(label: string): Promise<void> {
  const select = document.body.querySelector<HTMLElement>(
    '.execution-status-filter .el-select__wrapper',
  );
  select?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
  const option = Array.from(
    document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
  ).find((candidate) => candidate.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await flushPromises();
}

beforeEach(() => {
  vi.restoreAllMocks();
  unhandledErrors = [];
  rejectionListener = (event) => unhandledErrors.push(event.reason);
  errorListener = (event) => unhandledErrors.push(event.error ?? event.message);
  window.addEventListener('unhandledrejection', rejectionListener);
  window.addEventListener('error', errorListener);
});

afterEach(() => {
  window.removeEventListener('unhandledrejection', rejectionListener);
  window.removeEventListener('error', errorListener);
  cleanupOverlays();
  expect(unhandledErrors).toHaveLength(0);
});

describe('TeacherResponsibleProjectList', () => {
  it('responsibleProjectsLoad', async () => {
    vi.spyOn(projectApi, 'fetchTeacherResponsibleProjects').mockResolvedValue(
      page([project()]),
    );
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain('我的负责项目');
      expect(wrapper.text()).toContain('第一中学');
      expect(wrapper.text()).toContain('春季校运会');
      expect(wrapper.text()).toContain('跳绳');
      expect(wrapper.text()).toContain('20');
    });
  });

  it('projectFiltersSendCorrectParameters', async () => {
    const fetch = vi
      .spyOn(projectApi, 'fetchTeacherResponsibleProjects')
      .mockResolvedValue(page([project()]));
    await withMounted(async (wrapper) => {
      await chooseStatus('进行中');
      await wrapper.find('.keyword-filter input').setValue('  跳绳  ');
      await wrapper.find('.search-button').trigger('click');
      await flushPromises();
      expect(fetch).toHaveBeenLastCalledWith(
        { executionStatus: 'IN_PROGRESS', keyword: '  跳绳  ' },
        0,
        20,
      );
    });
  });

  it('projectPaginationWorks', async () => {
    const fetch = vi
      .spyOn(projectApi, 'fetchTeacherResponsibleProjects')
      .mockResolvedValue({
        ...page([project()]),
        totalElements: 21,
        totalPages: 2,
        hasNext: true,
      });
    await withMounted(async (wrapper) => {
      const next = wrapper.find('.el-pagination .btn-next');
      await next.trigger('click');
      await flushPromises();
      expect(fetch).toHaveBeenLastCalledWith(
        { executionStatus: undefined, keyword: '' },
        1,
        20,
      );
    });
  });

  it('projectFailureShowsRetry', async () => {
    const fetch = vi
      .spyOn(projectApi, 'fetchTeacherResponsibleProjects')
      .mockRejectedValueOnce(new ApiError(500, '暂时不可用'))
      .mockResolvedValueOnce(page([project()]));
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain('暂时不可用');
      await wrapper.find('.list-retry').trigger('click');
      await flushPromises();
      expect(fetch).toHaveBeenCalledTimes(2);
      expect(wrapper.text()).toContain('第一中学');
    });
  });
});
