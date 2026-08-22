<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import PublicShell from '../components/PublicShell.vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import RankingEntriesTable from '../components/RankingEntriesTable.vue';
import { ApiError } from '../api/http';
import { getRanking } from '../api/ranking';
import { schoolAdminNavigation } from '../router/schoolAdminNavigation';
import { studentNavigation } from '../router/studentNavigation';
import type { RankingDetail, RankingViewMode } from '../types/ranking';

const props = defineProps<{ mode: RankingViewMode }>();
const route = useRoute();
const detail = ref<RankingDetail | null>(null);
const loading = ref(true);
const error = ref('');
const isPublic = computed(() => props.mode === 'public');
const homePath = computed(() => isPublic.value ? '/rankings' : props.mode === 'student' ? '/student/rankings' : '/school-admin/rankings');
const navigation = computed(() => props.mode === 'student' ? studentNavigation : schoolAdminNavigation);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getRanking(props.mode, String(route.params.id));
  } catch (value) {
    detail.value = null;
    error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有查看此排行榜的权限。' : '排行榜详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '未记录';
}

onMounted(() => void load());
</script>

<template>
  <PublicShell v-if="isPublic">
    <main class="ranking-public-page">
      <section class="ranking-public-content">
        <RouterLink class="registration-back-link" to="/rankings">返回排行榜</RouterLink>
        <div v-if="loading" class="project-state" role="status">正在加载排行榜...</div>
        <div v-else-if="error" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
        <article v-else-if="detail" class="ranking-detail">
          <header class="ranking-detail-heading"><div><p class="eyebrow">PUBLISHED SNAPSHOT · V{{ detail.versionNumber }}</p><h1>{{ detail.name }}</h1><span>{{ detail.projectName }} · {{ detail.schoolName || '平台范围' }}</span></div><time>{{ formatDate(detail.publishedAt) }}</time></header>
          <RankingEntriesTable :entries="detail.entries" />
        </article>
      </section>
    </main>
  </PublicShell>
  <WorkspaceShell
    v-else
    :role-label="props.mode === 'student' ? '学生' : '学校管理员'"
    :workspace-title="props.mode === 'student' ? '学生个人工作台' : '学校管理工作台'"
    page-title="排行榜详情"
    description="查看已经发布的排行榜快照内容。"
    :home-path="props.mode === 'student' ? '/student' : '/school-admin'"
    :navigation="navigation"
  >
    <RouterLink class="registration-back-link" :to="homePath">返回排行榜</RouterLink>
    <div v-if="loading" class="project-state" role="status">正在加载排行榜...</div>
    <div v-else-if="error" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
    <article v-else-if="detail" class="ranking-detail ranking-workspace-detail">
      <header class="ranking-detail-heading"><div><p class="eyebrow">PUBLISHED SNAPSHOT · V{{ detail.versionNumber }}</p><h1>{{ detail.name }}</h1><span>{{ detail.projectName }} · {{ detail.schoolName || '平台范围' }}</span></div><time>{{ formatDate(detail.publishedAt) }}</time></header>
      <RankingEntriesTable :entries="detail.entries" />
    </article>
  </WorkspaceShell>
</template>
