<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { beginSchoolAdminAppeal, getSchoolAdminAppeal, rejectSchoolAdminAppeal } from '../api/schoolAdminAppeal';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { StudentAppeal } from '../types/studentAppeal';
import { labelForAppealStatus, labelForAppealType } from '../utils/studentAppealLabels';

const route = useRoute();
const detail = ref<StudentAppeal | null>(null);
const loading = ref(true);
const mutating = ref(false);
const error = ref('');
const message = ref('');
const resolution = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getSchoolAdminAppeal(String(route.params.id));
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 404
      ? '该申诉不存在或不属于当前学校。'
      : '申诉详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function beginProcessing() {
  if (!detail.value || mutating.value) return;
  mutating.value = true;
  error.value = '';
  try {
    await beginSchoolAdminAppeal(detail.value.appealId);
    message.value = '申诉已进入处理中。';
    await load();
  } catch {
    error.value = '开始处理失败，请稍后重试。';
  } finally {
    mutating.value = false;
  }
}

async function reject() {
  if (!detail.value || !resolution.value.trim() || mutating.value) return;
  mutating.value = true;
  error.value = '';
  try {
    await rejectSchoolAdminAppeal(detail.value.appealId, resolution.value.trim());
    resolution.value = '';
    message.value = '申诉已驳回。';
    await load();
  } catch {
    error.value = '驳回失败，请稍后重试。';
  } finally {
    mutating.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" page-title="申诉详情" description="查看本校申诉资料并执行当前领域支持的处理动作。" home-path="/school-admin" :navigation="navigation">
    <RouterLink class="project-back-link" to="/school-admin/appeals">返回本校申诉</RouterLink>
    <p v-if="message" class="project-inline-success">{{ message }}</p>
    <div v-if="loading" class="project-state">正在加载申诉详情...</div>
    <div v-else-if="error && !detail" class="project-state project-state-error"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/school-admin/appeals">返回申诉列表</RouterLink></div>
    <template v-else-if="detail">
      <header class="student-score-detail-heading">
        <div><p class="eyebrow">SCHOOL APPEAL</p><h2>{{ detail.challengeProjectName }}</h2><p>{{ detail.activityName }}</p></div>
        <span class="student-score-status">{{ labelForAppealStatus(detail.status) }}</span>
      </header>
      <p v-if="error" class="project-inline-error">{{ error }}</p>
      <section class="student-score-detail-grid">
        <div class="student-score-detail-section"><h3>申诉信息</h3><dl><div><dt>类型</dt><dd>{{ labelForAppealType(detail.appealType) }}</dd></div><div><dt>原因</dt><dd>{{ detail.appealReason }}</dd></div><div><dt>提交时间</dt><dd>{{ new Date(detail.createdAt).toLocaleString() }}</dd></div></dl></div>
        <div class="student-score-detail-section"><h3>处理动作</h3><div class="student-action-form"><button class="primary-button" type="button" :disabled="mutating || detail.status !== 'SUBMITTED'" @click="beginProcessing">开始处理</button><textarea v-model="resolution" rows="4" placeholder="驳回说明" :disabled="mutating || !['PROCESSING','RANK_CHECKING'].includes(detail.status)" /><button class="secondary-button" type="button" :disabled="mutating || !resolution.trim() || !['PROCESSING','RANK_CHECKING'].includes(detail.status)" @click="reject">驳回申诉</button></div></div>
        <div class="student-score-detail-section"><h3>处理结果</h3><p class="student-score-rules">{{ detail.resolution || '暂无处理结果。' }}</p></div>
      </section>
    </template>
  </WorkspaceShell>
</template>
