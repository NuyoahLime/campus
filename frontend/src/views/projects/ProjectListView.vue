<template>
  <PublicLayout>
    <div class="project-list-page">
      <h1>项目资源库</h1>

      <!-- Filters -->
      <el-card class="filter-card">
        <el-form :model="filter" label-width="80px" @submit.prevent="search">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="关键字">
                <el-input v-model="filter.keyword" placeholder="搜索项目名称" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="分类">
                <el-input v-model="filter.category" placeholder="输入分类" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="成绩类型">
                <el-select v-model="filter.scoreStorageType" placeholder="全部" clearable>
                  <el-option label="整数" value="INTEGER" />
                  <el-option label="小数" value="DECIMAL" />
                  <el-option label="时长" value="DURATION" />
                  <el-option label="等级" value="GRADE" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="场地要求">
                <el-input v-model="filter.venueKeyword" placeholder="搜索场地" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="器材要求">
                <el-input v-model="filter.equipmentKeyword" placeholder="搜索器材" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label-width="0">
                <el-button type="primary" @click="search">查询</el-button>
                <el-button @click="reset">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- Results -->
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
        <el-empty description="没有找到匹配的项目" />
      </div>
      <template v-else>
        <div class="project-grid">
          <el-card
            v-for="p in items"
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
                <el-tag size="small" type="warning">{{ comparisonDirectionLabel(p.comparisonDirection) }}</el-tag>
              </div>
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
import { ref, reactive, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { fetchPublicProjects } from '@/api/public-project';
import type { PublicProjectItem, ProjectListFilter } from '@/types/project';
import { ApiError } from '@/api/http';

const route = useRoute();
const router = useRouter();

const filter = reactive<ProjectListFilter>({
  keyword: '',
  category: '',
  scoreStorageType: undefined,
  venueKeyword: '',
  equipmentKeyword: '',
});

const items = ref<PublicProjectItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const currentPage = ref(1);
const pageSize = 20;
const totalElements = ref(0);

function buildFilterFromQuery(): ProjectListFilter {
  return {
    keyword: (route.query.keyword as string) || '',
    category: (route.query.category as string) || '',
    scoreStorageType: (route.query.scoreStorageType as string) || undefined,
    venueKeyword: (route.query.venueKeyword as string) || '',
    equipmentKeyword: (route.query.equipmentKeyword as string) || '',
  };
}

function applyQueryToFilter() {
  const q = buildFilterFromQuery();
  filter.keyword = q.keyword || '';
  filter.category = q.category || '';
  filter.scoreStorageType = q.scoreStorageType;
  filter.venueKeyword = q.venueKeyword || '';
  filter.equipmentKeyword = q.equipmentKeyword || '';
  currentPage.value = Number(route.query.page) || 1;
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const result = await fetchPublicProjects(
      {
        keyword: filter.keyword || undefined,
        category: filter.category || undefined,
        scoreStorageType: filter.scoreStorageType,
        venueKeyword: filter.venueKeyword || undefined,
        equipmentKeyword: filter.equipmentKeyword || undefined,
      },
      currentPage.value - 1,
      pageSize,
    );
    items.value = result.items;
    totalElements.value = result.totalElements;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

function search() {
  currentPage.value = 1;
  updateQuery();
}

function reset() {
  filter.keyword = '';
  filter.category = '';
  filter.scoreStorageType = undefined;
  filter.venueKeyword = '';
  filter.equipmentKeyword = '';
  currentPage.value = 1;
  updateQuery();
}

function handlePageChange(page: number) {
  currentPage.value = page;
  updateQuery();
}

function updateQuery() {
  const query: Record<string, string> = {};
  if (filter.keyword) query.keyword = filter.keyword;
  if (filter.category) query.category = filter.category;
  if (filter.scoreStorageType) query.scoreStorageType = filter.scoreStorageType;
  if (filter.venueKeyword) query.venueKeyword = filter.venueKeyword;
  if (filter.equipmentKeyword) query.equipmentKeyword = filter.equipmentKeyword;
  if (currentPage.value > 1) query.page = String(currentPage.value);
  router.replace({ query });
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

function comparisonDirectionLabel(dir: string): string {
  const map: Record<string, string> = {
    HIGHER_BETTER: '越高越好',
    LOWER_BETTER: '越低越好',
    EXACT: '精确匹配',
    MANUAL: '人工评定',
  };
  return map[dir] || dir;
}

onMounted(() => {
  applyQueryToFilter();
  loadData();
});

watch(
  () => route.query,
  () => {
    applyQueryToFilter();
    loadData();
  },
);
</script>

<style scoped>
.project-list-page h1 {
  font-size: 24px;
  margin-bottom: 20px;
}

.filter-card {
  margin-bottom: 24px;
}

.loading-area,
.empty-area {
  padding: 40px 0;
}

.error-area {
  padding: 20px 0;
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

.pagination-area {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
