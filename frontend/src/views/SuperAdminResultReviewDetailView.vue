<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { approvePendingResultReview, getPendingResultReview, rejectPendingResultReview } from '../api/activityResult';
import type { PendingResultReviewDetail } from '../types/activityResult';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import { labelForActivityResultAction, labelForActivityResultStatus } from '../utils/activityResultLabels';

const route = useRoute();
const router = useRouter();
const detail = ref<PendingResultReviewDetail | null>(null);
const reason = ref('');
const loading = ref(true);
const saving = ref(false);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try { detail.value = await getPendingResultReview(String(route.params.id)); }
  catch (value) { detail.value = null; error.value = value instanceof ApiError && value.status === 409 ? '审核对象已变化，无法继续使用旧页面。' : '审核详情加载失败，请稍后重试。'; }
  finally { loading.value = false; }
}

async function decide(action: 'approve' | 'reject') {
  if (action === 'reject' && !reason.value.trim()) { error.value = '驳回时必须填写原因。'; return; }
  saving.value = true;
  error.value = '';
  try {
    if (action === 'approve') await approvePendingResultReview(String(route.params.id));
    else await rejectPendingResultReview(String(route.params.id), reason.value.trim());
    await router.replace('/super-admin/activity-results/reviews');
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 409 ? '审核对象已变化，页面将重新读取权威详情。' : '审核操作失败，请稍后重试。';
    await load();
  } finally { saving.value = false; }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="超级管理员" workspace-title="平台管理工作台" page-title="成果审核详情" description="审核内容只绑定当前候选版本，过期页面不会静默提交。" home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel activity-editor"><RouterLink class="project-back-link" to="/super-admin/activity-results/reviews">返回成果审核</RouterLink><div v-if="loading" class="project-state" role="status">正在加载审核详情...</div><div v-else-if="!detail" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div><template v-else><div v-if="error" class="project-inline-error" role="alert">{{ error }}</div><section class="registration-detail-section"><header><p class="eyebrow">PENDING RESULT REVIEW</p><h2>{{ detail.candidateTitle }}</h2></header><div class="registration-detail-grid"><div><dt>学校</dt><dd>{{ detail.schoolName || detail.schoolId }}</dd></div><div><dt>活动</dt><dd>{{ detail.activityTitle || detail.activityId }}</dd></div><div><dt>活动状态</dt><dd>{{ detail.activityExecutionStatus || '未提供' }}</dd></div><div><dt>候选版本</dt><dd>V{{ detail.candidateVersionNumber }}</dd></div><div class="registration-detail-grid-wide"><dt>摘要</dt><dd>{{ detail.summaryText }}</dd></div><div class="registration-detail-grid-wide"><dt>成绩亮点</dt><dd>{{ detail.scoreHighlights.join('、') || '暂无' }}</dd></div><div class="registration-detail-grid-wide"><dt>媒体引用</dt><dd>{{ detail.mediaRefs.join('、') || '暂无' }}</dd></div></div></section><section class="registration-review-panel"><div><h2>处理审核</h2><p>当前状态：{{ labelForActivityResultStatus(detail.publicStatus) }}</p></div><div class="registration-review-actions"><button class="primary-button" type="button" :disabled="saving" @click="decide('approve')">通过</button><button class="registration-danger-button" type="button" :disabled="saving || !reason.trim()" @click="decide('reject')">驳回</button></div></section><label class="project-form-wide">驳回原因<textarea v-model="reason" rows="4" maxlength="2000" :disabled="saving"></textarea></label><section v-if="detail.history.length" class="registration-detail-section"><header><h2>当前版本审核记录</h2></header><div v-for="entry in detail.history" :key="entry.id" class="registration-detail-grid"><div><dt>动作</dt><dd>{{ labelForActivityResultAction(entry.action) }}</dd></div><div><dt>时间</dt><dd>{{ new Date(entry.createdAt).toLocaleString() }}</dd></div><div v-if="entry.reason" class="registration-detail-grid-wide"><dt>原因</dt><dd>{{ entry.reason }}</dd></div></div></section></template></section>
  </WorkspaceShell>
</template>
