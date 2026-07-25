<template>
  <div class="admindb"><h2>超级管理员后台</h2><p class="sub">{{ auth.user?.username }}</p>
    <el-row :gutter="16" class="stats">
      <el-col :xs="12" :sm="6" v-for="s in statCards" :key="s.key"><el-card shadow="hover" class="sc"><div class="num">{{ s.val }}</div><div class="lbl">{{ s.label }}</div></el-card></el-col></el-row>
    <div v-if="statsError" class="err"><el-alert :title="statsError" type="error" show-icon /><el-button size="small" @click="loadStats">重试</el-button></div>
    <div class="sec"><div class="sh"><h3>待审核申请</h3><el-button text type="primary" @click="$router.push('/admin/applications')">进入审核中心</el-button></div>
      <div v-if="recentError" class="err"><el-alert :title="recentError" type="error" show-icon /><el-button size="small" @click="loadRecent">重试</el-button></div>
      <div v-if="recentLoading"><el-skeleton :rows="3" animated /></div>
      <div v-else-if="recent.length===0" class="empty">暂无待审核申请</div>
      <el-card v-for="a in recent" :key="a.applicationId" shadow="hover" class="lc" @click="$router.push(`/admin/applications/${a.applicationId}`)"><div class="lr"><div><h4>{{ a.title }}</h4><p class="meta">{{ a.schoolName || a.schoolId }} · {{ a.applicantName || '未知' }}</p></div><el-tag type="warning" size="small">待审核</el-tag></div></el-card></div>
    <div class="sec"><h3>管理功能</h3><el-row :gutter="16"><el-col :xs="12" :sm="8" v-for="f in features" :key="f.path"><el-card shadow="hover" class="fc" @click="$router.push(f.path)"><h4>{{ f.label }}</h4><p class="st">暂未开放</p></el-card></el-col></el-row></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { fetchAdminApplications, fetchAdminStats } from '@/api/admin-application';
import type { AdminApplicationItem } from '@/types/admin-application';

const auth = useAuthStore();
const recent = ref<AdminApplicationItem[]>([]);
const statsLoading = ref(true);
const statsError = ref<string|null>(null);
const recentLoading = ref(true);
const recentError = ref<string|null>(null);
const statCards = ref([{key:'submitted',label:'待审核',val:0},{key:'createdToday',label:'今日新增',val:0},{key:'approved',label:'已批准',val:0},{key:'rejected',label:'已驳回',val:0}]);

async function loadStats() {
  statsLoading.value = true; statsError.value = null;
  try {
    const s = await fetchAdminStats();
    statCards.value[0].val = s.submitted; statCards.value[1].val = s.createdToday;
    statCards.value[2].val = s.approved; statCards.value[3].val = s.rejected;
  } catch { statsError.value = '加载统计失败，请重试'; }
  finally { statsLoading.value = false; }
}

async function loadRecent() {
  recentLoading.value = true; recentError.value = null;
  try { const r = await fetchAdminApplications({ status: 'SUBMITTED', size: 5 }); recent.value = r.items; }
  catch { recentError.value = '加载最近申请失败，请重试'; }
  finally { recentLoading.value = false; }
}

onMounted(() => { loadStats(); loadRecent(); });

const features = [{path:'/admin/projects',label:'项目管理'},{path:'/admin/applications',label:'活动申请审核'},{path:'/admin/public-review',label:'活动公开审核'},{path:'/admin/schools',label:'学校管理'},{path:'/admin/operations',label:'平台运营'}];
</script>

<style scoped>
.admindb h2 {margin-bottom:4px} .sub {color:#909399;margin-bottom:24px}
.stats {margin-bottom:28px} .sc {text-align:center} .num {font-size:32px;font-weight:700;color:#409eff} .lbl {color:#909399;margin-top:8px}
.sec {margin-bottom:24px} .sh {display:flex;justify-content:space-between;align-items:center;margin-bottom:12px} .sh h3 {margin:0;font-size:18px}
.lc {cursor:pointer;margin-bottom:8px} .lr {display:flex;justify-content:space-between;align-items:center} .lr h4 {margin:0 0 4px;font-size:15px}
.meta {color:#909399;font-size:13px;margin:0} .empty {color:#c0c4cc;padding:24px 0;text-align:center} .err {margin-bottom:16px}
.fc {cursor:pointer;text-align:center;padding:16px 0} .fc h4 {margin:0 0 4px} .st {color:#c0c4cc;font-size:13px;margin:0}
</style>
