<template>
  <div class="ranking-detail">
    <el-page-header title="返回我的排名" @back="router.push('/student/rankings')" />

    <el-skeleton v-if="loading" :rows="8" animated />

    <el-result
      v-else-if="notFound"
      icon="info"
      title="排名暂不可用"
      sub-title="排名尚未发布或已撤回"
    >
      <template #extra>
        <el-button @click="router.push('/student/rankings')">返回列表</el-button>
      </template>
    </el-result>

    <div v-else-if="errorMessage" class="state-panel">
      <el-alert
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />
      <el-button type="primary" data-testid="ranking-detail-retry" @click="load">
        重试
      </el-button>
    </div>

    <template v-else-if="detail">
      <header class="detail-header">
        <div>
          <h1>项目排名</h1>
          <p>{{ detail.schoolName }} · {{ detail.activityTitle }} · {{ detail.projectName }}</p>
        </div>
        <el-tag type="success">V{{ detail.versionNumber }}</el-tag>
      </header>

      <el-card shadow="never">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="学校">{{ detail.schoolName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="计分单位">{{ detail.scoreUnit || '无' }}</el-descriptions-item>
          <el-descriptions-item label="比较方向">
            {{ directionLabel(detail.comparisonDirection) }}
          </el-descriptions-item>
          <el-descriptions-item label="有效成绩规则">
            {{ detail.effectiveScoreRule }}
          </el-descriptions-item>
          <el-descriptions-item label="并列规则">
            {{ tiePolicyLabel(detail.tiePolicy) }}
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">
            {{ formatTime(detail.publishedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="上榜人数">{{ detail.totalRanked }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="own-card" data-testid="own-ranking-card">
        <template #header>我的排名</template>
        <div v-if="detail.myRank" class="own-ranking">
          <strong>第{{ detail.myRank }}名</strong>
          <span>成绩 {{ detail.myScoreDisplayValue }}</span>
        </div>
        <el-empty v-else description="暂未进入当前排名" :image-size="64" />
      </el-card>

      <el-card shadow="never">
        <el-table
          :data="detail.entries"
          :row-class-name="entryRowClass"
          :row-key="entryRowKey"
          empty-text="当前排名暂无条目"
        >
          <el-table-column prop="rankPosition" label="名次" width="100" />
          <el-table-column prop="studentDisplayName" label="学生" min-width="180">
            <template #default="{ row }">
              <span>{{ row.studentDisplayName }}</span>
              <el-tag v-if="row.isCurrentStudent" class="me-tag" size="small">我</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="scoreDisplayValue" label="成绩" min-width="140" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fetchStudentCurrentRanking } from '@/api/student-ranking';
import { ApiError } from '@/api/http';
import type {
  StudentCurrentRankingDetail,
  StudentRankingEntry,
  StudentRankingTiePolicy,
} from '@/types/student-ranking';

const route = useRoute();
const router = useRouter();
const detail = ref<StudentCurrentRankingDetail | null>(null);
const loading = ref(false);
const notFound = ref(false);
const errorMessage = ref('');

async function load() {
  loading.value = true;
  notFound.value = false;
  errorMessage.value = '';
  try {
    detail.value = await fetchStudentCurrentRanking(
      String(route.params.activityProjectId),
    );
  } catch (error) {
    detail.value = null;
    if (error instanceof ApiError && error.status === 404) {
      notFound.value = true;
    } else {
      errorMessage.value = '排名详情加载失败，请稍后重试';
      ElMessage.error(errorMessage.value);
    }
  } finally {
    loading.value = false;
  }
}

function entryRowClass({ row }: { row: StudentRankingEntry }) {
  return row.isCurrentStudent ? 'current-student-row' : '';
}

function entryRowKey(row: StudentRankingEntry) {
  return `${row.rankPosition}-${row.studentDisplayName}-${row.scoreDisplayValue}`;
}

function tiePolicyLabel(value: StudentRankingTiePolicy) {
  return value === 'COMPETITION'
    ? '允许并列（竞赛排名）'
    : '不允许并列（业务时间优先）';
}

function directionLabel(value: string) {
  return {
    HIGHER_BETTER: '越高越好',
    LOWER_BETTER: '越低越好',
    GRADE_ORDER: '等级顺序',
  }[value] ?? value;
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN');
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.ranking-detail {
  display: grid;
  gap: 16px;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.detail-header h1 {
  margin: 0;
  font-size: 26px;
}
.detail-header p {
  margin: 8px 0 0;
  color: #606266;
}
.own-ranking {
  display: flex;
  align-items: baseline;
  gap: 18px;
}
.own-ranking strong {
  color: #409eff;
  font-size: 30px;
}
.own-ranking span {
  color: #606266;
}
.me-tag {
  margin-left: 8px;
}
.state-panel {
  display: grid;
  justify-items: start;
  gap: 16px;
  padding-top: 24px;
}
:deep(.current-student-row td.el-table__cell) {
  background: #ecf5ff !important;
  font-weight: 600;
}
@media (max-width: 768px) {
  .detail-header {
    align-items: flex-start;
  }
  :deep(.el-descriptions__body) {
    overflow-x: auto;
  }
}
</style>
