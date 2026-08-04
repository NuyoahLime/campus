import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';
import ElementPlus from 'element-plus';
import SchoolAdminScoreReview from '@/views/workbench/SchoolAdminScoreReview.vue';
import * as entryApi from '@/api/school-admin-score-entry';
import * as reviewApi from '@/api/school-admin-score-review';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import type {
  SchoolAdminScoreAttemptDetail,
  SchoolAdminScoreAttemptItem,
  ScoreAttemptStatus,
  ScoreStorageType,
} from '@/types/school-admin-score-review';
import type {
  ScoreEntryParticipantOption,
  ScoreEntryProjectOption,
} from '@/types/school-admin-score-entry';

const ADMIN_ID = '11111111-1111-4111-8111-111111111111';
const SCHOOL_ID = '22222222-2222-4222-8222-222222222222';
const ACTIVITY_ID = '33333333-3333-4333-8333-333333333333';
const PROJECT_ID = '44444444-4444-4444-8444-444444444444';
const ACTIVITY_PROJECT_ID = '55555555-5555-4555-8555-555555555555';
const STUDENT_ID = '66666666-6666-4666-8666-666666666666';
const ATTEMPT_ID = '77777777-7777-4777-8777-777777777777';

let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function project(
  scoreStorageType: ScoreStorageType = 'INTEGER',
  overrides: Partial<ScoreEntryProjectOption> = {},
): ScoreEntryProjectOption {
  const suffix = scoreStorageType.toLowerCase();
  return {
    activityProjectId:
      scoreStorageType === 'INTEGER' ? ACTIVITY_PROJECT_ID : `${ACTIVITY_PROJECT_ID}-${suffix}`,
    activityId: ACTIVITY_ID,
    activityTitle: '校运会',
    executionStatus: 'ONGOING',
    projectId: scoreStorageType === 'INTEGER' ? PROJECT_ID : `${PROJECT_ID}-${suffix}`,
    projectName: {
      INTEGER: '跳绳',
      DECIMAL: '跳远',
      DURATION: '短跑',
      GRADE: '体操',
    }[scoreStorageType],
    scoreStorageType,
    scoreUnit: scoreStorageType === 'DURATION' ? '毫秒' : null,
    decimalPlaces: scoreStorageType === 'DECIMAL' ? 2 : null,
    gradeOrder: scoreStorageType === 'GRADE' ? 'A,B,C' : null,
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    ...overrides,
  };
}

function participant(
  overrides: Partial<ScoreEntryParticipantOption> = {},
): ScoreEntryParticipantOption {
  return {
    studentId: STUDENT_ID,
    displayName: 'Alice',
    studentNumber: 'S001',
    grade: '七年级',
    className: '1班',
    attemptCount: 0,
    latestAttemptNumber: null,
    latestAttemptStatus: null,
    latestScoreValue: null,
    ...overrides,
  };
}

function item(
  status: ScoreAttemptStatus = 'DRAFT',
  overrides: Partial<SchoolAdminScoreAttemptItem> = {},
): SchoolAdminScoreAttemptItem {
  return {
    attemptId: ATTEMPT_ID,
    schoolId: SCHOOL_ID,
    activityId: ACTIVITY_ID,
    activityTitle: '校运会',
    activityProjectId: ACTIVITY_PROJECT_ID,
    projectId: PROJECT_ID,
    projectName: '跳绳',
    studentId: STUDENT_ID,
    studentName: 'Alice',
    attemptNumber: 1,
    scoreStorageType: 'INTEGER',
    displayValue: '100',
    scoreUnit: '次',
    scoreBusinessTime: '2026-07-30T08:00:00Z',
    timeSource: 'ON_SITE_RECORD',
    status,
    currentEffective: status === 'APPROVED',
    enteredBy: ADMIN_ID,
    enteredByName: 'Admin Li',
    submittedAt: status === 'DRAFT' ? null : '2026-07-30T09:00:00Z',
    createdAt: '2026-07-30T08:30:00Z',
    effectiveScoreRule: 'BEST',
    comparisonDirection: 'HIGHER_BETTER',
    ...overrides,
  };
}

function detail(
  status: ScoreAttemptStatus = 'DRAFT',
  overrides: Partial<SchoolAdminScoreAttemptDetail> = {},
): SchoolAdminScoreAttemptDetail {
  return {
    ...item(status, overrides),
    integerValue: 100,
    decimalValue: null,
    durationMs: null,
    grade: null,
    decimalPlaces: null,
    gradeOrder: null,
    allowTie: true,
    reviewHistory: [],
    ...overrides,
  };
}

function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false,
  };
}

function mockBase(myRows: SchoolAdminScoreAttemptItem[] = [item()]) {
  vi.spyOn(reviewApi, 'fetchSchoolAdminScoreAttempts').mockResolvedValue(page([]));
  vi.spyOn(reviewApi, 'fetchSchoolAdminScoreAttempt').mockResolvedValue(detail());
  vi.spyOn(reviewApi, 'approveSchoolAdminScoreAttempt').mockResolvedValue(
    detail('APPROVED'),
  );
  vi.spyOn(reviewApi, 'rejectSchoolAdminScoreAttempt').mockResolvedValue(
    detail('REJECTED'),
  );
  vi.spyOn(entryApi, 'fetchMySchoolAdminScoreEntries').mockResolvedValue(page(myRows));
  vi.spyOn(entryApi, 'fetchScoreEntryProjects').mockResolvedValue(
    page([project(), project('DECIMAL'), project('DURATION'), project('GRADE')]),
  );
  vi.spyOn(entryApi, 'fetchScoreEntryParticipants').mockResolvedValue(
    page([participant()]),
  );
  vi.spyOn(entryApi, 'createSchoolAdminScoreDraft').mockResolvedValue(detail());
  vi.spyOn(entryApi, 'updateSchoolAdminScoreDraft').mockResolvedValue(detail());
  vi.spyOn(entryApi, 'submitSchoolAdminScoreDraft').mockResolvedValue(
    detail('PENDING_REVIEW'),
  );
}

function cleanupOverlays() {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-select__popper,.el-tooltip__popper',
    )
    .forEach(element => element.remove());
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const auth = useAuthStore();
  auth.user = {
    userId: ADMIN_ID,
    username: 'entry-admin',
    accountStatus: 'NORMAL',
    platformRole: null,
    roles: ['SCHOOL_ADMIN'],
    schoolMemberships: [],
    primaryRole: 'SCHOOL_ADMIN',
    primarySchoolId: SCHOOL_ID,
    onboardingRequired: false,
  };
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(SchoolAdminScoreReview, {
    attachTo: host,
    global: { plugins: [pinia, ElementPlus] },
  });
  await flushPromises();
  try {
    await run(wrapper);
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

async function clickTab(wrapper: ReturnType<typeof mount>, label: string) {
  const tab = wrapper
    .findAll<HTMLElement>('.el-tabs__item')
    .find(candidate => candidate.text().trim() === label);
  expect(tab).not.toBeUndefined();
  await tab?.trigger('click');
  await nextTick();
  await flushPromises();
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
  select?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
  const options = Array.from(
    document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
  ).filter(element => element.textContent?.trim() === label);
  expect(options.length).toBeGreaterThan(0);
  options.at(-1)?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
}

async function openEntry(wrapper: ReturnType<typeof mount>) {
  await wrapper.find('.open-entry-dialog').trigger('click');
  await flushPromises();
  expect(document.body.querySelector('.score-entry-dialog')).not.toBeNull();
}

async function selectProject(
  wrapper: ReturnType<typeof mount>,
  storageType: ScoreStorageType = 'INTEGER',
) {
  const selected = project(storageType);
  await chooseOption(
    '.entry-project-select',
    `${selected.activityTitle} / ${selected.projectName}`,
  );
  await flushPromises();
  expect(entryApi.fetchScoreEntryParticipants).toHaveBeenLastCalledWith(
    selected.activityProjectId,
    '',
    0,
    100,
  );
  return wrapper;
}

async function selectParticipant() {
  await chooseOption('.entry-participant-select', 'Alice / S001');
}

async function emitScoreValue(
  wrapper: ReturnType<typeof mount>,
  storageType: ScoreStorageType,
  value: number | string,
) {
  if (storageType === 'GRADE') {
    await chooseOption('.grade-score-select', String(value));
    return;
  }
  const numberInput = wrapper.findComponent({ name: 'ElInputNumber' });
  expect(numberInput.exists()).toBe(true);
  numberInput.vm.$emit('update:modelValue', value);
  await nextTick();
}

async function fillRequiredFields(
  wrapper: ReturnType<typeof mount>,
  storageType: ScoreStorageType = 'INTEGER',
  score: number | string = 100,
) {
  await selectProject(wrapper, storageType);
  await selectParticipant();
  await emitScoreValue(wrapper, storageType, score);
  const datePicker = wrapper.findComponent({ name: 'ElDatePicker' });
  expect(datePicker.exists()).toBe(true);
  datePicker.vm.$emit('update:modelValue', '2026-07-30T08:00:00Z');
  await nextTick();
  await chooseOption('.entry-time-source', '现场记录');
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolver, rejecter) => {
    resolve = resolver;
    reject = rejecter;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  vi.restoreAllMocks();
  unhandledErrors = [];
  rejectionListener = event => unhandledErrors.push(event.reason);
  errorListener = event => unhandledErrors.push(event.error ?? event.message);
  window.addEventListener('unhandledrejection', rejectionListener);
  window.addEventListener('error', errorListener);
});

afterEach(() => {
  window.removeEventListener('unhandledrejection', rejectionListener);
  window.removeEventListener('error', errorListener);
  cleanupOverlays();
  expect(unhandledErrors).toHaveLength(0);
});

describe('SchoolAdminScoreEntry', () => {
  it('myEntriesLoadForCurrentAdmin', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      expect(entryApi.fetchMySchoolAdminScoreEntries).toHaveBeenCalledWith(
        { status: undefined, keyword: undefined },
        0,
        20,
      );
      expect(wrapper.find('.my-entry-table').text()).toContain('Alice');
    });
  });

  it('myEntryStatusFilterWorks', async () => {
    mockBase([item('REJECTED')]);
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await chooseOption('.my-status-filter', '已驳回');
      await wrapper.find('.my-search-button').trigger('click');
      await flushPromises();
      expect(entryApi.fetchMySchoolAdminScoreEntries).toHaveBeenLastCalledWith(
        { status: 'REJECTED', keyword: undefined },
        0,
        20,
      );
    });
  });

  it('openEntryDialogLoadsProjectOptions', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      expect(entryApi.fetchScoreEntryProjects).toHaveBeenCalledWith('', 0, 100);
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '代录成绩',
      );
    });
  });

  it('projectSelectionLoadsAssignedParticipants', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper);
      expect(entryApi.fetchScoreEntryParticipants).toHaveBeenCalledTimes(1);
    });
  });

  it('changingProjectClearsSelectedStudent', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper);
      await selectParticipant();
      expect(
        document.body.querySelector('.entry-participant-select')?.textContent,
      ).toContain('Alice');
      await selectProject(wrapper, 'DECIMAL');
      expect(
        document.body.querySelector('.entry-participant-select')?.textContent,
      ).not.toContain('Alice');
    });
  });

  it('integerProjectShowsIntegerInputOnly', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper);
      expect(document.body.querySelector('.integer-score-input')).not.toBeNull();
      expect(document.body.querySelector('.decimal-score-input')).toBeNull();
      expect(document.body.querySelector('.duration-score-input')).toBeNull();
      expect(document.body.querySelector('.grade-score-select')).toBeNull();
    });
  });

  it('decimalProjectShowsDecimalInputOnly', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper, 'DECIMAL');
      expect(document.body.querySelector('.decimal-score-input')).not.toBeNull();
      expect(document.body.querySelector('.integer-score-input')).toBeNull();
      expect(document.body.querySelector('.duration-score-input')).toBeNull();
      expect(document.body.querySelector('.grade-score-select')).toBeNull();
    });
  });

  it('durationProjectAcceptsZero', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper, 'DURATION', 0);
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.createSchoolAdminScoreDraft).toHaveBeenCalledWith(
        expect.objectContaining({ durationMs: 0 }),
      );
    });
  });

  it('gradeProjectUsesGradeOrderOptions', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper, 'GRADE');
      const select = document.body.querySelector<HTMLElement>(
        '.grade-score-select .el-select__wrapper',
      );
      select?.click();
      await nextTick();
      await flushPromises();
      const labels = Array.from(
        document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
      )
        .map(option => option.textContent?.trim())
        .filter(label => ['A', 'B', 'C'].includes(label ?? ''));
      expect(labels.slice(-3)).toEqual(['A', 'B', 'C']);
    });
  });

  it('businessTimeIsRequired', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper);
      await selectParticipant();
      await emitScoreValue(wrapper, 'INTEGER', 100);
      await chooseOption('.entry-time-source', '现场记录');
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.createSchoolAdminScoreDraft).not.toHaveBeenCalled();
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '请选择成绩业务发生时间',
      );
    });
  });

  it('timeSourceIsRequired', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper);
      await selectParticipant();
      await emitScoreValue(wrapper, 'INTEGER', 100);
      const datePicker = wrapper.findComponent({ name: 'ElDatePicker' });
      datePicker.vm.$emit('update:modelValue', '2026-07-30T08:00:00Z');
      await nextTick();
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.createSchoolAdminScoreDraft).not.toHaveBeenCalled();
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '请选择时间来源',
      );
    });
  });

  it('saveDraftCallsCorrectApi', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper);
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.createSchoolAdminScoreDraft).toHaveBeenCalledWith({
        activityProjectId: ACTIVITY_PROJECT_ID,
        studentId: STUDENT_ID,
        integerValue: 100,
        scoreBusinessTime: '2026-07-30T08:00:00Z',
        timeSource: 'ON_SITE_RECORD',
      });
    });
  });

  it('doubleSaveDraftOnlyCreatesOnce', async () => {
    mockBase();
    const pending = deferred<SchoolAdminScoreAttemptDetail>();
    vi.mocked(entryApi.createSchoolAdminScoreDraft).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper);
      const button = bodyButton('.save-entry-draft');
      button.click();
      button.click();
      expect(entryApi.createSchoolAdminScoreDraft).toHaveBeenCalledTimes(1);
      pending.resolve(detail());
      await flushPromises();
    });
  });

  it('saveDraftReloadsMyEntries', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper);
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.fetchMySchoolAdminScoreEntries).toHaveBeenCalledTimes(1);
      expect(wrapper.find('.my-entry-table').exists()).toBe(true);
    });
  });

  it('saveAndSubmitCreatesThenSubmits', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper);
      bodyButton('.save-submit-entry').click();
      await flushPromises();
      expect(entryApi.createSchoolAdminScoreDraft).toHaveBeenCalledTimes(1);
      expect(entryApi.submitSchoolAdminScoreDraft).toHaveBeenCalledWith(ATTEMPT_ID);
    });
  });

  it('submitFailureDoesNotCreateSecondDraft', async () => {
    mockBase();
    vi.mocked(entryApi.submitSchoolAdminScoreDraft)
      .mockRejectedValueOnce(new ApiError(409, '暂无其他管理员'))
      .mockResolvedValue(detail('PENDING_REVIEW'));
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper);
      bodyButton('.save-submit-entry').click();
      await flushPromises();
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '草稿已保存',
      );
      bodyButton('.save-submit-entry').click();
      await flushPromises();
      expect(entryApi.createSchoolAdminScoreDraft).toHaveBeenCalledTimes(1);
      expect(entryApi.submitSchoolAdminScoreDraft).toHaveBeenCalledTimes(2);
    });
  });

  it('draftEntryCanBeEdited', async () => {
    mockBase([item()]);
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await wrapper.find('.edit-entry-button').trigger('click');
      await flushPromises();
      expect(reviewApi.fetchSchoolAdminScoreAttempt).toHaveBeenCalledWith(ATTEMPT_ID);
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.updateSchoolAdminScoreDraft).toHaveBeenCalledWith(
        ATTEMPT_ID,
        expect.objectContaining({ integerValue: 100 }),
      );
    });
  });

  it('draftEntryCanBeSubmitted', async () => {
    mockBase([item()]);
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await wrapper.find('.submit-entry-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.submit-entry-confirm-dialog')).not.toBeNull();
      bodyButton('.confirm-submit-entry').click();
      await flushPromises();
      expect(entryApi.submitSchoolAdminScoreDraft).toHaveBeenCalledWith(ATTEMPT_ID);
    });
  });

  it('doubleSubmitOnlyCallsApiOnce', async () => {
    mockBase([item()]);
    const pending = deferred<SchoolAdminScoreAttemptDetail>();
    vi.mocked(entryApi.submitSchoolAdminScoreDraft).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await wrapper.find('.submit-entry-button').trigger('click');
      await flushPromises();
      const button = bodyButton('.confirm-submit-entry');
      button.click();
      button.click();
      expect(entryApi.submitSchoolAdminScoreDraft).toHaveBeenCalledTimes(1);
      pending.resolve(detail('PENDING_REVIEW'));
      await flushPromises();
    });
  });

  it('rejectedEntryShowsReviewReason', async () => {
    mockBase([item('REJECTED')]);
    vi.mocked(reviewApi.fetchSchoolAdminScoreAttempt).mockResolvedValue(
      detail('REJECTED', {
        reviewHistory: [
          {
            reviewRecordId: 'review-1',
            reviewerId: 'reviewer-1',
            reviewerName: 'Admin Wang',
            reviewResult: 'REJECTED',
            reviewComment: '请核对原始记录',
            rejectReason: '成绩证据不一致',
            reviewedAt: '2026-07-30T10:00:00Z',
          },
        ],
      }),
    );
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await wrapper.find('.revise-entry-button').trigger('click');
      await flushPromises();
      const dialog = document.body.querySelector('.score-entry-dialog');
      expect(dialog?.textContent).toContain('成绩证据不一致');
      expect(dialog?.textContent).toContain('请核对原始记录');
    });
  });

  it('rejectedEntryCanBeRevisedToDraft', async () => {
    mockBase([item('REJECTED')]);
    vi.mocked(reviewApi.fetchSchoolAdminScoreAttempt).mockResolvedValue(
      detail('REJECTED'),
    );
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await wrapper.find('.revise-entry-button').trigger('click');
      await flushPromises();
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(entryApi.updateSchoolAdminScoreDraft).toHaveBeenCalledWith(
        ATTEMPT_ID,
        expect.objectContaining({ integerValue: 100 }),
      );
    });
  });

  it('pendingEntryHidesEditAction', async () => {
    mockBase([item('PENDING_REVIEW')]);
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      expect(wrapper.find('.edit-entry-button').exists()).toBe(false);
      expect(wrapper.find('.revise-entry-button').exists()).toBe(false);
      expect(wrapper.find('.pending-entry-hint').text()).toContain('等待其他管理员审核');
    });
  });

  it('approvedEntryIsReadOnly', async () => {
    mockBase([item('APPROVED')]);
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      expect(wrapper.find('.edit-entry-button').exists()).toBe(false);
      expect(wrapper.find('.submit-entry-button').exists()).toBe(false);
      expect(wrapper.find('.readonly-entry-hint').text()).toBe('只读');
      expect(wrapper.find('.my-view-button').exists()).toBe(true);
    });
  });

  it('projectOptionFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(entryApi.fetchScoreEntryProjects)
      .mockRejectedValueOnce(new ApiError(500, '项目候选失败'))
      .mockResolvedValue(page([project()]));
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '项目候选失败',
      );
      bodyButton('.project-options-retry').click();
      await flushPromises();
      expect(entryApi.fetchScoreEntryProjects).toHaveBeenCalledTimes(2);
    });
  });

  it('participantOptionFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(entryApi.fetchScoreEntryParticipants)
      .mockRejectedValueOnce(new ApiError(500, '学生候选失败'))
      .mockResolvedValue(page([participant()]));
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper);
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '学生候选失败',
      );
      bodyButton('.participant-options-retry').click();
      await flushPromises();
      expect(entryApi.fetchScoreEntryParticipants).toHaveBeenCalledTimes(2);
    });
  });

  it('createFailureKeepsDialogOpen', async () => {
    mockBase();
    vi.mocked(entryApi.createSchoolAdminScoreDraft).mockRejectedValue(
      new ApiError(409, '草稿创建冲突'),
    );
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await fillRequiredFields(wrapper);
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(document.body.querySelector('.score-entry-dialog')).not.toBeNull();
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '草稿创建冲突',
      );
    });
  });

  it('updateFailureKeepsDialogOpen', async () => {
    mockBase([item()]);
    vi.mocked(entryApi.updateSchoolAdminScoreDraft).mockRejectedValue(
      new ApiError(409, '草稿更新冲突'),
    );
    await withMounted(async wrapper => {
      await clickTab(wrapper, '我的录入');
      await wrapper.find('.edit-entry-button').trigger('click');
      await flushPromises();
      bodyButton('.save-entry-draft').click();
      await flushPromises();
      expect(document.body.querySelector('.score-entry-dialog')).not.toBeNull();
      expect(document.body.querySelector('.score-entry-dialog')?.textContent).toContain(
        '草稿更新冲突',
      );
    });
  });

  it('noUnhandledErrorsFromRealSelectAndDialog', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await openEntry(wrapper);
      await selectProject(wrapper, 'GRADE');
      await selectParticipant();
      await emitScoreValue(wrapper, 'GRADE', 'A');
      await chooseOption('.entry-time-source', '其他');
      expect(document.body.querySelector('.grade-score-select')?.textContent).toContain(
        'A',
      );
      expect(unhandledErrors).toHaveLength(0);
    });
  });
});
