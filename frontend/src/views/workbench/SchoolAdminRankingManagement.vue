<template>
  <div class="ranking-management">
    <div class="page-heading">
      <div>
        <h2>排名管理</h2>
        <p>预览当前有效成绩，并发布可追溯的不可变排名版本。</p>
      </div>
    </div>

    <el-card class="filters">
      <el-form :inline="true">
        <el-form-item label="执行状态">
          <el-select
            v-model="filter.executionStatus"
            class="execution-status-filter"
            clearable
            placeholder="全部"
            style="width: 150px"
          >
            <el-option
              v-for="option in executionStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排名状态">
          <el-select
            v-model="filter.rankingStatus"
            class="ranking-status-filter"
            clearable
            placeholder="全部"
            style="width: 150px"
          >
            <el-option
              v-for="option in rankingStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            class="keyword-filter"
            clearable
            maxlength="100"
            placeholder="活动或项目名称"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button class="search-button" type="primary" @click="search">查询</el-button>
          <el-button class="reset-button" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-skeleton v-if="loading" :rows="7" animated />
    <el-result
      v-else-if="listError"
      icon="error"
      title="排名项目加载失败"
      :sub-title="listError"
    >
      <template #extra>
        <el-button class="list-retry" type="primary" @click="loadProjects">重试</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="projects.length === 0" description="暂无排名项目" />
    <template v-else>
      <el-table
        class="ranking-project-table"
        :data="projects"
        row-key="activityProjectId"
      >
        <el-table-column prop="activityTitle" label="活动" min-width="150" />
        <el-table-column prop="projectName" label="项目" min-width="140" />
        <el-table-column label="活动状态" width="110">
          <template #default="{ row }">{{ executionStatusLabel(row.executionStatus) }}</template>
        </el-table-column>
        <el-table-column label="计分类型" width="100">
          <template #default="{ row }">{{ storageTypeLabel(row.scoreStorageType) }}</template>
        </el-table-column>
        <el-table-column label="比较方向" min-width="120">
          <template #default="{ row }">{{ comparisonLabel(row.comparisonDirection) }}</template>
        </el-table-column>
        <el-table-column
          prop="approvedEffectiveScoreCount"
          label="当前有效成绩数"
          width="125"
        />
        <el-table-column prop="pendingReviewCount" label="待审核成绩数" width="115" />
        <el-table-column label="当前排名版本" width="115">
          <template #default="{ row }">
            {{ row.currentVersionNumber ? `V${row.currentVersionNumber}` : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="排名状态" width="110">
          <template #default="{ row }">{{ rankingStatusLabel(row.rankingStatus) }}</template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="165">
          <template #default="{ row }">{{ formatTime(row.currentPublishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="390">
          <template #default="{ row }">
            <el-button
              class="detail-button"
              link
              type="primary"
              @click="openDetail(row.activityProjectId)"
            >
              查看详情
            </el-button>
            <el-button
              class="preview-button"
              link
              type="primary"
              :disabled="!row.canPreview"
              @click="openPreview(row.activityProjectId)"
            >
              预览排名
            </el-button>
            <el-button
              class="publish-button"
              link
              type="success"
              :disabled="!row.canPublish"
              @click="openPublish(row.activityProjectId)"
            >
              发布排名
            </el-button>
            <template v-if="row.currentVersionId">
              <el-button
                class="current-button"
                link
                @click="openCurrent(row.activityProjectId)"
              >
                当前排名
              </el-button>
              <el-button
                class="withdraw-button"
                link
                type="danger"
                @click="openWithdraw(row.activityProjectId)"
              >
                撤回
              </el-button>
            </template>
            <el-button
              v-if="row.lastVersionStatus"
              class="history-button"
              link
              @click="openHistory(row.activityProjectId)"
            >
              历史
            </el-button>
            <span
              v-if="row.comparisonDirection === 'NO_RANKING'"
              class="operation-warning disabled-ranking"
            >
              该项目不参与排名
            </span>
            <span v-else-if="row.pendingReviewCount > 0" class="operation-warning pending-warning">
              仍有待审核成绩
            </span>
            <span
              v-else-if="row.executionStatus === 'IN_PROGRESS'"
              class="operation-warning in-progress-warning"
            >
              活动结束后可发布
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        @current-change="changePage"
      />
    </template>

    <el-dialog v-model="detailVisible" class="ranking-detail-dialog" title="排名项目详情">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-result v-else-if="detailError" icon="error" title="详情加载失败" :sub-title="detailError">
        <template #extra>
          <el-button class="detail-retry" type="primary" @click="retryDetail">重试</el-button>
        </template>
      </el-result>
      <el-descriptions v-else-if="projectDetail" :column="2" border>
        <el-descriptions-item label="活动">{{ projectDetail.activityTitle }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ projectDetail.projectName }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ formatTime(projectDetail.activityStartTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ formatTime(projectDetail.activityEndTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="地点">{{ projectDetail.location || '—' }}</el-descriptions-item>
        <el-descriptions-item label="有效成绩规则">
          {{ projectDetail.effectiveScoreRule }}
        </el-descriptions-item>
        <el-descriptions-item label="项目说明" :span="2">
          {{ projectDetail.projectDescription || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="规则" :span="2">
          {{ projectDetail.rulesText || '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="previewVisible" class="ranking-preview-dialog" title="排名预览" width="860px">
      <el-skeleton v-if="previewLoading" :rows="7" animated />
      <el-result
        v-else-if="previewError"
        icon="error"
        title="排名预览失败"
        :sub-title="previewError"
      >
        <template #extra>
          <el-button class="preview-retry" type="primary" @click="retryPreview">重试</el-button>
        </template>
      </el-result>
      <template v-else-if="previewResult">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="活动">{{ previewResult.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ previewResult.projectName }}</el-descriptions-item>
          <el-descriptions-item label="比较方向">
            {{ comparisonLabel(previewResult.comparisonDirection) }}
          </el-descriptions-item>
          <el-descriptions-item label="并列规则">
            {{ tiePolicyLabel(previewResult.tiePolicy) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前有效成绩">
            {{ previewResult.totalRanked }}
          </el-descriptions-item>
          <el-descriptions-item label="待审核">
            {{ previewResult.pendingReviewCount }}
          </el-descriptions-item>
          <el-descriptions-item label="可发布">
            {{ previewResult.publishable ? '是' : '否' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-for="warning in previewResult.warnings"
          :key="warning"
          class="preview-warning"
          type="warning"
          :title="warning"
          :closable="false"
          show-icon
        />
        <el-empty v-if="previewResult.entries.length === 0" description="暂无可排名成绩" />
        <ranking-entry-table v-else :entries="previewResult.entries" :show-time="true" />
      </template>
    </el-dialog>

    <el-dialog v-model="publishVisible" class="ranking-publish-dialog" title="确认发布排名">
      <template v-if="publishPreview">
        <el-alert
          title="发布后将生成不可变版本"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="排名人数">
            {{ publishPreview.totalRanked }}
          </el-descriptions-item>
          <el-descriptions-item label="比较方向">
            {{ comparisonLabel(publishPreview.comparisonDirection) }}
          </el-descriptions-item>
          <el-descriptions-item label="并列规则">
            {{ tiePolicyLabel(publishPreview.tiePolicy) }}
          </el-descriptions-item>
          <el-descriptions-item label="数据指纹">
            {{ shortFingerprint(publishPreview.sourceFingerprint) }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button
          class="confirm-publish"
          type="primary"
          :loading="publishing"
          :disabled="publishing"
          @click="confirmPublish"
        >
          确认发布
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="currentVisible" class="ranking-current-dialog" title="当前排名" width="860px">
      <el-skeleton v-if="currentLoading" :rows="7" animated />
      <el-result
        v-else-if="currentError"
        icon="error"
        title="当前排名加载失败"
        :sub-title="currentError"
      >
        <template #extra>
          <el-button class="current-retry" type="primary" @click="retryCurrent">重试</el-button>
        </template>
      </el-result>
      <template v-else-if="currentVersion">
        <el-alert
          v-if="achievementStatusError"
          class="achievement-status-error"
          :title="achievementStatusError"
          type="warning"
          :closable="false"
          show-icon
        />
        <version-content
          :version="currentVersion"
          :achievement-statuses="achievementStatuses"
          :issuing-entry-id="issuingEntryId"
          enable-achievements
          @issue="openIssueConfirmation"
        />
      </template>
    </el-dialog>

    <el-dialog
      v-model="issueVisible"
      class="achievement-issue-dialog"
      title="确认签发成就"
      width="560px"
    >
      <el-alert
        title="成就内容将保存为不可变快照，请确认排名条目。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-descriptions v-if="issueEntry && currentVersion" :column="2" border>
        <el-descriptions-item label="学生">{{ issueEntry.studentDisplayName }}</el-descriptions-item>
        <el-descriptions-item label="学校">{{ issueEntry.schoolName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="活动">{{ currentVersion.activityTitle }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ currentVersion.projectName }}</el-descriptions-item>
        <el-descriptions-item label="排名版本">V{{ currentVersion.versionNumber }}</el-descriptions-item>
        <el-descriptions-item label="名次">第{{ issueEntry.rankPosition }}名</el-descriptions-item>
        <el-descriptions-item label="成绩">{{ issueEntry.scoreDisplayValue }}</el-descriptions-item>
        <el-descriptions-item label="成就标题">
          {{ currentVersion.activityTitle }} · {{ currentVersion.projectName }} · 第{{ issueEntry.rankPosition }}名
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button :disabled="issueSubmitting" @click="issueVisible = false">取消</el-button>
        <el-button
          class="confirm-achievement-issue"
          type="primary"
          :loading="issueSubmitting"
          :disabled="issueSubmitting"
          @click="confirmIssue"
        >
          确认签发
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" class="ranking-history-dialog" title="排名历史" width="920px">
      <el-skeleton v-if="historyLoading" :rows="6" animated />
      <el-result
        v-else-if="historyError"
        icon="error"
        title="历史版本加载失败"
        :sub-title="historyError"
      >
        <template #extra>
          <el-button class="history-retry" type="primary" @click="retryHistory">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="versions.length === 0" description="暂无历史版本" />
      <template v-else>
        <el-table class="history-table" :data="versions" row-key="versionId">
          <el-table-column label="版本" width="80">
            <template #default="{ row }">V{{ row.versionNumber }}</template>
          </el-table-column>
          <el-table-column prop="versionStatus" label="状态" width="110" />
          <el-table-column prop="entryCount" label="人数" width="80" />
          <el-table-column prop="publishedByName" label="发布人" min-width="110" />
          <el-table-column label="发布时间" min-width="160">
            <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column prop="withdrawnByName" label="撤回人" min-width="105" />
          <el-table-column label="撤回时间" min-width="160">
            <template #default="{ row }">{{ formatTime(row.withdrawnAt) }}</template>
          </el-table-column>
          <el-table-column prop="withdrawalReason" label="撤回原因" min-width="150" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button class="version-detail-button" link @click="openVersion(row.versionId)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <el-dialog
      v-model="versionVisible"
      class="ranking-version-dialog"
      title="历史版本详情"
      width="860px"
    >
      <el-skeleton v-if="versionLoading" :rows="7" animated />
      <el-result
        v-else-if="versionError"
        icon="error"
        title="版本详情加载失败"
        :sub-title="versionError"
      >
        <template #extra>
          <el-button class="version-retry" type="primary" @click="retryVersion">重试</el-button>
        </template>
      </el-result>
      <version-content v-else-if="selectedVersion" :version="selectedVersion" />
    </el-dialog>

    <el-dialog v-model="withdrawVisible" class="ranking-withdraw-dialog" title="撤回当前排名">
      <el-form label-position="top">
        <el-form-item
          label="撤回原因"
          :error="withdrawReasonError"
          required
        >
          <el-input
            v-model="withdrawReason"
            class="withdraw-reason"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
          <div v-if="withdrawReasonError" class="withdraw-reason-error">
            {{ withdrawReasonError }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button
          class="confirm-withdraw"
          type="danger"
          :loading="withdrawing"
          :disabled="withdrawing"
          @click="confirmWithdraw"
        >
          确认撤回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h, onMounted, ref } from 'vue';
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import { ApiError } from '@/api/http';
import {
  fetchRankingVersionAchievementStatuses,
  issueAchievementForRankingEntry,
} from '@/api/school-admin-achievement';
import {
  fetchCurrentRanking,
  fetchRankingProject,
  fetchRankingProjects,
  fetchRankingVersion,
  fetchRankingVersions,
  previewRanking,
  publishRanking,
  withdrawRanking,
} from '@/api/school-admin-ranking';
import type {
  RankingEntryItem,
  RankingExecutionStatus,
  RankingPreviewResult,
  RankingProjectDetail,
  RankingProjectFilter,
  RankingProjectItem,
  RankingStatus,
  RankingVersionDetail,
  RankingVersionSummary,
  TiePolicy,
} from '@/types/school-admin-ranking';
import type {
  SchoolAdminAchievementDetail,
  SchoolAdminAchievementStatus,
} from '@/types/student-achievement';

const RankingEntryTable = defineComponent({
  name: 'RankingEntryTable',
  props: {
    entries: {
      type: Array as () => RankingEntryItem[],
      required: true,
    },
    showTime: {
      type: Boolean,
      default: false,
    },
    achievementStatuses: {
      type: Object as () => Record<string, SchoolAdminAchievementStatus>,
      default: () => ({}),
    },
    enableAchievements: {
      type: Boolean,
      default: false,
    },
    issuingEntryId: {
      type: String,
      default: '',
    },
  },
  emits: {
    issue: (entry: RankingEntryItem) => Boolean(entry.rankingEntryId),
  },
  setup(props, { emit }) {
    return () => h(ElTable, {
      class: 'ranking-entry-table',
      data: props.entries,
      rowKey: props.enableAchievements ? 'rankingEntryId' : 'scoreAttemptId',
    }, {
      default: () => [
        h(ElTableColumn, { prop: 'rankPosition', label: '名次', width: 80 }),
        h(ElTableColumn, { prop: 'studentDisplayName', label: '学生', minWidth: 130 }),
        h(ElTableColumn, { prop: 'scoreDisplayValue', label: '成绩', minWidth: 110 }),
        ...(props.showTime
          ? [h(ElTableColumn, {
              label: '业务发生时间',
              minWidth: 170,
            }, {
              default: ({ row }: { row: RankingEntryItem }) => formatTime(row.scoreBusinessTime),
            })]
          : []),
        ...(props.enableAchievements
          ? [h(ElTableColumn, {
              label: '成就状态',
              minWidth: 180,
              fixed: 'right',
            }, {
              default: ({ row }: { row: RankingEntryItem }) => {
                if (!row.rankingEntryId) return h('span', '—');
                const status = props.achievementStatuses[row.rankingEntryId];
                if (status?.achievementRecordId) {
                  return h('div', { class: 'achievement-issued' }, [
                    h(ElTag, {
                      type: status.achievementStatus === 'ACTIVE' ? 'success' : 'danger',
                    }, () => status.achievementStatus === 'ACTIVE' ? '已签发 · 有效' : '已签发 · 已撤销'),
                    status.verificationCode
                      ? h('small', { class: 'achievement-code' }, status.verificationCode)
                      : null,
                  ]);
                }
                return h(ElButton, {
                  class: 'issue-achievement-button',
                  link: true,
                  type: 'primary',
                  loading: props.issuingEntryId === row.rankingEntryId,
                  disabled: Boolean(props.issuingEntryId),
                  'data-ranking-entry-id': row.rankingEntryId,
                  onClick: () => emit('issue', row),
                }, () => '签发成就');
              },
            })]
          : []),
      ],
    });
  },
});

const VersionContent = defineComponent({
  name: 'VersionContent',
  props: {
    version: {
      type: Object as () => RankingVersionDetail,
      required: true,
    },
    achievementStatuses: {
      type: Object as () => Record<string, SchoolAdminAchievementStatus>,
      default: () => ({}),
    },
    enableAchievements: {
      type: Boolean,
      default: false,
    },
    issuingEntryId: {
      type: String,
      default: '',
    },
  },
  emits: {
    issue: (entry: RankingEntryItem) => Boolean(entry.rankingEntryId),
  },
  setup(props, { emit }) {
    return () => h('div', { class: 'version-content' }, [
      h(ElDescriptions, { column: 3, border: true }, {
        default: () => [
          h(ElDescriptionsItem, { label: '版本号' }, () => `V${props.version.versionNumber}`),
          h(ElDescriptionsItem, { label: '状态' }, () => props.version.versionStatus),
          h(ElDescriptionsItem, { label: '排名人数' }, () => String(props.version.entryCount)),
          h(ElDescriptionsItem, { label: '发布人' }, () => props.version.publishedByName || '—'),
          h(ElDescriptionsItem, { label: '发布时间' }, () => formatTime(props.version.publishedAt)),
          h(ElDescriptionsItem, { label: '比较方向' }, () =>
            comparisonLabel(props.version.comparisonDirection)),
          h(ElDescriptionsItem, { label: '有效成绩规则' }, () =>
            props.version.effectiveScoreRule),
          h(ElDescriptionsItem, { label: '并列规则' }, () =>
            tiePolicyLabel(props.version.tiePolicy)),
          h(ElDescriptionsItem, { label: '来源指纹' }, () =>
            shortFingerprint(props.version.sourceFingerprint)),
        ],
      }),
      h(RankingEntryTable, {
        entries: props.version.entries,
        achievementStatuses: props.achievementStatuses,
        enableAchievements: props.enableAchievements,
        issuingEntryId: props.issuingEntryId,
        onIssue: (entry: RankingEntryItem) => emit('issue', entry),
      }),
    ]);
  },
});

const executionStatusOptions: Array<{ value: RankingExecutionStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'ENDED', label: '已结束' },
  { value: 'CANCELLED', label: '已取消' },
];
const rankingStatusOptions: Array<{ value: RankingStatus; label: string }> = [
  { value: 'NOT_PUBLISHED', label: '未发布' },
  { value: 'CURRENT', label: '当前版本' },
  { value: 'WITHDRAWN', label: '已撤回' },
  { value: 'DISABLED', label: '不参与排名' },
];

const filter = ref<RankingProjectFilter>({});
const projects = ref<RankingProjectItem[]>([]);
const loading = ref(false);
const listError = ref('');
const currentPage = ref(1);
const pageSize = 20;
const total = ref(0);

const activeProject = ref<RankingProjectItem | null>(null);
const projectDetail = ref<RankingProjectDetail | null>(null);
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');

const previewResult = ref<RankingPreviewResult | null>(null);
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewError = ref('');
const publishPreview = ref<RankingPreviewResult | null>(null);
const publishVisible = ref(false);
const publishing = ref(false);

const currentVersion = ref<RankingVersionDetail | null>(null);
const currentVisible = ref(false);
const currentLoading = ref(false);
const currentError = ref('');
const achievementStatuses = ref<Record<string, SchoolAdminAchievementStatus>>({});
const achievementStatusError = ref('');
const issueVisible = ref(false);
const issueEntry = ref<RankingEntryItem | null>(null);
const issueSubmitting = ref(false);
const issuingEntryId = ref('');

const historyProject = ref<RankingProjectItem | null>(null);
const versions = ref<RankingVersionSummary[]>([]);
const historyVisible = ref(false);
const historyLoading = ref(false);
const historyError = ref('');

const selectedVersionId = ref('');
const selectedVersion = ref<RankingVersionDetail | null>(null);
const versionVisible = ref(false);
const versionLoading = ref(false);
const versionError = ref('');

const withdrawProject = ref<RankingProjectItem | null>(null);
const withdrawVisible = ref(false);
const withdrawReason = ref('');
const withdrawReasonError = ref('');
const withdrawing = ref(false);

onMounted(() => {
  void loadProjects();
});

async function loadProjects() {
  if (loading.value) return;
  loading.value = true;
  listError.value = '';
  try {
    const result = await fetchRankingProjects(normalizedFilter(), currentPage.value - 1, pageSize);
    projects.value = result.items;
    total.value = result.totalElements;
  } catch (error) {
    listError.value = errorMessage(error);
    projects.value = [];
  } finally {
    loading.value = false;
  }
}

function normalizedFilter(): RankingProjectFilter {
  return {
    executionStatus: filter.value.executionStatus,
    rankingStatus: filter.value.rankingStatus,
    keyword: filter.value.keyword?.trim() || undefined,
  };
}

async function search() {
  currentPage.value = 1;
  await loadProjects();
}

async function reset() {
  filter.value = {};
  currentPage.value = 1;
  await loadProjects();
}

async function changePage() {
  await loadProjects();
}

async function openDetail(activityProjectId: string) {
  const project = requireListedProject(activityProjectId);
  activeProject.value = project;
  detailVisible.value = true;
  await loadDetail(project.activityProjectId);
}

async function loadDetail(activityProjectId: string) {
  detailLoading.value = true;
  detailError.value = '';
  projectDetail.value = null;
  try {
    projectDetail.value = await fetchRankingProject(activityProjectId);
  } catch (error) {
    detailError.value = errorMessage(error);
  } finally {
    detailLoading.value = false;
  }
}

async function retryDetail() {
  if (activeProject.value) await loadDetail(activeProject.value.activityProjectId);
}

async function openPreview(activityProjectId: string) {
  const project = requireListedProject(activityProjectId);
  activeProject.value = project;
  previewVisible.value = true;
  await loadPreview(project);
}

async function loadPreview(project: RankingProjectItem): Promise<RankingPreviewResult | null> {
  previewLoading.value = true;
  previewError.value = '';
  previewResult.value = null;
  try {
    const result = await previewRanking(project.activityProjectId);
    previewResult.value = result;
    return result;
  } catch (error) {
    previewError.value = errorMessage(error);
    return null;
  } finally {
    previewLoading.value = false;
  }
}

async function retryPreview() {
  if (activeProject.value) await loadPreview(activeProject.value);
}

async function openPublish(activityProjectId: string) {
  const project = requireListedProject(activityProjectId);
  if (publishing.value) return;
  activeProject.value = project;
  let latest = previewResult.value?.activityProjectId === project.activityProjectId
    ? previewResult.value
    : null;
  if (!latest) {
    latest = await loadPreview(project);
    if (!latest) {
      previewVisible.value = true;
      return;
    }
  }
  if (!latest.publishable) {
    previewResult.value = latest;
    previewVisible.value = true;
    return;
  }
  publishPreview.value = latest;
  publishVisible.value = true;
}

async function confirmPublish() {
  const project = activeProject.value;
  const preview = publishPreview.value;
  if (!project || !preview || publishing.value) return;
  publishing.value = true;
  try {
    const version = await publishRanking(project.activityProjectId, {
      expectedSourceFingerprint: preview.sourceFingerprint,
    });
    publishVisible.value = false;
    previewVisible.value = false;
    await loadProjects();
    currentVersion.value = version;
    await loadAchievementStatuses(version.versionId);
    currentError.value = '';
    currentVisible.value = true;
    ElMessage.success('排名发布成功');
  } catch (error) {
    if (error instanceof ApiError && error.code === 'RANKING_SOURCE_CHANGED') {
      previewResult.value = null;
      publishPreview.value = null;
      publishVisible.value = false;
      ElMessage.error('成绩数据已变化，请重新预览');
    } else {
      ElMessage.error(errorMessage(error));
    }
  } finally {
    publishing.value = false;
  }
}

async function openCurrent(activityProjectId: string) {
  const project = requireListedProject(activityProjectId);
  activeProject.value = project;
  currentVisible.value = true;
  await loadCurrent(project.activityProjectId);
}

async function loadCurrent(activityProjectId: string) {
  currentLoading.value = true;
  currentError.value = '';
  currentVersion.value = null;
  achievementStatuses.value = {};
  achievementStatusError.value = '';
  try {
    const version = await fetchCurrentRanking(activityProjectId);
    currentVersion.value = version;
    await loadAchievementStatuses(version.versionId);
  } catch (error) {
    currentError.value = errorMessage(error);
  } finally {
    currentLoading.value = false;
  }
}

async function loadAchievementStatuses(rankingVersionId: string) {
  achievementStatusError.value = '';
  try {
    const statuses = await fetchRankingVersionAchievementStatuses(rankingVersionId);
    achievementStatuses.value = Object.fromEntries(
      statuses.map(status => [status.rankingEntryId, status]),
    );
  } catch (error) {
    achievementStatuses.value = {};
    achievementStatusError.value = `成就签发状态加载失败：${errorMessage(error)}`;
  }
}

function openIssueConfirmation(entry: RankingEntryItem) {
  if (!entry.rankingEntryId || issueSubmitting.value || issuingEntryId.value) return;
  if (achievementStatuses.value[entry.rankingEntryId]?.achievementRecordId) return;
  issueEntry.value = entry;
  issueVisible.value = true;
}

async function confirmIssue() {
  const entry = issueEntry.value;
  const rankingEntryId = entry?.rankingEntryId;
  if (!rankingEntryId || issueSubmitting.value || issuingEntryId.value) return;
  issueSubmitting.value = true;
  issuingEntryId.value = rankingEntryId;
  try {
    const issued = await issueAchievementForRankingEntry(rankingEntryId);
    updateAchievementStatus(issued);
    issueVisible.value = false;
    ElMessage.success(issued.created ? '成就签发成功' : '该成就已签发');
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    issueSubmitting.value = false;
    issuingEntryId.value = '';
  }
}

function updateAchievementStatus(issued: SchoolAdminAchievementDetail) {
  achievementStatuses.value = {
    ...achievementStatuses.value,
    [issued.rankingEntryId]: {
      rankingEntryId: issued.rankingEntryId,
      achievementRecordId: issued.recordId,
      achievementStatus: issued.status,
      verificationCode: issued.verificationCode,
      issuedAt: issued.issuedAt,
    },
  };
}

async function retryCurrent() {
  if (activeProject.value) await loadCurrent(activeProject.value.activityProjectId);
}

async function openHistory(activityProjectId: string) {
  const project = requireListedProject(activityProjectId);
  historyProject.value = project;
  historyVisible.value = true;
  await loadHistory(project.activityProjectId);
}

async function loadHistory(activityProjectId: string) {
  historyLoading.value = true;
  historyError.value = '';
  try {
    const result = await fetchRankingVersions(activityProjectId, 0, 100);
    versions.value = result.items;
  } catch (error) {
    historyError.value = errorMessage(error);
  } finally {
    historyLoading.value = false;
  }
}

async function retryHistory() {
  if (historyProject.value) await loadHistory(historyProject.value.activityProjectId);
}

async function openVersion(versionId: string) {
  selectedVersionId.value = versionId;
  versionVisible.value = true;
  await loadVersion(versionId);
}

async function loadVersion(versionId: string) {
  versionLoading.value = true;
  versionError.value = '';
  selectedVersion.value = null;
  try {
    selectedVersion.value = await fetchRankingVersion(versionId);
  } catch (error) {
    versionError.value = errorMessage(error);
  } finally {
    versionLoading.value = false;
  }
}

async function retryVersion() {
  if (selectedVersionId.value) await loadVersion(selectedVersionId.value);
}

function openWithdraw(activityProjectId: string) {
  const project = requireListedProject(activityProjectId);
  withdrawProject.value = project;
  withdrawReason.value = '';
  withdrawReasonError.value = '';
  withdrawVisible.value = true;
}

async function confirmWithdraw() {
  const project = withdrawProject.value;
  const reason = withdrawReason.value.trim();
  if (!reason) {
    withdrawReasonError.value = '请输入撤回原因';
    return;
  }
  if (!project || withdrawing.value) return;
  withdrawing.value = true;
  withdrawReasonError.value = '';
  try {
    await withdrawRanking(project.activityProjectId, { reason });
    withdrawVisible.value = false;
    currentVisible.value = false;
    currentVersion.value = null;
    await loadProjects();
    historyProject.value = project;
    await loadHistory(project.activityProjectId);
    ElMessage.success('排名已撤回');
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    withdrawing.value = false;
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败，请重试';
}

function requireListedProject(activityProjectId: string): RankingProjectItem {
  const project = projects.value.find(
    item => item.activityProjectId === activityProjectId,
  );
  if (!project) {
    throw new Error('排名项目已不在当前列表中');
  }
  return project;
}

function executionStatusLabel(status: RankingExecutionStatus): string {
  return executionStatusOptions.find(option => option.value === status)?.label ?? status;
}

function rankingStatusLabel(status: RankingStatus): string {
  return rankingStatusOptions.find(option => option.value === status)?.label ?? status;
}

function storageTypeLabel(storageType: string): string {
  return {
    INTEGER: '整数',
    DECIMAL: '小数',
    DURATION: '时长',
    GRADE: '等级',
  }[storageType] ?? storageType;
}

function comparisonLabel(direction: string): string {
  return {
    HIGHER_BETTER: '越高越好',
    LOWER_BETTER: '越低越好',
    GRADE_ORDER: '等级顺序',
    NO_RANKING: '不排名',
  }[direction] ?? direction;
}

function tiePolicyLabel(policy: TiePolicy): string {
  return policy === 'COMPETITION' ? '竞赛排名（允许并列）' : '较早业务时间优先';
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

function shortFingerprint(value: string): string {
  return value.length <= 20 ? value : `${value.slice(0, 12)}…${value.slice(-8)}`;
}
</script>

<style scoped>
.ranking-management {
  display: grid;
  gap: 18px;
}

.page-heading h2 {
  margin: 0;
}

.page-heading p {
  margin: 7px 0 0;
  color: var(--el-text-color-secondary);
}

.filters :deep(.el-card__body) {
  padding-bottom: 2px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 18px;
}

.operation-warning {
  display: block;
  margin-top: 4px;
  color: var(--el-color-warning);
  font-size: 12px;
}

.preview-warning {
  margin-top: 12px;
}

.ranking-entry-table {
  margin-top: 16px;
}

.achievement-status-error {
  margin-bottom: 12px;
}

.achievement-issue-dialog :deep(.el-descriptions) {
  margin-top: 16px;
}

.achievement-issued {
  display: grid;
  justify-items: start;
  gap: 5px;
}

.achievement-code {
  max-width: 150px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  text-overflow: ellipsis;
}
</style>
