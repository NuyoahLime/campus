<template>
  <PublicLayout>
    <div class="activity-detail-page">
      <div v-if="loading" class="loading-area">
        <el-skeleton :rows="6" animated />
      </div>

      <div v-else-if="notFound" class="error-area">
        <el-result icon="error" title="404" sub-title="活动不存在或未公开">
          <template #extra>
            <el-button type="primary" @click="$router.push('/activities')">返回活动列表</el-button>
          </template>
        </el-result>
      </div>

      <div v-else-if="error" class="error-area">
        <el-result icon="error" title="加载失败" :sub-title="error">
          <template #extra>
            <el-button type="primary" @click="loadDetail">重试</el-button>
          </template>
        </el-result>
      </div>

      <template v-else-if="activity">
        <el-page-header @back="$router.push('/activities')" title="返回活动列表" />

        <h1 class="activity-title">{{ activity.title }}</h1>
        <el-tag :type="statusTagType(activity.status)" size="large" class="status-tag">
          {{ activityStatusLabel(activity.status) }}
        </el-tag>

        <el-divider />

        <!-- Description -->
        <section class="detail-section">
          <h3>活动简介</h3>
          <p class="text-content">{{ activity.description || '暂无说明' }}</p>
        </section>

        <el-divider />

        <!-- Projects -->
        <section class="detail-section">
          <h3>已配置项目</h3>
          <div v-if="activity.projects.length === 0" class="text-content">
            暂无项目
          </div>
          <div v-else class="project-list">
            <el-card
              v-for="p in activity.projects"
              :key="p.projectId"
              class="project-link-card"
              shadow="hover"
              @click="$router.push(`/projects/${p.projectId}`)"
            >
              <span>查看项目详情</span>
              <el-icon><ArrowRight /></el-icon>
            </el-card>
          </div>
        </section>
      </template>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { fetchPublicActivityById } from '@/api/public-activity';
import type { PublicActivityDetail } from '@/types/activity';
import { ArrowRight } from '@element-plus/icons-vue';
import { ApiError } from '@/api/http';

const props = defineProps<{ activityId: string }>();

const activity = ref<PublicActivityDetail | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const notFound = ref(false);

async function loadDetail() {
  loading.value = true;
  error.value = null;
  notFound.value = false;
  try {
    activity.value = await fetchPublicActivityById(props.activityId);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      notFound.value = true;
    } else {
      error.value = e instanceof ApiError ? e.message : '加载失败';
    }
  } finally {
    loading.value = false;
  }
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

onMounted(() => {
  loadDetail();
});
</script>

<style scoped>
.activity-title {
  font-size: 28px;
  margin-top: 16px;
  margin-bottom: 12px;
}

.status-tag {
  margin-bottom: 8px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.text-content {
  color: #606266;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.project-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
}

.project-link-card {
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.loading-area {
  padding: 40px 0;
}

.error-area {
  padding: 40px 0;
}
</style>
