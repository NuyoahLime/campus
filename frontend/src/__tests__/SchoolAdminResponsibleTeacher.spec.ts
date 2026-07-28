import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import { ElMessageBox } from 'element-plus';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';

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

function draftDetail(overrides: any = {}) {
  return {
    activityId: ACTIVITY_ID, schoolId: 'aaa', title: 'T', description: '', startTime: '2026-09-01T00:00:00.000Z', endTime: '2026-09-02T00:00:00.000Z', location: 'R', executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED', createdBy: 'ccc', projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [] }], responsibleTeachers: [], ...overrides,
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
});
