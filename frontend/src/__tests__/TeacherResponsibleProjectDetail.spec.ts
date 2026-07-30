import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import ElementPlus from 'element-plus';
import TeacherResponsibleProjectDetail from '@/views/workbench/TeacherResponsibleProjectDetail.vue';
import * as projectApi from '@/api/teacher-responsible-project';
import * as scoreApi from '@/api/teacher-score-entry';
import type {
  TeacherProjectParticipantItem,
  TeacherResponsibleProjectDetail as ProjectDetail,
} from '@/types/teacher-responsible-project';
import type { ScoreStorageType } from '@/types/school-admin-score-review';

const ACTIVITY_PROJECT_ID = '11111111-1111-4111-8111-111111111111';
const STUDENT_ID = '22222222-2222-4222-8222-222222222222';
let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function detail(
  scoreStorageType: ScoreStorageType = 'INTEGER',
  overrides: Partial<ProjectDetail> = {},
): ProjectDetail {
  return {
    activityProjectId: ACTIVITY_PROJECT_ID,
    activityId: '33333333-3333-4333-8333-333333333333',
    activityTitle: '春季校运会',
    schoolId: '44444444-4444-4444-8444-444444444444',
    schoolName: '第一中学',
    executionStatus: 'IN_PROGRESS',
    startTime: '2026-07-30T08:00:00Z',
    endTime: '2026-07-30T10:00:00Z',
    location: '田径场',
    projectId: '55555555-5555-4555-8555-555555555555',
    projectName: '跳绳',
    category: 'SPORT',
    scoreStorageType,
    scoreUnit: scoreStorageType === 'DURATION' ? '毫秒' : '次',
    decimalPlaces: scoreStorageType === 'DECIMAL' ? 2 : null,
    gradeOrder: scoreStorageType === 'GRADE' ? 'A,B,C' : null,
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    participantCount: 1,
    enteredAttemptCount: 0,
    pendingReviewCount: 0,
    rejectedCount: 0,
    activityDescription: '活动说明',
    projectDescription: '项目说明',
    rulesText: '比赛规则',
    venueRequirements: '室外场地',
    equipmentRequirements: '跳绳',
    allowTie: true,
    responsibleTeachers: [
      { userId: 'teacher-1', username: 'Teacher Li', subject: '体育', title: '教师' },
    ],
    ...overrides,
  };
}

function participant(
  overrides: Partial<TeacherProjectParticipantItem> = {},
): TeacherProjectParticipantItem {
  return {
    studentId: STUDENT_ID,
    displayName: 'Alice',
    studentNumber: 'S001',
    grade: '七年级',
    className: '1班',
    attemptCount: 0,
    latestAttemptId: null,
    latestAttemptNumber: null,
    latestAttemptStatus: null,
    latestScoreValue: null,
    hasApprovedScore: false,
    assignedAt: '2026-07-30T07:00:00Z',
    ...overrides,
  };
}

function page(items: TeacherProjectParticipantItem[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false,
  };
}

function mockBase(projectDetail = detail()): void {
  vi.spyOn(projectApi, 'fetchTeacherResponsibleProject').mockResolvedValue(projectDetail);
  vi.spyOn(projectApi, 'fetchTeacherProjectParticipants').mockResolvedValue(
    page([participant()]),
  );
  vi.spyOn(scoreApi, 'createTeacherScoreAttempt').mockResolvedValue({
    attemptId: 'attempt-1',
    activityProjectId: ACTIVITY_PROJECT_ID,
    activityId: projectDetail.activityId,
    activityTitle: projectDetail.activityTitle,
    schoolId: projectDetail.schoolId,
    schoolName: projectDetail.schoolName,
    projectId: projectDetail.projectId,
    projectName: projectDetail.projectName,
    studentId: STUDENT_ID,
    studentName: 'Alice',
    attemptNumber: 1,
    scoreStorageType: projectDetail.scoreStorageType,
    displayValue: '100',
    scoreUnit: projectDetail.scoreUnit,
    integerValue: projectDetail.scoreStorageType === 'INTEGER' ? 100 : null,
    decimalValue: null,
    durationMs: null,
    grade: null,
    decimalPlaces: projectDetail.decimalPlaces,
    gradeOrder: projectDetail.gradeOrder,
    scoreBusinessTime: '2026-07-30T10:00:00Z',
    timeSource: 'ON_SITE_RECORD',
    status: 'PENDING_REVIEW',
    submittedAt: '2026-07-30T10:01:00Z',
    createdAt: '2026-07-30T10:01:00Z',
    updatedAt: '2026-07-30T10:01:00Z',
    currentEffective: false,
    reviewHistory: [],
  });
}

function cleanupOverlays(): void {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-select__popper,.el-picker__popper',
    )
    .forEach((element) => element.remove());
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
): Promise<void> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/teacher/responsible/:activityProjectId',
        component: TeacherResponsibleProjectDetail,
      },
      { path: '/teacher/responsible', component: { template: '<div />' } },
    ],
  });
  await router.push(`/teacher/responsible/${ACTIVITY_PROJECT_ID}`);
  await router.isReady();
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(TeacherResponsibleProjectDetail, {
    attachTo: host,
    global: { plugins: [router, ElementPlus] },
  });
  await flushPromises();
  try {
    await run(wrapper);
  } finally {
    wrapper.unmount();
    await nextTick();
    await flushPromises();
    host.remove();
    cleanupOverlays();
  }
}

async function openScoreDialog(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.find('.enter-score-button').trigger('click');
  await flushPromises();
  expect(document.body.querySelector('.teacher-score-dialog')).not.toBeNull();
}

async function setNativeInput(selector: string, value: string): Promise<void> {
  const input = document.body.querySelector<HTMLInputElement>(selector);
  expect(input).not.toBeNull();
  if (!input) return;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  input.dispatchEvent(new FocusEvent('blur', { bubbles: true }));
  await nextTick();
  await flushPromises();
}

async function chooseOption(selectSelector: string, label: string): Promise<void> {
  const select = document.body.querySelector<HTMLElement>(
    `${selectSelector} .el-select__wrapper`,
  );
  expect(select).not.toBeNull();
  select?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
  const option = Array.from(
    document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
  ).find((candidate) => candidate.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
}

async function fillCommonFields(): Promise<void> {
  await setNativeInput(
    '.business-time-input .el-input__inner',
    '2026-07-30 10:00:00',
  );
  await chooseOption('.time-source-select', '现场记录');
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

describe('TeacherResponsibleProjectDetail', () => {
  it('projectDetailLoads', async () => {
    mockBase();
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain('第一中学');
      expect(wrapper.text()).toContain('比赛规则');
      expect(wrapper.text()).toContain('Teacher Li');
    });
  });

  it('projectParticipantsLoad', async () => {
    mockBase();
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain('Alice');
      expect(wrapper.text()).toContain('S001');
    });
  });

  it('participantSearchWorks', async () => {
    mockBase();
    const fetch = vi.mocked(projectApi.fetchTeacherProjectParticipants);
    await withMounted(async (wrapper) => {
      await wrapper.find('.participant-keyword-filter input').setValue(' Alice ');
      await wrapper.find('.participant-search-button').trigger('click');
      await flushPromises();
      expect(fetch).toHaveBeenLastCalledWith(
        ACTIVITY_PROJECT_ID,
        { keyword: ' Alice ', status: undefined },
        0,
        20,
      );
    });
  });

  it('terminalProjectHidesEntryButton', async () => {
    mockBase(detail('INTEGER', { executionStatus: 'ENDED' }));
    await withMounted(async (wrapper) => {
      expect(wrapper.find('.enter-score-button').exists()).toBe(false);
      expect(wrapper.text()).toContain('活动已结束，无法录入成绩');
    });
  });

  it.each([
    ['INTEGER', '.integer-score-input', '.decimal-score-input'],
    ['DECIMAL', '.decimal-score-input', '.integer-score-input'],
    ['DURATION', '.duration-score-input', '.integer-score-input'],
  ] as const)('%s project shows only its numeric input', async (type, visible, hidden) => {
    mockBase(detail(type));
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      expect(document.body.querySelector(visible)).not.toBeNull();
      expect(document.body.querySelector(hidden)).toBeNull();
    });
  });

  it('gradeProjectUsesGradeOrder', async () => {
    mockBase(detail('GRADE'));
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      await chooseOption('.grade-score-select', 'B');
      expect(document.body.querySelector('.grade-score-select')?.textContent).toContain('B');
    });
  });

  it('businessTimeAndTimeSourceAreRequired', async () => {
    mockBase();
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      await setNativeInput('.integer-score-input .el-input__inner', '100');
      document.body
        .querySelector<HTMLButtonElement>('.submit-score-button')
        ?.click();
      await flushPromises();
      expect(document.body.textContent).toContain('请选择业务发生时间');
      expect(document.body.textContent).toContain('请选择时间来源');
      expect(scoreApi.createTeacherScoreAttempt).not.toHaveBeenCalled();
    });
  });

  it('durationProjectAcceptsZeroAndSubmitCallsCorrectApi', async () => {
    mockBase(detail('DURATION'));
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      await setNativeInput('.duration-score-input .el-input__inner', '0');
      await fillCommonFields();
      document.body
        .querySelector<HTMLButtonElement>('.submit-score-button')
        ?.click();
      await flushPromises();
      expect(scoreApi.createTeacherScoreAttempt).toHaveBeenCalledWith(
        expect.objectContaining({
          activityProjectId: ACTIVITY_PROJECT_ID,
          studentId: STUDENT_ID,
          durationMs: 0,
          timeSource: 'ON_SITE_RECORD',
        }),
      );
    });
  });

  it('payloadExcludesAttemptNumberAndStorageType', async () => {
    mockBase();
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      await setNativeInput('.integer-score-input .el-input__inner', '100');
      await fillCommonFields();
      document.body
        .querySelector<HTMLButtonElement>('.submit-score-button')
        ?.click();
      await flushPromises();
      const payload = vi.mocked(scoreApi.createTeacherScoreAttempt).mock.calls[0]?.[0];
      expect(payload).toBeDefined();
      expect(JSON.stringify(payload)).not.toContain('attemptNumber');
      expect(JSON.stringify(payload)).not.toContain('scoreStorageType');
    });
  });

  it('doubleSubmitOnlyCallsApiOnce', async () => {
    mockBase();
    let releaseRequest: (() => void) | undefined;
    vi.mocked(scoreApi.createTeacherScoreAttempt).mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseRequest = () =>
            resolve({} as Awaited<ReturnType<typeof scoreApi.createTeacherScoreAttempt>>);
        }),
    );
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      await setNativeInput('.integer-score-input .el-input__inner', '100');
      await fillCommonFields();
      const button = document.body.querySelector<HTMLButtonElement>(
        '.submit-score-button',
      );
      button?.click();
      button?.click();
      await nextTick();
      expect(scoreApi.createTeacherScoreAttempt).toHaveBeenCalledTimes(1);
      releaseRequest?.();
      await flushPromises();
    });
  });

  it('submitSuccessReloadsParticipants', async () => {
    mockBase();
    const fetch = vi.mocked(projectApi.fetchTeacherProjectParticipants);
    await withMounted(async (wrapper) => {
      await openScoreDialog(wrapper);
      await setNativeInput('.integer-score-input .el-input__inner', '100');
      await fillCommonFields();
      document.body
        .querySelector<HTMLButtonElement>('.submit-score-button')
        ?.click();
      await flushPromises();
      expect(fetch).toHaveBeenCalledTimes(2);
      const remainingDialog = document.body.querySelector('.teacher-score-dialog');
      const overlay = remainingDialog?.closest<HTMLElement>('.el-overlay');
      expect(remainingDialog === null || overlay?.style.display === 'none').toBe(true);
    });
  });
});
