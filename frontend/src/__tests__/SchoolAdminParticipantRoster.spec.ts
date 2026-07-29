import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import { nextTick } from 'vue';
import ElementPlus, { ElMessageBox } from 'element-plus';
import SchoolAdminActivityDetail from '@/views/workbench/SchoolAdminActivityDetail.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type {
  ActivityParticipantItem,
  ProjectParticipantItem,
  SchoolAdminActivityDetail as SchoolAdminActivityDetailData,
  SchoolStudentAccountItem,
} from '@/types/school-admin-activity';

const ACTIVITY_ID = '11111111-1111-4111-8111-111111111111';
const PROJECT_ID = '22222222-2222-4222-8222-222222222222';
const ACTIVITY_PROJECT_ID = '33333333-3333-4333-8333-333333333333';
const STUDENT_ID = '44444444-4444-4444-8444-444444444444';
const OTHER_STUDENT_ID = '55555555-5555-4555-8555-555555555555';

let unhandledErrors: unknown[] = [];
let unhandledListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/school-admin/activities', component: { template: '<div>list</div>' } },
      {
        path: '/school-admin/activities/:activityId',
        component: SchoolAdminActivityDetail,
        props: true,
      },
    ],
  });
}

function detail(
  overrides: Partial<SchoolAdminActivityDetailData> = {},
): SchoolAdminActivityDetailData {
  return {
    activityId: ACTIVITY_ID,
    schoolId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    title: '校运会',
    description: '参赛人员管理测试',
    startTime: '2026-09-01T00:00:00.000Z',
    endTime: '2026-09-02T00:00:00.000Z',
    location: '体育场',
    executionStatus: 'DRAFT',
    publicStatus: 'NOT_SUBMITTED',
    createdBy: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    projects: [{
      id: ACTIVITY_PROJECT_ID,
      activityId: ACTIVITY_ID,
      projectId: PROJECT_ID,
    }],
    responsibleTeachers: [{
      id: 'teacher-assignment-1',
      activityProjectId: ACTIVITY_PROJECT_ID,
      teacherMembershipId: 'teacher-membership-1',
      userId: '66666666-6666-4666-8666-666666666666',
      username: 'teacher',
      subject: '体育',
      title: '教师',
      membershipStatus: 'ACTIVE',
      accountStatus: 'NORMAL',
    }],
    ...overrides,
  };
}

function participant(
  overrides: Partial<ActivityParticipantItem> = {},
): ActivityParticipantItem {
  return {
    studentId: STUDENT_ID,
    displayName: 'Alice',
    grade: '八年级',
    className: '一班',
    studentNumber: 'S001',
    assignedProjectCount: 0,
    hasScoreAttempt: false,
    joinedAt: '2026-07-20T08:00:00.000Z',
    ...overrides,
  };
}

function projectParticipant(
  overrides: Partial<ProjectParticipantItem> = {},
): ProjectParticipantItem {
  return {
    activityProjectParticipantId: 'assignment-1',
    activityProjectId: ACTIVITY_PROJECT_ID,
    participantId: 'participant-1',
    studentId: STUDENT_ID,
    displayName: 'Alice',
    attemptCount: 2,
    hasScoreAttempt: true,
    latestAttemptId: 'attempt-2',
    latestAttemptStatus: 'APPROVED',
    latestScoreValue: '12.34',
    hasApprovedScore: true,
    assignedAt: '2026-07-21T08:00:00.000Z',
    ...overrides,
  };
}

function student(
  overrides: Partial<SchoolStudentAccountItem> = {},
): SchoolStudentAccountItem {
  return {
    userId: STUDENT_ID,
    username: 'Alice',
    role: 'STUDENT',
    accountStatus: 'NORMAL',
    schoolName: '测试学校',
    createdAt: '2026-07-01T08:00:00.000Z',
    ...overrides,
  };
}

function page(items: ActivityParticipantItem[], totalElements = items.length) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
    hasNext: totalElements > 20,
  };
}

function mockBase(
  activityDetail = detail(),
  roster: ActivityParticipantItem[] = [participant()],
) {
  vi.spyOn(api, 'fetchActivity').mockResolvedValue(activityDetail);
  vi.spyOn(api, 'fetchAvailableProjects').mockResolvedValue({
    items: [],
    page: 0,
    size: 100,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  });
  vi.spyOn(api, 'fetchActivityParticipants').mockResolvedValue(page(roster));
  vi.spyOn(api, 'fetchActiveSchoolStudents').mockResolvedValue([]);
  vi.spyOn(api, 'fetchProjectParticipants').mockResolvedValue([]);
}

function cleanupOverlays() {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-select__popper,.el-tooltip__popper',
    )
    .forEach(element => element.remove());
}

async function withMounted(
  run: (context: {
    wrapper: ReturnType<typeof mount>;
    host: HTMLElement;
  }) => Promise<void>,
) {
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
  try {
    await run({ wrapper, host });
  } finally {
    await nextTick();
    await flushPromises();
    wrapper.unmount();
    await nextTick();
    await flushPromises();
    host.remove();
    cleanupOverlays();
  }
}

function bodyButton(selector: string): HTMLButtonElement {
  const button = document.body.querySelector<HTMLButtonElement>(selector);
  expect(button).not.toBeNull();
  return button as HTMLButtonElement;
}

async function chooseOption(selectSelector: string, label: string) {
  const select = document.body.querySelector<HTMLElement>(
    `${selectSelector} .el-select__wrapper`,
  );
  expect(select).not.toBeNull();
  select?.click();
  await nextTick();
  await flushPromises();
  const option = Array.from(
    document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
  ).find(item => item.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.restoreAllMocks();
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never);
  unhandledErrors = [];
  unhandledListener = event => {
    unhandledErrors.push(event.reason);
  };
  errorListener = event => {
    unhandledErrors.push(event.error ?? event.message);
  };
  window.addEventListener('unhandledrejection', unhandledListener);
  window.addEventListener('error', errorListener);
});

afterEach(() => {
  window.removeEventListener('unhandledrejection', unhandledListener);
  window.removeEventListener('error', errorListener);
  cleanupOverlays();
  expect(unhandledErrors).toHaveLength(0);
});

describe('SchoolAdminParticipantRoster', () => {
  it('activityRosterLoadsAndRendersStudents', async () => {
    mockBase();
    await withMounted(async ({ wrapper }) => {
      expect(api.fetchActivityParticipants).toHaveBeenCalledWith(ACTIVITY_ID, '', 0, 20);
      expect(wrapper.find('.participant-table').text()).toContain('Alice');
      expect(wrapper.find('.participant-table').text()).toContain('八年级 / 一班');
      expect(wrapper.find('.participant-table').text()).toContain('S001');
    });
  });

  it('rosterSearchSendsTrimmedKeyword', async () => {
    mockBase();
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.participant-search input').setValue('  Alice  ');
      await wrapper.find('.participant-search-button').trigger('click');
      await flushPromises();
      expect(api.fetchActivityParticipants).toHaveBeenLastCalledWith(
        ACTIVITY_ID,
        'Alice',
        0,
        20,
      );
    });
  });

  it('rosterPaginationReloadsCorrectPage', async () => {
    mockBase();
    vi.mocked(api.fetchActivityParticipants).mockResolvedValue(page([participant()], 21));
    await withMounted(async () => {
      bodyButton('.participant-pagination .btn-next').click();
      await flushPromises();
      expect(api.fetchActivityParticipants).toHaveBeenLastCalledWith(ACTIVITY_ID, '', 1, 20);
    });
  });

  it('existingParticipantsExcludedFromStudentSelector', async () => {
    mockBase();
    vi.mocked(api.fetchActiveSchoolStudents).mockResolvedValue([
      student(),
      student({ userId: OTHER_STUDENT_ID, username: 'Bob' }),
    ]);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.add-participant-button').trigger('click');
      await flushPromises();
      const selector = document.body.querySelector('.student-selector');
      expect(selector).not.toBeNull();
      await chooseOption('.student-selector', 'Bob');
      expect(document.body.querySelector('.student-selector')?.textContent).toContain('Bob');
      expect(
        Array.from(document.body.querySelectorAll('.el-select-dropdown__item'))
          .some(item => item.textContent?.trim() === 'Alice'),
      ).toBe(false);
    });
  });

  it('addParticipantCallsCorrectApiAndReloads', async () => {
    mockBase();
    vi.mocked(api.fetchActiveSchoolStudents).mockResolvedValue([
      student({ userId: OTHER_STUDENT_ID, username: 'Bob' }),
    ]);
    vi.spyOn(api, 'addActivityParticipant').mockResolvedValue(
      participant({ studentId: OTHER_STUDENT_ID, displayName: 'Bob' }),
    );
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.add-participant-button').trigger('click');
      await flushPromises();
      await chooseOption('.student-selector', 'Bob');
      bodyButton('.confirm-add-participant').click();
      await flushPromises();
      expect(api.addActivityParticipant).toHaveBeenCalledWith(ACTIVITY_ID, OTHER_STUDENT_ID);
      expect(api.fetchActivityParticipants).toHaveBeenCalledTimes(3);
      const overlay = document.body.querySelector('.student-selector')?.closest('.el-overlay');
      expect((overlay as HTMLElement | null)?.style.display).toBe('none');
    });
  });

  it('doubleAddParticipantOnlyCallsApiOnce', async () => {
    mockBase();
    vi.mocked(api.fetchActiveSchoolStudents).mockResolvedValue([
      student({ userId: OTHER_STUDENT_ID, username: 'Bob' }),
    ]);
    const pending = deferred<ActivityParticipantItem>();
    vi.spyOn(api, 'addActivityParticipant').mockReturnValue(pending.promise);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.add-participant-button').trigger('click');
      await flushPromises();
      await chooseOption('.student-selector', 'Bob');
      const button = bodyButton('.confirm-add-participant');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      expect(api.addActivityParticipant).toHaveBeenCalledTimes(1);
      pending.resolve(participant({ studentId: OTHER_STUDENT_ID, displayName: 'Bob' }));
      await flushPromises();
    });
  });

  it('participantWithProjectAssignmentCannotBeRemoved', async () => {
    mockBase(detail(), [participant({ assignedProjectCount: 1 })]);
    await withMounted(async ({ wrapper }) => {
      const button = wrapper.find<HTMLButtonElement>('.remove-participant-button');
      expect(button.attributes('disabled')).toBeDefined();
      expect(button.attributes('title')).toBe('请先取消项目分配');
    });
  });

  it('participantWithScoreCannotBeRemoved', async () => {
    mockBase(detail(), [participant({ hasScoreAttempt: true })]);
    await withMounted(async ({ wrapper }) => {
      const button = wrapper.find<HTMLButtonElement>('.remove-participant-button');
      expect(button.attributes('disabled')).toBeDefined();
      expect(button.attributes('title')).toBe('已有成绩，无法移出活动');
    });
  });

  it('removeParticipantCallsCorrectApiAndReloads', async () => {
    mockBase();
    vi.spyOn(api, 'removeActivityParticipant').mockResolvedValue();
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.remove-participant-button').trigger('click');
      await flushPromises();
      expect(api.removeActivityParticipant).toHaveBeenCalledWith(ACTIVITY_ID, STUDENT_ID);
      expect(api.fetchActivityParticipants).toHaveBeenCalledTimes(2);
    });
  });

  it('projectRosterRendersScoreSummary', async () => {
    mockBase();
    vi.mocked(api.fetchProjectParticipants).mockResolvedValue([projectParticipant()]);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      expect(api.fetchProjectParticipants).toHaveBeenCalledWith(ACTIVITY_ID, PROJECT_ID);
      expect(document.body.textContent).toContain('APPROVED');
      expect(document.body.textContent).toContain('12.34');
      expect(document.body.textContent).toContain('2');
    });
  });

  it('assignedProjectParticipantsExcludedFromSelector', async () => {
    mockBase(detail(), [
      participant(),
      participant({ studentId: OTHER_STUDENT_ID, displayName: 'Bob', studentNumber: 'S002' }),
    ]);
    vi.mocked(api.fetchProjectParticipants).mockResolvedValue([projectParticipant()]);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      await chooseOption('.project-student-selector', 'Bob');
      expect(document.body.querySelector('.project-student-selector')?.textContent).toContain('Bob');
      expect(
        Array.from(document.body.querySelectorAll('.el-select-dropdown__item'))
          .some(item => item.textContent?.trim() === 'Alice'),
      ).toBe(false);
    });
  });

  it('assignProjectParticipantCallsCorrectApiAndReloads', async () => {
    mockBase(detail(), [
      participant(),
      participant({ studentId: OTHER_STUDENT_ID, displayName: 'Bob', studentNumber: 'S002' }),
    ]);
    vi.spyOn(api, 'assignProjectParticipant').mockResolvedValue(
      projectParticipant({
        studentId: OTHER_STUDENT_ID,
        displayName: 'Bob',
        attemptCount: 0,
        hasScoreAttempt: false,
      }),
    );
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      await chooseOption('.project-student-selector', 'Bob');
      bodyButton('.confirm-project-assignment').click();
      await flushPromises();
      expect(api.assignProjectParticipant).toHaveBeenCalledWith(
        ACTIVITY_ID,
        PROJECT_ID,
        OTHER_STUDENT_ID,
      );
      expect(api.fetchProjectParticipants).toHaveBeenCalledTimes(2);
      expect(api.fetchActivityParticipants).toHaveBeenCalledTimes(3);
    });
  });

  it('doubleAssignProjectParticipantOnlyCallsApiOnce', async () => {
    mockBase(detail(), [
      participant({ studentId: OTHER_STUDENT_ID, displayName: 'Bob', studentNumber: 'S002' }),
    ]);
    const pending = deferred<ProjectParticipantItem>();
    vi.spyOn(api, 'assignProjectParticipant').mockReturnValue(pending.promise);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      await chooseOption('.project-student-selector', 'Bob');
      const button = bodyButton('.confirm-project-assignment');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      expect(api.assignProjectParticipant).toHaveBeenCalledTimes(1);
      pending.resolve(projectParticipant({
        studentId: OTHER_STUDENT_ID,
        displayName: 'Bob',
        attemptCount: 0,
        hasScoreAttempt: false,
      }));
      await flushPromises();
    });
  });

  it('participantWithScoreCannotBeUnassigned', async () => {
    mockBase();
    vi.mocked(api.fetchProjectParticipants).mockResolvedValue([projectParticipant()]);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      const button = bodyButton('.unassign-project-participant');
      expect(button.disabled).toBe(true);
      expect(button.title).toBe('已有成绩，无法取消分配');
    });
  });

  it('unassignProjectParticipantCallsCorrectApiAndReloads', async () => {
    mockBase();
    vi.mocked(api.fetchProjectParticipants).mockResolvedValue([
      projectParticipant({
        attemptCount: 0,
        hasScoreAttempt: false,
        latestAttemptId: null,
        latestAttemptStatus: null,
        latestScoreValue: null,
        hasApprovedScore: false,
      }),
    ]);
    vi.spyOn(api, 'unassignProjectParticipant').mockResolvedValue();
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      bodyButton('.unassign-project-participant').click();
      await flushPromises();
      expect(api.unassignProjectParticipant).toHaveBeenCalledWith(
        ACTIVITY_ID,
        PROJECT_ID,
        STUDENT_ID,
      );
      expect(api.fetchProjectParticipants).toHaveBeenCalledTimes(2);
      expect(api.fetchActivityParticipants).toHaveBeenCalledTimes(3);
    });
  });

  it('doubleUnassignOnlyCallsApiOnce', async () => {
    mockBase();
    vi.mocked(api.fetchProjectParticipants).mockResolvedValue([
      projectParticipant({
        attemptCount: 0,
        hasScoreAttempt: false,
        latestAttemptId: null,
        latestAttemptStatus: null,
        latestScoreValue: null,
        hasApprovedScore: false,
      }),
    ]);
    const pending = deferred<void>();
    vi.spyOn(api, 'unassignProjectParticipant').mockReturnValue(pending.promise);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      const button = bodyButton('.unassign-project-participant');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await vi.waitFor(() => {
        expect(api.unassignProjectParticipant).toHaveBeenCalledTimes(1);
      });
      pending.resolve();
      await flushPromises();
    });
  });

  it('terminalActivityHidesParticipantMutationActions', async () => {
    mockBase(detail({ executionStatus: 'ENDED' }));
    vi.mocked(api.fetchProjectParticipants).mockResolvedValue([
      projectParticipant({ hasScoreAttempt: false }),
    ]);
    await withMounted(async ({ wrapper }) => {
      expect(wrapper.find('.add-participant-button').exists()).toBe(false);
      expect(wrapper.find('.remove-participant-button').exists()).toBe(false);
      expect(wrapper.find('.manage-project-participants-button').exists()).toBe(true);
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.project-student-selector')).toBeNull();
      expect(document.body.querySelector('.unassign-project-participant')).toBeNull();
    });
  });

  it('rosterFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchActivityParticipants)
      .mockRejectedValueOnce(new ApiError(500, '名册加载失败'))
      .mockResolvedValue(page([participant()]));
    await withMounted(async ({ wrapper }) => {
      expect(wrapper.text()).toContain('名册加载失败');
      await wrapper.find('.participant-retry').trigger('click');
      await flushPromises();
      expect(api.fetchActivityParticipants).toHaveBeenCalledTimes(2);
      expect(wrapper.find('.participant-table').text()).toContain('Alice');
    });
  });

  it('projectRosterFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchProjectParticipants)
      .mockRejectedValueOnce(new ApiError(500, '项目名册加载失败'))
      .mockResolvedValue([projectParticipant()]);
    await withMounted(async ({ wrapper }) => {
      await wrapper.find('.manage-project-participants-button').trigger('click');
      await flushPromises();
      expect(document.body.textContent).toContain('项目名册加载失败');
      bodyButton('.project-participant-retry').click();
      await flushPromises();
      expect(api.fetchProjectParticipants).toHaveBeenCalledTimes(2);
      expect(document.body.textContent).toContain('APPROVED');
    });
  });
});
