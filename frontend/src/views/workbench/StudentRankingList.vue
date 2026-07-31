<template>
  <div class="ranking-page">
    <header class="page-header">
      <div>
        <h1>我的排名</h1>
        <p>查看本人已参加项目的当前发布排名。</p>
      </div>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter" @submit.prevent="search">
        <el-form-item label="活动状态">
          <el-select
            v-model="filter.executionStatus"
            clearable
            placeholder="全部状态"
            data-testid="execution-status-filter"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已结束" value="ENDED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="排名状态">
          <el-select
            v-model="filter.rankingAvailability"
            clearable
            placeholder="全部状态"
            data-testid="ranking-availability-filter"
          >
            <el-option label="当前排名" value="CURRENT" />
            <el-option label="尚未发布" value="NOT_PUBLISHED" />
            <el-option label="排名已撤回" value="WITHDRAWN" />
            <el-option label="不参与排名" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            clearable
            maxlength="100"
            placeholder="学校、活动或项目"
            data-testid="ranking-keyword"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" data-testid="ranking-search" @click="search">
            查询
          </el-button>
          <el-button data-testid="ranking-reset" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div v-if="errorMessage" class="state-panel">
        <el-alert
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
        />
        <el-button type="primary" data-testid="ranking-list-retry" @click="load">
          重试
        </el-button>
      </div>

      <el-table
        v-else
        v-loading="loading"
        :data="items"
        :row-key="(row: StudentRankingProjectItem) => row.activityProjectId"
        empty-text="暂无参赛项目排名"
      >
        <el-table-column prop="schoolName" label="学校" min-width="150" />
        <el-table-column prop="activityTitle" label="活动" min-width="170" />
        <el-table-column prop="projectName" label="项目" min-width="150" />
        <el-table-column label="活动状态" width="110">
          <template #default="{ row }">{{ executionLabel(row.executionStatus) }}</template>
        </el-table-column>
        <el-table-column label="比较方向" width="120">
          <template #default="{ row }">{{ directionLabel(row.comparisonDirection) }}</template>
        </el-table-column>
        <el-table-column label="排名状态" width="130">
          <template #default="{ row }">
            <el-tag :type="availabilityTag(row.rankingAvailability)">
              {{ availabilityLabel(row.rankingAvailability) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            {{ row.currentVersionNumber ? `V${row.currentVersionNumber}` : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="上榜人数" width="100">
          <template #default="{ row }">{{ row.totalRanked ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="我的名次" width="110">
          <template #default="{ row }">
            <strong v-if="row.myRank">第{{ row.myRank }}名</strong>
            <span v-else-if="row.rankingAvailability === 'CURRENT'">暂未上榜</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="我的成绩" width="110">
          <template #default="{ row }">{{ row.myScoreDisplayValue ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.rankingAvailability === 'CURRENT'"
              link
              type="primary"
              :data-testid="`view-ranking-${row.activityProjectId}`"
              @click="viewRanking(row.activityProjectId)"
            >
              查看排名
            </el-button>
            <span v-else class="unavailable-text">
              {{ unavailableAction(row.rankingAvailability) }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="!errorMessage && total > 0"
        class="pagination"
        background
        layout="total, prev, pager, next"
        :current-page="page + 1"
        :page-size="size"
        :total="total"
        @current-change="changePage"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fetchStudentRankingProjects } from '@/api/student-ranking';
import type {
  StudentRankingAvailability,
  StudentRankingFilter,
  StudentRankingProjectItem,
} from '@/types/student-ranking';

const router = useRouter();
const filter = reactive<StudentRankingFilter>({
  executionStatus: '',
  rankingAvailability: '',
  keyword: '',
});
const items = ref<StudentRankingProjectItem[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const page = ref(0);
const size = 20;
const total = ref(0);

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await fetchStudentRankingProjects(filter, page.value, size);
    items.value = result.items;
    total.value = result.totalElements;
  } catch {
    errorMessage.value = '排名项目加载失败，请稍后重试';
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

function search() {
  filter.keyword = filter.keyword?.trim() ?? '';
  page.value = 0;
  void load();
}

function reset() {
  filter.executionStatus = '';
  filter.rankingAvailability = '';
  filter.keyword = '';
  page.value = 0;
  void load();
}

function changePage(value: number) {
  page.value = value - 1;
  void load();
}

function viewRanking(activityProjectId: string) {
  void router.push(`/student/rankings/${activityProjectId}`);
}

function availabilityLabel(value: StudentRankingAvailability) {
  return {
    CURRENT: '当前排名',
    NOT_PUBLISHED: '尚未发布',
    WITHDRAWN: '排名已撤回',
    DISABLED: '该项目不参与排名',
  }[value];
}

function unavailableAction(value: StudentRankingAvailability) {
  return {
    CURRENT: '',
    NOT_PUBLISHED: '尚未发布',
    WITHDRAWN: '排名已撤回',
    DISABLED: '不参与排名',
  }[value];
}

function availabilityTag(value: StudentRankingAvailability) {
  return {
    CURRENT: 'success',
    NOT_PUBLISHED: 'info',
    WITHDRAWN: 'warning',
    DISABLED: 'info',
  }[value] as 'success' | 'warning' | 'info';
}

function executionLabel(value: string) {
  return {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    IN_PROGRESS: '进行中',
    ENDED: '已结束',
    CANCELLED: '已取消',
  }[value] ?? value;
}

function directionLabel(value: string) {
  return {
    HIGHER_BETTER: '越高越好',
    LOWER_BETTER: '越低越好',
    GRADE_ORDER: '等级顺序',
    NO_RANKING: '不排名',
  }[value] ?? value;
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—';
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.ranking-page {
  display: grid;
  gap: 16px;
}
.page-header h1 {
  margin: 0;
  font-size: 26px;
}
.page-header p {
  margin: 8px 0 0;
  color: #909399;
}
.filter-card :deep(.el-select) {
  width: 150px;
}
.filter-card :deep(.el-input) {
  width: 230px;
}
.state-panel {
  display: grid;
  justify-items: start;
  gap: 16px;
  padding: 20px 0;
}
.pagination {
  justify-content: flex-end;
  margin-top: 20px;
}
.unavailable-text {
  color: #909399;
  font-size: 13px;
}
@media (max-width: 768px) {
  .filter-card :deep(.el-form-item),
  .filter-card :deep(.el-select),
  .filter-card :deep(.el-input) {
    width: 100%;
  }
  .pagination {
    justify-content: center;
  }
}
</style>
