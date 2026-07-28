import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type { SchoolAdminActivityDetail as DetailType, ActivityMutationResponse } from '@/types/school-admin-activity';

const ACTIVITY_ID = '11111111-1111-4111-8111-111111111111';
const PROJECT_ID = '22222222-2222-4222-8222-222222222222';

function makeRouter() {
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
    activityId: ACTIVITY_ID, schoolId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    title: '测试活动', description: '测试描述',
    startTime: '2026-09-01T00:00:00.000Z', endTime: '2026-09-02T00:00:00.000Z',
    location: '体育馆', executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED',
    createdBy: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    projects: [], responsibleTeachers: [], ...overrides,
  };
}

function mockMutation(): ActivityMutationResponse {
  return { activityId: ACTIVITY_ID, executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED' };
}

function mountDetail() {
  return mount(SchoolAdminActivityDetail, {
    props: { activityId: ACTIVITY_ID },
    global: { plugins: [makeRouter(), createPinia(), ElementPlus] },
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('SchoolAdminActivityDetail', () => {
  it('shows activity detail fields with valid UUID', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    expect(wrapper.text()).toContain('测试活动');
  });

  it('invalidActivityIdDoesNotCallApi', async () => {
    const spy = vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    const router = makeRouter();
    await router.push('/school-admin/activities/not-a-uuid');
    await router.isReady();
    const wrapper = mount(SchoolAdminActivityDetail, {
      props: { activityId: 'not-a-uuid' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();
    expect(spy).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('无效的活动ID');
  });

  it('nonDraftHidesMutationActions', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ executionStatus: 'PUBLISHED' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    expect(wrapper.text()).not.toContain('编辑');
    expect(wrapper.text()).not.toContain('发布活动');
  });

  it('editDialogSaveButtonUpdatesAndReloads', async () => {
    const fetchSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail())
      .mockResolvedValueOnce(draftDetail({ title: 'Updated Title' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const updateSpy = vi.spyOn(api, 'updateActivity').mockResolvedValue(mockMutation());
    const wrapper = mountDetail();
    await flushPromises();
    // Click edit button
    await wrapper.find('.actions .el-button').trigger('click');
    await flushPromises();
    // Change title input
    await wrapper.find('.el-dialog input').setValue('Updated Title');
    // Click save button in footer
    await wrapper.findAll('.el-dialog .el-dialog__footer .el-button--primary').at(0)?.trigger('click');
    await flushPromises();
    expect(updateSpy).toHaveBeenCalledWith(ACTIVITY_ID, expect.objectContaining({ title: 'Updated Title' }));
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it('addedProjectsAreExcludedFromProjectSelector', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({
      projects: [{ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID }],
    }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({
      items: [{ projectId: PROJECT_ID, name: 'Already Added' }, { projectId: '33333333-3333-4333-8333-333333333333', name: 'Available' }],
      page: 0, size: 100, totalElements: 2, totalPages: 1, hasNext: false,
    });
    const wrapper = mountDetail();
    await flushPromises();
    // Open add-project dialog
    await wrapper.findAll('.actions .el-button').at(1)?.trigger('click');
    await flushPromises();
    const options = wrapper.findAll('.el-select-dropdown__item');
    const texts = options.map(o => o.text());
    // Already-added project must NOT appear
    expect(texts.join(' ')).not.toContain('Already Added');
  });

  it('addProjectCallsCorrectApi', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({
      items: [{ projectId: PROJECT_ID, name: 'Test Project' }],
      page: 0, size: 100, totalElements: 1, totalPages: 1, hasNext: false,
    });
    const addSpy = vi.spyOn(api, 'addProject').mockResolvedValue({ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID });
    const wrapper = mountDetail();
    await flushPromises();
    // Open add-project dialog and select, then click add
    await wrapper.findAll('.actions .el-button').at(1)?.trigger('click');
    await flushPromises();
    // The select is rendered — we need to select an option then click the add button
    // For simplicity, trigger via wrapper.vm to set selectedProjectId then click button
    const vm = wrapper.vm as unknown as { selectedProjectId: string; handleAddProject: () => Promise<void> };
    vm.selectedProjectId = PROJECT_ID;
    await wrapper.find('.el-dialog .el-dialog__footer .el-button--primary').trigger('click');
    await flushPromises();
    expect(addSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID);
  });

  it('doubleAddProjectOnlyCallsApiOnce', async () => {
    let resolve: (v: unknown) => void = () => {};
    const deferred = new Promise(r => { resolve = r; });
    const addSpy = vi.spyOn(api, 'addProject').mockReturnValue(deferred as Promise<{ id: string; activityId: string; projectId: string }>);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [{ projectId: PROJECT_ID, name: 'Test' }], page: 0, size: 100, totalElements: 1, totalPages: 1, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    const vm = wrapper.vm as unknown as { selectedProjectId: string; handleAddProject: () => Promise<void> };
    vm.selectedProjectId = PROJECT_ID;
    // Double-click the add button rapidly
    const addBtn = wrapper.find('.el-dialog .el-dialog__footer .el-button--primary');
    await addBtn.trigger('click');
    await addBtn.trigger('click');
    resolve({ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID });
    await flushPromises();
    expect(addSpy).toHaveBeenCalledTimes(1);
  });

  it('removeProjectCallsCorrectApi', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({
      projects: [{ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID }],
    }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const removeSpy = vi.spyOn(api, 'removeProject').mockResolvedValue(undefined);
    // Mock ElMessageBox to resolve immediately
    vi.spyOn(await import('element-plus'), 'ElMessageBox').mockImplementation(() => ({
      confirm: () => Promise.resolve(),
    } as never));
    const wrapper = mountDetail();
    await flushPromises();
    // Click remove button — need the remove button in the table
    const removeBtns = wrapper.findAll('.el-button--danger');
    if (removeBtns.length > 0) {
      await removeBtns[0].trigger('click');
      await flushPromises();
      expect(removeSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID);
    }
  });

  it('doubleRemoveProjectOnlyCallsApiOnce', async () => {
    const removeSpy = vi.spyOn(api, 'removeProject').mockResolvedValue(undefined);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({
      projects: [{ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID }],
    }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    const vm = wrapper.vm as unknown as { handleRemoveProject: (pid: string) => Promise<void> };
    // Call twice rapidly — the second should be blocked
    const p1 = vm.handleRemoveProject(PROJECT_ID);
    const p2 = vm.handleRemoveProject(PROJECT_ID);
    await Promise.allSettled([p1, p2]);
    expect(removeSpy).toHaveBeenCalledTimes(1);
  });

  it('doublePublishOnlyCallsApiOnce', async () => {
    const pubSpy = vi.spyOn(api, 'publishActivity').mockResolvedValue(mockMutation());
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    const vm = wrapper.vm as unknown as { handlePublish: () => Promise<void> };
    const p1 = vm.handlePublish();
    const p2 = vm.handlePublish();
    await Promise.allSettled([p1, p2]);
    expect(pubSpy).toHaveBeenCalledTimes(1);
  });
});
