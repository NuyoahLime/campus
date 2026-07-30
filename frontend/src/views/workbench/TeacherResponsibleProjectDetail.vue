<template>
  <div class="teacher-project-detail">
    <div class="page-heading">
      <el-button class="back-button" @click="$router.push('/teacher/responsible')">
        返回
      </el-button>
      <h2>负责项目详情</h2>
    </div>

    <el-skeleton v-if="detailLoading" :rows="8" animated />
    <el-result
      v-else-if="detailError"
      icon="error"
      title="项目详情加载失败"
      :sub-title="detailError"
    >
      <template #extra>
        <el-button class="detail-retry" type="primary" @click="loadDetail">重试</el-button>
      </template>
    </el-result>
    <template v-else-if="detail">
      <el-card class="project-summary">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学校">{{ detail.schoolName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="活动状态">
            {{ executionStatusLabel(detail.executionStatus) }}
          </el-descriptions-item>
          <el-descriptions-item label="活动时间">
            {{ formatRange(detail.startTime, detail.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="活动地点">
            {{ detail.location || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="项目名称">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="活动说明" :span="2">
            {{ detail.activityDescription || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="项目说明" :span="2">
            {{ detail.projectDescription || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="规则" :span="2">
            {{ detail.rulesText || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="器材要求">
            {{ detail.equipmentRequirements || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="场地要求">
            {{ detail.venueRequirements || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="计分类型">
            {{ scoreTypeLabel(detail.scoreStorageType) }}
          </el-descriptions-item>
          <el-descriptions-item label="计分单位">
            {{ detail.scoreUnit || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="比较方向">
            {{ comparisonLabel(detail.comparisonDirection) }}
          </el-descriptions-item>
          <el-descriptions-item label="有效成绩规则">
            {{ effectiveRuleLabel(detail.effectiveScoreRule) }}
          </el-descriptions-item>
          <el-descriptions-item label="允许并列">
            {{ detail.allowTie ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="负责教师">
            {{ responsibleTeacherNames }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-alert
        v-if="terminal"
        class="terminal-alert"
        type="warning"
        :closable="false"
        title="活动已结束，无法录入成绩"
        show-icon
      />

      <section class="participant-section">
        <h3>参赛人员</h3>
        <el-card class="participant-filters">
          <el-form :inline="true">
            <el-form-item label="成绩状态">
              <el-select
                v-model="participantFilter.status"
                class="participant-status-filter"
                clearable
                placeholder="全部状态"
                style="width: 180px"
              >
                <el-option
                  v-for="option in participantStatusOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input
                v-model="participantFilter.keyword"
                class="participant-keyword-filter"
                maxlength="100"
                clearable
                placeholder="姓名或学号"
                @keyup.enter="searchParticipants"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                class="participant-search-button"
                type="primary"
                @click="searchParticipants"
              >
                查询
              </el-button>
              <el-button class="participant-reset-button" @click="resetParticipants">
                重置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-skeleton v-if="participantsLoading" :rows="6" animated />
        <el-result
          v-else-if="participantsError"
          icon="error"
          title="参赛人员加载失败"
          :sub-title="participantsError"
        >
          <template #extra>
            <el-button
              class="participants-retry"
              type="primary"
              @click="loadParticipants"
            >
              重试
            </el-button>
          </template>
        </el-result>
        <el-empty
          v-else-if="participants.length === 0"
          description="暂无项目参赛人员"
        />
        <template v-else>
          <el-table
            class="participant-table"
            :data="participants"
            row-key="studentId"
          >
            <el-table-column prop="displayName" label="学生" min-width="120" />
            <el-table-column prop="studentNumber" label="学号" min-width="120">
              <template #default="{ row }">{{ row.studentNumber || '-' }}</template>
            </el-table-column>
            <el-table-column prop="grade" label="年级" min-width="90">
              <template #default="{ row }">{{ row.grade || '-' }}</template>
            </el-table-column>
            <el-table-column prop="className" label="班级" min-width="100">
              <template #default="{ row }">{{ row.className || '-' }}</template>
            </el-table-column>
            <el-table-column prop="attemptCount" label="尝试次数" width="90" />
            <el-table-column label="最新成绩" min-width="120">
              <template #default="{ row }">
                <span class="latest-score">{{ row.latestScoreValue || '暂无成绩' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最新状态" width="120">
              <template #default="{ row }">
                {{ attemptStatusLabel(row.latestAttemptStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="已通过" width="80">
              <template #default="{ row }">{{ row.hasApprovedScore ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column v-if="!terminal" label="操作" fixed="right" width="110">
              <template #default="{ row }">
                <el-button
                  class="enter-score-button"
                  link
                  type="primary"
                  @click="openScoreDialog(row)"
                >
                  录入成绩
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="participantPage"
            class="pagination"
            layout="total, prev, pager, next"
            :total="participantTotal"
            :page-size="pageSize"
            @current-change="changeParticipantPage"
          />
        </template>
      </section>
    </template>

    <el-dialog
      v-model="scoreDialogVisible"
      class="teacher-score-dialog"
      title="录入成绩"
      width="560px"
      destroy-on-close
      @closed="resetScoreForm"
    >
      <template v-if="detail && selectedParticipant">
        <el-descriptions :column="1" border class="score-context">
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="学生">
            {{ selectedParticipant.displayName }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="120px" class="score-form">
          <el-form-item
            v-if="detail.scoreStorageType === 'INTEGER'"
            label="整数成绩"
            :error="scoreErrors.value"
          >
            <el-input-number
              v-model="scoreForm.integerValue"
              class="integer-score-input"
              :min="0"
              :step="1"
              :precision="0"
            />
          </el-form-item>
          <el-form-item
            v-if="detail.scoreStorageType === 'DECIMAL'"
            label="小数成绩"
            :error="scoreErrors.value"
          >
            <el-input-number
              v-model="scoreForm.decimalValue"
              class="decimal-score-input"
              :min="0"
              :step="decimalStep"
              :precision="detail.decimalPlaces ?? undefined"
            />
          </el-form-item>
          <el-form-item
            v-if="detail.scoreStorageType === 'DURATION'"
            label="时长（毫秒）"
            :error="scoreErrors.value"
          >
            <el-input-number
              v-model="scoreForm.durationMs"
              class="duration-score-input"
              :min="0"
              :step="1"
              :precision="0"
            />
          </el-form-item>
          <el-form-item
            v-if="detail.scoreStorageType === 'GRADE'"
            label="等级"
            :error="scoreErrors.value"
          >
            <el-select
              v-model="scoreForm.grade"
              class="grade-score-select"
              placeholder="请选择等级"
              style="width: 100%"
            >
              <el-option
                v-for="grade in gradeOptions"
                :key="grade"
                :label="grade"
                :value="grade"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="业务发生时间" :error="scoreErrors.businessTime">
            <el-date-picker
              v-model="scoreForm.scoreBusinessTime"
              class="business-time-input"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ssZ"
              placeholder="请选择时间"
              style="width: 100%"
            />
            <span v-if="scoreErrors.businessTime" class="field-error">
              {{ scoreErrors.businessTime }}
            </span>
          </el-form-item>
          <el-form-item label="时间来源" :error="scoreErrors.timeSource">
            <el-select
              v-model="scoreForm.timeSource"
              class="time-source-select"
              placeholder="请选择时间来源"
              style="width: 100%"
            >
              <el-option label="学生报告" value="STUDENT_REPORTED" />
              <el-option label="教师确认" value="TEACHER_CONFIRMED" />
              <el-option label="现场记录" value="ON_SITE_RECORD" />
              <el-option label="其他" value="OTHER" />
            </el-select>
            <span v-if="scoreErrors.timeSource" class="field-error">
              {{ scoreErrors.timeSource }}
            </span>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="submitError"
          class="submit-error"
          type="error"
          :closable="false"
          :title="submitError"
        />
      </template>
      <template #footer>
        <el-button @click="scoreDialogVisible = false">取消</el-button>
        <el-button
          class="submit-score-button"
          type="primary"
          :loading="submittingScore"
          :disabled="submittingScore"
          @click="submitScore"
        >
          提交审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ApiError } from '@/api/http';
import {
  fetchTeacherProjectParticipants,
  fetchTeacherResponsibleProject,
} from '@/api/teacher-responsible-project';
import { createTeacherScoreAttempt } from '@/api/teacher-score-entry';
import type {
  TeacherProjectParticipantFilter,
  TeacherProjectParticipantItem,
  TeacherParticipantScoreStatus,
  TeacherResponsibleProjectDetail,
} from '@/types/teacher-responsible-project';
import type { CreateTeacherScorePayload } from '@/types/teacher-score-entry';
import type { ScoreAttemptStatus, ScoreStorageType } from '@/types/school-admin-score-review';

interface ScoreFormState {
  integerValue: number | null;
  decimalValue: number | null;
  durationMs: number | null;
  grade: string;
  scoreBusinessTime: string;
  timeSource: string;
}

const route = useRoute();
const activityProjectId = computed(() => String(route.params.activityProjectId ?? ''));
const pageSize = 20;
const detail = ref<TeacherResponsibleProjectDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<string | null>(null);
const participants = ref<TeacherProjectParticipantItem[]>([]);
const participantsLoading = ref(false);
const participantsError = ref<string | null>(null);
const participantPage = ref(1);
const participantTotal = ref(0);
const participantFilter = reactive<TeacherProjectParticipantFilter>({
  keyword: '',
  status: undefined,
});
const scoreDialogVisible = ref(false);
const selectedParticipant = ref<TeacherProjectParticipantItem | null>(null);
const submittingScore = ref(false);
const submitError = ref<string | null>(null);
const scoreErrors = reactive({
  value: '',
  businessTime: '',
  timeSource: '',
});
const scoreForm = reactive<ScoreFormState>(emptyScoreForm());

const participantStatusOptions: Array<{
  value: TeacherParticipantScoreStatus;
  label: string;
}> = [
  { value: 'NO_SCORE', label: '未录入' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'INVALIDATED', label: '已失效' },
];

const terminal = computed(
  () =>
    detail.value?.executionStatus === 'ENDED' ||
    detail.value?.executionStatus === 'CANCELLED',
);
const gradeOptions = computed(() =>
  (detail.value?.gradeOrder ?? '')
    .split(',')
    .map((grade) => grade.trim())
    .filter(Boolean),
);
const decimalStep = computed(() => 10 ** -(detail.value?.decimalPlaces ?? 0));
const responsibleTeacherNames = computed(() => {
  const names = detail.value?.responsibleTeachers.map((teacher) => teacher.username) ?? [];
  return names.length > 0 ? names.join('、') : '-';
});

async function loadDetail(): Promise<void> {
  detailLoading.value = true;
  detailError.value = null;
  try {
    detail.value = await fetchTeacherResponsibleProject(activityProjectId.value);
    await loadParticipants();
  } catch (caught) {
    detail.value = null;
    detailError.value = errorMessage(caught, '项目详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

async function loadParticipants(): Promise<void> {
  if (!activityProjectId.value) return;
  participantsLoading.value = true;
  participantsError.value = null;
  try {
    const response = await fetchTeacherProjectParticipants(
      activityProjectId.value,
      participantFilter,
      participantPage.value - 1,
      pageSize,
    );
    participants.value = response.items;
    participantTotal.value = response.totalElements;
  } catch (caught) {
    participants.value = [];
    participantTotal.value = 0;
    participantsError.value = errorMessage(caught, '参赛人员加载失败');
  } finally {
    participantsLoading.value = false;
  }
}

function searchParticipants(): void {
  participantPage.value = 1;
  void loadParticipants();
}

function resetParticipants(): void {
  participantFilter.keyword = '';
  participantFilter.status = undefined;
  searchParticipants();
}

function changeParticipantPage(): void {
  void loadParticipants();
}

function openScoreDialog(participant: TeacherProjectParticipantItem): void {
  if (terminal.value) return;
  selectedParticipant.value = participant;
  resetScoreForm();
  scoreDialogVisible.value = true;
}

async function submitScore(): Promise<void> {
  if (
    submittingScore.value ||
    !detail.value ||
    !selectedParticipant.value ||
    terminal.value
  ) {
    return;
  }
  if (!validateScoreForm(detail.value.scoreStorageType)) return;
  submittingScore.value = true;
  submitError.value = null;
  const payload: CreateTeacherScorePayload = {
    activityProjectId: detail.value.activityProjectId,
    studentId: selectedParticipant.value.studentId,
    scoreBusinessTime: scoreForm.scoreBusinessTime,
    timeSource: scoreForm.timeSource,
  };
  addScoreValue(payload, detail.value.scoreStorageType);
  try {
    await createTeacherScoreAttempt(payload);
    scoreDialogVisible.value = false;
    resetScoreForm();
    await loadParticipants();
    window.dispatchEvent(new CustomEvent('teacher-score-attempts:changed'));
  } catch (caught) {
    submitError.value = errorMessage(caught, '成绩提交失败');
  } finally {
    submittingScore.value = false;
  }
}

function validateScoreForm(type: ScoreStorageType): boolean {
  scoreErrors.value = '';
  scoreErrors.businessTime = scoreForm.scoreBusinessTime ? '' : '请选择业务发生时间';
  scoreErrors.timeSource = scoreForm.timeSource ? '' : '请选择时间来源';
  if (type === 'INTEGER' && scoreForm.integerValue === null) {
    scoreErrors.value = '请输入整数成绩';
  } else if (type === 'DECIMAL' && scoreForm.decimalValue === null) {
    scoreErrors.value = '请输入小数成绩';
  } else if (type === 'DURATION' && scoreForm.durationMs === null) {
    scoreErrors.value = '请输入时长，0 毫秒是合法成绩';
  } else if (type === 'GRADE' && !scoreForm.grade) {
    scoreErrors.value = '请选择等级';
  }
  return !scoreErrors.value && !scoreErrors.businessTime && !scoreErrors.timeSource;
}

function addScoreValue(payload: CreateTeacherScorePayload, type: ScoreStorageType): void {
  if (type === 'INTEGER') payload.integerValue = scoreForm.integerValue;
  if (type === 'DECIMAL') payload.decimalValue = scoreForm.decimalValue;
  if (type === 'DURATION') payload.durationMs = scoreForm.durationMs;
  if (type === 'GRADE') payload.grade = scoreForm.grade;
}

function resetScoreForm(): void {
  Object.assign(scoreForm, emptyScoreForm());
  scoreErrors.value = '';
  scoreErrors.businessTime = '';
  scoreErrors.timeSource = '';
  submitError.value = null;
}

function emptyScoreForm(): ScoreFormState {
  return {
    integerValue: null,
    decimalValue: null,
    durationMs: null,
    grade: '',
    scoreBusinessTime: '',
    timeSource: '',
  };
}

function attemptStatusLabel(status: ScoreAttemptStatus | null): string {
  if (!status) return '未录入';
  return {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    INVALIDATED: '已失效',
  }[status];
}

function executionStatusLabel(status: string): string {
  return {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    IN_PROGRESS: '进行中',
    ENDED: '已结束',
    CANCELLED: '已取消',
  }[status] ?? status;
}

function scoreTypeLabel(type: ScoreStorageType): string {
  return {
    INTEGER: '整数',
    DECIMAL: '小数',
    DURATION: '时长',
    GRADE: '等级',
  }[type];
}

function comparisonLabel(value: string): string {
  return {
    HIGHER_BETTER: '数值越高越好',
    LOWER_BETTER: '数值越低越好',
    GRADE_ORDER: '按等级顺序',
    NO_RANKING: '不排名',
  }[value] ?? value;
}

function effectiveRuleLabel(value: string): string {
  return {
    BEST: '最佳成绩',
    LAST: '最近成绩',
    ADMIN_DESIGNATED: '管理员指定',
  }[value] ?? value;
}

function formatRange(start: string | null, end: string | null): string {
  if (!start && !end) return '-';
  return `${formatTime(start)} 至 ${formatTime(end)}`;
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function errorMessage(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback;
}

onMounted(() => {
  void loadDetail();
});
</script>

<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.page-heading h2 {
  margin: 0;
}

.project-summary,
.terminal-alert,
.participant-filters {
  margin-bottom: 16px;
}

.participant-section h3 {
  margin-top: 24px;
}

.pagination {
  justify-content: center;
  margin-top: 20px;
}

.score-context {
  margin-bottom: 18px;
}

.submit-error {
  margin-top: 12px;
}

.field-error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
  width: 100%;
}
</style>
