import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import ElementPlus from 'element-plus';
import TeacherScoreEntryList from '@/views/workbench/TeacherScoreEntryList.vue';
import * as scoreApi from '@/api/teacher-score-entry';
import { ApiError } from '@/api/http';
import type {
  TeacherScoreAttemptDetail,
  TeacherScoreAttemptItem,
} from '@/types/teacher-score-entry';
import type { ScoreAttemptStatus } from '@/types/school-admin-score-review';

const ATTEMPT_ID = '11111111-1111-4111-8111-111111111111';
let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function item(
  status: ScoreAttemptStatus = 'DRAFT',
  overrides: Partial<TeacherScoreAttemptItem> = {},
): TeacherScoreAttemptItem {
  return {
    attemptId: ATTEMPT_ID,
    activityProjectId: '22222222-2222-4222-8222-222222222222',
    activityId: '33333333-3333-4333-8333-333333333333',
    activityTitle: '春季校运会',
    schoolId: '44444444-4444-4444-8444-444444444444',
    schoolName: '第一中学',
    projectId: '55555555-5555-4555-8555-555555555555',
    projectName: '跳绳',
    studentId: '66666666-6666-4666-8666-666666666666',
    studentName: 'Alice',
    attemptNumber: 1,
    scoreStorageType: 'INTEGER',
    displayValue: '100',
    scoreUnit: '次',
    scoreBusinessTime: '2026-07-30T08:00:00Z',
    timeSource: 'ON_SITE_RECORD',
    status,
    submittedAt: status === 'DRAFT' ? null : '2026-07-30T09:00:00Z',
    createdAt: '2026-07-30T08:30:00Z',
    updatedAt: '2026-07-30T09:00:00Z',
    currentEffective: status === 'APPROVED',
    ...overrides,
  };
}

function detail(
  status: ScoreAttemptStatus = 'DRAFT',
  overrides: Partial<TeacherScoreAttemptDetail> = {},
): TeacherScoreAttemptDetail {
  return {
    ...item(status, overrides),
    integerValue: 100,
    decimalValue: null,
    durationMs: null,
    grade: null,
    decimalPlaces: 0,
    gradeOrder: null,
    reviewHistory:
      status === 'REJECTED'
        ? [
            {
              reviewRecordId: 'review-1',
              reviewerId: 'admin-1',
              reviewerName: 'Admin Li',
              reviewResult: 'REJECTED',
              reviewComment: '请核对',
              rejectReason: '成绩凭据不一致',
              reviewedAt: '2026-07-30T09:30:00Z',
            },
          ]
        : [],
    ...overrides,
  };
}

function page(items: TeacherScoreAttemptItem[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false,
  };
}

function mockBase(rows: TeacherScoreAttemptItem[] = [item()]): void {
  vi.spyOn(scoreApi, 'fetchMyTeacherScoreAttempts').mockResolvedValue(page(rows));
  vi.spyOn(scoreApi, 'fetchTeacherScoreAttemptDetail').mockResolvedValue(detail());
  vi.spyOn(scoreApi, 'updateTeacherScoreDraft').mockResolvedValue(detail('DRAFT'));
  vi.spyOn(scoreApi, 'submitTeacherScoreDraft').mockResolvedValue(
    detail('PENDING_REVIEW'),
  );
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
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(TeacherScoreEntryList, {
    attachTo: host,
    global: { plugins: [ElementPlus] },
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

async function chooseStatus(label: string): Promise<void> {
  const select = document.body.querySelector<HTMLElement>(
    '.status-filter .el-select__wrapper',
  );
  select?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
  const option = Array.from(
    document.body.querySelectorAll<HTMLElement>('.el-select-dropdown__item'),
  ).find((candidate) => candidate.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await flushPromises();
}

async function setNativeInput(selector: string, value: string): Promise<void> {
  const input = document.body.querySelector<HTMLInputElement>(selector);
  expect(input).not.toBeNull();
  if (!input) return;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  await nextTick();
  await flushPromises();
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

describe('TeacherScoreEntryList', () => {
  it('myEntriesLoad', async () => {
    mockBase();
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain('成绩录入');
      expect(wrapper.text()).toContain('第一中学');
      expect(wrapper.text()).toContain('Alice');
      expect(wrapper.text()).toContain('100次');
    });
  });

  it('statusFilterWorks', async () => {
    mockBase();
    const fetch = vi.mocked(scoreApi.fetchMyTeacherScoreAttempts);
    await withMounted(async (wrapper) => {
      await chooseStatus('已驳回');
      await wrapper.find('.search-button').trigger('click');
      await flushPromises();
      expect(fetch).toHaveBeenLastCalledWith(
        { status: 'REJECTED', keyword: '' },
        0,
        20,
      );
    });
  });

  it('rejectedEntryShowsReason', async () => {
    mockBase([item('REJECTED')]);
    vi.mocked(scoreApi.fetchTeacherScoreAttemptDetail).mockResolvedValue(
      detail('REJECTED'),
    );
    await withMounted(async (wrapper) => {
      await wrapper.find('.view-button').trigger('click');
      await flushPromises();
      expect(document.body.textContent).toContain('成绩凭据不一致');
      expect(document.body.textContent).toContain('Admin Li');
    });
  });

  it('rejectedEntryCanBeUpdatedAndReturnsDraft', async () => {
    mockBase([item('REJECTED')]);
    vi.mocked(scoreApi.fetchTeacherScoreAttemptDetail).mockResolvedValue(
      detail('REJECTED'),
    );
    await withMounted(async (wrapper) => {
      await wrapper.find('.revise-button').trigger('click');
      await flushPromises();
      expect(document.body.textContent).toContain('最近驳回原因：成绩凭据不一致');
      await setNativeInput('.edit-integer-input .el-input__inner', '120');
      document.body
        .querySelector<HTMLButtonElement>('.save-draft-button')
        ?.click();
      await flushPromises();
      expect(scoreApi.updateTeacherScoreDraft).toHaveBeenCalledWith(
        ATTEMPT_ID,
        expect.objectContaining({ integerValue: 120 }),
      );
      expect(scoreApi.submitTeacherScoreDraft).not.toHaveBeenCalled();
    });
  });

  it('draftCanBeResubmitted', async () => {
    mockBase([item('DRAFT')]);
    await withMounted(async (wrapper) => {
      await wrapper.find('.submit-draft-button').trigger('click');
      await flushPromises();
      expect(scoreApi.submitTeacherScoreDraft).toHaveBeenCalledWith(ATTEMPT_ID);
    });
  });

  it.each([
    ['PENDING_REVIEW', '等待学校管理员审核'],
    ['APPROVED', '只读'],
  ] as const)('%s entry is read only', async (status, label) => {
    mockBase([item(status)]);
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain(label);
      expect(wrapper.find('.edit-button').exists()).toBe(false);
      expect(wrapper.find('.revise-button').exists()).toBe(false);
      expect(wrapper.find('.submit-draft-button').exists()).toBe(false);
    });
  });

  it('detailShowsReviewHistory', async () => {
    mockBase([item('REJECTED')]);
    vi.mocked(scoreApi.fetchTeacherScoreAttemptDetail).mockResolvedValue(
      detail('REJECTED'),
    );
    await withMounted(async (wrapper) => {
      await wrapper.find('.view-button').trigger('click');
      await flushPromises();
      expect(document.body.textContent).toContain('审核历史');
      expect(document.body.textContent).toContain('审核驳回');
      expect(document.body.textContent).toContain('请核对');
    });
  });

  it('listFailureShowsRetry', async () => {
    const fetch = vi
      .spyOn(scoreApi, 'fetchMyTeacherScoreAttempts')
      .mockRejectedValueOnce(new ApiError(500, '服务暂不可用'))
      .mockResolvedValueOnce(page([item()]));
    vi.spyOn(scoreApi, 'fetchTeacherScoreAttemptDetail').mockResolvedValue(detail());
    vi.spyOn(scoreApi, 'updateTeacherScoreDraft').mockResolvedValue(detail());
    vi.spyOn(scoreApi, 'submitTeacherScoreDraft').mockResolvedValue(
      detail('PENDING_REVIEW'),
    );
    await withMounted(async (wrapper) => {
      expect(wrapper.text()).toContain('服务暂不可用');
      await wrapper.find('.list-retry').trigger('click');
      await flushPromises();
      expect(fetch).toHaveBeenCalledTimes(2);
      expect(wrapper.text()).toContain('Alice');
    });
  });
});
