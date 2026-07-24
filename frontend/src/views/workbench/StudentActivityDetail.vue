<template>
  <div class="page-container">
    <el-page-header @back="$router.push('/student/activities')" title="返回活动列表" />
    <div v-if="loading"><el-skeleton :rows="5" animated /></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="活动不存在" /></div>
    <template v-else-if="detail">
      <h1>{{ detail.title }}</h1>
      <el-tag :type="statusTag(detail.executionStatus)">{{ statusLabel(detail.executionStatus) }}</el-tag>
      <el-divider />
      <section>
        <h3>活动介绍</h3>
        <p class="text">{{ detail.description || '暂无介绍' }}</p>
      </section>
      <section v-if="detail.startTime || detail.endTime || detail.location">
        <h3>时间与地点</h3>
        <p class="text">{{ fmt(detail.startTime) }} - {{ fmt(detail.endTime) }}</p>
        <p class="text" v-if="detail.location">{{ detail.location }}</p>
      </section>
      <el-divider />
      <section>
        <h3>我的参赛项目</h3>
        <div v-if="detail.projects.length===0" class="empty-hint">暂无项目</div>
        <el-card v-for="p in detail.projects" :key="p.activityProjectId" shadow="hover" class="proj-card" @click="$router.push(`/student/projects/${p.activityProjectId}`)">
          <div class="card-row">
            <div><strong>{{ p.projectName }}</strong> · {{ p.category }}</div>
            <div><el-tag v-if="p.latestAttemptStatus" size="small">{{ p.latestAttemptStatus }}</el-tag><span v-else class="no-score">暂无成绩</span></div>
          </div>
        </el-card>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { fetchMyActivityById } from '@/api/student-activity';
import { ApiError } from '@/api/http';
import type { StudentActivityDetail } from '@/types/student-activity';

const route = useRoute();
const detail = ref<StudentActivityDetail | null>(null);
const loading = ref(true);
const notFound = ref(false);

async function load() {
  loading.value = true; notFound.value = false;
  try { detail.value = await fetchMyActivityById(route.params.activityId as string); }
  catch (e) { if (e instanceof ApiError && e.status===404) notFound.value = true; }
  finally { loading.value = false; }
}

function statusLabel(s: string) { const m: Record<string,string>={DRAFT:'草稿',PUBLISHED:'已发布',IN_PROGRESS:'进行中',ENDED:'已结束',CANCELLED:'已取消'}; return m[s]||s; }
function statusTag(s: string) { const m: Record<string,string>={PUBLISHED:'info',IN_PROGRESS:'success',ENDED:'',CANCELLED:'danger'}; return (m[s]||'info') as 'success'|'warning'|'info'|'danger'|''; }
function fmt(iso: string|null) { if(!iso) return ''; return new Date(iso).toLocaleDateString('zh-CN'); }

onMounted(() => load());
</script>

<style scoped>
.page-container h1 { font-size: 24px; margin: 16px 0 12px; }
.text { color: #606266; line-height: 1.8; white-space: pre-wrap; }
section { margin-bottom: 20px; }
section h3 { font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.proj-card { cursor: pointer; margin-bottom: 8px; }
.card-row { display: flex; justify-content: space-between; align-items: center; }
.no-score { color: #c0c4cc; font-size: 13px; }
.empty-hint { color: #c0c4cc; padding: 20px 0; }
</style>
