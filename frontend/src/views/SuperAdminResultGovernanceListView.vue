<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listGovernanceResults } from '../api/activityResult';
import type { GovernanceResultSummary } from '../types/activityResult';
import type { PageResponse } from '../types/schoolGovernance';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import { labelForActivityResultStatus } from '../utils/activityResultLabels';

const status = ref('');
const blocked = ref('');
const page = ref(0);
const result = ref<PageResponse<GovernanceResultSummary> | null>(null);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true; error.value = '';
  try { result.value = await listGovernanceResults(page.value, 20, status.value, blocked.value === '' ? undefined : blocked.value === 'true'); }
  catch (value) { result.value = null; error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有成果治理权限。' : '成果治理列表加载失败，请稍后重试。'; }
  finally { loading.value = false; }
}
watch([status, blocked], () => { page.value = 0; void load(); });
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="超级管理员" workspace-title="平台管理工作台" page-title="成果治理" description="通过后端发现已公开或已下架成果，查看治理历史并执行平台操作。" home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="governance-panel"><div class="governance-toolbar"><div><h2>成果治理对象</h2><p>共 {{ result?.totalElements ?? 0 }} 条</p></div><div class="governance-filters"><label class="governance-filter"><span>公开状态</span><select v-model="status"><option value="">全部</option><option value="PUBLIC">已公开</option><option value="PLATFORM_TAKEDOWN">已下架</option><option value="ANOMALY_PENDING">异常处理中</option></select></label><label class="governance-filter"><span>可见性</span><select v-model="blocked"><option value="">全部</option><option value="false">可见</option><option value="true">已阻断</option></select></label></div></div><div v-if="loading" class="registration-state" role="status">正在加载治理列表...</div><div v-else-if="error" class="registration-state registration-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div><div v-else-if="!result?.items.length" class="registration-state"><strong>暂无治理对象</strong></div><div v-else class="governance-table-wrap"><table class="governance-table"><thead><tr><th>学校</th><th>活动</th><th>执行状态</th><th>成果状态</th><th>公开版本</th><th>可见性</th><th>操作</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.resultId"><td>{{ item.schoolName }}</td><td>{{ item.activityTitle }}</td><td>{{ item.activityExecutionStatus }}</td><td>{{ labelForActivityResultStatus(item.publicStatus) }}</td><td>{{ item.currentPublicVersionId || '无' }}</td><td>{{ item.publicVisibilityBlocked ? '已阻断' : '可见' }}</td><td><RouterLink class="registration-detail-link" :to="`/super-admin/activity-results/${item.resultId}`">治理详情</RouterLink></td></tr></tbody></table></div></section>
  </WorkspaceShell>
</template>
