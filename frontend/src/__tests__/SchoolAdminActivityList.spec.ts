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
    id: '11111111-1111-4111-8111-111111111111',
    schoolId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    title: '数学挑战赛',
    startTime: '2026-09-01T08:00:00Z',
    endTime: '2026-09-02T17:00:00Z',
    location: '体育馆',
    executionStatus: 'DRAFT',
    publicStatus: 'NOT_SUBMITTED',
    ...overrides,
  };
}

function mockPage(items: SchoolAdminActivityItem[], overrides: Partial<PageResponse<SchoolAdminActivityItem>> = {}): PageResponse<SchoolAdminActivityItem> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: Math.max(1, Math.ceil(items.length / 20)), hasNext: false, ...overrides };
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.restoreAllMocks();
});

describe('SchoolAdminActivityList', () => {
  it('renders activity items', async () => {
    vi.spyOn(api, 'fetchActivities').mockResolvedValue(
      mockPage([sampleItem(), sampleItem({ id: '22222222-2222-4222-8222-222222222222', title: '英语竞赛' })])
    );
    const router = makeRouter(); await router.push('/school-admin/activities'); await router.isReady();
    const wrapper = mount(SchoolAdminActivityList, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    expect(wrapper.text()).toContain('数学挑战赛');
    expect(wrapper.text()).toContain('英语竞赛');
    wrapper.unmount();
  });

  it('listRetryCallsApiAgainAndRendersResult', async () => {
    let callCount = 0;
    const spy = vi.spyOn(api, 'fetchActivities').mockImplementation(async () => {
      callCount++;
      if (callCount === 1) throw new ApiError(500, '服务器错误');
      return mockPage([sampleItem({ title: 'Retry Success' })]);
    });
    const router = makeRouter(); await router.push('/school-admin/activities'); await router.isReady();
    const wrapper = mount(SchoolAdminActivityList, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    expect(wrapper.text()).toContain('加载失败');
    await wrapper.find('.el-result .el-button').trigger('click');
    await flushPromises();
    expect(spy).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Retry Success');
    wrapper.unmount();
  });

  it('listRestoresFiltersFromRoute', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));
    const router = makeRouter(); await router.push('/school-admin/activities?executionStatus=DRAFT&keyword=math&page=1'); await router.isReady();
    const wrapper = mount(SchoolAdminActivityList, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    expect(spy).toHaveBeenCalledWith(expect.objectContaining({ executionStatus: 'DRAFT', keyword: 'math' }), 0, 20);
    wrapper.unmount();
  });

  it('listWritesFiltersToRoute', async () => {
    vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));
    const router = makeRouter(); await router.push('/school-admin/activities'); await router.isReady();
    const wrapper = mount(SchoolAdminActivityList, {
      global: { plugins: [router, createPinia(), ElementPlus], stubs: { teleport: true } },
    });
    await flushPromises();
    const statusSelect = wrapper.find('.filter .el-select__wrapper');
    expect(statusSelect.exists()).toBe(true);
    await statusSelect.trigger('click');
    await flushPromises();
    const options = wrapper.findAll('.el-select-dropdown__item');
    expect(options.length).toBeGreaterThan(0);
    const draftOption = options.find(o => o.text() === '草稿');
    expect(draftOption).toBeDefined();
    await draftOption!.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query.executionStatus).toBe('DRAFT');
    wrapper.unmount();
  });

  it('listPaginationUsesZeroBasedApiPage', async () => {
    const spy = vi.spyOn(api, 'fetchActivities').mockResolvedValue(mockPage([]));
    const router = makeRouter(); await router.push('/school-admin/activities?page=2'); await router.isReady();
    const wrapper = mount(SchoolAdminActivityList, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    expect(spy).toHaveBeenCalledWith(expect.anything(), 1, 20);
    wrapper.unmount();
  });
});
