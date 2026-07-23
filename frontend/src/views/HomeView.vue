<template>
  <PublicLayout>
    <div class="home">
      <!-- Hero Section -->
      <section class="hero">
        <h1 class="hero-title">欢迎来到 Campus Guinness</h1>
        <p class="hero-desc">
          校园吉尼斯纪录平台 — 发现挑战项目，参与校园活动，创造你的纪录
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="$router.push('/projects')">
            浏览项目资源库
          </el-button>
          <el-button size="large" @click="$router.push('/activities')">
            查看校园活动
          </el-button>
        </div>
      </section>

      <!-- Latest Projects -->
      <section class="section">
        <div class="section-header">
          <h2>最新公开项目</h2>
          <el-button text type="primary" @click="$router.push('/projects')">
            查看全部 &rarr;
          </el-button>
        </div>
        <div v-if="projectsLoading" class="loading-area">
          <el-skeleton :rows="3" animated />
        </div>
        <div v-else-if="projectError" class="error-area">
          <p>加载失败：{{ projectError }}</p>
          <el-button @click="loadProjects">重试</el-button>
        </div>
        <div v-else-if="projects.length === 0" class="empty-area">
          <p>暂无公开项目</p>
        </div>
        <div v-else class="project-grid">
          <el-card
            v-for="p in projects"
            :key="p.projectId"
            class="project-card"
            shadow="hover"
            @click="$router.push(`/projects/${p.projectId}`)"
          >
            <template #header>
              <span class="card-title">{{ p.name }}</span>
            </template>
            <div class="card-body">
              <p class="card-category">{{ p.category }}</p>
              <p class="card-desc">{{ p.descriptionSummary || '暂无简介' }}</p>
              <div class="card-meta">
                <el-tag size="small">{{ scoreStorageTypeLabel(p.scoreStorageType) }}</el-tag>
                <el-tag v-if="p.scoreUnit" size="small" type="info">{{ p.scoreUnit }}</el-tag>
              </div>
            </div>
          </el-card>
        </div>
      </section>

      <!-- Latest Activities -->
      <section class="section">
        <div class="section-header">
          <h2>最新公开活动</h2>
          <el-button text type="primary" @click="$router.push('/activities')">
            查看全部 &rarr;
          </el-button>
        </div>
        <div v-if="activitiesLoading" class="loading-area">
          <el-skeleton :rows="3" animated />
        </div>
        <div v-else-if="activityError" class="error-area">
          <p>加载失败：{{ activityError }}</p>
          <el-button @click="loadActivities">重试</el-button>
        </div>
        <div v-else-if="activities.length === 0" class="empty-area">
          <p>暂无公开活动</p>
        </div>
        <div v-else class="activity-list">
          <el-card
            v-for="a in activities"
            :key="a.id"
            class="activity-card"
            shadow="hover"
            @click="$router.push(`/activities/${a.id}`)"
          >
            <div class="activity-card-content">
              <div class="activity-info">
                <h3>{{ a.title }}</h3>
                <p class="activity-location" v-if="a.location">{{ a.location }}</p>
                <p class="activity-time" v-if="a.startTime || a.endTime">
                  {{ formatDate(a.startTime) }} - {{ formatDate(a.endTime) }}
                </p>
              </div>
              <el-tag :type="statusTagType(a.status)">{{ activityStatusLabel(a.status) }}</el-tag>
            </div>
          </el-card>
        </div>
      </section>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { fetchPublicProjects } from '@/api/public-project';
import { fetchPublicActivities } from '@/api/public-activity';
import type { PublicProjectItem } from '@/types/project';
import type { PublicActivityItem } from '@/types/activity';
import { ApiError } from '@/api/http';

const projects = ref<PublicProjectItem[]>([]);
const projectsLoading = ref(true);
const projectError = ref<string | null>(null);

const activities = ref<PublicActivityItem[]>([]);
const activitiesLoading = ref(true);
const activityError = ref<string | null>(null);

async function loadProjects() {
  projectsLoading.value = true;
  projectError.value = null;
  try {
    const result = await fetchPublicProjects({}, 0, 6);
    projects.value = result.items;
  } catch (e) {
    projectError.value = e instanceof ApiError ? e.message : '加载失败';
  } finally {
    projectsLoading.value = false;
  }
}

async function loadActivities() {
  activitiesLoading.value = true;
  activityError.value = null;
  try {
    const result = await fetchPublicActivities(0, 6);
    activities.value = result.items;
  } catch (e) {
    activityError.value = e instanceof ApiError ? e.message : '加载失败';
  } finally {
    activitiesLoading.value = false;
  }
}

function scoreStorageTypeLabel(type: string): string {
  const map: Record<string, string> = {
    INTEGER: '整数',
    DECIMAL: '小数',
    DURATION: '时长',
    GRADE: '等级',
  };
  return map[type] || type;
}

function activityStatusLabel(status: string): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    IN_PROGRESS: '进行中',
    ENDED: '已结束',
    CANCELLED: '已取消',
  };
  return map[status] || status;
}

function statusTagType(status: string): 'success' | 'warning' | 'info' | 'danger' | '' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | ''> = {
    PUBLISHED: 'info',
    IN_PROGRESS: 'success',
    ENDED: '',
    CANCELLED: 'danger',
  };
  return map[status] || 'info';
}

function formatDate(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
}

onMounted(() => {
  loadProjects();
  loadActivities();
});
</script>

<style scoped>
.hero {
  text-align: center;
  padding: 60px 20px 40px;
}

.hero-title {
  font-size: 36px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 16px;
}

.hero-desc {
  font-size: 18px;
  color: #606266;
  margin-bottom: 32px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.section {
  margin-top: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h2 {
  font-size: 22px;
  font-weight: 600;
}

.loading-area,
.error-area,
.empty-area {
  padding: 40px;
  text-align: center;
  color: #909399;
}

.error-area {
  color: #f56c6c;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.project-card {
  cursor: pointer;
}

.card-title {
  font-weight: 600;
}

.card-category {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.card-desc {
  color: #606266;
  font-size: 14px;
  margin-bottom: 12px;
  line-height: 1.5;
}

.card-meta {
  display: flex;
  gap: 8px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-card {
  cursor: pointer;
}

.activity-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.activity-info h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.activity-location {
  color: #606266;
  font-size: 14px;
  margin: 0;
}

.activity-time {
  color: #909399;
  font-size: 13px;
  margin: 4px 0 0 0;
}
</style>
