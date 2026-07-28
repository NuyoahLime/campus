import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import { ElMessageBox } from 'element-plus';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import type { SchoolAdminActivityDetail as DetailType, ActivityMutationResponse } from '@/types/school-admin-activity';

const ACTIVITY_ID = '11111111-1111-4111-8111-111111111111';
const PROJECT_ID = '22222222-2222-4222-8222-222222222222';
const OTHER_PROJECT_ID = '33333333-3333-4333-8333-333333333333';

function makeRouter() {
  return createRouter({ history: createWebHistory(), routes: [
    { path: '/', component: { template: '<div>home</div>' } },
    { path: '/school-admin/activities', component: { template: '<div>list</div>' } },
    { path: '/school-admin/activities/:activityId', component: SchoolAdminActivityDetail, props: true },
  ]});
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
    global: {
      plugins: [makeRouter(), createPinia(), ElementPlus],
      stubs: { teleport: true },
    },
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.restoreAllMocks();
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never);
});

describe('SchoolAdminActivityDetail', () => {
  it('shows activity detail fields with valid UUID', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    expect(wrapper.text()).toContain('测试活动');
    wrapper.unmount();
  });

  it('invalidActivityIdDoesNotCallApi', async () => {
    const spy = vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    const router = makeRouter(); await router.push('/school-admin/activities/not-a-uuid'); await router.isReady();
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: 'not-a-uuid' }, global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    expect(spy).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('无效的活动ID');
    wrapper.unmount();
  });

  it('nonDraftHidesMutationActions', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ executionStatus: 'PUBLISHED' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    expect(wrapper.find('.actions').exists()).toBe(false);
    wrapper.unmount();
  });

  it('publishCallsCorrectApiAndReloads', async () => {
    const fetchSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail())
      .mockResolvedValueOnce(draftDetail({ executionStatus: 'PUBLISHED' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const pubSpy = vi.spyOn(api, 'publishActivity').mockResolvedValue(mockMutation());
    const wrapper = mountDetail();
    await flushPromises();
    await wrapper.find('.actions .el-button--success').trigger('click');
    await flushPromises();
    expect(pubSpy).toHaveBeenCalledWith(ACTIVITY_ID);
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('doublePublishOnlyCallsApiOnce', async () => {
    let resolve: (v: ActivityMutationResponse) => void = () => {};
    const deferred = new Promise<ActivityMutationResponse>(r => { resolve = r; });
    const pubSpy = vi.spyOn(api, 'publishActivity').mockReturnValue(deferred);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    await wrapper.find('.actions .el-button--success').trigger('click');
    await wrapper.find('.actions .el-button--success').trigger('click');
    resolve(mockMutation());
    await flushPromises();
    expect(pubSpy).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('removeProjectCallsCorrectApi', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({
      projects: [{ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID }],
    }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const removeSpy = vi.spyOn(api, 'removeProject').mockResolvedValue(undefined);
    const wrapper = mountDetail();
    await flushPromises();
    const removeBtn = wrapper.find('.el-button--danger');
    expect(removeBtn.exists()).toBe(true);
    await removeBtn.trigger('click');
    await flushPromises();
    expect(removeSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID);
    wrapper.unmount();
  });

  it('doubleRemoveProjectOnlyCallsApiOnce', async () => {
    let resolve: (v: void) => void = () => {};
    const deferred = new Promise<void>(r => { resolve = r; });
    const removeSpy = vi.spyOn(api, 'removeProject').mockReturnValue(deferred);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({
      projects: [{ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID }],
    }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mountDetail();
    await flushPromises();
    const removeBtn = wrapper.find('.el-button--danger');
    expect(removeBtn.exists()).toBe(true);
    await removeBtn.trigger('click');
    await removeBtn.trigger('click');
    resolve();
    await flushPromises();
    expect(removeSpy).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('addedProjectsAreExcludedFromProjectSelector', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({
      projects: [{ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID }],
    }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({
      items: [{ projectId: PROJECT_ID, name: 'Already Added' }, { projectId: OTHER_PROJECT_ID, name: 'Available' }],
      page: 0, size: 100, totalElements: 2, totalPages: 1, hasNext: false,
    });
    const wrapper = mountDetail();
    await flushPromises();
    await wrapper.findAll('.actions .el-button').at(1)?.trigger('click');
    await flushPromises();
    // Open the select dropdown to see real options
    const selectTrigger = wrapper.find('.el-select__wrapper');
    expect(selectTrigger.exists()).toBe(true);
    await selectTrigger.trigger('click');
    await flushPromises();
    // Collect visible option texts
    const options = wrapper.findAll('.el-select-dropdown__item');
    const optionTexts = options.map(o => o.text());
    expect(optionTexts).toContain('Available');
    expect(optionTexts).not.toContain('Already Added');
    wrapper.unmount();
  });

  it('editDialogSaveButtonUpdatesAndReloads', async () => {
    const fetchSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail())
      .mockResolvedValueOnce(draftDetail({ title: 'Updated Title' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const updateSpy = vi.spyOn(api, 'updateActivity').mockResolvedValue(mockMutation());
    const wrapper = mountDetail();
    await flushPromises();
    // Click real edit button
    await wrapper.find('.actions .el-button').trigger('click');
    await flushPromises();
    // Find the title input inside the dialog and update it
    const titleInput = wrapper.find('.el-dialog input[type="text"]');
    expect(titleInput.exists()).toBe(true);
    await titleInput.setValue('Updated Title');
    // Click real save button in the dialog footer
    const saveBtn = wrapper.find('.el-dialog .el-dialog__footer .el-button--primary');
    expect(saveBtn.exists()).toBe(true);
    await saveBtn.trigger('click');
    await flushPromises();
    expect(updateSpy).toHaveBeenCalledWith(ACTIVITY_ID, expect.objectContaining({ title: 'Updated Title' }));
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    wrapper.unmount();
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
    // Click real "添加项目" button
    await wrapper.findAll('.actions .el-button').at(1)?.trigger('click');
    await flushPromises();
    // Open the select dropdown
    const selectTrigger = wrapper.find('.el-select__wrapper');
    expect(selectTrigger.exists()).toBe(true);
    await selectTrigger.trigger('click');
    await flushPromises();
    // Click the project option in the dropdown
    const option = wrapper.find('.el-select-dropdown__item');
    expect(option.exists()).toBe(true);
    await option.trigger('click');
    await flushPromises();
    // Click real "添加" button in footer
    const addBtn = wrapper.find('.el-dialog .el-dialog__footer .el-button--primary');
    expect(addBtn.exists()).toBe(true);
    await addBtn.trigger('click');
    await flushPromises();
    expect(addSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID);
    wrapper.unmount();
  });

  it('doubleAddProjectOnlyCallsApiOnce', async () => {
    let resolve: (v: unknown) => void = () => {};
    const deferred = new Promise(r => { resolve = r; });
    const addSpy = vi.spyOn(api, 'addProject').mockReturnValue(deferred as Promise<{ id: string; activityId: string; projectId: string }>);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({
      items: [{ projectId: PROJECT_ID, name: 'Test Project' }],
      page: 0, size: 100, totalElements: 1, totalPages: 1, hasNext: false,
    });
    const wrapper = mountDetail();
    await flushPromises();
    await wrapper.findAll('.actions .el-button').at(1)?.trigger('click');
    await flushPromises();
    const selectTrigger = wrapper.find('.el-select__wrapper');
    expect(selectTrigger.exists()).toBe(true);
    await selectTrigger.trigger('click');
    await flushPromises();
    const option = wrapper.find('.el-select-dropdown__item');
    expect(option.exists()).toBe(true);
    await option.trigger('click');
    await flushPromises();
    const addBtn = wrapper.find('.el-dialog .el-dialog__footer .el-button--primary');
    await addBtn.trigger('click');
    await addBtn.trigger('click');
    resolve({ id: 'p1', activityId: ACTIVITY_ID, projectId: PROJECT_ID });
    await flushPromises();
    expect(addSpy).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
});
