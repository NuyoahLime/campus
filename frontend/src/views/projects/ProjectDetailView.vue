<template>
  <PublicLayout>
    <div class="project-detail-page">
      <div v-if="loading" class="loading-area">
        <el-skeleton :rows="8" animated />
      </div>

      <div v-else-if="notFound" class="error-area">
        <el-result icon="error" title="404" sub-title="项目不存在或未公开">
          <template #extra>
            <el-button type="primary" @click="$router.push('/projects')">返回项目列表</el-button>
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

      <template v-else-if="project">
        <el-page-header @back="$router.push('/projects')" title="返回项目列表" />

        <h1 class="project-name">{{ project.name }}</h1>
        <el-tag class="category-tag">{{ project.category }}</el-tag>

        <el-divider />

        <!-- Description -->
        <section class="detail-section">
          <h3>项目简介</h3>
          <p class="text-content">{{ project.description || '暂无说明' }}</p>
        </section>

        <!-- Rules -->
        <section class="detail-section">
          <h3>比赛规则</h3>
          <p class="text-content">{{ project.rulesText || '暂无说明' }}</p>
        </section>

        <!-- Requirements -->
        <el-row :gutter="24">
          <el-col :span="12">
            <section class="detail-section">
              <h3>场地要求</h3>
              <p class="text-content">{{ project.venueRequirements || '暂无要求' }}</p>
            </section>
          </el-col>
          <el-col :span="12">
            <section class="detail-section">
              <h3>器材要求</h3>
              <p class="text-content">{{ project.equipmentRequirements || '暂无要求' }}</p>
            </section>
          </el-col>
        </el-row>

        <el-divider />

        <!-- Score Config -->
        <section class="detail-section">
          <h3>成绩配置</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="成绩存储类型">
              {{ scoreStorageTypeLabel(project.scoreStorageType) }}
            </el-descriptions-item>
            <el-descriptions-item label="成绩指标类型">
              {{ project.scoreIndicatorType }}
            </el-descriptions-item>
            <el-descriptions-item label="比较方向">
              {{ comparisonDirectionLabel(project.comparisonDirection) }}
            </el-descriptions-item>
            <el-descriptions-item label="有效成绩规则">
              {{ project.effectiveScoreRule || '暂无' }}
            </el-descriptions-item>
            <el-descriptions-item label="允许并列">
              {{ project.allowTie ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="单位">
              {{ project.scoreUnit || '暂无' }}
            </el-descriptions-item>
            <el-descriptions-item label="小数位">
              {{ project.decimalPlaces !== null ? project.decimalPlaces : 'N/A' }}
            </el-descriptions-item>
            <el-descriptions-item label="等级顺序">
              {{ project.gradeOrder || '暂无' }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
      </template>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { fetchPublicProjectById } from '@/api/public-project';
import type { PublicProjectDetail } from '@/types/project';
import { ApiError } from '@/api/http';

const props = defineProps<{ projectId: string }>();

const project = ref<PublicProjectDetail | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const notFound = ref(false);

async function loadDetail() {
  loading.value = true;
  error.value = null;
  notFound.value = false;
  try {
    project.value = await fetchPublicProjectById(props.projectId);
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

function scoreStorageTypeLabel(type: string): string {
  const map: Record<string, string> = {
    INTEGER: '整数成绩',
    DECIMAL: '小数成绩',
    DURATION: '时长成绩',
    GRADE: '等级成绩',
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
  loadDetail();
});
</script>

<style scoped>
.project-name {
  font-size: 28px;
  margin-top: 16px;
  margin-bottom: 12px;
}

.category-tag {
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

.loading-area {
  padding: 40px 0;
}

.error-area {
  padding: 40px 0;
}
</style>
