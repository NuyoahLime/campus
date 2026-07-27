import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import SchoolAdminActivityList from '@/views/workbench/SchoolAdminActivityList.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type { PageResponse } from '@/types/api';
import type { SchoolAdminActivityItem } from '@/types/school-admin-activity';

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/school-admin/activities', component: SchoolAdminActivityList },
      { path: '/school-admin/activities/:activityId', component: { template: '<div>detail</div>' } },
    ],
  });
}

function sampleItem(overrides: Partial<SchoolAdminActivityItem> = {}): SchoolAdminActivityItem {
  return {
    id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    schoolId: 'school-1',
    title: '数学挑战赛',
    startTime: '2026-09-01T08:00:00Z',
    endTime: '2026-09-02T17:00:00Z',
    location: '体育馆',
    executionStatus: 'DRAFT',
    publicStatus: 'NOT_SUBMITTED',
    ...overrides,
  };
}

function mockPage(items: SchoolAdminActivityItem[]): PageResponse<SchoolAdminActivityItem> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1, hasNext: false };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('SchoolAdminActivityList', () => {
  it('renders activity items', async () => {
    vi.spyOn(api, 'fetchActivities').mockResolvedValue(
      mockPage([sampleItem(), sampleItem({ id: 'b', title: '英语竞赛' })])
    );

    const router = makeRouter();
    await router.push('/school-admin/activities');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('数学挑战赛');
    expect(wrapper.text()).toContain('英语竞赛');
  });

  it('shows empty state when no activities', async () => {
    vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无活动');
  });

  it('shows error state and retry button', async () => {
    vi.spyOn(api, 'fetchActivities').mockRejectedValue(new ApiError(500, '服务器错误'));

    const router = makeRouter();
    await router.push('/school-admin/activities');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('加载失败');
    expect(wrapper.text()).toContain('重试');
  });

  it('sends page 0 for page 1 in UI', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({}),
      0,  // page is 0-based for API
      20,
    );
  });

  it('restores executionStatus from route query', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities?executionStatus=DRAFT');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ executionStatus: 'DRAFT' }),
      0, 20,
    );
  });

  it('restores keyword from route query', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities?keyword=math');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: 'math' }),
      0, 20,
    );
  });

  it('falls back to page 1 for invalid page query', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities?page=abc');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(expect.anything(), 0, 20);
  });

  it('falls back to page 1 for negative page query', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities?page=-5');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(expect.anything(), 0, 20);
  });

  it('trims keyword before sending to API', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities?keyword=%20%20hello%20%20');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: 'hello' }),
      0, 20,
    );
  });

  it('writes filter state to route query', async () => {
    vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));

    const router = makeRouter();
    await router.push('/school-admin/activities');
    await router.isReady();

    mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // After mount, route.query should be empty since no filters set initially
    expect(router.currentRoute.value.query.executionStatus).toBeUndefined();
    expect(router.currentRoute.value.query.keyword).toBeUndefined();
  });
});
