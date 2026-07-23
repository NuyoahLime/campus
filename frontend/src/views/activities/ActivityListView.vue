<template>
  <PublicLayout>
    <div class="activity-list-page">
      <h1>校园活动</h1>

      <div v-if="loading" class="loading-area">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="error" class="error-area">
        <el-result icon="error" title="加载失败" :sub-title="error">
          <template #extra>
            <el-button type="primary" @click="loadData">重试</el-button>
          </template>
        </el-result>
      </div>
      <div v-else-if="items.length === 0" class="empty-area">
        <el-empty description="暂无公开活动" />
      </div>
      <template v-else>
        <div class="activity-list">
          <el-card
            v-for="a in items"
            :key="a.id"
            class="activity-card"
            shadow="hover"
            @click="$router.push(`/activities/${a.id}`)"
          >
            <div class="activity-card-content">
              <div class="activity-info">
                <h3>{{ a.title }}</h3>
                <p class="activity-location" v-if="a.location">
                  <el-icon><Location /></el-icon> {{ a.location }}
                </p>
                <p class="activity-time" v-if="a.startTime || a.endTime">
                  <el-icon><Clock /></el-icon>
                  {{ formatDate(a.startTime) }} - {{ formatDate(a.endTime) }}
                </p>
              </div>
              <el-tag :type="statusTagType(a.status)" size="large">
                {{ activityStatusLabel(a.status) }}
              </el-tag>
            </div>
          </el-card>
        </div>

        <div class="pagination-area">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="totalElements"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { fetchPublicActivities } from '@/api/public-activity';
import type { PublicActivityItem } from '@/types/activity';
import { Clock, Location } from '@element-plus/icons-vue';
import { ApiError } from '@/api/http';

const route = useRoute();
const router = useRouter();

const items = ref<PublicActivityItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const currentPage = ref(1);
const pageSize = 20;
const totalElements = ref(0);

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const result = await fetchPublicActivities(currentPage.value - 1, pageSize);
    items.value = result.items;
    totalElements.value = result.totalElements;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  currentPage.value = page;
  const query: Record<string, string> = {};
  if (page > 1) query.page = String(page);
  router.replace({ query });
  loadData();
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
  currentPage.value = Number(route.query.page) || 1;
  loadData();
});
</script>

<style scoped>
.activity-list-page h1 {
  font-size: 24px;
  margin-bottom: 20px;
}

.loading-area,
.empty-area {
  padding: 40px 0;
}

.error-area {
  padding: 20px 0;
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
  margin: 0 0 8px 0;
}

.activity-location {
  color: #606266;
  font-size: 14px;
  margin: 0 0 4px 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.activity-time {
  color: #909399;
  font-size: 13px;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination-area {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
