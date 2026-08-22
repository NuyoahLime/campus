<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import PublicShell from '../components/PublicShell.vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listRankings } from '../api/ranking';
import { schoolAdminNavigation } from '../router/schoolAdminNavigation';
import { studentNavigation } from '../router/studentNavigation';
import type { RankingPage, RankingViewMode } from '../types/ranking';

const props = defineProps<{ mode: RankingViewMode }>();
const result = ref<RankingPage | null>(null);
const loading = ref(true);
const error = ref('');
const page = ref(0);

const isPublic = computed(() => props.mode === 'public');
const navigation = computed(() => props.mode === 'student' ? studentNavigation : schoolAdminNavigation);
const title = computed(() => isPublic.value ? '公开排行榜' : props.mode === 'student' ? '我的排行榜' : '本校排行榜');
const description = computed(() => isPublic.value
  ? '查看平台已经发布的排行榜快照。'
  : props.mode === 'student' ? '查看当前学生身份可访问的已发布排行榜。' : '查看当前学校范围内已发布的排行榜。');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listRankings(props.mode, page.value, 20);
  } catch (value) {
    result.value = null;
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有排行榜读取权限。'
      : '排行榜加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function changePage(next: number) {
  if (next >= 0 && next < (result.value?.totalPages ?? 0)) {
    page.value = next;
    void load();
  }
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '未记录';
}

onMounted(() => void load());
</script>

<template>
  <PublicShell v-if="isPublic" active="rankings">
    <main class="ranking-public-page">
      <section class="ranking-public-content">
        <div class="project-page-heading">
          <p class="eyebrow">PUBLISHED RANKINGS</p>
          <h1>{{ title }}</h1>
          <span>{{ description }}</span>
        </div>
        <div v-if="loading" class="project-state" role="status">正在加载排行榜...</div>
        <div v-else-if="error" class="project-state project-state-error" role="alert">
          <strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button>
        </div>
        <div v-else-if="!result?.items.length" class="project-state">
          <strong>暂无已发布排行榜</strong><p>已发布的排行榜快照会显示在这里。</p>
        </div>
        <div v-else class="ranking-card-grid">
          <RouterLink v-for="item in result.items" :key="item.id" class="ranking-card" :to="`/rankings/${item.id}`">
            <div class="ranking-card-meta"><span>{{ item.layer }}</span><span>V{{ item.versionNumber }}</span></div>
            <h2>{{ item.name }}</h2>
            <p>{{ item.projectName }}</p>
            <span>{{ item.schoolName || '平台范围' }} · 发布于 {{ formatDate(item.publishedAt) }}</span>
          </RouterLink>
        </div>
        <div v-if="result && result.totalPages > 1" class="project-pagination">
          <button class="secondary-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
          <span>{{ page + 1 }} / {{ result.totalPages }}</span>
          <button class="secondary-button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">下一页</button>
        </div>
      </section>
    </main>
  </PublicShell>

  <WorkspaceShell
    v-else
    :role-label="props.mode === 'student' ? '学生' : '学校管理员'"
    :workspace-title="props.mode === 'student' ? '学生个人工作台' : '学校管理工作台'"
    :page-title="title"
    :description="description"
    :home-path="props.mode === 'student' ? '/student' : '/school-admin'"
    :navigation="navigation"
  >
    <section class="student-score-panel ranking-workspace-panel">
      <header class="student-score-toolbar">
        <div><p class="eyebrow">PUBLISHED RANKINGS</p><h2>{{ title }}</h2><span>共 {{ result?.totalElements ?? 0 }} 个已发布快照</span></div>
      </header>
      <div v-if="loading" class="project-state" role="status">正在加载排行榜...</div>
      <div v-else-if="error" class="project-state project-state-error" role="alert">
        <strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button>
      </div>
      <div v-else-if="!result?.items.length" class="project-state"><strong>暂无已发布排行榜</strong><p>已发布的排行榜快照会显示在这里。</p></div>
      <div v-else class="student-score-table-wrap">
        <table class="student-score-table ranking-table">
          <thead><tr><th>排行榜</th><th>层级</th><th>挑战项目</th><th>学校</th><th>版本</th><th>发布时间</th><th></th></tr></thead>
          <tbody>
            <tr v-for="item in result.items" :key="item.id">
              <td><strong>{{ item.name }}</strong></td><td>{{ item.layer }}</td><td>{{ item.projectName }}</td>
              <td>{{ item.schoolName || '平台范围' }}</td><td>V{{ item.versionNumber }}</td><td>{{ formatDate(item.publishedAt) }}</td>
              <td><RouterLink class="registration-detail-link" :to="`/${props.mode}/rankings/${item.id}`">查看详情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="result && result.totalPages > 1" class="project-pagination">
        <button class="secondary-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
        <span>{{ page + 1 }} / {{ result.totalPages }}</span>
        <button class="secondary-button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">下一页</button>
      </div>
    </section>
  </WorkspaceShell>
</template>
