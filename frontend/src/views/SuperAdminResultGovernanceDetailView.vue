<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getGovernanceResult, resetActivityResultTakedown, takedownActivityResult } from '../api/activityResult';
import type { GovernanceResultDetail } from '../types/activityResult';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import { labelForActivityResultAction, labelForActivityResultStatus } from '../utils/activityResultLabels';

const route = useRoute();
const detail = ref<GovernanceResultDetail | null>(null);
const reason = ref('');
const loading = ref(true);
const saving = ref(false);
const error = ref('');
const message = ref('');

async function load() {
  loading.value = true; error.value = '';
  try { detail.value = await getGovernanceResult(String(route.params.id)); }
  catch (value) { detail.value = null; error.value = value instanceof ApiError && value.status === 409 ? '治理对象数据发生变化，请重新读取。' : '治理详情加载失败，请稍后重试。'; }
  finally { loading.value = false; }
}

async function run(action: 'takedown' | 'reset') {
  if (!reason.value.trim()) { error.value = '请填写治理原因。'; return; }
  saving.value = true; error.value = ''; message.value = '';
  try { if (action === 'takedown') await takedownActivityResult(String(route.params.id), reason.value.trim()); else await resetActivityResultTakedown(String(route.params.id), reason.value.trim()); reason.value = ''; message.value = '治理操作已完成。'; await load(); }
  catch (value) { error.value = value instanceof ApiError && value.status === 409 ? '治理对象状态已变化，请重新读取后重试。' : '治理操作失败，请稍后重试。'; await load(); }
  finally { saving.value = false; }
}
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="超级管理员" workspace-title="平台管理工作台" page-title="成果治理详情" description="治理操作不会删除版本或历史，公开版本指针会保留用于审计。" home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel activity-editor"><RouterLink class="project-back-link" to="/super-admin/activity-results">返回成果治理</RouterLink><div v-if="loading" class="project-state" role="status">正在加载治理详情...</div><div v-else-if="!detail" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div><template v-else><div v-if="error" class="project-inline-error" role="alert">{{ error }}</div><div v-if="message" class="project-inline-success" role="status">{{ message }}</div><section class="registration-detail-section"><header><p class="eyebrow">RESULT GOVERNANCE</p><h2>{{ detail.currentPublicVersion.title }}</h2></header><div class="registration-detail-grid"><div><dt>学校</dt><dd>{{ detail.schoolName }}</dd></div><div><dt>活动</dt><dd>{{ detail.activityTitle }}</dd></div><div><dt>活动状态</dt><dd>{{ detail.activityExecutionStatus }}</dd></div><div><dt>公开状态</dt><dd>{{ labelForActivityResultStatus(detail.publicStatus) }}</dd></div><div><dt>公开可见性</dt><dd>{{ detail.publicVisibilityBlocked ? '已阻断' : '可见' }}</dd></div><div><dt>公开版本</dt><dd>V{{ detail.currentPublicVersion.versionNumber }}</dd></div><div class="registration-detail-grid-wide"><dt>摘要</dt><dd>{{ detail.currentPublicVersion.summaryText }}</dd></div><div class="registration-detail-grid-wide"><dt>亮点</dt><dd>{{ detail.currentPublicVersion.scoreHighlights.join('、') || '暂无' }}</dd></div></div></section><section class="registration-review-panel"><div><h2>平台治理</h2><p>状态：{{ labelForActivityResultStatus(detail.publicStatus) }}</p></div><div class="registration-review-actions"><button v-if="!detail.publicVisibilityBlocked" class="registration-danger-button" type="button" :disabled="saving || !reason.trim()" @click="run('takedown')">下架成果</button><button v-else class="primary-button" type="button" :disabled="saving || !reason.trim()" @click="run('reset')">重置下架</button></div></section><label class="project-form-wide">处理原因<textarea v-model="reason" rows="4" maxlength="2000" :disabled="saving"></textarea></label><section v-if="detail.governanceHistory.length" class="registration-detail-section"><header><h2>治理历史</h2></header><div v-for="entry in detail.governanceHistory" :key="entry.id" class="registration-detail-grid"><div><dt>动作</dt><dd>{{ labelForActivityResultAction(entry.action) }}</dd></div><div><dt>时间</dt><dd>{{ new Date(entry.createdAt).toLocaleString() }}</dd></div><div class="registration-detail-grid-wide"><dt>原因</dt><dd>{{ entry.reason }}</dd></div></div></section></template></section>
  </WorkspaceShell>
</template>
