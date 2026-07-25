<template>
  <div class="app-list">
    <div class="header"><h2>我的活动申请</h2><el-button type="primary" @click="$router.push('/teacher/applications/new')">新建申请</el-button></div>
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="状态"><el-select v-model="f.status" placeholder="全部" clearable @change="search"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
        <el-form-item label="学校"><el-select v-model="f.schoolId" placeholder="全部学校" clearable @change="search" style="width:200px"><el-option v-for="s in teacherSchools" :key="s.schoolId" :label="s.schoolName || '学校名称暂不可用'" :value="s.schoolId" /></el-select></el-form-item>
        <el-form-item label="关键词"><el-input v-model="f.keyword" placeholder="申请标题" clearable @change="search" /></el-form-item>
      </el-form>
    </el-card>
    <div v-if="loading"><el-skeleton :rows="5" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <div v-else-if="items.length===0"><el-empty description="暂无申请" /></div>
    <template v-else>
      <el-card v-for="a in items" :key="a.applicationId" shadow="hover" class="app-card" @click="$router.push(`/teacher/applications/${a.applicationId}`)">
        <div class="app-row">
          <div><h3>{{ a.title }}</h3><p class="meta">{{ a.schoolName || a.schoolId }} · v{{ a.applicationVersion }}</p></div>
          <div class="right"><el-tag :type="appStatusTagType(a.status)">{{ appStatusLabel(a.status) }}</el-tag><p class="time">{{ fmt(a.updatedAt) }}</p></div>
        </div>
      </el-card>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="total" :page-size="20" v-model:current-page="page" @current-change="load" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchMyApplications, fetchTeacherSchools, type TeacherSchoolItem } from '@/api/teacher-application';
import { ApiError } from '@/api/http';
import { appStatusLabel, appStatusTagType } from '@/utils/application-status';
import type { TeacherActivityApplicationItem } from '@/types/teacher-application';

const route = useRoute(); const router = useRouter();
const items = ref<TeacherActivityApplicationItem[]>([]);
const loading = ref(true); const error = ref<string|null>(null);
const page = ref(1); const total = ref(0);
const f = reactive({ status: '', schoolId: '', keyword: '' });
const statusOpts = [{ v: 'DRAFT', l: '草稿' }, { v: 'SUBMITTED', l: '待审核' }, { v: 'APPROVED', l: '已通过' }, { v: 'REJECTED', l: '已驳回' }, { v: 'WITHDRAWN', l: '已撤回' }];
const teacherSchools = ref<TeacherSchoolItem[]>([]);
const schoolsFailed = ref(false);

function parsePage(v: unknown): number { const n = Number(v); return Number.isFinite(n) && n >= 1 ? n : 1; }
function parseStatus(v: unknown): string { const VALID = ['DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN']; const s = String(v || ''); return VALID.includes(s) ? s : ''; }

onMounted(async () => {
  try { teacherSchools.value = await fetchTeacherSchools(); } catch { schoolsFailed.value = true; }
  f.status = parseStatus(route.query.status);
  page.value = parsePage(route.query.page);
  f.keyword = String(route.query.keyword || '').trim();
  const qs = String(route.query.schoolId || '');
  if (qs && teacherSchools.value.some(s => s.schoolId === qs)) f.schoolId = qs;
  else if (qs) { router.replace({ query: { ...route.query, schoolId: undefined } }); }
  load();
});

function buildQuery() {
  const q: Record<string, string> = {};
  if (f.status) q.status = f.status; if (f.schoolId) q.schoolId = f.schoolId; if (f.keyword) q.keyword = f.keyword;
  if (page.value > 1) q.page = String(page.value);
  router.replace({ query: q });
}

function search() { page.value = 1; buildQuery(); load(); }

async function load() {
  loading.value = true; error.value = null;
  try { const r = await fetchMyApplications({ status: f.status || undefined, schoolId: f.schoolId || undefined, keyword: f.keyword || undefined, page: page.value - 1, size: 20 }); items.value = r.items; total.value = r.totalElements; }
  catch (e) { error.value = e instanceof ApiError ? e.message : '加载失败'; }
  finally { loading.value = false; }
}

function fmt(iso: string|null) { return iso ? new Date(iso).toLocaleDateString('zh-CN') : ''; }
</script>

<style scoped>
.app-list .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.app-list .header h2 { margin: 0; }
.filter-card { margin-bottom: 16px; }
.app-card { cursor: pointer; margin-bottom: 10px; }
.app-row { display: flex; justify-content: space-between; align-items: center; }
.app-row h3 { margin: 0 0 6px 0; font-size: 16px; }
.meta { color: #909399; font-size: 13px; margin: 0; }
.right { text-align: right; }
.time { color: #909399; font-size: 12px; margin-top: 6px; }
.pager { display: flex; justify-content: center; margin-top: 24px; }
</style>
