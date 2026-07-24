<template>
  <div class="page-container">
    <h2>我的成绩</h2>
    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="审核状态"><el-select v-model="filter.status" placeholder="全部" clearable><el-option label="待审核" value="PENDING_REVIEW" /><el-option label="已通过" value="APPROVED" /><el-option label="已驳回" value="REJECTED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <div v-if="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="items.length===0"><el-empty description="暂无成绩记录" /></div>
    <template v-else>
      <el-card v-for="s in items" :key="s.attemptId" shadow="hover" class="score-card" @click="$router.push(`/student/scores/${s.attemptId}`)">
        <div class="card-row">
          <div class="card-main">
            <h3>{{ s.projectName }} <span class="attempt-num">#{{ s.attemptNumber }}</span></h3>
            <p class="card-sub">{{ s.activityTitle }}</p>
          </div>
          <div class="card-right">
            <div class="score-val">{{ s.scoreDisplay }}</div>
            <el-tag :type="scoreTag(s.status)" size="small">{{ scoreLabel(s.status) }}</el-tag>
            <div class="time-text" v-if="s.submittedAt">{{ fmt(s.submittedAt) }}</div>
          </div>
        </div>
      </el-card>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="load" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { fetchMyScores } from '@/api/student-score';
import type { StudentScoreItem } from '@/types/student-score';

const items = ref<StudentScoreItem[]>([]);
const loading = ref(true);
const page = ref(1); const size = 20; const total = ref(0);
const filter = reactive({ status: undefined as string | undefined });

async function load() {
  loading.value = true;
  try { const r = await fetchMyScores({ status: filter.status, page: page.value - 1, size }); items.value = r.items; total.value = r.totalElements; }
  finally { loading.value = false; }
}
function search() { page.value = 1; load(); }
function reset() { filter.status = undefined; search(); }
function scoreLabel(s: string) { const m: Record<string,string>={DRAFT:'草稿',PENDING_REVIEW:'待审核',APPROVED:'已通过',REJECTED:'已驳回',INVALIDATED:'已失效'}; return m[s]||s; }
function scoreTag(s: string) { const m: Record<string,string>={APPROVED:'success',PENDING_REVIEW:'warning',REJECTED:'danger'}; return (m[s]||'info') as 'success'|'warning'|'danger'|'info'; }
function fmt(iso: string|null) { if(!iso) return ''; return new Date(iso).toLocaleDateString('zh-CN'); }

onMounted(() => load());
</script>

<style scoped>
.page-container h2 { margin-bottom: 20px; }
.filter-card { margin-bottom: 20px; }
.score-card { cursor: pointer; margin-bottom: 10px; }
.card-row { display: flex; justify-content: space-between; align-items: center; }
.card-main h3 { margin: 0 0 4px 0; font-size: 16px; }
.attempt-num { color: #909399; font-size: 14px; }
.card-sub { color: #909399; font-size: 13px; margin: 0; }
.card-right { text-align: right; }
.score-val { font-size: 20px; font-weight: 700; color: #409eff; margin-bottom: 4px; }
.time-text { color: #909399; font-size: 12px; margin-top: 4px; }
.pager { display: flex; justify-content: center; margin-top: 24px; }
</style>
