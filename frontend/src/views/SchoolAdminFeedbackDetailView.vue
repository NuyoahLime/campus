<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { beginSchoolAdminFeedback, getSchoolAdminFeedback, resolveSchoolAdminFeedback } from '../api/schoolAdminFeedback';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { StudentFeedback } from '../types/studentFeedback';
import { labelForFeedbackStatus, labelForFeedbackType } from '../utils/studentFeedbackLabels';

const route = useRoute();
const detail = ref<StudentFeedback | null>(null);
const loading = ref(true);
const mutating = ref(false);
const error = ref('');
const message = ref('');
const reply = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getSchoolAdminFeedback(String(route.params.id));
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 404
      ? '该反馈不存在或不属于当前学校。'
      : '反馈详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function beginProcessing() {
  if (!detail.value || mutating.value) return;
  mutating.value = true;
  error.value = '';
  try {
    await beginSchoolAdminFeedback(detail.value.feedbackId);
    message.value = '反馈已进入处理中。';
    await load();
  } catch {
    error.value = '开始处理失败，请稍后重试。';
  } finally {
    mutating.value = false;
  }
}

async function resolve() {
  if (!detail.value || !reply.value.trim() || mutating.value) return;
  mutating.value = true;
  error.value = '';
  try {
    await resolveSchoolAdminFeedback(detail.value.feedbackId, reply.value.trim());
    reply.value = '';
    message.value = '反馈已回复。';
    await load();
  } catch {
    error.value = '回复失败，请稍后重试。';
  } finally {
    mutating.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell
    role-label="学校管理员"
    workspace-title="学校管理工作台"
    page-title="反馈详情"
    description="查看本校反馈资料并执行当前领域支持的处理动作。"
    home-path="/school-admin"
    :navigation="navigation"
  >
    <RouterLink class="project-back-link" to="/school-admin/feedback">返回本校反馈</RouterLink>
    <p v-if="message" class="project-inline-success">{{ message }}</p>
    <div v-if="loading" class="project-state">正在加载反馈详情...</div>
    <div v-else-if="error && !detail" class="project-state project-state-error">
      <strong>{{ error }}</strong>
      <RouterLink class="secondary-button" to="/school-admin/feedback">返回反馈列表</RouterLink>
    </div>
    <template v-else-if="detail">
      <header class="student-score-detail-heading">
        <div>
          <p class="eyebrow">SCHOOL FEEDBACK</p>
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
          <h3>处理动作</h3>
          <div class="student-action-form">
            <button
              class="primary-button"
              type="button"
              :disabled="mutating || !['SUBMITTED', 'ESCALATED'].includes(detail.status)"
              @click="beginProcessing"
            >
              开始处理
            </button>
            <textarea
              v-model="reply"
              rows="4"
              placeholder="回复内容"
              :disabled="mutating || detail.status !== 'PROCESSING'"
            />
            <button
              class="secondary-button"
              type="button"
              :disabled="mutating || !reply.trim() || detail.status !== 'PROCESSING'"
              @click="resolve"
            >
              回复并完成
            </button>
          </div>
        </div>
        <div class="student-score-detail-section">
          <h3>当前回复</h3>
          <p class="student-score-rules">{{ detail.reply || '暂无回复。' }}</p>
        </div>
      </section>
    </template>
  </WorkspaceShell>
</template>
