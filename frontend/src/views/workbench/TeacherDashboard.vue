<template>
  <div class="tdb">
    <h2>欢迎，{{ auth.user?.username }}</h2>
    <p class="subtitle">教师工作台</p>
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card" @click="$router.push('/teacher/applications')"><div class="num">{{ stats.total }}</div><div class="lbl">申请总数</div></el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card stat-warn"><div class="num">{{ stats.pending }}</div><div class="lbl">待审核</div></el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card stat-ok"><div class="num">{{ stats.approved }}</div><div class="lbl">已通过</div></el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card stat-err"><div class="num">{{ stats.rejected }}</div><div class="lbl">已驳回</div></el-card></el-col>
    </el-row>
    <div class="section">
      <div class="sh"><h3>最近申请</h3><el-button text type="primary" @click="$router.push('/teacher/applications')">查看全部</el-button></div>
      <div v-if="recent.length===0" class="empty-hint">暂无申请，<el-button text type="primary" @click="$router.push('/teacher/applications/new')">立即创建</el-button></div>
      <el-card v-for="a in recent" :key="a.applicationId" shadow="hover" class="list-card" @click="$router.push(`/teacher/applications/${a.applicationId}`)">
        <div class="lc"><div><h4>{{ a.title }}</h4><p class="meta">{{ a.schoolName || a.schoolId }} · v{{ a.applicationVersion }} · {{ fmt(a.updatedAt) }}</p></div><el-tag :type="appStatusTagType(a.status)" size="small">{{ appStatusLabel(a.status) }}</el-tag></div>
      </el-card>
    </div>
    <div class="section">
      <div class="sh"><h3>快捷入口</h3></div>
      <el-button type="primary" @click="$router.push('/teacher/applications/new')">新建活动申请</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { fetchMyApplications, fetchMyStats } from '@/api/teacher-application';
import { appStatusLabel, appStatusTagType } from '@/utils/application-status';
import type { TeacherActivityApplicationItem } from '@/types/teacher-application';

const auth = useAuthStore();
const stats = reactive({ total: 0, pending: 0, approved: 0, rejected: 0 });
const recent = ref<TeacherActivityApplicationItem[]>([]);

onMounted(async () => {
  try {
    const [r, s] = await Promise.all([fetchMyApplications({ size: 5 }), fetchMyStats()]);
    recent.value = r.items;
    stats.total = s.total;
    stats.pending = s.submitted;
    stats.approved = s.approved;
    stats.rejected = s.rejected;
  } catch { /* silent */ }
});

function fmt(iso: string | null) { return iso ? new Date(iso).toLocaleDateString('zh-CN') : ''; }
</script>

<style scoped>
.tdb h2 { margin-bottom: 4px; } .subtitle { color: #909399; margin-bottom: 24px; }
.stats-row { margin-bottom: 32px; } .stat-card { text-align: center; cursor: pointer; }
.num { font-size: 32px; font-weight: 700; color: #409eff; } .lbl { color: #909399; font-size: 14px; margin-top: 8px; }
.stat-warn .num { color: #e6a23c; } .stat-ok .num { color: #67c23a; } .stat-err .num { color: #f56c6c; }
.section { margin-bottom: 28px; } .sh { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; } .sh h3 { margin: 0; font-size: 18px; }
.list-card { cursor: pointer; margin-bottom: 8px; } .lc { display: flex; justify-content: space-between; align-items: center; } .lc h4 { margin: 0 0 4px 0; font-size: 15px; }
.meta { color: #909399; font-size: 13px; margin: 0; } .empty-hint { color: #c0c4cc; padding: 24px 0; text-align: center; }
</style>
