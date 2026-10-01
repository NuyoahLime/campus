<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listPendingResultReviews } from '../api/activityResult';
import type { PendingResultReviewSummary } from '../types/activityResult';
import type { PageResponse } from '../types/schoolGovernance';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';

const page = ref(0);
const result = ref<PageResponse<PendingResultReviewSummary> | null>(null);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try { result.value = await listPendingResultReviews(page.value); }
  catch (value) { result.value = null; error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有成果审核权限。' : '成果审核列表加载失败，请稍后重试。'; }
  finally { loading.value = false; }
}

function changePage(next: number) {
  if (next < 0 || next >= (result.value?.totalPages ?? 0)) return;
  page.value = next;
  void load();
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="超级管理员" workspace-title="平台管理工作台" page-title="成果审核" description="审核学校提交的精确成果版本，并保留完整审核记录。" home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="governance-panel">
      <div class="governance-toolbar"><div><h2>待审核成果</h2><p>共 {{ result?.totalElements ?? 0 }} 条</p></div><button class="secondary-button" type="button" :disabled="loading" @click="load">刷新</button></div>
      <div v-if="loading" class="registration-state" role="status">正在加载审核队列...</div>
      <div v-else-if="error" class="registration-state registration-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <div v-else-if="!result?.items.length" class="registration-state"><strong>暂无待审核成果</strong><p>新的平台审核提交会显示在这里。</p></div>
      <div v-else class="governance-table-wrap"><table class="governance-table"><thead><tr><th>学校</th><th>活动</th><th>活动状态</th><th>成果标题</th><th>版本</th><th>提交时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.resultId"><td>{{ item.schoolName }}</td><td>{{ item.activityTitle }}</td><td>{{ item.activityExecutionStatus }}</td><td>{{ item.candidateTitle }}</td><td>V{{ item.candidateVersionNumber }}</td><td>{{ new Date(item.submittedAt).toLocaleString() }}</td><td><RouterLink class="registration-detail-link" :to="`/super-admin/activity-results/reviews/${item.resultId}`">查看详情</RouterLink></td></tr></tbody></table></div>
      <nav v-if="result && result.totalPages > 1" class="registration-pagination"><button class="secondary-button" type="button" :disabled="loading || page === 0" @click="changePage(page - 1)">上一页</button><span>第 {{ page + 1 }} / {{ result.totalPages }} 页</span><button class="secondary-button" type="button" :disabled="loading || !result.hasNext" @click="changePage(page + 1)">下一页</button></nav>
    </section>
  </WorkspaceShell>
</template>
