<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getStudentAppeal, withdrawStudentAppeal } from '../api/studentAppeal';
import { studentNavigation as navigation } from '../router/studentNavigation';
import type { StudentAppeal } from '../types/studentAppeal';
import { labelForAppealStatus, labelForAppealType } from '../utils/studentAppealLabels';
import { formatStudentScore, labelForStudentScoreStorageType } from '../utils/studentScoreLabels';

const route = useRoute();
const detail = ref<StudentAppeal | null>(null);
const loading = ref(true);
const mutating = ref(false);
const error = ref('');
const message = ref('');
const canWithdraw = computed(() => ['SUBMITTED', 'PROCESSING', 'RANK_CHECKING'].includes(detail.value?.status ?? ''));

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getStudentAppeal(String(route.params.id));
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 404
      ? '该申诉不存在或当前不可查看。'
      : '申诉详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function withdraw() {
  if (!detail.value || !canWithdraw.value || mutating.value) return;
  mutating.value = true;
  error.value = '';
  message.value = '';
  try {
    await withdrawStudentAppeal(detail.value.appealId);
    message.value = '申诉已撤回。';
    await load();
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 409
      ? '当前申诉状态不允许撤回。'
      : '撤回失败，请稍后重试。';
  } finally {
    mutating.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="学生" workspace-title="学生个人工作台" page-title="申诉详情" description="查看自己的申诉处理状态和处理意见。" home-path="/student" :navigation="navigation" :show-identity="false">
    <RouterLink class="project-back-link" to="/student/appeals">返回我的申诉</RouterLink>
    <p v-if="message" class="project-inline-success">{{ message }}</p>
    <div v-if="loading" class="project-state">正在加载申诉详情...</div>
    <div v-else-if="error && !detail" class="project-state project-state-error"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/student/appeals">返回申诉列表</RouterLink></div>
    <template v-else-if="detail">
      <header class="student-score-detail-heading">
        <div>
          <p class="eyebrow">APPEAL DETAIL</p>
          <h2>{{ detail.challengeProjectName }}</h2>
          <p>{{ detail.activityName }}</p>
        </div>
        <button class="secondary-button" type="button" :disabled="!canWithdraw || mutating" @click="withdraw">
          {{ mutating ? '处理中...' : '撤回申诉' }}
        </button>
      </header>
      <p v-if="error" class="project-inline-error">{{ error }}</p>
      <section class="student-score-detail-grid">
        <div class="student-score-detail-section">
          <h3>申诉信息</h3>
          <dl>
            <div><dt>申诉类型</dt><dd>{{ labelForAppealType(detail.appealType) }}</dd></div>
            <div><dt>状态</dt><dd><span class="student-score-status">{{ labelForAppealStatus(detail.status) }}</span></dd></div>
            <div><dt>提交时间</dt><dd>{{ new Date(detail.createdAt).toLocaleString() }}</dd></div>
            <div><dt>申诉原因</dt><dd>{{ detail.appealReason }}</dd></div>
          </dl>
        </div>
        <div class="student-score-detail-section">
          <h3>关联成绩</h3>
          <dl>
            <div><dt>成绩</dt><dd>{{ formatStudentScore(detail.scoreValue, detail.scoreStorageType, detail.scoreUnit) }}</dd></div>
            <div><dt>成绩类型</dt><dd>{{ labelForStudentScoreStorageType(detail.scoreStorageType) }}</dd></div>
            <div><dt>成绩 ID</dt><dd class="student-score-breakable">{{ detail.scoreAttemptId }}</dd></div>
          </dl>
        </div>
        <div class="student-score-detail-section">
          <h3>处理结果</h3>
          <p class="student-score-rules">{{ detail.resolution || '暂无处理意见。' }}</p>
        </div>
      </section>
    </template>
  </WorkspaceShell>
</template>
