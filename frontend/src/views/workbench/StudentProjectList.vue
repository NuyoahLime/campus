<template>
  <div class="page-container">
    <h2>我的参赛项目</h2>
    <!-- Filters -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="关键词"><el-input v-model="filter.keyword" placeholder="活动或项目名" clearable /></el-form-item>
        <el-form-item label="活动状态"><el-select v-model="filter.executionStatus" placeholder="全部" clearable><el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="成绩状态"><el-select v-model="filter.scoreStatus" placeholder="全部" clearable><el-option label="已通过" value="APPROVED" /><el-option label="待审核" value="PENDING_REVIEW" /><el-option label="已驳回" value="REJECTED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <div v-if="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="items.length===0"><el-empty description="暂无参赛项目" /></div>
    <template v-else>
      <el-card v-for="p in items" :key="p.activityProjectId" shadow="hover" class="proj-card" @click="$router.push(`/student/projects/${p.activityProjectId}`)">
        <div class="card-row">
          <div class="card-main">
            <h3>{{ p.projectName }} <el-tag size="small">{{ p.category }}</el-tag></h3>
            <p class="card-sub">{{ p.activityTitle }} · {{ scoreTypeLabel(p.scoreStorageType) }}<span v-if="p.scoreUnit"> · {{ p.scoreUnit }}</span></p>
          </div>
          <div class="card-right">
            <div class="score-display" v-if="p.latestScoreDisplay">{{ p.latestScoreDisplay }}</div>
            <div class="attempt-info">{{ p.attemptCount }} 次尝试</div>
            <el-tag v-if="p.latestAttemptStatus" :type="scoreTag(p.latestAttemptStatus)" size="small">{{ scoreLabel(p.latestAttemptStatus) }}</el-tag>
          </div>
        </div>
      </el-card>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="load" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { fetchMyProjects } from '@/api/student-project';
import type { StudentProjectItem, StudentProjectFilter } from '@/types/student-project';

const items = ref<StudentProjectItem[]>([]);
const loading = ref(true);
const page = ref(1); const size = 20; const total = ref(0);
const filter = reactive<StudentProjectFilter>({ executionStatus: undefined, scoreStatus: undefined, keyword: '' });
const statusOptions = [{ value: 'DRAFT', label: '草稿' }, { value: 'PUBLISHED', label: '已发布' }, { value: 'IN_PROGRESS', label: '进行中' }, { value: 'ENDED', label: '已结束' }];

async function load() {
  loading.value = true;
  try { const r = await fetchMyProjects({ ...filter }, page.value - 1, size); items.value = r.items; total.value = r.totalElements; }
  finally { loading.value = false; }
}
function search() { page.value = 1; load(); }
function reset() { filter.executionStatus = undefined; filter.scoreStatus = undefined; filter.keyword = ''; search(); }
function scoreTypeLabel(t: string) { const m: Record<string,string>={INTEGER:'整数',DECIMAL:'小数',DURATION:'时长',GRADE:'等级'}; return m[t]||t; }
function scoreLabel(s: string) { const m: Record<string,string>={DRAFT:'草稿',PENDING_REVIEW:'待审核',APPROVED:'已通过',REJECTED:'已驳回',INVALIDATED:'已失效'}; return m[s]||s; }
function scoreTag(s: string) { const m: Record<string,string>={APPROVED:'success',PENDING_REVIEW:'warning',REJECTED:'danger'}; return (m[s]||'info') as 'success'|'warning'|'danger'|'info'; }

onMounted(() => load());
</script>

<style scoped>
.page-container h2 { margin-bottom: 20px; }
.filter-card { margin-bottom: 20px; }
.proj-card { cursor: pointer; margin-bottom: 10px; }
.card-row { display: flex; justify-content: space-between; align-items: center; }
.card-main h3 { margin: 0 0 6px 0; font-size: 16px; }
.card-sub { color: #909399; font-size: 13px; margin: 0; }
.card-right { text-align: right; }
.score-display { font-size: 20px; font-weight: 700; color: #409eff; }
.attempt-info { color: #909399; font-size: 13px; margin: 4px 0; }
.pager { display: flex; justify-content: center; margin-top: 24px; }
</style>
