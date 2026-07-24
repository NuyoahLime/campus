<template>
  <div class="student-dashboard">
    <h2>欢迎，{{ auth.user?.username }}</h2>
    <p class="subtitle">学生工作台</p>

    <!-- Stats Cards -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="$router.push('/student/activities')">
          <div class="stat-number">{{ stats.activityCount }}</div>
          <div class="stat-label">参与活动</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="$router.push('/student/projects')">
          <div class="stat-number">{{ stats.projectCount }}</div>
          <div class="stat-label">参赛项目</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card stat-success" @click="$router.push('/student/scores')">
          <div class="stat-number">{{ stats.approvedCount }}</div>
          <div class="stat-label">已通过成绩</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card stat-warning">
          <div class="stat-number">{{ stats.pendingCount }}</div>
          <div class="stat-label">待审核/驳回</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Recent Activities -->
    <section class="section">
      <div class="section-header">
        <h3>最近活动</h3>
        <el-button text type="primary" @click="$router.push('/student/activities')">查看全部</el-button>
      </div>
      <div v-if="recentActivities.length === 0" class="empty-hint">暂无参与的活动</div>
      <el-card v-for="a in recentActivities" :key="a.activityId" shadow="hover" class="list-card"
               @click="$router.push(`/student/activities/${a.activityId}`)">
        <div class="list-card-content">
          <div>
            <h4>{{ a.title }}</h4>
            <p class="meta">{{ activityStatusLabel(a.executionStatus) }} · {{ a.assignedProjectCount }} 个项目</p>
          </div>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </el-card>
    </section>

    <!-- Recent Projects -->
    <section class="section">
      <div class="section-header">
        <h3>最近参赛项目</h3>
        <el-button text type="primary" @click="$router.push('/student/projects')">查看全部</el-button>
      </div>
      <div v-if="recentProjects.length === 0" class="empty-hint">暂无参赛项目</div>
      <el-card v-for="p in recentProjects" :key="p.activityProjectId" shadow="hover" class="list-card"
               @click="$router.push(`/student/projects/${p.activityProjectId}`)">
        <div class="list-card-content">
          <div>
            <h4>{{ p.projectName }}</h4>
            <p class="meta">{{ p.activityTitle }} · {{ p.category }}</p>
          </div>
          <el-tag v-if="p.latestAttemptStatus" size="small">{{ attemptStatusLabel(p.latestAttemptStatus) }}</el-tag>
        </div>
      </el-card>
    </section>

    <!-- Recent Scores -->
    <section class="section">
      <div class="section-header">
        <h3>最近成绩</h3>
        <el-button text type="primary" @click="$router.push('/student/scores')">查看全部</el-button>
      </div>
      <div v-if="recentScores.length === 0" class="empty-hint">暂无成绩记录</div>
      <el-card v-for="s in recentScores" :key="s.attemptId" shadow="hover" class="list-card"
               @click="$router.push(`/student/scores/${s.attemptId}`)">
        <div class="list-card-content">
          <div>
            <h4>{{ s.projectName }} <span class="attempt-num">#{{ s.attemptNumber }}</span></h4>
            <p class="meta">{{ s.activityTitle }} · {{ s.scoreDisplay }}</p>
          </div>
          <el-tag :type="scoreStatusType(s.status)" size="small">{{ scoreStatusLabel(s.status) }}</el-tag>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { ArrowRight } from '@element-plus/icons-vue';
import { fetchMyActivities } from '@/api/student-activity';
import { fetchMyProjects } from '@/api/student-project';
import { fetchMyScores } from '@/api/student-score';
import type { StudentActivityItem } from '@/types/student-activity';
import type { StudentProjectItem } from '@/types/student-project';
import type { StudentScoreItem } from '@/types/student-score';

const auth = useAuthStore();

const stats = reactive({ activityCount: 0, projectCount: 0, approvedCount: 0, pendingCount: 0 });
const recentActivities = ref<StudentActivityItem[]>([]);
const recentProjects = ref<StudentProjectItem[]>([]);
const recentScores = ref<StudentScoreItem[]>([]);

onMounted(async () => {
  try {
    const [acts, projs, scores] = await Promise.all([
      fetchMyActivities(0, 4),
      fetchMyProjects({}, 0, 4),
      fetchMyScores({ size: 4 }),
    ]);
    stats.activityCount = acts.totalElements;
    stats.projectCount = projs.totalElements;
    recentActivities.value = acts.items;
    recentProjects.value = projs.items;
    recentScores.value = scores.items;

    // Stats
    const allScores = await fetchMyScores({ size: 100 });
    stats.approvedCount = allScores.items.filter((s) => s.status === 'APPROVED').length;
    stats.pendingCount = allScores.items.filter((s) => s.status === 'PENDING_REVIEW' || s.status === 'REJECTED').length;
  } catch { /* silently handle */ }
});

function activityStatusLabel(s: string) { const m: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', IN_PROGRESS: '进行中', ENDED: '已结束', CANCELLED: '已取消' }; return m[s] || s; }
function attemptStatusLabel(s: string) { const m: Record<string, string> = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已驳回', INVALIDATED: '已失效' }; return m[s] || s; }
function scoreStatusLabel(s: string) { return attemptStatusLabel(s); }
function scoreStatusType(s: string) { const m: Record<string, string> = { APPROVED: 'success', PENDING_REVIEW: 'warning', REJECTED: 'danger', INVALIDATED: 'info' }; return (m[s] || 'info') as 'success' | 'warning' | 'danger' | 'info'; }
</script>

<style scoped>
.student-dashboard h2 { margin-bottom: 4px; }
.subtitle { color: #909399; margin-bottom: 32px; }
.stats-row { margin-bottom: 32px; }
.stat-card { cursor: pointer; text-align: center; }
.stat-number { font-size: 32px; font-weight: 700; color: #409eff; }
.stat-success .stat-number { color: #67c23a; }
.stat-warning .stat-number { color: #e6a23c; }
.stat-label { color: #909399; font-size: 14px; margin-top: 8px; }
.section { margin-bottom: 32px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.section-header h3 { font-size: 18px; font-weight: 600; margin: 0; }
.list-card { cursor: pointer; margin-bottom: 8px; }
.list-card-content { display: flex; justify-content: space-between; align-items: center; }
.list-card-content h4 { margin: 0 0 4px 0; font-size: 15px; }
.meta { color: #909399; font-size: 13px; margin: 0; }
.empty-hint { color: #c0c4cc; padding: 24px 0; text-align: center; }
.attempt-num { color: #909399; font-size: 13px; }
</style>
