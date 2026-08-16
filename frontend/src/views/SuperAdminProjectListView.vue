<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listGovernanceProjects } from '../api/challengeProjects';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type { GovernanceProjectListItem, ProjectPage, ProjectStatus } from '../types/challengeProject';

const items = ref<GovernanceProjectListItem[]>([]);
const result = ref<ProjectPage<GovernanceProjectListItem> | null>(null);
const status = ref<ProjectStatus | ''>('');
const category = ref('');
const search = ref('');
const loading = ref(true);
const error = ref('');
const page = ref(0);

function statusLabel(value: ProjectStatus) {
  return value === 'DRAFT' ? '草稿' : value === 'PUBLISHED' ? '已上架' : '已下架';
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listGovernanceProjects(page.value, 20, status.value, category.value, search.value);
    items.value = result.value.items;
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有项目治理权限。' : '项目治理列表加载失败。';
    items.value = [];
  } finally {
    loading.value = false;
  }
}

function searchProjects() { page.value = 0; void load(); }
function changePage(value: number) { if (value >= 0 && value < (result.value?.totalPages ?? 0)) { page.value = value; void load(); } }
watch(status, () => { page.value = 0; void load(); });
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="超级管理员" workspace-title="平台管理工作台" page-title="挑战项目管理" description="维护平台公开挑战项目、规则版本和生命周期状态。" home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel">
      <div class="project-admin-toolbar"><div><p class="eyebrow">PROJECT GOVERNANCE</p><h2>挑战项目</h2><span>共 {{ result?.totalElements ?? 0 }} 个项目</span></div><RouterLink class="primary-button" to="/super-admin/projects/new">新建草稿</RouterLink></div>
      <form class="project-filter-bar project-admin-filter" @submit.prevent="searchProjects">
        <label>搜索<input v-model="search" type="search" placeholder="项目名称或说明"></label>
        <label>分类<input v-model="category" type="search" placeholder="分类"></label>
        <label>状态<select v-model="status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已上架</option><option value="ARCHIVED">已下架</option></select></label>
        <button class="secondary-button" type="submit" :disabled="loading">筛选</button>
      </form>
      <div v-if="loading" class="project-state">正在加载项目...</div>
      <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <div v-else-if="items.length === 0" class="project-state"><strong>暂无项目</strong><p>可以创建一个新的草稿项目。</p></div>
      <div v-else class="project-admin-table-wrap"><table class="project-admin-table"><thead><tr><th>项目名称</th><th>分类</th><th>状态</th><th>成绩类型</th><th>规则版本</th><th>更新时间</th><th></th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.name }}</strong></td><td>{{ item.category }}</td><td><span class="project-status" :data-status="item.status">{{ statusLabel(item.status) }}</span></td><td>{{ item.scoreStorageType }}</td><td>{{ item.currentRuleVersionNumber ? `V${item.currentRuleVersionNumber}` : '未发布' }}</td><td>{{ new Date(item.updatedAt).toLocaleString() }}</td><td><RouterLink class="registration-detail-link" :to="`/super-admin/projects/${item.id}`">查看详情</RouterLink></td></tr></tbody></table></div>
      <div v-if="result && result.totalPages > 1" class="project-pagination"><button class="secondary-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button><span>{{ page + 1 }} / {{ result.totalPages }}</span><button class="secondary-button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">下一页</button></div>
    </section>
  </WorkspaceShell>
</template>
