import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';
import ElementPlus from 'element-plus';
import SchoolAdminScoreReview from '@/views/workbench/SchoolAdminScoreReview.vue';
import * as api from '@/api/school-admin-score-review';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import type {
  SchoolAdminScoreAttemptDetail,
  SchoolAdminScoreAttemptItem,
} from '@/types/school-admin-score-review';

const ADMIN_ID = '11111111-1111-4111-8111-111111111111';
const ENTRANT_ID = '22222222-2222-4222-8222-222222222222';
const ATTEMPT_ID = '33333333-3333-4333-8333-333333333333';

let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function item(
  overrides: Partial<SchoolAdminScoreAttemptItem> = {},
): SchoolAdminScoreAttemptItem {
  return {
    attemptId: ATTEMPT_ID,
    schoolId: '44444444-4444-4444-8444-444444444444',
    activityId: '55555555-5555-4555-8555-555555555555',
    activityTitle: '校运会',
    activityProjectId: '66666666-6666-4666-8666-666666666666',
    projectId: '77777777-7777-4777-8777-777777777777',
    projectName: '跳绳',
    studentId: '88888888-8888-4888-8888-888888888888',
    studentName: 'Alice',
    attemptNumber: 2,
    scoreStorageType: 'INTEGER',
    displayValue: '100',
    scoreUnit: '次',
    scoreBusinessTime: '2026-07-20T08:00:00.000Z',
    timeSource: 'TEACHER',
    status: 'PENDING_REVIEW',
    currentEffective: false,
    enteredBy: ENTRANT_ID,
    enteredByName: 'Teacher Zhang',
    submittedAt: '2026-07-20T09:00:00.000Z',
    createdAt: '2026-07-20T08:30:00.000Z',
    effectiveScoreRule: 'BEST',
    comparisonDirection: 'HIGHER_BETTER',
    ...overrides,
  };
}

function detail(
  overrides: Partial<SchoolAdminScoreAttemptDetail> = {},
): SchoolAdminScoreAttemptDetail {
  return {
    ...item(overrides),
    integerValue: 100,
    decimalValue: null,
    durationMs: null,
    grade: null,
    decimalPlaces: 0,
    gradeOrder: null,
    allowTie: true,
    reviewHistory: [],
    ...overrides,
  };
}

function page(
  items: SchoolAdminScoreAttemptItem[] = [item()],
  totalElements = items.length,
) {
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
  rows: SchoolAdminScoreAttemptItem[] = [item()],
  scoreDetail: SchoolAdminScoreAttemptDetail = detail(),
  total = rows.length,
) {
  vi.spyOn(api, 'fetchSchoolAdminScoreAttempts').mockResolvedValue(page(rows, total));
  vi.spyOn(api, 'fetchSchoolAdminScoreAttempt').mockResolvedValue(scoreDetail);
  vi.spyOn(api, 'approveSchoolAdminScoreAttempt').mockResolvedValue(
    detail({ status: 'APPROVED', currentEffective: true }),
  );
  vi.spyOn(api, 'rejectSchoolAdminScoreAttempt').mockResolvedValue(
    detail({ status: 'REJECTED' }),
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
  userId = ADMIN_ID,
) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const auth = useAuthStore();
  auth.user = {
    userId,
    username: 'review-admin',
    accountStatus: 'NORMAL',
    platformRole: null,
    roles: ['SCHOOL_ADMIN'],
    schoolMemberships: [],
    primaryRole: 'SCHOOL_ADMIN',
    primarySchoolId: '44444444-4444-4444-8444-444444444444',
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
  ).find(element => element.textContent?.trim() === label);
  expect(option).not.toBeUndefined();
  option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await nextTick();
  await flushPromises();
}

function setBodyInput(selector: string, value: string) {
  const input = document.body.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector);
  expect(input).not.toBeNull();
  if (input) {
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
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

describe('SchoolAdminScoreReview', () => {
  it('pendingScoresLoadByDefault', async () => {
    mockBase();
    await withMounted(async wrapper => {
      expect(api.fetchSchoolAdminScoreAttempts).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'PENDING_REVIEW' }),
        0,
        20,
      );
      expect(wrapper.find('.score-table').text()).toContain('Alice');
    });
  });

  it('filtersSendCorrectParameters', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.activity-filter input').setValue(' activity-1 ');
      await wrapper.find('.project-filter input').setValue(' project-1 ');
      await wrapper.find('.keyword-filter input').setValue(' Alice ');
      await wrapper.find('.search-button').trigger('click');
      await flushPromises();
      expect(api.fetchSchoolAdminScoreAttempts).toHaveBeenLastCalledWith(
        {
          status: 'PENDING_REVIEW',
          activityId: 'activity-1',
          projectId: 'project-1',
          keyword: 'Alice',
        },
        0,
        20,
      );
    });
  });

  it('resetRestoresPendingReviewFilter', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await chooseOption('.status-filter', '已通过');
      await wrapper.find('.reset-button').trigger('click');
      await flushPromises();
      expect(api.fetchSchoolAdminScoreAttempts).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'PENDING_REVIEW' }),
        0,
        20,
      );
    });
  });

  it('paginationReloadsCorrectPage', async () => {
    mockBase([item()], detail(), 41);
    await withMounted(async wrapper => {
      await wrapper.find('.btn-next').trigger('click');
      await flushPromises();
      expect(api.fetchSchoolAdminScoreAttempts).toHaveBeenLastCalledWith(
        expect.any(Object),
        1,
        20,
      );
    });
  });

  it('scoreValueRendersForInteger', async () => {
    mockBase([item({ scoreStorageType: 'INTEGER', displayValue: '100' })]);
    await withMounted(async wrapper => {
      expect(wrapper.find('.score-value').text()).toContain('100');
    });
  });

  it('scoreValueRendersForDecimal', async () => {
    mockBase([item({ scoreStorageType: 'DECIMAL', displayValue: '12.35' })]);
    await withMounted(async wrapper => {
      expect(wrapper.find('.score-value').text()).toContain('12.35');
    });
  });

  it('scoreValueRendersForDuration', async () => {
    mockBase([item({ scoreStorageType: 'DURATION', displayValue: '1分2秒', scoreUnit: null })]);
    await withMounted(async wrapper => {
      expect(wrapper.find('.score-value').text()).toContain('1分2秒');
    });
  });

  it('scoreValueRendersForGrade', async () => {
    mockBase([item({ scoreStorageType: 'GRADE', displayValue: 'A', scoreUnit: null })]);
    await withMounted(async wrapper => {
      expect(wrapper.find('.score-value').text()).toBe('A');
    });
  });

  it('detailDialogLoadsReviewHistory', async () => {
    mockBase([item()], detail({
      reviewHistory: [{
        reviewRecordId: 'history-1',
        reviewerId: ADMIN_ID,
        reviewerName: 'Admin Li',
        reviewResult: 'REJECTED',
        reviewComment: '请重试',
        rejectReason: '证据不一致',
        reviewedAt: '2026-07-21T08:00:00.000Z',
      }],
    }));
    await withMounted(async wrapper => {
      await wrapper.find('.view-button').trigger('click');
      await flushPromises();
      expect(api.fetchSchoolAdminScoreAttempt).toHaveBeenCalledWith(ATTEMPT_ID);
      expect(document.body.querySelector('.score-detail-dialog')?.textContent).toContain('Admin Li');
      expect(document.body.querySelector('.review-history')?.textContent).toContain('证据不一致');
    });
  });

  it('ownEnteredScoreDisablesReviewButtons', async () => {
    mockBase([item({ enteredBy: ADMIN_ID })]);
    await withMounted(async wrapper => {
      expect(wrapper.find<HTMLButtonElement>('.approve-button').element.disabled).toBe(true);
      expect(wrapper.find<HTMLButtonElement>('.reject-button').element.disabled).toBe(true);
      expect(wrapper.text()).toContain('不能审核本人录入的成绩');
    });
  });

  it('differentAdminCanOpenApproveDialog', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.approve-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.approve-dialog')).not.toBeNull();
      expect(document.body.querySelector('.approve-dialog')?.textContent).toContain('审核通过');
    });
  });

  it('bestRuleDoesNotShowDesignationChoice', async () => {
    mockBase([item({ effectiveScoreRule: 'BEST' })]);
    await withMounted(async wrapper => {
      await wrapper.find('.approve-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.designation-choice')).toBeNull();
    });
  });

  it('lastRuleDoesNotShowDesignationChoice', async () => {
    mockBase([item({ effectiveScoreRule: 'LAST' })]);
    await withMounted(async wrapper => {
      await wrapper.find('.approve-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.designation-choice')).toBeNull();
    });
  });

  it('adminDesignatedRequiresExplicitChoice', async () => {
    mockBase([item({ effectiveScoreRule: 'ADMIN_DESIGNATED' })]);
    await withMounted(async wrapper => {
      await wrapper.find('.approve-button').trigger('click');
      await flushPromises();
      bodyButton('.confirm-approve').click();
      await flushPromises();
      expect(api.approveSchoolAdminScoreAttempt).not.toHaveBeenCalled();
      expect(document.body.querySelector('.approve-dialog')?.textContent)
        .toContain('请选择是否设为当前有效成绩');
    });
  });

  it('approveCallsCorrectApiAndReloads', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.approve-button').trigger('click');
      await flushPromises();
      setBodyInput('.approve-comment textarea', '  verified  ');
      bodyButton('.confirm-approve').click();
      await flushPromises();
      expect(api.approveSchoolAdminScoreAttempt).toHaveBeenCalledWith(ATTEMPT_ID, {
        reviewComment: 'verified',
      });
      expect(api.fetchSchoolAdminScoreAttempts).toHaveBeenCalledTimes(2);
      expect(api.fetchSchoolAdminScoreAttempt).toHaveBeenCalledWith(ATTEMPT_ID);
    });
  });

  it('doubleApproveOnlyCallsApiOnce', async () => {
    mockBase();
    const pending = deferred<SchoolAdminScoreAttemptDetail>();
    vi.mocked(api.approveSchoolAdminScoreAttempt).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await wrapper.find('.approve-button').trigger('click');
      await flushPromises();
      const button = bodyButton('.confirm-approve');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      expect(api.approveSchoolAdminScoreAttempt).toHaveBeenCalledTimes(1);
      pending.resolve(detail({ status: 'APPROVED' }));
      await flushPromises();
    });
  });

  it('rejectReasonIsRequired', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.reject-button').trigger('click');
      await flushPromises();
      bodyButton('.confirm-reject').click();
      await flushPromises();
      expect(api.rejectSchoolAdminScoreAttempt).not.toHaveBeenCalled();
      expect(document.body.querySelector('.reject-dialog')?.textContent).toContain('请输入驳回原因');
    });
  });

  it('rejectCallsCorrectApiAndReloads', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.reject-button').trigger('click');
      await flushPromises();
      setBodyInput('.reject-reason textarea', '  证据不一致  ');
      setBodyInput('.reject-comment textarea', '  请重试  ');
      bodyButton('.confirm-reject').click();
      await flushPromises();
      expect(api.rejectSchoolAdminScoreAttempt).toHaveBeenCalledWith(ATTEMPT_ID, {
        rejectReason: '证据不一致',
        reviewComment: '请重试',
      });
      expect(api.fetchSchoolAdminScoreAttempts).toHaveBeenCalledTimes(2);
    });
  });

  it('doubleRejectOnlyCallsApiOnce', async () => {
    mockBase();
    const pending = deferred<SchoolAdminScoreAttemptDetail>();
    vi.mocked(api.rejectSchoolAdminScoreAttempt).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await wrapper.find('.reject-button').trigger('click');
      await flushPromises();
      setBodyInput('.reject-reason textarea', '错误成绩');
      const button = bodyButton('.confirm-reject');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      expect(api.rejectSchoolAdminScoreAttempt).toHaveBeenCalledTimes(1);
      pending.resolve(detail({ status: 'REJECTED' }));
      await flushPromises();
    });
  });

  it('listFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchSchoolAdminScoreAttempts)
      .mockRejectedValueOnce(new ApiError(500, '列表失败'))
      .mockResolvedValue(page([item()]));
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('列表失败');
      await wrapper.find('.list-retry').trigger('click');
      await flushPromises();
      expect(wrapper.find('.score-table').text()).toContain('Alice');
    });
  });

  it('detailFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchSchoolAdminScoreAttempt)
      .mockRejectedValueOnce(new ApiError(500, '详情失败'))
      .mockResolvedValue(detail());
    await withMounted(async wrapper => {
      await wrapper.find('.view-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.score-detail-dialog')?.textContent).toContain('详情失败');
      bodyButton('.detail-retry').click();
      await flushPromises();
      expect(api.fetchSchoolAdminScoreAttempt).toHaveBeenCalledTimes(2);
      expect(document.body.querySelector('.score-detail-dialog')?.textContent).toContain('Alice');
    });
  });

  it('completedScoreHidesReviewActions', async () => {
    mockBase([item({ status: 'APPROVED', currentEffective: true })]);
    await withMounted(async wrapper => {
      expect(wrapper.find('.approve-button').exists()).toBe(false);
      expect(wrapper.find('.reject-button').exists()).toBe(false);
      expect(wrapper.find('.view-button').exists()).toBe(true);
    });
  });
});
