<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listManagedActivities } from '../api/activityManagement';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { ActivityManagementListItem } from '../types/activityManagement';
import type { PageResponse } from '../types/schoolGovernance';
import { labelForActivityExecution, labelForActivityPublic } from '../utils/activityManagementLabels';

const items = ref<ActivityManagementListItem[]>([]);
const result = ref<PageResponse<ActivityManagementListItem> | null>(null);
const loading = ref(true);
const error = ref('');
const page = ref(0);
const status = ref('');
const query = ref('');

async function load() {
  loading.value = true; error.value = '';
  try { result.value = await listManagedActivities(page.value, 20, status.value, query.value); items.value = result.value.items; }
  catch (value) { error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有本校活动管理权限。' : '活动列表加载失败，请稍后重试。'; items.value = []; }
  finally { loading.value = false; }
}
function search() { page.value = 0; void load(); }
function changePage(next: number) { if (next >= 0 && next < (result.value?.totalPages ?? 0)) { page.value = next; void load(); } }
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" page-title="活动管理" description="管理本校活动的基本资料、规则快照和公开生命周期。" home-path="/school-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel activity-management-panel">
      <div class="project-admin-toolbar"><div><p class="eyebrow">ACTIVITY MANAGEMENT</p><h2>本校活动</h2><span>共 {{ result?.totalElements ?? 0 }} 条活动</span></div><RouterLink class="primary-button" to="/school-admin/activities/new">创建活动</RouterLink></div>
      <form class="project-filter-bar project-admin-filter" @submit.prevent="search">
        <label>关键词<input v-model="query" type="search" placeholder="活动名称或挑战项目"></label>
        <label>状态<select v-model="status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="IN_PROGRESS">进行中</option><option value="ENDED">已结束</option><option value="CANCELLED">已取消</option><option value="PUBLIC">已公开</option></select></label>
        <button class="secondary-button" type="submit" :disabled="loading">筛选</button>
      </form>
      <div v-if="loading" class="project-state">正在加载活动...</div>
      <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <div v-else-if="!items.length" class="project-state"><strong>暂无本校活动</strong><p>创建活动后，它会显示在这里。</p></div>
      <div v-else class="project-admin-table-wrap"><table class="project-admin-table activity-admin-table"><thead><tr><th>活动名称</th><th>挑战项目</th><th>执行状态</th><th>公开状态</th><th>活动时间</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.title }}</strong></td><td>{{ item.projectName || '未关联项目' }}<small v-if="item.ruleVersionNumber">V{{ item.ruleVersionNumber }}</small></td><td><span class="activity-status" :data-status="item.executionStatus">{{ labelForActivityExecution(item.executionStatus) }}</span></td><td>{{ labelForActivityPublic(item.publicStatus) }}</td><td>{{ item.startTime ? new Date(item.startTime).toLocaleString() : '未设置' }}</td><td>{{ new Date(item.updatedAt).toLocaleString() }}</td><td class="activity-row-actions"><RouterLink class="registration-detail-link" :to="`/school-admin/activities/${item.id}`">查看详情</RouterLink><RouterLink v-if="item.executionStatus === 'DRAFT'" class="registration-detail-link" :to="`/school-admin/activities/${item.id}/edit`">编辑</RouterLink></td></tr></tbody></table></div>
      <div v-if="result && result.totalPages > 1" class="project-pagination"><button class="secondary-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button><span>{{ page + 1 }} / {{ result.totalPages }}</span><button class="secondary-button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">下一页</button></div>
    </section>
  </WorkspaceShell>
</template>
