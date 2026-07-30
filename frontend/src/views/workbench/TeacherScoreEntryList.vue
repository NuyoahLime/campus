<template>
  <div class="teacher-score-entries">
    <h2>成绩录入</h2>

    <el-card class="filters">
      <el-form :inline="true">
        <el-form-item label="审核状态">
          <el-select
            v-model="filter.status"
            class="status-filter"
            clearable
            placeholder="全部状态"
            style="width: 170px"
          >
            <el-option
              v-for="option in statusOptions"
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
            maxlength="100"
            clearable
            placeholder="学校、活动、项目或学生"
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
    <el-result
      v-else-if="error"
      icon="error"
      title="成绩记录加载失败"
      :sub-title="error"
    >
      <template #extra>
        <el-button class="list-retry" type="primary" @click="loadEntries">重试</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="items.length === 0" description="暂无本人录入记录" />
    <template v-else>
      <el-table class="teacher-score-table" :data="items" row-key="attemptId">
        <el-table-column prop="schoolName" label="学校" min-width="140" />
        <el-table-column prop="activityTitle" label="活动" min-width="150" />
        <el-table-column prop="projectName" label="项目" min-width="130" />
        <el-table-column prop="studentName" label="学生" min-width="110" />
        <el-table-column prop="attemptNumber" label="尝试编号" width="90" />
        <el-table-column label="成绩" min-width="110">
          <template #default="{ row }">
            {{ row.displayValue }}{{ row.scoreUnit || '' }}
          </template>
        </el-table-column>
        <el-table-column label="业务发生时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.scoreBusinessTime) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="当前有效" width="90">
          <template #default="{ row }">{{ row.currentEffective ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="250">
          <template #default="{ row }">
            <el-button
              class="view-button"
              link
              type="primary"
              @click="openDetail(row.attemptId)"
            >
              查看
            </el-button>
            <el-button
              v-if="row.status === 'REJECTED'"
              class="revise-button"
              link
              type="warning"
              @click="openEdit(row.attemptId)"
            >
              修改
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              class="edit-button"
              link
              type="primary"
              @click="openEdit(row.attemptId)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              class="submit-draft-button"
              link
              type="success"
              :disabled="submittingAttemptId === row.attemptId"
              @click="submitDraft(row.attemptId)"
            >
              提交审核
            </el-button>
            <span v-if="row.status === 'PENDING_REVIEW'" class="status-hint">
              等待学校管理员审核
            </span>
            <span
              v-if="row.status === 'APPROVED' || row.status === 'INVALIDATED'"
              class="status-hint"
            >
              只读
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

    <el-dialog
      v-model="detailVisible"
      class="teacher-score-detail-dialog"
      title="成绩详情"
      width="700px"
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
          <el-button class="detail-retry" type="primary" @click="reloadDetail">
            重试
          </el-button>
        </template>
      </el-result>
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学校">{{ detail.schoolName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="学生">{{ detail.studentName }}</el-descriptions-item>
          <el-descriptions-item label="尝试编号">
            {{ detail.attemptNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="成绩">
            {{ detail.displayValue }}{{ detail.scoreUnit || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="业务发生时间">
            {{ formatTime(detail.scoreBusinessTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="时间来源">
            {{ timeSourceLabel(detail.timeSource) }}
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            {{ statusLabel(detail.status) }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatTime(detail.submittedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前有效">
            {{ detail.currentEffective ? '是' : '否' }}
          </el-descriptions-item>
        </el-descriptions>
        <h3 class="history-heading">审核历史</h3>
        <el-empty
          v-if="detail.reviewHistory.length === 0"
          description="暂无审核记录"
        />
        <el-timeline v-else class="review-history">
          <el-timeline-item
            v-for="history in detail.reviewHistory"
            :key="history.reviewRecordId"
            :timestamp="formatTime(history.reviewedAt)"
          >
            <strong>{{ history.reviewResult === 'APPROVED' ? '审核通过' : '审核驳回' }}</strong>
            <div>审核人：{{ history.reviewerName }}</div>
            <div v-if="history.rejectReason" class="reject-reason">
              驳回原因：{{ history.rejectReason }}
            </div>
            <div v-if="history.reviewComment">审核意见：{{ history.reviewComment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editVisible"
      class="teacher-score-edit-dialog"
      title="修改成绩"
      width="560px"
      destroy-on-close
      @closed="resetEditForm"
    >
      <el-skeleton v-if="editLoading" :rows="6" animated />
      <template v-else-if="editDetail">
        <el-alert
          v-if="latestRejectReason"
          class="latest-reject-reason"
          type="warning"
          :closable="false"
          :title="`最近驳回原因：${latestRejectReason}`"
        />
        <el-descriptions :column="1" border class="edit-context">
          <el-descriptions-item label="项目">{{ editDetail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="学生">{{ editDetail.studentName }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="120px">
          <el-form-item
            v-if="editDetail.scoreStorageType === 'INTEGER'"
            label="整数成绩"
            :error="editErrors.value"
          >
            <el-input-number
              v-model="editForm.integerValue"
              class="edit-integer-input"
              :min="0"
              :step="1"
              :precision="0"
            />
          </el-form-item>
          <el-form-item
            v-if="editDetail.scoreStorageType === 'DECIMAL'"
            label="小数成绩"
            :error="editErrors.value"
          >
            <el-input-number
              v-model="editForm.decimalValue"
              class="edit-decimal-input"
              :min="0"
              :step="editDecimalStep"
              :precision="editDetail.decimalPlaces ?? undefined"
            />
          </el-form-item>
          <el-form-item
            v-if="editDetail.scoreStorageType === 'DURATION'"
            label="时长（毫秒）"
            :error="editErrors.value"
          >
            <el-input-number
              v-model="editForm.durationMs"
              class="edit-duration-input"
              :min="0"
              :step="1"
              :precision="0"
            />
          </el-form-item>
          <el-form-item
            v-if="editDetail.scoreStorageType === 'GRADE'"
            label="等级"
            :error="editErrors.value"
          >
            <el-select
              v-model="editForm.grade"
              class="edit-grade-select"
              placeholder="请选择等级"
              style="width: 100%"
            >
              <el-option
                v-for="grade in editGradeOptions"
                :key="grade"
                :label="grade"
                :value="grade"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="业务发生时间" :error="editErrors.businessTime">
            <el-date-picker
              v-model="editForm.scoreBusinessTime"
              class="edit-business-time"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ssZ"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="时间来源" :error="editErrors.timeSource">
            <el-select
              v-model="editForm.timeSource"
              class="edit-time-source"
              placeholder="请选择时间来源"
              style="width: 100%"
            >
              <el-option label="学生报告" value="STUDENT_REPORTED" />
              <el-option label="教师确认" value="TEACHER_CONFIRMED" />
              <el-option label="现场记录" value="ON_SITE_RECORD" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="editError"
          class="edit-error"
          type="error"
          :closable="false"
          :title="editError"
        />
      </template>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button
          class="save-draft-button"
          type="primary"
          :loading="savingDraft"
          :disabled="savingDraft || editLoading"
          @click="saveDraft"
        >
          保存为草稿
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ApiError } from '@/api/http';
import {
  fetchMyTeacherScoreAttempts,
  fetchTeacherScoreAttemptDetail,
  submitTeacherScoreDraft,
  updateTeacherScoreDraft,
} from '@/api/teacher-score-entry';
import type {
  TeacherScoreAttemptDetail,
  TeacherScoreAttemptItem,
  TeacherScoreFilter,
  UpdateTeacherScorePayload,
} from '@/types/teacher-score-entry';
import type { ScoreAttemptStatus, ScoreStorageType } from '@/types/school-admin-score-review';

interface EditFormState {
  integerValue: number | null;
  decimalValue: number | null;
  durationMs: number | null;
  grade: string;
  scoreBusinessTime: string;
  timeSource: string;
}

const pageSize = 20;
const currentPage = ref(1);
const total = ref(0);
const items = ref<TeacherScoreAttemptItem[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const filter = reactive<TeacherScoreFilter>({ status: undefined, keyword: '' });
const submittingAttemptId = ref<string | null>(null);
const detailVisible = ref(false);
const detailAttemptId = ref<string | null>(null);
const detail = ref<TeacherScoreAttemptDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<string | null>(null);
const editVisible = ref(false);
const editLoading = ref(false);
const editDetail = ref<TeacherScoreAttemptDetail | null>(null);
const savingDraft = ref(false);
const editError = ref<string | null>(null);
const editErrors = reactive({ value: '', businessTime: '', timeSource: '' });
const editForm = reactive<EditFormState>(emptyEditForm());

const statusOptions: Array<{ value: ScoreAttemptStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'INVALIDATED', label: '已失效' },
];
const editGradeOptions = computed(() =>
  (editDetail.value?.gradeOrder ?? '')
    .split(',')
    .map((grade) => grade.trim())
    .filter(Boolean),
);
const editDecimalStep = computed(
  () => 10 ** -(editDetail.value?.decimalPlaces ?? 0),
);
const latestRejectReason = computed(
  () =>
    editDetail.value?.reviewHistory.find(
      (history) => history.reviewResult === 'REJECTED',
    )?.rejectReason ?? null,
);

async function loadEntries(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const response = await fetchMyTeacherScoreAttempts(
      filter,
      currentPage.value - 1,
      pageSize,
    );
    items.value = response.items;
    total.value = response.totalElements;
  } catch (caught) {
    items.value = [];
    total.value = 0;
    error.value = errorMessage(caught, '成绩记录加载失败');
  } finally {
    loading.value = false;
  }
}

function search(): void {
  currentPage.value = 1;
  void loadEntries();
}

function reset(): void {
  filter.status = undefined;
  filter.keyword = '';
  search();
}

function changePage(): void {
  void loadEntries();
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
    detail.value = await fetchTeacherScoreAttemptDetail(detailAttemptId.value);
  } catch (caught) {
    detail.value = null;
    detailError.value = errorMessage(caught, '详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

async function openEdit(attemptId: string): Promise<void> {
  editVisible.value = true;
  editLoading.value = true;
  editError.value = null;
  try {
    const loaded = await fetchTeacherScoreAttemptDetail(attemptId);
    if (loaded.status !== 'DRAFT' && loaded.status !== 'REJECTED') {
      throw new Error('当前状态不可编辑');
    }
    editDetail.value = loaded;
    Object.assign(editForm, {
      integerValue: loaded.integerValue,
      decimalValue: loaded.decimalValue,
      durationMs: loaded.durationMs,
      grade: loaded.grade ?? '',
      scoreBusinessTime: loaded.scoreBusinessTime,
      timeSource: loaded.timeSource,
    });
  } catch (caught) {
    editDetail.value = null;
    editError.value = errorMessage(caught, '成绩详情加载失败');
  } finally {
    editLoading.value = false;
  }
}

async function saveDraft(): Promise<void> {
  if (savingDraft.value || !editDetail.value) return;
  if (!validateEditForm(editDetail.value.scoreStorageType)) return;
  savingDraft.value = true;
  editError.value = null;
  const payload: UpdateTeacherScorePayload = {
    scoreBusinessTime: editForm.scoreBusinessTime,
    timeSource: editForm.timeSource,
  };
  addScoreValue(payload, editDetail.value.scoreStorageType);
  try {
    await updateTeacherScoreDraft(editDetail.value.attemptId, payload);
    editVisible.value = false;
    resetEditForm();
    await loadEntries();
  } catch (caught) {
    editError.value = errorMessage(caught, '成绩保存失败');
  } finally {
    savingDraft.value = false;
  }
}

async function submitDraft(attemptId: string): Promise<void> {
  if (submittingAttemptId.value) return;
  submittingAttemptId.value = attemptId;
  try {
    await submitTeacherScoreDraft(attemptId);
    await loadEntries();
    if (detailAttemptId.value === attemptId && detailVisible.value) {
      await reloadDetail();
    }
  } catch (caught) {
    error.value = errorMessage(caught, '草稿提交失败');
  } finally {
    submittingAttemptId.value = null;
  }
}

function validateEditForm(type: ScoreStorageType): boolean {
  editErrors.value = '';
  editErrors.businessTime = editForm.scoreBusinessTime ? '' : '请选择业务发生时间';
  editErrors.timeSource = editForm.timeSource ? '' : '请选择时间来源';
  if (type === 'INTEGER' && editForm.integerValue === null) {
    editErrors.value = '请输入整数成绩';
  } else if (type === 'DECIMAL' && editForm.decimalValue === null) {
    editErrors.value = '请输入小数成绩';
  } else if (type === 'DURATION' && editForm.durationMs === null) {
    editErrors.value = '请输入时长，0 毫秒是合法成绩';
  } else if (type === 'GRADE' && !editForm.grade) {
    editErrors.value = '请选择等级';
  }
  return !editErrors.value && !editErrors.businessTime && !editErrors.timeSource;
}

function addScoreValue(payload: UpdateTeacherScorePayload, type: ScoreStorageType): void {
  if (type === 'INTEGER') payload.integerValue = editForm.integerValue;
  if (type === 'DECIMAL') payload.decimalValue = editForm.decimalValue;
  if (type === 'DURATION') payload.durationMs = editForm.durationMs;
  if (type === 'GRADE') payload.grade = editForm.grade;
}

function resetEditForm(): void {
  editDetail.value = null;
  Object.assign(editForm, emptyEditForm());
  editErrors.value = '';
  editErrors.businessTime = '';
  editErrors.timeSource = '';
  editError.value = null;
}

function emptyEditForm(): EditFormState {
  return {
    integerValue: null,
    decimalValue: null,
    durationMs: null,
    grade: '',
    scoreBusinessTime: '',
    timeSource: '',
  };
}

function statusLabel(status: ScoreAttemptStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function timeSourceLabel(source: string): string {
  return {
    STUDENT_REPORTED: '学生报告',
    TEACHER_CONFIRMED: '教师确认',
    ON_SITE_RECORD: '现场记录',
    OTHER: '其他',
  }[source] ?? source;
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function errorMessage(caught: unknown, fallback: string): string {
  if (caught instanceof ApiError || caught instanceof Error) return caught.message;
  return fallback;
}

function refreshFromScoreChange(): void {
  void loadEntries();
}

onMounted(() => {
  window.addEventListener('teacher-score-attempts:changed', refreshFromScoreChange);
  void loadEntries();
});

onBeforeUnmount(() => {
  window.removeEventListener('teacher-score-attempts:changed', refreshFromScoreChange);
});
</script>

<style scoped>
.teacher-score-entries h2 {
  margin-top: 0;
}

.filters {
  margin-bottom: 16px;
}

.pagination {
  justify-content: center;
  margin-top: 20px;
}

.status-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.history-heading {
  margin-top: 22px;
}

.reject-reason {
  color: var(--el-color-danger);
}

.latest-reject-reason,
.edit-context,
.edit-error {
  margin-bottom: 16px;
}
</style>
