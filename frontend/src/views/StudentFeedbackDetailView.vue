<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { closeStudentFeedback, getStudentFeedback } from '../api/studentFeedback';
import { studentNavigation as navigation } from '../router/studentNavigation';
import type { StudentFeedback } from '../types/studentFeedback';
import { labelForFeedbackStatus, labelForFeedbackType } from '../utils/studentFeedbackLabels';

const route = useRoute();
const detail = ref<StudentFeedback | null>(null);
const closeReason = ref('');
const loading = ref(true);
const mutating = ref(false);
const error = ref('');
const message = ref('');
const canClose = computed(() => ['SUBMITTED', 'PROCESSING', 'RESOLVED'].includes(detail.value?.status ?? ''));

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getStudentFeedback(String(route.params.id));
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 404
      ? '该反馈不存在或当前不可查看。'
      : '反馈详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function close() {
  if (!detail.value || !canClose.value || !closeReason.value.trim() || mutating.value) return;
  mutating.value = true;
  error.value = '';
  message.value = '';
  try {
    await closeStudentFeedback(detail.value.feedbackId, closeReason.value.trim());
    closeReason.value = '';
    message.value = '反馈已关闭。';
    await load();
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 409
      ? '当前反馈状态不允许关闭。'
      : '关闭失败，请稍后重试。';
  } finally {
    mutating.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="学生" workspace-title="学生个人工作台" page-title="反馈详情" description="查看自己的反馈内容、回复和关闭状态。" home-path="/student" :navigation="navigation" :show-identity="false">
    <RouterLink class="project-back-link" to="/student/feedback">返回意见反馈</RouterLink>
    <p v-if="message" class="project-inline-success">{{ message }}</p>
    <div v-if="loading" class="project-state">正在加载反馈详情...</div>
    <div v-else-if="error && !detail" class="project-state project-state-error"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/student/feedback">返回反馈列表</RouterLink></div>
    <template v-else-if="detail">
      <header class="student-score-detail-heading">
        <div>
          <p class="eyebrow">FEEDBACK DETAIL</p>
          <h2>{{ labelForFeedbackType(detail.feedbackType) }}</h2>
          <p>{{ new Date(detail.createdAt).toLocaleString() }}</p>
        </div>
        <span class="student-score-status">{{ labelForFeedbackStatus(detail.status) }}</span>
      </header>
      <p v-if="error" class="project-inline-error">{{ error }}</p>
      <section class="student-score-detail-grid">
        <div class="student-score-detail-section">
          <h3>反馈内容</h3>
          <p class="student-score-rules">{{ detail.content }}</p>
        </div>
        <div class="student-score-detail-section">
          <h3>处理回复</h3>
          <p class="student-score-rules">{{ detail.reply || '暂无回复。' }}</p>
        </div>
        <div class="student-score-detail-section">
          <h3>关闭反馈</h3>
          <p class="student-score-rules">{{ detail.closeReason || '如问题已处理，可以关闭反馈。' }}</p>
          <form v-if="canClose" class="student-action-form" @submit.prevent="close">
            <textarea v-model="closeReason" rows="4" placeholder="关闭说明" required />
            <button class="secondary-button" type="submit" :disabled="mutating || !closeReason.trim()">
              {{ mutating ? '处理中...' : '关闭反馈' }}
            </button>
          </form>
        </div>
      </section>
    </template>
  </WorkspaceShell>
</template>
