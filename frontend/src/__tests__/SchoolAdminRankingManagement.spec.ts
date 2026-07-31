import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '@/api/http';
import * as achievementApi from '@/api/school-admin-achievement';
import * as api from '@/api/school-admin-ranking';
import type {
  RankingPreviewResult,
  RankingProjectItem,
  RankingVersionDetail,
  RankingVersionSummary,
} from '@/types/school-admin-ranking';
import SchoolAdminRankingManagement from '@/views/workbench/SchoolAdminRankingManagement.vue';
import type { SchoolAdminAchievementDetail } from '@/types/student-achievement';

const PROJECT_ID = '11111111-1111-4111-8111-111111111111';
const VERSION_ID = '22222222-2222-4222-8222-222222222222';
const ENTRY_ID = '33333333-3333-4333-8333-333333333333';
const SECOND_ENTRY_ID = '44444444-4444-4444-8444-444444444444';
const FINGERPRINT = 'a'.repeat(64);

let unhandledErrors: unknown[] = [];
let rejectionListener: (event: PromiseRejectionEvent) => void;
let errorListener: (event: ErrorEvent) => void;

function project(overrides: Partial<RankingProjectItem> = {}): RankingProjectItem {
  return {
    activityProjectId: PROJECT_ID,
    activityId: 'activity-1',
    activityTitle: '校园跳绳赛',
    executionStatus: 'ENDED',
    projectId: 'challenge-1',
    projectName: '一分钟跳绳',
    scoreStorageType: 'INTEGER',
    scoreUnit: '次',
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    allowTie: true,
    approvedEffectiveScoreCount: 2,
    pendingReviewCount: 0,
    rankingStatus: 'CURRENT',
    currentVersionId: VERSION_ID,
    currentVersionNumber: 1,
    currentVersionEntryCount: 2,
    currentPublishedAt: '2026-07-30T08:00:00Z',
    lastVersionStatus: 'PUBLISHED',
    canPreview: true,
    canPublish: true,
    ...overrides,
  };
}

function preview(overrides: Partial<RankingPreviewResult> = {}): RankingPreviewResult {
  return {
    activityProjectId: PROJECT_ID,
    activityTitle: '校园跳绳赛',
    projectName: '一分钟跳绳',
    scoreStorageType: 'INTEGER',
    scoreUnit: '次',
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    tiePolicy: 'COMPETITION',
    sourceFingerprint: FINGERPRINT,
    totalRanked: 2,
    pendingReviewCount: 0,
    publishable: true,
    warnings: [],
    entries: [
      {
        rankPosition: 1,
        studentId: 'student-2',
        studentDisplayName: 'Bob',
        schoolName: '学校',
        scoreDisplayValue: '99',
        scoreAttemptId: 'attempt-2',
        scoreBusinessTime: '2026-07-30T08:01:00Z',
      },
      {
        rankPosition: 2,
        studentId: 'student-1',
        studentDisplayName: 'Alice',
        schoolName: '学校',
        scoreDisplayValue: '98',
        scoreAttemptId: 'attempt-1',
        scoreBusinessTime: '2026-07-30T08:02:00Z',
      },
    ],
    ...overrides,
  };
}

function version(overrides: Partial<RankingVersionDetail> = {}): RankingVersionDetail {
  return {
    versionId: VERSION_ID,
    versionNumber: 1,
    versionStatus: 'PUBLISHED',
    entryCount: 2,
    publishedBy: 'admin-1',
    publishedByName: 'Admin Li',
    publishedAt: '2026-07-30T08:10:00Z',
    withdrawnBy: null,
    withdrawnByName: null,
    withdrawnAt: null,
    withdrawalReason: null,
    createdReason: 'MANUAL',
    activityProjectId: PROJECT_ID,
    activityTitle: '校园跳绳赛',
    projectName: '一分钟跳绳',
    scoreStorageType: 'INTEGER',
    scoreUnit: '次',
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: 'BEST',
    tiePolicy: 'COMPETITION',
    gradeOrder: null,
    allowTie: true,
    decimalPlaces: 0,
    currentRuleVersionId: 'rule-1',
    sourceFingerprint: FINGERPRINT,
    entries: preview().entries.map((entry, index) => ({
      ...entry,
      rankingEntryId: index === 0 ? ENTRY_ID : SECOND_ENTRY_ID,
    })),
    ...overrides,
  };
}

function summary(overrides: Partial<RankingVersionSummary> = {}): RankingVersionSummary {
  const current = version(overrides);
  return {
    versionId: current.versionId,
    versionNumber: current.versionNumber,
    versionStatus: current.versionStatus,
    entryCount: current.entryCount,
    publishedBy: current.publishedBy,
    publishedByName: current.publishedByName,
    publishedAt: current.publishedAt,
    withdrawnBy: current.withdrawnBy,
    withdrawnByName: current.withdrawnByName,
    withdrawnAt: current.withdrawnAt,
    withdrawalReason: current.withdrawalReason,
    createdReason: current.createdReason,
  };
}

function page(items: RankingProjectItem[] = [project()], totalElements = items.length) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
    hasNext: totalElements > 20,
  };
}

function historyPage(items: RankingVersionSummary[] = [summary()]) {
  return {
    items,
    page: 0,
    size: 100,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
  };
}

function mockBase(rows: RankingProjectItem[] = [project()], total = rows.length) {
  vi.spyOn(api, 'fetchRankingProjects').mockResolvedValue(page(rows, total));
  vi.spyOn(api, 'fetchRankingProject').mockResolvedValue({
    ...project(),
    activityStartTime: '2026-07-30T07:00:00Z',
    activityEndTime: '2026-07-30T08:00:00Z',
    location: '体育馆',
    projectDescription: '项目说明',
    rulesText: '规则',
    gradeOrder: null,
    decimalPlaces: 0,
    currentRuleVersionId: 'rule-1',
    lastPublishedBy: 'admin-1',
    lastPublishedByName: 'Admin Li',
    lastWithdrawalReason: null,
  });
  vi.spyOn(api, 'previewRanking').mockResolvedValue(preview());
  vi.spyOn(api, 'publishRanking').mockResolvedValue(version());
  vi.spyOn(api, 'fetchCurrentRanking').mockResolvedValue(version());
  vi.spyOn(api, 'fetchRankingVersions').mockResolvedValue(historyPage());
  vi.spyOn(api, 'fetchRankingVersion').mockResolvedValue(version());
  vi.spyOn(api, 'withdrawRanking').mockResolvedValue();
  vi.spyOn(
    achievementApi,
    'fetchRankingVersionAchievementStatuses',
  ).mockResolvedValue([]);
  vi.spyOn(
    achievementApi,
    'issueAchievementForRankingEntry',
  ).mockResolvedValue(achievementDetail());
  vi.spyOn(
    achievementApi,
    'fetchSchoolAchievementRecord',
  ).mockResolvedValue(achievementDetail());
}

function achievementDetail(
  overrides: Partial<SchoolAdminAchievementDetail> = {},
): SchoolAdminAchievementDetail {
  return {
    recordId: '55555555-5555-4555-8555-555555555555',
    activityProjectId: PROJECT_ID,
    rankingVersionId: VERSION_ID,
    rankingVersionNumber: 1,
    rankingEntryId: ENTRY_ID,
    studentId: 'student-2',
    studentDisplayName: 'Bob',
    schoolName: '学校',
    activityTitle: '校园跳绳赛',
    projectName: '一分钟跳绳',
    rankPosition: 1,
    scoreDisplayValue: '99',
    scoreStorageType: 'INTEGER',
    recordTitle: '校园跳绳赛 · 一分钟跳绳 · 第1名',
    verificationCode: 'a'.repeat(32),
    status: 'ACTIVE',
    issuedAt: '2026-07-30T08:20:00Z',
    issuedBy: 'admin-1',
    issuedByName: 'Admin Li',
    revokedAt: null,
    revokedBy: null,
    revocationReason: null,
    created: true,
    ...overrides,
  };
}

function cleanupOverlays() {
  document.body
    .querySelectorAll(
      '.el-overlay,.el-popper-container,.el-select__popper,.el-tooltip__popper,.el-message',
    )
    .forEach(element => element.remove());
}

async function withMounted(
  run: (wrapper: ReturnType<typeof mount>) => Promise<void>,
) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const wrapper = mount(SchoolAdminRankingManagement, {
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

function setBodyInput(selector: string, value: string) {
  const input = document.body.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector);
  expect(input).not.toBeNull();
  if (input) {
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
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

describe('SchoolAdminRankingManagement', () => {
  it('rankingProjectsLoad', async () => {
    mockBase();
    await withMounted(async wrapper => {
      expect(api.fetchRankingProjects).toHaveBeenCalledWith({}, 0, 20);
      expect(wrapper.find('.ranking-project-table').text()).toContain('校园跳绳赛');
    });
  });

  it('filtersSendCorrectParameters', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await chooseOption('.execution-status-filter', '已结束');
      await chooseOption('.ranking-status-filter', '当前版本');
      await wrapper.find('.keyword-filter input').setValue('  跳绳  ');
      await wrapper.find('.search-button').trigger('click');
      await flushPromises();
      expect(api.fetchRankingProjects).toHaveBeenLastCalledWith(
        { executionStatus: 'ENDED', rankingStatus: 'CURRENT', keyword: '跳绳' },
        0,
        20,
      );
    });
  });

  it('resetClearsFilters', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await chooseOption('.execution-status-filter', '已结束');
      await wrapper.find('.keyword-filter input').setValue('跳绳');
      await wrapper.find('.reset-button').trigger('click');
      await flushPromises();
      expect(api.fetchRankingProjects).toHaveBeenLastCalledWith({}, 0, 20);
    });
  });

  it('paginationReloadsCorrectPage', async () => {
    mockBase([project()], 41);
    await withMounted(async wrapper => {
      await wrapper.find('.btn-next').trigger('click');
      await flushPromises();
      expect(api.fetchRankingProjects).toHaveBeenLastCalledWith({}, 1, 20);
    });
  });

  it('noRankingProjectShowsDisabledState', async () => {
    mockBase([project({
      comparisonDirection: 'NO_RANKING',
      rankingStatus: 'DISABLED',
      canPreview: false,
      canPublish: false,
    })]);
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('该项目不参与排名');
      expect(wrapper.find<HTMLButtonElement>('.preview-button').element.disabled).toBe(true);
      expect(wrapper.find<HTMLButtonElement>('.publish-button').element.disabled).toBe(true);
    });
  });

  it('pendingReviewDisablesPublish', async () => {
    mockBase([project({ pendingReviewCount: 2, canPublish: false })]);
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('仍有待审核成绩');
      expect(wrapper.find<HTMLButtonElement>('.publish-button').element.disabled).toBe(true);
    });
  });

  it('inProgressProjectAllowsPreviewOnly', async () => {
    mockBase([project({
      executionStatus: 'IN_PROGRESS',
      canPreview: true,
      canPublish: false,
    })]);
    await withMounted(async wrapper => {
      expect(wrapper.find<HTMLButtonElement>('.preview-button').element.disabled).toBe(false);
      expect(wrapper.find<HTMLButtonElement>('.publish-button').element.disabled).toBe(true);
      expect(wrapper.text()).toContain('活动结束后可发布');
    });
  });

  it('previewDialogLoadsEntries', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      expect(api.previewRanking).toHaveBeenCalledWith(PROJECT_ID);
      expect(document.body.querySelector('.ranking-preview-dialog')?.textContent).toContain('Bob');
    });
  });

  it('previewKeepsBackendOrder', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      const text = document.body.querySelector('.ranking-entry-table')?.textContent ?? '';
      expect(text.indexOf('Bob')).toBeLessThan(text.indexOf('Alice'));
    });
  });

  it('previewShowsCompetitionTiePolicy', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.ranking-preview-dialog')?.textContent)
        .toContain('竞赛排名（允许并列）');
    });
  });

  it('previewShowsNoTiePolicy', async () => {
    mockBase();
    vi.mocked(api.previewRanking).mockResolvedValue(
      preview({ tiePolicy: 'EARLIER_BUSINESS_TIME' }),
    );
    await withMounted(async wrapper => {
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.ranking-preview-dialog')?.textContent)
        .toContain('较早业务时间优先');
    });
  });

  it('previewWarningShowsPendingCount', async () => {
    mockBase();
    vi.mocked(api.previewRanking).mockResolvedValue(
      preview({ pendingReviewCount: 1, publishable: false, warnings: ['仍有待审核成绩'] }),
    );
    await withMounted(async wrapper => {
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.ranking-preview-dialog')?.textContent)
        .toContain('仍有待审核成绩');
    });
  });

  it('publishAutomaticallyPreviewsWhenNeeded', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.publish-button').trigger('click');
      await flushPromises();
      expect(api.previewRanking).toHaveBeenCalledWith(PROJECT_ID);
      expect(document.body.querySelector('.ranking-publish-dialog')).not.toBeNull();
    });
  });

  it('publishUsesPreviewFingerprint', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.publish-button').trigger('click');
      await flushPromises();
      bodyButton('.confirm-publish').click();
      await flushPromises();
      expect(api.publishRanking).toHaveBeenCalledWith(PROJECT_ID, {
        expectedSourceFingerprint: FINGERPRINT,
      });
    });
  });

  it('doublePublishOnlyCallsApiOnce', async () => {
    mockBase();
    const pending = deferred<RankingVersionDetail>();
    vi.mocked(api.publishRanking).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await wrapper.find('.publish-button').trigger('click');
      await flushPromises();
      const button = bodyButton('.confirm-publish');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      expect(api.publishRanking).toHaveBeenCalledTimes(1);
      pending.resolve(version());
      await flushPromises();
    });
  });

  it('publishSuccessReloadsList', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.publish-button').trigger('click');
      await flushPromises();
      bodyButton('.confirm-publish').click();
      await flushPromises();
      expect(api.fetchRankingProjects).toHaveBeenCalledTimes(2);
      expect(document.body.querySelector('.ranking-current-dialog')).not.toBeNull();
    });
  });

  it('staleFingerprintClearsPreview', async () => {
    mockBase();
    vi.mocked(api.publishRanking).mockRejectedValue(
      new ApiError(409, 'changed', 'RANKING_SOURCE_CHANGED'),
    );
    await withMounted(async wrapper => {
      await wrapper.find('.publish-button').trigger('click');
      await flushPromises();
      bodyButton('.confirm-publish').click();
      await flushPromises();
      await wrapper.find('.publish-button').trigger('click');
      await flushPromises();
      expect(api.previewRanking).toHaveBeenCalledTimes(2);
    });
  });

  it('currentVersionUsesSnapshotApi', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();
      expect(api.fetchCurrentRanking).toHaveBeenCalledWith(PROJECT_ID);
      expect(api.previewRanking).not.toHaveBeenCalled();
      expect(document.body.querySelector('.ranking-current-dialog')?.textContent)
        .toContain('Admin Li');
    });
  });

  it('currentRankingLoadsAchievementStatusesOnce', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();

      expect(
        achievementApi.fetchRankingVersionAchievementStatuses,
      ).toHaveBeenCalledTimes(1);
      expect(
        achievementApi.fetchRankingVersionAchievementStatuses,
      ).toHaveBeenCalledWith(VERSION_ID);
    });
  });

  it('unissuedEntryShowsIssueButtonAndConfirmationSnapshot', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();

      bodyButton(
        `.ranking-current-dialog [data-ranking-entry-id="${ENTRY_ID}"]`,
      ).click();
      await flushPromises();

      const dialog = document.body.querySelector('.achievement-issue-dialog');
      expect(dialog).not.toBeNull();
      expect(dialog?.textContent).toContain('Bob');
      expect(dialog?.textContent).toContain('校园跳绳赛');
      expect(dialog?.textContent).toContain('一分钟跳绳');
      expect(dialog?.textContent).toContain('第1名');
      expect(dialog?.textContent).toContain('99');
    });
  });

  it('issuedEntryShowsExistingStatus', async () => {
    mockBase();
    vi.mocked(
      achievementApi.fetchRankingVersionAchievementStatuses,
    ).mockResolvedValue([
      {
        rankingEntryId: ENTRY_ID,
        achievementRecordId: 'record-1',
        achievementStatus: 'ACTIVE',
        verificationCode: 'a'.repeat(32),
        issuedAt: '2026-07-30T08:20:00Z',
      },
    ]);

    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();

      expect(
        document.body.querySelector('.ranking-current-dialog')?.textContent,
      ).toContain('已签发 · 有效');
      expect(
        document.body.querySelector(
          `.ranking-current-dialog [data-ranking-entry-id="${ENTRY_ID}"]`,
        ),
      ).toBeNull();
    });
  });

  it('doubleIssueOnlyCallsApiOnceAndUsesRankingEntryId', async () => {
    mockBase();
    const pending = deferred<SchoolAdminAchievementDetail>();
    vi.mocked(
      achievementApi.issueAchievementForRankingEntry,
    ).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();
      bodyButton(
        `.ranking-current-dialog [data-ranking-entry-id="${ENTRY_ID}"]`,
      ).click();
      await flushPromises();

      const confirm = bodyButton('.confirm-achievement-issue');
      confirm.click();
      confirm.click();
      await nextTick();

      expect(
        achievementApi.issueAchievementForRankingEntry,
      ).toHaveBeenCalledTimes(1);
      expect(
        achievementApi.issueAchievementForRankingEntry,
      ).toHaveBeenCalledWith(ENTRY_ID);
      pending.resolve(achievementDetail());
      await flushPromises();
    });
  });

  it('issueSuccessUpdatesEntryWithoutReloadAndHasNoPayload', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();
      bodyButton(
        `.ranking-current-dialog [data-ranking-entry-id="${ENTRY_ID}"]`,
      ).click();
      await flushPromises();
      bodyButton('.confirm-achievement-issue').click();
      await flushPromises();

      expect(
        achievementApi.issueAchievementForRankingEntry,
      ).toHaveBeenCalledWith(ENTRY_ID);
      expect(
        vi.mocked(achievementApi.issueAchievementForRankingEntry).mock
          .calls[0],
      ).toHaveLength(1);
      expect(api.fetchCurrentRanking).toHaveBeenCalledTimes(1);
      expect(
        achievementApi.fetchRankingVersionAchievementStatuses,
      ).toHaveBeenCalledTimes(1);
      expect(
        document.body.querySelector('.ranking-current-dialog')?.textContent,
      ).toContain('已签发 · 有效');
    });
  });

  it('historyLoadsVersions', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.history-button').trigger('click');
      await flushPromises();
      expect(api.fetchRankingVersions).toHaveBeenCalledWith(PROJECT_ID, 0, 100);
      expect(document.body.querySelector('.history-table')?.textContent).toContain('V1');
    });
  });

  it('historicalVersionLoadsByVersionId', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.history-button').trigger('click');
      await flushPromises();
      bodyButton('.version-detail-button').click();
      await flushPromises();
      expect(api.fetchRankingVersion).toHaveBeenCalledWith(VERSION_ID);
    });
  });

  it('historicalVersionIsNotRecalculated', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.history-button').trigger('click');
      await flushPromises();
      bodyButton('.version-detail-button').click();
      await flushPromises();
      expect(api.fetchRankingVersion).toHaveBeenCalledTimes(1);
      expect(api.previewRanking).not.toHaveBeenCalled();
    });
  });

  it('withdrawReasonIsRequired', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.withdraw-button').trigger('click');
      await flushPromises();
      bodyButton('.confirm-withdraw').click();
      await flushPromises();
      expect(api.withdrawRanking).not.toHaveBeenCalled();
      expect(document.body.querySelector('.ranking-withdraw-dialog')?.textContent)
        .toContain('请输入撤回原因');
    });
  });

  it('withdrawCallsCorrectApi', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.withdraw-button').trigger('click');
      await flushPromises();
      setBodyInput('.withdraw-reason textarea', '  发现遗漏  ');
      bodyButton('.confirm-withdraw').click();
      await flushPromises();
      expect(api.withdrawRanking).toHaveBeenCalledWith(PROJECT_ID, {
        reason: '发现遗漏',
      });
    });
  });

  it('doubleWithdrawOnlyCallsApiOnce', async () => {
    mockBase();
    const pending = deferred<void>();
    vi.mocked(api.withdrawRanking).mockReturnValue(pending.promise);
    await withMounted(async wrapper => {
      await wrapper.find('.withdraw-button').trigger('click');
      await flushPromises();
      setBodyInput('.withdraw-reason textarea', '发现遗漏');
      const button = bodyButton('.confirm-withdraw');
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      expect(api.withdrawRanking).toHaveBeenCalledTimes(1);
      pending.resolve();
      await flushPromises();
    });
  });

  it('withdrawKeepsHistoryVisible', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await wrapper.find('.history-button').trigger('click');
      await flushPromises();
      await wrapper.find('.withdraw-button').trigger('click');
      await flushPromises();
      setBodyInput('.withdraw-reason textarea', '发现遗漏');
      bodyButton('.confirm-withdraw').click();
      await flushPromises();
      expect(document.body.querySelector('.ranking-history-dialog')).not.toBeNull();
      expect(api.fetchRankingVersions).toHaveBeenCalledTimes(2);
    });
  });

  it('listFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchRankingProjects)
      .mockRejectedValueOnce(new ApiError(500, '列表失败'))
      .mockResolvedValue(page());
    await withMounted(async wrapper => {
      expect(wrapper.text()).toContain('列表失败');
      await wrapper.find('.list-retry').trigger('click');
      await flushPromises();
      expect(wrapper.find('.ranking-project-table').text()).toContain('校园跳绳赛');
    });
  });

  it('previewFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.previewRanking)
      .mockRejectedValueOnce(new ApiError(500, '预览失败'))
      .mockResolvedValue(preview());
    await withMounted(async wrapper => {
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelector('.ranking-preview-dialog')?.textContent)
        .toContain('预览失败');
      bodyButton('.preview-retry').click();
      await flushPromises();
      expect(api.previewRanking).toHaveBeenCalledTimes(2);
    });
  });

  it('currentVersionFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchCurrentRanking)
      .mockRejectedValueOnce(new ApiError(500, '当前版本失败'))
      .mockResolvedValue(version());
    await withMounted(async wrapper => {
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();
      bodyButton('.current-retry').click();
      await flushPromises();
      expect(api.fetchCurrentRanking).toHaveBeenCalledTimes(2);
    });
  });

  it('historyFailureShowsRetry', async () => {
    mockBase();
    vi.mocked(api.fetchRankingVersions)
      .mockRejectedValueOnce(new ApiError(500, '历史失败'))
      .mockResolvedValue(historyPage());
    await withMounted(async wrapper => {
      await wrapper.find('.history-button').trigger('click');
      await flushPromises();
      bodyButton('.history-retry').click();
      await flushPromises();
      expect(api.fetchRankingVersions).toHaveBeenCalledTimes(2);
    });
  });

  it('noUnhandledErrorsFromRealDialogsAndSelects', async () => {
    mockBase();
    await withMounted(async wrapper => {
      await chooseOption('.execution-status-filter', '已结束');
      await wrapper.find('.preview-button').trigger('click');
      await flushPromises();
      await wrapper.find('.current-button').trigger('click');
      await flushPromises();
      expect(document.body.querySelectorAll('.el-overlay').length).toBeGreaterThan(0);
      expect(unhandledErrors).toHaveLength(0);
    });
  });
});
