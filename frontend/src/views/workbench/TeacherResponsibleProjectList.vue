<template>
  <div class="teacher-responsible-projects">
    <h2>我的负责项目</h2>

    <el-card class="filters">
      <el-form :inline="true">
        <el-form-item label="执行状态">
          <el-select
            v-model="filter.executionStatus"
            class="execution-status-filter"
            clearable
            placeholder="全部状态"
            style="width: 170px"
          >
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            class="keyword-filter"
            maxlength="100"
            clearable
            placeholder="学校、活动或项目"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button class="search-button" type="primary" @click="search">查询</el-button>
          <el-button class="reset-button" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-result
      v-else-if="error"
      icon="error"
      title="负责项目加载失败"
      :sub-title="error"
    >
      <template #extra>
        <el-button class="list-retry" type="primary" @click="loadProjects">重试</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="items.length === 0" description="暂无负责项目" />
    <template v-else>
      <el-table
        class="responsible-project-table"
        :data="items"
        row-key="activityProjectId"
        @row-click="openProject"
      >
        <el-table-column prop="schoolName" label="学校" min-width="150" />
        <el-table-column prop="activityTitle" label="活动" min-width="170" />
        <el-table-column prop="projectName" label="项目" min-width="150" />
        <el-table-column label="执行状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.executionStatus) }}</template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="210">
          <template #default="{ row }">
            {{ formatRange(row.startTime, row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="130">
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column prop="participantCount" label="参赛人数" width="90" />
        <el-table-column prop="enteredAttemptCount" label="本人录入" width="90" />
        <el-table-column prop="pendingReviewCount" label="待审核" width="80" />
        <el-table-column prop="rejectedCount" label="已驳回" width="80" />
        <el-table-column label="计分类型" width="100">
          <template #default="{ row }">{{ scoreTypeLabel(row.scoreStorageType) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="90">
          <template #default="{ row }">
            <el-button
              class="detail-button"
              link
              type="primary"
              @click.stop="openProject(row)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        @current-change="changePage"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ApiError } from '@/api/http';
import { fetchTeacherResponsibleProjects } from '@/api/teacher-responsible-project';
import type {
  ActivityExecutionStatus,
  TeacherResponsibleProjectFilter,
  TeacherResponsibleProjectItem,
} from '@/types/teacher-responsible-project';
import type { ScoreStorageType } from '@/types/school-admin-score-review';

const router = useRouter();
const pageSize = 20;
const currentPage = ref(1);
const total = ref(0);
const items = ref<TeacherResponsibleProjectItem[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const filter = reactive<TeacherResponsibleProjectFilter>({
  executionStatus: undefined,
  keyword: '',
});

const statusOptions: Array<{ value: ActivityExecutionStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'ENDED', label: '已结束' },
  { value: 'CANCELLED', label: '已取消' },
];

async function loadProjects(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const response = await fetchTeacherResponsibleProjects(
      filter,
      currentPage.value - 1,
      pageSize,
    );
    items.value = response.items;
    total.value = response.totalElements;
  } catch (caught) {
    items.value = [];
    total.value = 0;
    error.value = errorMessage(caught, '负责项目加载失败');
  } finally {
    loading.value = false;
  }
}

function search(): void {
  currentPage.value = 1;
  void loadProjects();
}

function reset(): void {
  filter.executionStatus = undefined;
  filter.keyword = '';
  search();
}

function changePage(): void {
  void loadProjects();
}

function openProject(project: TeacherResponsibleProjectItem): void {
  void router.push(`/teacher/responsible/${project.activityProjectId}`);
}

function statusLabel(status: ActivityExecutionStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function scoreTypeLabel(type: ScoreStorageType): string {
  return {
    INTEGER: '整数',
    DECIMAL: '小数',
    DURATION: '时长',
    GRADE: '等级',
  }[type];
}

function formatRange(start: string | null, end: string | null): string {
  if (!start && !end) return '-';
  return `${formatTime(start)} 至 ${formatTime(end)}`;
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function errorMessage(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback;
}

onMounted(() => {
  void loadProjects();
});
</script>

<style scoped>
.teacher-responsible-projects h2 {
  margin-top: 0;
}

.filters {
  margin-bottom: 16px;
}

.responsible-project-table :deep(.el-table__row) {
  cursor: pointer;
}

.pagination {
  justify-content: center;
  margin-top: 20px;
}
</style>
