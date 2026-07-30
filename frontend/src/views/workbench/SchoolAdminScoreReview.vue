<template>
  <div class="score-review-page">
    <h2>成绩管理</h2>

    <el-card class="filters">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="filter.status" class="status-filter" style="width: 160px">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="活动ID">
          <el-input v-model="filter.activityId" class="activity-filter" clearable />
        </el-form-item>
        <el-form-item label="项目ID">
          <el-input v-model="filter.projectId" class="project-filter" clearable />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            class="keyword-filter"
            maxlength="100"
            clearable
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button class="search-button" type="primary" @click="search">查询</el-button>
          <el-button class="reset-button" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-result v-else-if="listError" icon="error" title="成绩加载失败" :sub-title="listError">
      <template #extra>
        <el-button class="list-retry" type="primary" @click="loadList">重试</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="items.length === 0" description="暂无成绩" />
    <template v-else>
      <el-table class="score-table" :data="items" row-key="attemptId">
        <el-table-column prop="studentName" label="学生" min-width="120" />
        <el-table-column prop="activityTitle" label="活动" min-width="150" />
        <el-table-column prop="projectName" label="项目" min-width="140" />
        <el-table-column prop="attemptNumber" label="尝试次数" width="90" />
        <el-table-column label="成绩" min-width="110">
          <template #default="{ row }">
            <span class="score-value">{{ row.displayValue }}{{ row.scoreUnit || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="enteredByName" label="录入人" min-width="120" />
        <el-table-column label="业务发生时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.scoreBusinessTime) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="当前有效" width="100">
          <template #default="{ row }">{{ row.currentEffective ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="250">
          <template #default="{ row }">
            <el-button class="view-button" link type="primary" @click="openDetail(row.attemptId)">
              查看
            </el-button>
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button
                class="approve-button"
                link
                type="success"
                :disabled="isOwnEntry(row)"
                @click="openApprove(row)"
              >
                审核通过
              </el-button>
              <el-button
                class="reject-button"
                link
                type="danger"
                :disabled="isOwnEntry(row)"
                @click="openReject(row)"
              >
                审核驳回
              </el-button>
              <span v-if="isOwnEntry(row)" class="self-review-warning">
                不能审核本人录入的成绩
              </span>
            </template>
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

    <el-dialog
      v-model="detailVisible"
      class="score-detail-dialog"
      title="成绩详情"
      width="720px"
      destroy-on-close
    >
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <el-result
        v-else-if="detailError"
        icon="error"
        title="详情加载失败"
        :sub-title="detailError"
      >
        <template #extra>
          <el-button class="detail-retry" type="primary" @click="reloadDetail">重试</el-button>
        </template>
      </el-result>
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生">{{ detail.studentName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="尝试编号">{{ detail.attemptNumber }}</el-descriptions-item>
          <el-descriptions-item label="分数值">
            {{ detail.displayValue }}{{ detail.scoreUnit || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="分数类型">{{ detail.scoreStorageType }}</el-descriptions-item>
          <el-descriptions-item label="分数单位">{{ displayEmpty(detail.scoreUnit) }}</el-descriptions-item>
          <el-descriptions-item label="业务发生时间">
            {{ formatTime(detail.scoreBusinessTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="时间来源">
            {{ displayEmpty(detail.timeSource) }}
          </el-descriptions-item>
          <el-descriptions-item label="录入人">{{ detail.enteredByName }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatTime(detail.submittedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="当前有效状态">
            {{ detail.currentEffective ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="有效成绩规则">
            {{ detail.effectiveScoreRule }}
          </el-descriptions-item>
          <el-descriptions-item label="比较方向">
            {{ detail.comparisonDirection }}
          </el-descriptions-item>
          <el-descriptions-item label="原始整数">{{ displayEmpty(detail.integerValue) }}</el-descriptions-item>
          <el-descriptions-item label="原始小数">{{ displayEmpty(detail.decimalValue) }}</el-descriptions-item>
          <el-descriptions-item label="原始时长">{{ displayEmpty(detail.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="原始等级">{{ displayEmpty(detail.grade) }}</el-descriptions-item>
        </el-descriptions>

        <h3>审核历史</h3>
        <el-empty v-if="detail.reviewHistory.length === 0" description="暂无审核历史" />
        <el-timeline v-else class="review-history">
          <el-timeline-item
            v-for="history in detail.reviewHistory"
            :key="history.reviewRecordId"
            :timestamp="formatTime(history.reviewedAt)"
          >
            <strong>{{ history.reviewResult === 'APPROVED' ? '审核通过' : '审核驳回' }}</strong>
            · {{ history.reviewerName }}
            <div v-if="history.rejectReason">原因：{{ history.rejectReason }}</div>
            <div v-if="history.reviewComment">备注：{{ history.reviewComment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>

    <el-dialog
      v-model="approveVisible"
      class="approve-dialog"
      title="审核通过"
      width="520px"
      :close-on-click-modal="!approving"
    >
      <el-form label-position="top">
        <el-form-item label="审核备注">
          <el-input
            v-model="approveComment"
            class="approve-comment"
            type="textarea"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          v-if="selectedAttempt?.effectiveScoreRule === 'ADMIN_DESIGNATED'"
          label="设为当前有效成绩"
          required
          :error="designationError"
        >
          <el-radio-group v-model="designationChoice" class="designation-choice">
            <el-radio :value="true">是</el-radio>
            <el-radio :value="false">否</el-radio>
          </el-radio-group>
          <div v-if="designationError" class="field-error designation-error">
            {{ designationError }}
          </div>
        </el-form-item>
        <div v-if="approveError" class="submit-error">{{ approveError }}</div>
      </el-form>
      <template #footer>
        <el-button :disabled="approving" @click="approveVisible = false">取消</el-button>
        <el-button
          class="confirm-approve"
          type="primary"
          :loading="approving"
          :disabled="approving"
          @click="submitApprove"
        >
          确认通过
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="rejectVisible"
      class="reject-dialog"
      title="审核驳回"
      width="520px"
      :close-on-click-modal="!rejecting"
    >
      <el-form label-position="top">
        <el-form-item label="驳回原因" required :error="rejectReasonError">
          <el-input
            v-model="rejectReason"
            class="reject-reason"
            type="textarea"
            maxlength="1000"
            show-word-limit
          />
          <div v-if="rejectReasonError" class="field-error reject-reason-error">
            {{ rejectReasonError }}
          </div>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="rejectComment"
            class="reject-comment"
            type="textarea"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <div v-if="rejectError" class="submit-error">{{ rejectError }}</div>
      </el-form>
      <template #footer>
        <el-button :disabled="rejecting" @click="rejectVisible = false">取消</el-button>
        <el-button
          class="confirm-reject"
          type="danger"
          :loading="rejecting"
          :disabled="rejecting"
          @click="submitReject"
        >
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  approveSchoolAdminScoreAttempt,
  fetchSchoolAdminScoreAttempt,
  fetchSchoolAdminScoreAttempts,
  rejectSchoolAdminScoreAttempt,
} from '@/api/school-admin-score-review';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import type {
  ApproveScorePayload,
  SchoolAdminScoreAttemptDetail,
  SchoolAdminScoreAttemptItem,
  ScoreAttemptStatus,
} from '@/types/school-admin-score-review';

const auth = useAuthStore();
const pageSize = 20;
const items = ref<SchoolAdminScoreAttemptItem[]>([]);
const total = ref(0);
const currentPage = ref(1);
const loading = ref(false);
const listError = ref<string | null>(null);
const filter = reactive({
  status: 'PENDING_REVIEW' as ScoreAttemptStatus,
  activityId: '',
  projectId: '',
  keyword: '',
});

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref<string | null>(null);
const detailAttemptId = ref<string | null>(null);
const detail = ref<SchoolAdminScoreAttemptDetail | null>(null);

const selectedAttempt = ref<SchoolAdminScoreAttemptItem | null>(null);
const approveVisible = ref(false);
const approveComment = ref('');
const designationChoice = ref<boolean | null>(null);
const designationError = ref('');
const approveError = ref<string | null>(null);
const approving = ref(false);

const rejectVisible = ref(false);
const rejectReason = ref('');
const rejectComment = ref('');
const rejectReasonError = ref('');
const rejectError = ref<string | null>(null);
const rejecting = ref(false);

const statusOptions: { value: ScoreAttemptStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'INVALIDATED', label: '已作废' },
];

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}

async function loadList(): Promise<void> {
  loading.value = true;
  listError.value = null;
  try {
    const result = await fetchSchoolAdminScoreAttempts(
      {
        status: filter.status,
        activityId: filter.activityId.trim() || undefined,
        projectId: filter.projectId.trim() || undefined,
        keyword: filter.keyword.trim() || undefined,
      },
      currentPage.value - 1,
      pageSize,
    );
    items.value = result.items;
    total.value = result.totalElements;
  } catch (error) {
    listError.value = errorMessage(error, '成绩加载失败');
  } finally {
    loading.value = false;
  }
}

async function search(): Promise<void> {
  currentPage.value = 1;
  await loadList();
}

async function reset(): Promise<void> {
  filter.status = 'PENDING_REVIEW';
  filter.activityId = '';
  filter.projectId = '';
  filter.keyword = '';
  currentPage.value = 1;
  await loadList();
}

async function changePage(page: number): Promise<void> {
  currentPage.value = page;
  await loadList();
}

async function openDetail(attemptId: string): Promise<void> {
  detailAttemptId.value = attemptId;
  detailVisible.value = true;
  await reloadDetail();
}

async function reloadDetail(): Promise<void> {
  if (!detailAttemptId.value) return;
  detailLoading.value = true;
  detailError.value = null;
  try {
    detail.value = await fetchSchoolAdminScoreAttempt(detailAttemptId.value);
  } catch (error) {
    detail.value = null;
    detailError.value = errorMessage(error, '详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

function isOwnEntry(attempt: SchoolAdminScoreAttemptItem): boolean {
  return auth.user?.userId === attempt.enteredBy;
}

function openApprove(attempt: SchoolAdminScoreAttemptItem): void {
  if (isOwnEntry(attempt) || attempt.status !== 'PENDING_REVIEW') return;
  selectedAttempt.value = attempt;
  approveComment.value = '';
  designationChoice.value = null;
  designationError.value = '';
  approveError.value = null;
  approveVisible.value = true;
}

async function submitApprove(): Promise<void> {
  if (approving.value || !selectedAttempt.value) return;
  if (
    selectedAttempt.value.effectiveScoreRule === 'ADMIN_DESIGNATED' &&
    designationChoice.value === null
  ) {
    designationError.value = '请选择是否设为当前有效成绩';
    return;
  }
  designationError.value = '';
  approveError.value = null;
  approving.value = true;
  const attemptId = selectedAttempt.value.attemptId;
  const payload: ApproveScorePayload = {
    reviewComment: approveComment.value.trim() || null,
  };
  if (selectedAttempt.value.effectiveScoreRule === 'ADMIN_DESIGNATED') {
    payload.makeCurrentEffective = designationChoice.value;
  }
  try {
    await approveSchoolAdminScoreAttempt(attemptId, payload);
    approveVisible.value = false;
    await loadList();
    try {
      detail.value = await fetchSchoolAdminScoreAttempt(attemptId);
      if (detailVisible.value) {
        detailAttemptId.value = attemptId;
        detailError.value = null;
      }
    } catch (error) {
      detailError.value = errorMessage(error, '详情加载失败');
    }
  } catch (error) {
    approveError.value = errorMessage(error, '审核通过失败');
  } finally {
    approving.value = false;
  }
}

function openReject(attempt: SchoolAdminScoreAttemptItem): void {
  if (isOwnEntry(attempt) || attempt.status !== 'PENDING_REVIEW') return;
  selectedAttempt.value = attempt;
  rejectReason.value = '';
  rejectComment.value = '';
  rejectReasonError.value = '';
  rejectError.value = null;
  rejectVisible.value = true;
}

async function submitReject(): Promise<void> {
  if (rejecting.value || !selectedAttempt.value) return;
  const reason = rejectReason.value.trim();
  if (!reason) {
    rejectReasonError.value = '请输入驳回原因';
    return;
  }
  rejectReasonError.value = '';
  rejectError.value = null;
  rejecting.value = true;
  try {
    await rejectSchoolAdminScoreAttempt(selectedAttempt.value.attemptId, {
      rejectReason: reason,
      reviewComment: rejectComment.value.trim() || null,
    });
    rejectVisible.value = false;
    await loadList();
  } catch (error) {
    rejectError.value = errorMessage(error, '审核驳回失败');
  } finally {
    rejecting.value = false;
  }
}

function statusLabel(status: ScoreAttemptStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function displayEmpty(value: string | number | null): string | number {
  return value === null || value === '' ? '-' : value;
}

onMounted(() => {
  void loadList();
});
</script>

<style scoped>
.score-review-page h2 {
  margin-top: 0;
}
.filters {
  margin-bottom: 16px;
}
.pagination {
  justify-content: center;
  margin-top: 20px;
}
.self-review-warning {
  display: block;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 18px;
}
.submit-error {
  color: var(--el-color-danger);
  margin-bottom: 12px;
}
.field-error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
  width: 100%;
}
.review-history {
  margin-top: 12px;
}
</style>
