import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type { SchoolAdminActivityDetail as DetailType } from '@/types/school-admin-activity';

function makeRouter(activityId: string) {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/school-admin/activities', component: { template: '<div>list</div>' } },
      { path: '/school-admin/activities/:activityId', component: SchoolAdminActivityDetail, props: true },
    ],
  });
}

function draftDetail(overrides: Partial<DetailType> = {}): DetailType {
  return {
    activityId: 'act-1',
    schoolId: 'school-1',
    title: '测试活动',
    description: '测试描述',
    startTime: '2026-09-01T00:00:00.000Z',
    endTime: '2026-09-02T00:00:00.000Z',
    location: '体育馆',
    executionStatus: 'DRAFT',
    publicStatus: 'NOT_SUBMITTED',
    createdBy: 'user-1',
    projects: [],
    responsibleTeachers: [],
    ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('SchoolAdminActivityDetail', () => {
  it('shows activity detail fields', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('测试活动');
    expect(wrapper.text()).toContain('测试描述');
    expect(wrapper.text()).toContain('体育馆');
  });

  it('shows edit and publish buttons when DRAFT', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('编辑');
    expect(wrapper.text()).toContain('发布活动');
  });

  it('hides edit and publish buttons when not DRAFT', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ executionStatus: 'PUBLISHED' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).not.toContain('编辑');
    expect(wrapper.text()).not.toContain('发布活动');
  });

  it('shows error state on load failure', async () => {
    vi.spyOn(api, 'fetchActivity').mockRejectedValue(new ApiError(500, '服务器错误'));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('加载失败');
    expect(wrapper.text()).toContain('重试');
  });

  it('rejects time order error in edit form', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // Open edit dialog
    const vm = wrapper.vm as unknown as {
      eForm: Record<string, string>;
      openEdit: () => void;
      handleUpdate: () => Promise<void>;
    };
    vm.openEdit();
    await flushPromises();

    vm.eForm.startTime = '2026-12-31T00:00:00';
    vm.eForm.endTime = '2026-01-01T00:00:00';

    const updateSpy = vi.spyOn(api, 'updateActivity');
    await vm.handleUpdate();
    await flushPromises();

    expect(updateSpy).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('结束时间不得早于开始时间');
  });

  it('reloads projects when add-project dialog opens', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    const projSpy = vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [{ projectId: 'p1', name: 'Project A' }], page: 0, size: 100, totalElements: 1, totalPages: 1, hasNext: false });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // Initial load should have called once
    const initialCalls = projSpy.mock.calls.length;

    // Open add-project dialog via the function
    const vm = wrapper.vm as unknown as { openAddProject: () => Promise<void> };
    await vm.openAddProject();
    await flushPromises();

    // Should have called again (reloaded)
    expect(projSpy.mock.calls.length).toBeGreaterThan(initialCalls);
  });

  it('reloads detail after publish succeeds', async () => {
    vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail())
      .mockResolvedValueOnce(draftDetail({ executionStatus: 'PUBLISHED' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'publishActivity').mockResolvedValue({ activityId: 'act-1', executionStatus: 'PUBLISHED', publicStatus: 'NOT_SUBMITTED' });

    const router = makeRouter('act-1');
    await router.push('/school-admin/activities/act-1');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityDetail, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // Call publish
    const vm = wrapper.vm as unknown as { handlePublish: () => Promise<void> };
    await vm.handlePublish();
    await flushPromises();

    // fetchActivity should have been called twice (initial load + reload)
    expect(api.fetchActivity).toHaveBeenCalledTimes(2);
  });
});
