import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import { ElMessageBox } from 'element-plus';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type { SchoolAdminActivityDetail, ResponsibleTeacherItem } from '@/types/school-admin-activity';

const ACTIVITY_ID = '11111111-1111-4111-8111-111111111111';
const PROJECT_ID = '22222222-2222-4222-8222-222222222222';
const TEACHER_ID = '33333333-3333-4333-8333-333333333333';

function makeRouter() {
  return createRouter({ history: createWebHistory(), routes: [
    { path: '/', component: { template: '<div>home</div>' } },
    { path: '/school-admin/activities', component: { template: '<div>list</div>' } },
    { path: '/school-admin/activities/:activityId', component: SchoolAdminActivityDetail, props: true },
  ]});
}

function draftDetail(overrides: Partial<SchoolAdminActivityDetail> = {}): SchoolAdminActivityDetail {
  return {
    activityId: ACTIVITY_ID, schoolId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', title: 'T', description: '',
    startTime: '2026-09-01T00:00:00.000Z', endTime: '2026-09-02T00:00:00.000Z', location: 'R',
    executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED', createdBy: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [] }],
    responsibleTeachers: [], ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.restoreAllMocks();
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never);
});

describe('SchoolAdminResponsibleTeacher', () => {
  it('teacherDirectoryLoadsSameSchoolTeachers', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const dirSpy = vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: TEACHER_ID, membershipId: 'm1', username: 'teacher1', subject: 'Math', title: 'Sr' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([]);
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click'); // manage teachers
    await flushPromises();
    expect(dirSpy).toHaveBeenCalled();
    wrapper.unmount();
  });

  it('assignedTeachersRenderUnderCorrectProject', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    expect(wrapper.text()).toContain('teacher1');
    expect(wrapper.text()).toContain('Math');
    wrapper.unmount();
  });

  it('assignedTeachersExcludedFromSelector', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: TEACHER_ID, membershipId: 'm1', username: 'teacher1', subject: 'Math', title: 'Sr' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]);
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('暂无可分配教师');
    wrapper.unmount();
  });

  it('missingTeacherDisablesPublish', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    const btn = wrapper.find('.el-button--success');
    expect(btn.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('terminalActivityHidesTeacherMutationActions', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ executionStatus: 'ENDED' }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    expect(wrapper.text()).not.toContain('管理教师');
    wrapper.unmount();
  });

  it('teacherDirectoryFailureShowsRetry', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockRejectedValue(new ApiError(500, 'fail'));
    vi.spyOn(api, 'fetchSchoolTeachers').mockRejectedValue(new ApiError(500, 'fail'));
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('重新加载');
    wrapper.unmount();
  });

  it('assignTeacherCallsCorrectApiAndReloads', async () => {
    const actSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail())
      .mockResolvedValueOnce(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: '44444444-4444-4444-8444-444444444444', membershipId: 'm2', username: 't2', subject: 'Sci', title: '' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValueOnce([]).mockResolvedValueOnce([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]);
    const assignSpy = vi.spyOn(api, 'assignResponsibleTeacher').mockResolvedValue({ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' });
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click');
    await flushPromises();
    const select = wrapper.find('.el-dialog .el-select__wrapper');
    expect(select.exists()).toBe(true);
    await select.trigger('click');
    await flushPromises();
    const options = wrapper.findAll('.el-select-dropdown__item');
    const target = options.find(o => o.text().includes('t2'));
    expect(target).toBeDefined();
    await target!.trigger('click');
    await flushPromises();
    await wrapper.find('.el-dialog .el-dialog__footer .el-button--primary').trigger('click');
    await flushPromises();
    expect(assignSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID, '44444444-4444-4444-8444-444444444444');
    expect(actSpy).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('unassignTeacherCallsCorrectApiAndReloads', async () => {
    const actSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }))
      .mockResolvedValueOnce(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValueOnce([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]).mockResolvedValueOnce([]);
    const unassignSpy = vi.spyOn(api, 'unassignResponsibleTeacher').mockResolvedValue(undefined);
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click');
    await flushPromises();
    await wrapper.find('.el-tag__close').trigger('click');
    await flushPromises();
    expect(unassignSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID, TEACHER_ID);
    expect(actSpy).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('doubleAssignTeacherOnlyCallsApiOnce', async () => {
    let resolveAssign: (v: ResponsibleTeacherItem) => void = () => undefined;
    const deferred = new Promise<ResponsibleTeacherItem>(r => { resolveAssign = r; });
    const assignSpy = vi.spyOn(api, 'assignResponsibleTeacher').mockReturnValue(deferred);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: TEACHER_ID, membershipId: 'm1', username: 't', subject: '', title: '' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([]);
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click');
    await flushPromises();
    const sel = wrapper.find('.el-dialog .el-select__wrapper');
    expect(sel.exists()).toBe(true);
    await sel.trigger('click');
    await flushPromises();
    const opts = wrapper.findAll('.el-select-dropdown__item');
    expect(opts.length).toBeGreaterThan(0);
    await opts[0].trigger('click');
    await flushPromises();
    const btn = wrapper.find('.el-dialog .el-dialog__footer .el-button--primary');
    await btn.trigger('click');
    await btn.trigger('click');
    resolveAssign({ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 't', subject: '', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' });
    await flushPromises();
    expect(assignSpy).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('doubleUnassignTeacherOnlyCallsApiOnce', async () => {
    let resolveUnassign: () => void = () => undefined;
    const deferredUnassign = new Promise<void>(r => { resolveUnassign = r; });
    const unassignSpy = vi.spyOn(api, 'unassignResponsibleTeacher').mockReturnValue(deferredUnassign);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]);
    const wrapper = mount(SchoolAdminActivityDetail, { props: { activityId: ACTIVITY_ID }, global: { plugins: [makeRouter(), createPinia(), ElementPlus], stubs: { teleport: true } } });
    await flushPromises();
    await wrapper.find('.el-button--small').trigger('click');
    await flushPromises();
    const closeBtn = wrapper.find('.el-tag__close');
    await closeBtn.trigger('click');
    await closeBtn.trigger('click');
    resolveUnassign();
    await flushPromises();
    expect(unassignSpy).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
});
