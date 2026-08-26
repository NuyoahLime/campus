<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import {
  createSchoolAdminScoreDraft,
  approveScoreAttempt,
  getSchoolAdminScoreCandidates,
  getSchoolAdminScoreDetail,
  getSchoolAdminScores,
  rejectScoreAttempt,
  returnScoreAttemptToDraft,
  submitScoreAttempt,
  updateSchoolAdminScoreDraft
} from '../api/schoolAdminScore';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type {
  SchoolAdminScoreCandidate,
  SchoolAdminScoreCandidateProject,
  SchoolAdminScoreDraftRequest,
  SchoolAdminScoreDraftUpdateRequest,
  SchoolAdminScoreListItem
} from '../types/schoolAdminScore';

type DraftMode = 'create' | 'edit';
type LifecycleAction = 'submit' | 'approve' | 'reject' | 'return-to-draft';
type LifecycleDialog = {
  action: LifecycleAction;
  score: SchoolAdminScoreListItem;
};
type PendingAction = {
  attemptId: string;
  action: LifecycleAction;
};
type DraftEditor = {
  mode: DraftMode;
  scoreAttemptId: string | null;
  activityProjectId: string;
  studentId: string;
  studentDisplay: string;
  studentNumber: string;
  projectName: string;
  scoreStorageType: string;
  attemptNumber: number | null;
  integerValue: string;
  decimalValue: string;
  durationMs: string;
  grade: string;
  scoreBusinessTime: string;
};

type CandidateRow = SchoolAdminScoreCandidateProject & Pick<SchoolAdminScoreCandidate, 'studentId' | 'studentDisplay' | 'studentNumber'>;

const route = useRoute();
const activityId = () => String(route.params.id);

const loading = ref(true);
const saving = ref(false);
const error = ref('');
const message = ref('');
const activityTitle = ref('');
const activityStatus = ref('');
const scores = ref<SchoolAdminScoreListItem[]>([]);
const candidates = ref<SchoolAdminScoreCandidate[]>([]);
const editor = ref<DraftEditor | null>(null);
const lifecycleDialog = ref<LifecycleDialog | null>(null);
const rejectReason = ref('');
const rejectReasonError = ref('');
const pendingAction = ref<PendingAction | null>(null);

const candidateRows = computed<CandidateRow[]>(() =>
  candidates.value.flatMap((candidate) =>
    candidate.projects.map((project) => ({
      ...project,
      studentId: candidate.studentId,
      studentDisplay: candidate.studentDisplay,
      studentNumber: candidate.studentNumber
    }))
  )
);

function labelForScoreStorageType(value: string | null | undefined): string {
  return {
    INTEGER: '整数',
    DECIMAL: '小数',
    DURATION: '时长',
    GRADE: '等级'
  }[value ?? ''] ?? '成绩类型';
}

function labelForScoreStatus(value: string | null | undefined): string {
  return {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    INVALIDATED: '已作废'
  }[value ?? ''] ?? '未知状态';
}

function displayScore(score: SchoolAdminScoreListItem): string {
  if (score.integerValue !== null && score.integerValue !== undefined) return String(score.integerValue);
  if (score.decimalValue !== null && score.decimalValue !== undefined) return String(score.decimalValue);
  if (score.durationMs !== null && score.durationMs !== undefined) return `${score.durationMs} ms`;
  if (score.grade !== null && score.grade !== undefined && score.grade !== '') return score.grade;
  return '未填写';
}

function editorValueSummary(value: DraftEditor): string {
  if (value.scoreStorageType === 'INTEGER' && String(value.integerValue).trim()) return String(value.integerValue);
  if (value.scoreStorageType === 'DECIMAL' && String(value.decimalValue).trim()) return String(value.decimalValue);
  if (value.scoreStorageType === 'DURATION' && String(value.durationMs).trim()) return `${value.durationMs} ms`;
  if (value.scoreStorageType === 'GRADE' && String(value.grade).trim()) return String(value.grade);
  return '未填写';
}

function localInputValue(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function currentLocalInputValue() {
  return localInputValue(new Date().toISOString());
}

function buildEditor(detail: {
  scoreAttemptId: string;
  activityProjectId: string;
  studentId: string;
  studentDisplay: string | null;
  studentNumber: string | null;
  projectName: string;
  scoreStorageType: string;
  attemptNumber: number;
  scoreBusinessTime: string | null;
  integerValue?: number | null;
  decimalValue?: number | string | null;
  durationMs?: number | null;
  grade?: string | null;
}, mode: DraftMode): DraftEditor {
  const numericValue = detail.scoreStorageType === 'INTEGER'
    ? detail.integerValue === null || detail.integerValue === undefined ? '' : String(detail.integerValue)
    : detail.decimalValue === null || detail.decimalValue === undefined ? '' : String(detail.decimalValue);
  return {
    mode,
    scoreAttemptId: detail.scoreAttemptId,
    activityProjectId: detail.activityProjectId,
    studentId: detail.studentId,
    studentDisplay: detail.studentDisplay || '',
    studentNumber: detail.studentNumber || '',
    projectName: detail.projectName,
    scoreStorageType: detail.scoreStorageType,
    attemptNumber: detail.attemptNumber,
    integerValue: detail.scoreStorageType === 'INTEGER' ? numericValue : '',
    decimalValue: detail.scoreStorageType === 'DECIMAL' ? numericValue : '',
    durationMs: detail.scoreStorageType === 'DURATION' && detail.durationMs !== null && detail.durationMs !== undefined
      ? String(detail.durationMs)
      : '',
    grade: detail.scoreStorageType === 'GRADE' && detail.grade ? detail.grade : '',
    scoreBusinessTime: localInputValue(detail.scoreBusinessTime)
  };
}

function emptyEditor(candidate: CandidateRow): DraftEditor {
  return {
    mode: 'create',
    scoreAttemptId: null,
    activityProjectId: candidate.activityProjectId,
    studentId: candidate.studentId,
    studentDisplay: candidate.studentDisplay || '',
    studentNumber: candidate.studentNumber || '',
    projectName: candidate.projectName,
    scoreStorageType: candidate.scoreStorageType,
    attemptNumber: candidate.latestAttemptNumber ? candidate.latestAttemptNumber + 1 : 1,
    integerValue: '',
    decimalValue: '',
    durationMs: '',
    grade: '',
    scoreBusinessTime: currentLocalInputValue()
  };
}

async function loadAll(clearMessage = true) {
  loading.value = true;
  error.value = '';
  if (clearMessage) message.value = '';
  try {
    const [scoreResult, candidateResult] = await Promise.all([
      getSchoolAdminScores(activityId()),
      getSchoolAdminScoreCandidates(activityId())
    ]);
    activityTitle.value = scoreResult.activityTitle || candidateResult.activityTitle;
    activityStatus.value = scoreResult.activityStatus || candidateResult.activityStatus;
    scores.value = scoreResult.scores;
    candidates.value = candidateResult.candidates;
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有本校成绩管理权限。'
      : '成绩管理数据加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function refreshScores() {
  return loadAll();
}

async function openCreate(candidate: CandidateRow) {
  error.value = '';
  message.value = '';
  editor.value = emptyEditor(candidate);
}

async function openEdit(scoreAttemptId: string) {
  error.value = '';
  message.value = '';
  try {
    const detail = await getSchoolAdminScoreDetail(scoreAttemptId);
    editor.value = buildEditor(detail, 'edit');
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 404
      ? '草稿未找到，请刷新后再试。'
      : '草稿加载失败，请稍后重试。';
  }
}

function closeEditor() {
  editor.value = null;
}

function isActionPending(scoreAttemptId: string): boolean {
  return pendingAction.value?.attemptId === scoreAttemptId;
}

function actionLabel(action: LifecycleAction): string {
  return {
    submit: '提交审核',
    approve: '审核通过',
    reject: '驳回',
    'return-to-draft': '退回草稿'
  }[action];
}

function actionSuccessMessage(action: LifecycleAction): string {
  return {
    submit: '成绩已提交审核。',
    approve: '审核已通过。',
    reject: '成绩已驳回。',
    'return-to-draft': '成绩已退回草稿。'
  }[action];
}

function openLifecycleDialog(action: LifecycleAction, score: SchoolAdminScoreListItem) {
  if (isActionPending(score.scoreAttemptId)) return;
  rejectReason.value = '';
  rejectReasonError.value = '';
  lifecycleDialog.value = { action, score };
}

function closeLifecycleDialog(force = false) {
  if (!force && lifecycleDialog.value && isActionPending(lifecycleDialog.value.score.scoreAttemptId)) return;
  lifecycleDialog.value = null;
  rejectReason.value = '';
  rejectReasonError.value = '';
}

function lifecycleErrorMessage(value: unknown): string {
  if (!(value instanceof ApiError)) return '操作失败，请稍后重试。';
  if (value.status === 400) return '请求数据不合法，请检查后重试。';
  if (value.status === 401) return '登录状态已失效，请重新登录。';
  if (value.status === 403) return '当前账号无权执行此操作。';
  if (value.status === 404) return '成绩记录不存在或已不可用。';
  if (value.status === 409) return '成绩状态已发生变化，请查看最新状态。';
  return '操作失败，请稍后重试。';
}

async function confirmLifecycleAction() {
  const dialog = lifecycleDialog.value;
  if (!dialog || isActionPending(dialog.score.scoreAttemptId)) return;

  const reason = rejectReason.value.trim();
  if (dialog.action === 'reject' && !reason) {
    rejectReasonError.value = '请输入驳回原因。';
    return;
  }

  const attemptId = dialog.score.scoreAttemptId;
  pendingAction.value = { attemptId, action: dialog.action };
  rejectReasonError.value = '';
  error.value = '';
  message.value = '';

  try {
    switch (dialog.action) {
      case 'submit':
        await submitScoreAttempt(attemptId);
        break;
      case 'approve':
        await approveScoreAttempt(attemptId);
        break;
      case 'reject':
        await rejectScoreAttempt(attemptId, reason);
        break;
      case 'return-to-draft':
        await returnScoreAttemptToDraft(attemptId);
        break;
    }
    closeLifecycleDialog(true);
    await loadAll(false);
    message.value = actionSuccessMessage(dialog.action);
  } catch (value) {
    const isConflict = value instanceof ApiError && value.status === 409;
    if (isConflict) {
      await loadAll(false);
    }
    error.value = lifecycleErrorMessage(value);
  } finally {
    pendingAction.value = null;
  }
}

function parseInteger(value: string | number): number | null {
  const normalized = String(value).trim();
  if (!normalized) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseDecimal(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function parseDuration(value: string | number): number | null {
  const normalized = String(value).trim();
  if (!normalized) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseGrade(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function toIso(value: string): string | null {
  if (!value.trim()) return null;
  return new Date(value).toISOString();
}

function buildRequestPayload(current: DraftEditor): SchoolAdminScoreDraftRequest {
  return {
    studentId: current.studentId,
    integerValue: current.scoreStorageType === 'INTEGER' ? parseInteger(current.integerValue) : null,
    decimalValue: current.scoreStorageType === 'DECIMAL' ? parseDecimal(current.decimalValue) : null,
    durationMs: current.scoreStorageType === 'DURATION' ? parseDuration(current.durationMs) : null,
    grade: current.scoreStorageType === 'GRADE' ? parseGrade(current.grade) : null,
    scoreBusinessTime: toIso(current.scoreBusinessTime)
  };
}

function buildUpdatePayload(current: DraftEditor): SchoolAdminScoreDraftUpdateRequest {
  const { studentId: _studentId, ...editableFields } = buildRequestPayload(current);
  return editableFields;
}

async function saveEditor() {
  if (!editor.value) return;
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    const current = editor.value;
    const payload = buildRequestPayload(current);
    if (current.mode === 'create') {
      const created = await createSchoolAdminScoreDraft(current.activityProjectId, payload);
      await loadAll(false);
      await openEdit(created.scoreAttemptId);
      message.value = '成绩草稿已创建。';
    } else {
      const updated = await updateSchoolAdminScoreDraft(current.scoreAttemptId!, buildUpdatePayload(current));
      await loadAll(false);
      await openEdit(updated.scoreAttemptId);
      message.value = '成绩草稿已保存。';
    }
  } catch (value) {
    if (value instanceof ApiError) {
      if (value.status === 403) {
        error.value = value.code === 'SCORE_SCOPE_DENIED'
          ? '当前账号不能管理其他学校的活动。'
          : '当前账号没有本校成绩管理权限。';
      } else if (value.status === 404) {
        error.value = value.code === 'SCORE_STUDENT_NOT_FOUND'
          ? '目标学生不存在。'
          : '目标草稿或活动不存在。';
      } else if (value.status === 409) {
        error.value = value.code === 'SCORE_STUDENT_NOT_PARTICIPANT'
          ? '该学生不是本次活动参与者。'
          : '当前草稿状态不允许继续编辑。';
      } else if (value.status === 400) {
        error.value = '成绩值不符合当前规则，请检查输入。';
      } else {
        error.value = '成绩草稿保存失败，请稍后重试。';
      }
    } else {
      error.value = '成绩草稿保存失败，请稍后重试。';
    }
  } finally {
    saving.value = false;
  }
}

watch(() => route.fullPath, () => {
  closeLifecycleDialog(true);
  closeEditor();
  void loadAll();
});
onMounted(() => void loadAll());
</script>

<template>
  <WorkspaceShell
    role-label="学校管理员"
    workspace-title="学校管理工作台"
    page-title="成绩管理"
    :description="activityTitle || '管理本校活动成绩草稿、候选人和已保存记录。'"
    home-path="/school-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="project-admin-panel">
      <div class="project-detail-toolbar">
          <div>
            <RouterLink class="project-back-link" :to="`/school-admin/activities/${activityId()}`">返回活动详情</RouterLink>
            <p v-if="activityTitle" class="eyebrow">SCORE MANAGEMENT</p>
            <h2>{{ activityTitle || '成绩管理' }}</h2>
            <span v-if="activityStatus">{{ activityStatus }}</span>
          </div>
        <button class="secondary-button" type="button" :disabled="loading || saving" @click="refreshScores">刷新</button>
      </div>

      <div v-if="loading" class="project-state" role="status">正在加载成绩管理...</div>
      <div v-else-if="error && !activityTitle" class="project-state project-state-error" role="alert">
        <strong>{{ error }}</strong>
        <button class="secondary-button" type="button" @click="refreshScores">重新加载</button>
      </div>

      <template v-else>
        <div v-if="error" class="project-inline-error" role="alert">{{ error }}</div>
        <div v-if="message" class="project-inline-success" role="status">{{ message }}</div>

        <section class="score-section">
          <header class="score-section-heading">
            <div>
              <p class="eyebrow">CANDIDATES</p>
              <h3>候选人列表</h3>
              <span>用于创建新的成绩草稿</span>
            </div>
          </header>
          <div v-if="!candidateRows.length" class="project-state participant-state">当前活动没有可用的成绩候选人。</div>
          <div v-else class="project-admin-table-wrap">
            <table class="project-admin-table score-table">
              <thead>
                <tr>
                  <th>学生</th>
                  <th>学号</th>
                  <th>项目</th>
                  <th>最近草稿</th>
                  <th>成绩类型</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in candidateRows" :key="`${row.studentId}-${row.activityProjectId}`">
                  <td><strong>{{ row.studentDisplay || row.studentId }}</strong></td>
                  <td>{{ row.studentNumber || '未提供' }}</td>
                  <td>{{ row.projectName }}</td>
                  <td>{{ row.latestStatus ? `${labelForScoreStatus(row.latestStatus)}${row.latestAttemptNumber ? ` #${row.latestAttemptNumber}` : ''}` : '未创建' }}</td>
                  <td>{{ labelForScoreStorageType(row.scoreStorageType) }}</td>
                  <td>
                    <button class="primary-button" type="button" :disabled="saving" @click="openCreate(row)">创建草稿</button>
                    <button
                      v-if="row.latestAttemptId && row.latestStatus === 'DRAFT'"
                      class="secondary-button"
                      type="button"
                      :disabled="saving"
                      @click="openEdit(row.latestAttemptId)"
                    >
                      继续编辑
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="score-section">
          <header class="score-section-heading">
            <div>
              <p class="eyebrow">SCORES</p>
              <h3>已保存成绩</h3>
              <span>支持刷新和仅草稿编辑</span>
            </div>
          </header>
          <div v-if="!scores.length" class="project-state participant-state">当前活动还没有成绩记录。</div>
          <div v-else class="project-admin-table-wrap">
            <table class="project-admin-table score-table">
              <thead>
                <tr>
                  <th>学生</th>
                  <th>项目</th>
                  <th>尝试</th>
                  <th>状态</th>
                  <th>成绩</th>
                  <th>记录时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="score in scores" :key="score.scoreAttemptId">
                  <td><strong>{{ score.studentDisplay || score.studentId }}</strong></td>
                  <td>{{ score.projectName }}</td>
                  <td>#{{ score.attemptNumber }}</td>
                  <td><span class="score-status" :data-status="score.status">{{ labelForScoreStatus(score.status) }}</span></td>
                  <td>{{ displayScore(score) }}</td>
                  <td>{{ score.scoreBusinessTime ? new Date(score.scoreBusinessTime).toLocaleString() : '未记录' }}</td>
                  <td class="score-row-actions">
                    <button
                      v-if="score.status === 'DRAFT'"
                      class="secondary-button"
                      type="button"
                      :disabled="saving || isActionPending(score.scoreAttemptId)"
                      @click="openEdit(score.scoreAttemptId)"
                    >
                      编辑草稿
                    </button>
                    <button
                      v-if="score.status === 'DRAFT'"
                      class="primary-button"
                      type="button"
                      :disabled="saving || isActionPending(score.scoreAttemptId)"
                      @click="openLifecycleDialog('submit', score)"
                    >
                      提交审核
                    </button>
                    <button
                      v-if="score.status === 'PENDING_REVIEW'"
                      class="primary-button"
                      type="button"
                      :disabled="saving || isActionPending(score.scoreAttemptId)"
                      @click="openLifecycleDialog('approve', score)"
                    >
                      审核通过
                    </button>
                    <button
                      v-if="score.status === 'PENDING_REVIEW'"
                      class="secondary-button danger-outline"
                      type="button"
                      :disabled="saving || isActionPending(score.scoreAttemptId)"
                      @click="openLifecycleDialog('reject', score)"
                    >
                      驳回
                    </button>
                    <button
                      v-if="score.status === 'REJECTED'"
                      class="secondary-button"
                      type="button"
                      :disabled="saving || isActionPending(score.scoreAttemptId)"
                      @click="openLifecycleDialog('return-to-draft', score)"
                    >
                      退回草稿
                    </button>
                    <span v-if="isActionPending(score.scoreAttemptId)" class="score-action-pending" role="status">处理中...</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="editor" class="score-section">
          <header class="score-section-heading">
            <div>
              <p class="eyebrow">{{ editor.mode === 'create' ? 'CREATE DRAFT' : 'EDIT DRAFT' }}</p>
              <h3>{{ editor.mode === 'create' ? '创建成绩草稿' : '编辑成绩草稿' }}</h3>
              <span>{{ editor.studentDisplay || editor.studentId }} · {{ editor.projectName }}</span>
            </div>
            <button class="secondary-button" type="button" :disabled="saving" @click="closeEditor">取消</button>
          </header>

          <div class="score-editor-summary">
            <span>学生：<strong>{{ editor.studentDisplay || editor.studentId }}</strong></span>
            <span>学号：<strong>{{ editor.studentNumber || '未提供' }}</strong></span>
            <span>项目：<strong>{{ editor.projectName }}</strong></span>
            <span>成绩类型：<strong>{{ labelForScoreStorageType(editor.scoreStorageType) }}</strong></span>
            <span v-if="editor.attemptNumber">尝试：<strong>#{{ editor.attemptNumber }}</strong></span>
            <span v-if="editor.mode === 'edit'">当前值：<strong>{{ editorValueSummary(editor) }}</strong></span>
          </div>

          <form class="project-form score-editor-form" @submit.prevent="saveEditor">
            <fieldset>
              <legend>草稿信息</legend>
              <div class="project-form-grid">
                <label class="project-form-wide">
                  学生
                  <input :value="editor.studentDisplay || editor.studentId" readonly>
                </label>
                <label>
                  学号
                  <input :value="editor.studentNumber || '未提供'" readonly>
                </label>
                <label class="project-form-wide">
                  项目
                  <input :value="editor.projectName" readonly>
                </label>
                <label>
                  成绩类型
                  <input :value="labelForScoreStorageType(editor.scoreStorageType)" readonly>
                </label>
                <label>
                  记录时间
                  <input v-model="editor.scoreBusinessTime" type="datetime-local">
                </label>
              </div>
            </fieldset>

            <fieldset>
              <legend>成绩值</legend>
              <div class="project-form-grid">
                <label v-if="editor.scoreStorageType === 'INTEGER'" class="project-form-wide">
                  整数值
                  <input v-model="editor.integerValue" type="number" step="1" required>
                </label>
                <label v-else-if="editor.scoreStorageType === 'DECIMAL'" class="project-form-wide">
                  小数值
                  <input v-model="editor.decimalValue" type="text" inputmode="decimal" required>
                </label>
                <label v-else-if="editor.scoreStorageType === 'DURATION'" class="project-form-wide">
                  时长（毫秒）
                  <input v-model="editor.durationMs" type="number" step="1" min="0" required>
                </label>
                <label v-else-if="editor.scoreStorageType === 'GRADE'" class="project-form-wide">
                  等级
                  <input v-model="editor.grade" type="text" required>
                </label>
              </div>
            </fieldset>

            <div class="project-form-actions">
              <button class="primary-button" type="submit" :disabled="saving">
                {{ saving ? '保存中...' : (editor.mode === 'create' ? '创建草稿' : '保存草稿') }}
              </button>
              <button class="secondary-button" type="button" :disabled="saving" @click="closeEditor">放弃编辑</button>
            </div>
          </form>
        </section>
      </template>

      <div v-if="lifecycleDialog" class="project-modal-backdrop" @click.self="closeLifecycleDialog()">
        <section class="project-modal score-lifecycle-modal" role="dialog" aria-modal="true" aria-labelledby="score-lifecycle-title">
          <p class="eyebrow">SCORE LIFECYCLE</p>
          <h2 id="score-lifecycle-title">{{ actionLabel(lifecycleDialog.action) }}</h2>
          <p class="modal-copy">
            {{ lifecycleDialog.action === 'submit'
              ? '确认将这条成绩提交审核吗？'
              : lifecycleDialog.action === 'approve'
                ? '确认通过这条成绩的审核吗？'
                : lifecycleDialog.action === 'return-to-draft'
                  ? '确认将这条被驳回的成绩退回草稿吗？'
                  : '请填写驳回原因，确认后这条成绩将进入已驳回状态。' }}
          </p>
          <div class="score-lifecycle-summary">
            <strong>{{ lifecycleDialog.score.studentDisplay || lifecycleDialog.score.studentId }}</strong>
            <span>{{ lifecycleDialog.score.projectName }} · #{{ lifecycleDialog.score.attemptNumber }}</span>
          </div>
          <label v-if="lifecycleDialog.action === 'reject'" class="score-reject-field">
            <span>驳回原因</span>
            <textarea
              v-model="rejectReason"
              rows="4"
              maxlength="500"
              :disabled="Boolean(pendingAction)"
              aria-describedby="score-reject-error"
            ></textarea>
            <small id="score-reject-error" v-if="rejectReasonError" class="field-error">{{ rejectReasonError }}</small>
          </label>
          <div class="project-modal-actions">
            <button class="secondary-button" type="button" :disabled="Boolean(pendingAction)" @click="closeLifecycleDialog()">取消</button>
            <button
              :class="lifecycleDialog.action === 'reject' ? 'secondary-button danger-outline' : 'primary-button'"
              type="button"
              :disabled="Boolean(pendingAction)"
              @click="confirmLifecycleAction"
            >
              {{ pendingAction ? '处理中...' : `确认${actionLabel(lifecycleDialog.action)}` }}
            </button>
          </div>
        </section>
      </div>
    </section>
  </WorkspaceShell>
</template>
