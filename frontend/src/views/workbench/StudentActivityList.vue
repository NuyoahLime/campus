<template>
  <div class="page-container">
    <h2>我的活动</h2>
    <div v-if="loading" class="loading-area"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="error" class="error-area"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <div v-else-if="items.length === 0" class="empty-area"><el-empty description="你还没有参与任何活动" /></div>
    <template v-else>
      <el-card v-for="a in items" :key="a.activityId" shadow="hover" class="item-card" @click="$router.push(`/student/activities/${a.activityId}`)">
        <div class="card-row">
          <div class="card-main">
            <h3>{{ a.title }}</h3>
            <p class="card-loc" v-if="a.location">{{ a.location }}</p>
            <p class="card-time" v-if="a.startTime || a.endTime">{{ fmt(a.startTime) }} - {{ fmt(a.endTime) }}</p>
          </div>
          <div class="card-right">
            <el-tag :type="statusTag(a.executionStatus)">{{ statusLabel(a.executionStatus) }}</el-tag>
            <p class="project-count">{{ a.assignedProjectCount }} 个项目</p>
          </div>
        </div>
      </el-card>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="load" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchMyActivities } from '@/api/student-activity';
import { ApiError } from '@/api/http';
import type { StudentActivityItem } from '@/types/student-activity';

const items = ref<StudentActivityItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const page = ref(1);
const size = 20;
const total = ref(0);

async function load() {
  loading.value = true; error.value = null;
  try { const r = await fetchMyActivities(page.value - 1, size); items.value = r.items; total.value = r.totalElements; }
  catch (e) { error.value = e instanceof ApiError ? e.message : '加载失败'; }
  finally { loading.value = false; }
}

function statusLabel(s: string) { const m: Record<string,string>={DRAFT:'草稿',PUBLISHED:'已发布',IN_PROGRESS:'进行中',ENDED:'已结束',CANCELLED:'已取消'}; return m[s]||s; }
function statusTag(s: string) { const m: Record<string,string>={PUBLISHED:'info',IN_PROGRESS:'success',ENDED:'',CANCELLED:'danger'}; return (m[s]||'info') as 'success'|'warning'|'info'|'danger'|''; }
function fmt(iso: string|null) { if(!iso) return ''; return new Date(iso).toLocaleDateString('zh-CN'); }

onMounted(() => load());
</script>

<style scoped>
.page-container h2 { margin-bottom: 20px; }
.item-card { cursor: pointer; margin-bottom: 12px; }
.card-row { display: flex; justify-content: space-between; align-items: center; }
.card-main h3 { margin: 0 0 6px 0; font-size: 16px; }
.card-loc { color: #606266; font-size: 14px; margin: 0 0 4px 0; }
.card-time { color: #909399; font-size: 13px; margin: 0; }
.card-right { text-align: right; }
.project-count { color: #909399; font-size: 13px; margin: 8px 0 0 0; }
.pager { display: flex; justify-content: center; margin-top: 24px; }
.loading-area, .empty-area { padding: 40px 0; }
</style>
