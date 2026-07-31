<template>
  <div class="achievement-page">
    <header class="page-header">
      <div>
        <h1>我的成就</h1>
        <p>查看已签发给你的历史成就记录及其当前状态。</p>
      </div>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter" @submit.prevent="search">
        <el-form-item label="状态">
          <el-select
            v-model="filter.status"
            clearable
            placeholder="全部状态"
            data-testid="achievement-status-filter"
          >
            <el-option label="有效" value="ACTIVE" />
            <el-option label="已撤销" value="REVOKED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            clearable
            maxlength="100"
            placeholder="学校、活动、项目或验证码"
            data-testid="achievement-keyword"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" data-testid="achievement-search" @click="search">
            查询
          </el-button>
          <el-button data-testid="achievement-reset" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div v-if="errorMessage" class="state-panel">
        <el-alert :title="errorMessage" type="error" :closable="false" show-icon />
        <el-button
          type="primary"
          data-testid="achievement-list-retry"
          @click="load"
        >
          重试
        </el-button>
      </div>

      <el-table
        v-else
        v-loading="loading"
        :data="items"
        :row-key="(row: StudentAchievementItem) => row.recordId"
        empty-text="暂无成就记录"
      >
        <el-table-column prop="recordTitle" label="成就标题" min-width="250" />
        <el-table-column prop="schoolName" label="学校" min-width="150" />
        <el-table-column prop="activityTitle" label="活动" min-width="170" />
        <el-table-column prop="projectName" label="项目" min-width="150" />
        <el-table-column label="排名版本" width="100">
          <template #default="{ row }">V{{ row.rankingVersionNumber }}</template>
        </el-table-column>
        <el-table-column label="名次" width="90">
          <template #default="{ row }">第{{ row.rankPosition }}名</template>
        </el-table-column>
        <el-table-column prop="scoreDisplayValue" label="成绩" width="110" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签发时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.issuedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.recordId)">
              查看详情
            </el-button>
            <el-button link @click="copyCode(row.verificationCode)">
              复制验证码
            </el-button>
            <el-button link @click="openVerification(row.verificationCode)">
              公开验真
            </el-button>
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
import { fetchMyAchievementRecords } from '@/api/student-achievement';
import type {
  AchievementStatus,
  StudentAchievementFilter,
  StudentAchievementItem,
} from '@/types/student-achievement';

const router = useRouter();
const filter = reactive<StudentAchievementFilter>({ status: '', keyword: '' });
const items = ref<StudentAchievementItem[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const page = ref(0);
const size = 20;
const total = ref(0);

async function load() {
  if (loading.value) return;
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await fetchMyAchievementRecords(filter, page.value, size);
    items.value = result.items;
    total.value = result.totalElements;
  } catch {
    errorMessage.value = '成就记录加载失败，请稍后重试';
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
  filter.status = '';
  filter.keyword = '';
  page.value = 0;
  void load();
}

function changePage(value: number) {
  page.value = value - 1;
  void load();
}

function viewDetail(recordId: string) {
  void router.push(`/student/achievements/${recordId}`);
}

function openVerification(code: string) {
  void router.push(`/achievements/verify/${code}`);
}

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code);
    ElMessage.success('验证码已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}

function statusLabel(status: AchievementStatus) {
  return status === 'ACTIVE' ? '有效' : '已撤销';
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.achievement-page {
  display: grid;
  gap: 16px;
}
.page-header h1 {
  margin: 0;
  font-size: 26px;
}
.page-header p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}
.filter-card :deep(.el-select) {
  width: 150px;
}
.filter-card :deep(.el-input) {
  width: 260px;
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
