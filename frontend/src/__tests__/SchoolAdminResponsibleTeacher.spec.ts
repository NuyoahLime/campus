import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import { ElMessageBox } from 'element-plus';
import { nextTick } from 'vue';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type {
  SchoolAdminActivityDetail as SchoolAdminActivityDetailData,
  ResponsibleTeacherItem,
} from '@/types/school-admin-activity';

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

function draftDetail(
  overrides: Partial<SchoolAdminActivityDetailData> = {},
): SchoolAdminActivityDetailData {
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

function cleanupTeleport() {
  document.body.querySelectorAll('.el-overlay,.el-popper-container,.el-select__popper,.el-tooltip__popper').forEach(el => el.remove());
}

afterEach(() => { cleanupTeleport(); });

async function mountResponsibleTeacherWithRealTeleport() {
  const router = makeRouter();
  await router.push(`/school-admin/activities/${ACTIVITY_ID}`);
  await router.isReady();
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(SchoolAdminActivityDetail, {
    attachTo: host,
    props: { activityId: ACTIVITY_ID },
    global: { plugins: [router, createPinia(), ElementPlus] },
  });
  await flushPromises();
  return { wrapper, host };
}

async function unmountRealTeleport(
  wrapper: ReturnType<typeof mount>,
  host: HTMLElement,
) {
  await nextTick();
  await flushPromises();

  wrapper.unmount();

  await nextTick();
  await flushPromises();

  host.remove();
  cleanupTeleport();
}

describe('SchoolAdminResponsibleTeacher', () => {
  it('teacherDirectoryLoadsSameSchoolTeachers', async () => {
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    const dirSpy = vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: TEACHER_ID, membershipId: 'm1', username: 'teacher1', subject: 'Math', title: 'Sr' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([]);
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => { expect(document.body.querySelector('.el-dialog')).not.toBeNull(); });
      expect(dirSpy).toHaveBeenCalled();
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
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
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => {
        expect(document.body.querySelector('.el-dialog')).not.toBeNull();
      });
      await vi.waitFor(() => {
        expect(document.body.textContent).toContain('暂无可分配教师');
      });
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
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
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => {
        expect(document.body.querySelector('.el-dialog')).not.toBeNull();
      });
      await vi.waitFor(() => {
        expect(document.body.textContent).toContain('重新加载');
      });
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
  });

  it('assignTeacherCallsCorrectApiAndReloads', async () => {
    const actSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail())
      .mockResolvedValueOnce(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: '44444444-4444-4444-8444-444444444444', membershipId: 'm2', username: 't2', subject: 'Sci', title: '' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValueOnce([]).mockResolvedValueOnce([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]);
    const assignSpy = vi.spyOn(api, 'assignResponsibleTeacher').mockResolvedValue({ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm2', userId: '44444444-4444-4444-8444-444444444444', username: 't2', subject: 'Sci', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' });
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => { expect(document.body.querySelector('.el-dialog')).not.toBeNull(); });
      const selectTrigger = document.body.querySelector<HTMLElement>('.el-dialog .el-select__wrapper');
      expect(selectTrigger).not.toBeNull();
      selectTrigger!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await flushPromises();
      const options = await vi.waitFor(() => {
        const found = Array.from(document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'));
        expect(found.length).toBeGreaterThan(0);
        return found;
      });
      const target = options.find(o => o.textContent?.includes('t2'));
      expect(target).toBeDefined();
      target!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await nextTick(); await flushPromises();
      const addBtn = await vi.waitFor(() => {
        const btn = document.body.querySelector<HTMLButtonElement>('.el-dialog .el-dialog__footer .el-button--primary');
        expect(btn).not.toBeNull();
        expect(btn!.disabled).toBe(false);
        return btn!;
      });
      addBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await flushPromises();
      expect(assignSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID, '44444444-4444-4444-8444-444444444444');
      expect(actSpy).toHaveBeenCalledTimes(2);
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
  });

  it('unassignTeacherCallsCorrectApiAndReloads', async () => {
    const actSpy = vi.spyOn(api, 'fetchActivity')
      .mockResolvedValueOnce(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }))
      .mockResolvedValueOnce(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValueOnce([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]).mockResolvedValueOnce([]);
    const unassignSpy = vi.spyOn(api, 'unassignResponsibleTeacher').mockResolvedValue(undefined);
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => {
        expect(document.body.querySelector('.el-dialog')).not.toBeNull();
      });
      const closeBtn = document.body.querySelector<HTMLElement>('.el-dialog .el-tag__close');
      expect(closeBtn).not.toBeNull();
      closeBtn!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await flushPromises();
      expect(unassignSpy).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID, TEACHER_ID);
      expect(actSpy).toHaveBeenCalledTimes(2);
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
  });

  it('doubleAssignTeacherOnlyCallsApiOnce', async () => {
    let resolveAssign: (v: ResponsibleTeacherItem) => void = () => undefined;
    const deferred = new Promise<ResponsibleTeacherItem>(r => { resolveAssign = r; });
    const assignSpy = vi.spyOn(api, 'assignResponsibleTeacher').mockReturnValue(deferred);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail());
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [{ userId: TEACHER_ID, membershipId: 'm1', username: 't', subject: '', title: '' }], page: 0, size: 50, totalElements: 1, totalPages: 1, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([]);
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => { expect(document.body.querySelector('.el-dialog')).not.toBeNull(); });
      const sel = document.body.querySelector<HTMLElement>('.el-dialog .el-select__wrapper');
      expect(sel).not.toBeNull();
      sel!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await flushPromises();
      const opts = await vi.waitFor(() => {
        const found = Array.from(document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'));
        expect(found.length).toBeGreaterThan(0);
        return found;
      });
      opts[0].dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await nextTick(); await flushPromises();
      const btn = document.body.querySelector<HTMLButtonElement>('.el-dialog .el-dialog__footer .el-button--primary');
      expect(btn).not.toBeNull();
      btn!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      btn!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      resolveAssign({ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 't', subject: '', title: '', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' });
      await flushPromises();
      expect(assignSpy).toHaveBeenCalledTimes(1);
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
  });

  it('doubleUnassignTeacherOnlyCallsApiOnce', async () => {
    let resolveUnassign: () => void = () => undefined;
    const deferredUnassign = new Promise<void>(r => { resolveUnassign = r; });
    const unassignSpy = vi.spyOn(api, 'unassignResponsibleTeacher').mockReturnValue(deferredUnassign);
    vi.spyOn(api, 'fetchActivity').mockResolvedValue(draftDetail({ responsibleTeachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }], projects: [{ id: 'ap1', activityId: ACTIVITY_ID, projectId: PROJECT_ID, _teachers: [{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }] }] }));
    vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchSchoolTeachers').mockResolvedValue({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false });
    vi.spyOn(api, 'fetchResponsibleTeachers').mockResolvedValue([{ id: 'r1', activityProjectId: 'ap1', teacherMembershipId: 'm1', userId: TEACHER_ID, username: 'teacher1', subject: 'Math', title: 'Sr', membershipStatus: 'ACTIVE', accountStatus: 'NORMAL' }]);
    const { wrapper, host } = await mountResponsibleTeacherWithRealTeleport();
    try {
      await wrapper.find('.el-button--small').trigger('click');
      await flushPromises();
      await vi.waitFor(() => {
        expect(document.body.querySelector('.el-dialog')).not.toBeNull();
      });
      const closeBtn = document.body.querySelector<HTMLElement>('.el-dialog .el-tag__close');
      expect(closeBtn).not.toBeNull();
      closeBtn!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      closeBtn!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      resolveUnassign();
      await flushPromises();
      expect(unassignSpy).toHaveBeenCalledTimes(1);
    } finally {
      await unmountRealTeleport(wrapper, host);
    }
  });
});
