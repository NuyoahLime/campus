<template>
  <div class="achievement-detail">
    <el-page-header title="返回我的成就" @back="backToList" />

    <el-skeleton v-if="loading" :rows="8" animated />
    <el-result
      v-else-if="errorMessage"
      icon="error"
      :title="notFound ? '未找到成就记录' : '成就详情加载失败'"
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button v-if="!notFound" type="primary" @click="load">重试</el-button>
        <el-button @click="backToList">返回我的成就</el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <header class="detail-heading">
        <div>
          <h1>{{ detail.recordTitle }}</h1>
          <p>{{ detail.schoolName }}</p>
        </div>
        <el-tag :type="detail.status === 'ACTIVE' ? 'success' : 'danger'" size="large">
          {{ detail.status === 'ACTIVE' ? '有效' : '已撤销' }}
        </el-tag>
      </header>

      <el-alert
        v-if="detail.status === 'ACTIVE'"
        title="该成就记录当前有效"
        type="success"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        title="该成就记录已撤销"
        type="error"
        :closable="false"
        show-icon
      />

      <el-card shadow="never">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="成就标题">{{ detail.recordTitle }}</el-descriptions-item>
          <el-descriptions-item label="学校">{{ detail.schoolName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="排名版本">
            V{{ detail.rankingVersionNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="名次">第{{ detail.rankPosition }}名</el-descriptions-item>
          <el-descriptions-item label="成绩">{{ detail.scoreDisplayValue }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ detail.status === 'ACTIVE' ? '有效' : '已撤销' }}
          </el-descriptions-item>
          <el-descriptions-item label="签发时间">
            {{ formatTime(detail.issuedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="验证码">
            <span class="verification-code">{{ detail.verificationCode }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'REVOKED'" label="撤销时间">
            {{ formatTime(detail.revokedAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'REVOKED'" label="撤销原因">
            {{ detail.revocationReason || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <div class="actions">
        <el-button type="primary" @click="copyCode">复制验证码</el-button>
        <el-button @click="openVerification">打开公开验真页</el-button>
        <el-button @click="backToList">返回我的成就</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/http';
import { fetchMyAchievementRecord } from '@/api/student-achievement';
import type { StudentAchievementDetail } from '@/types/student-achievement';

const route = useRoute();
const router = useRouter();
const detail = ref<StudentAchievementDetail | null>(null);
const loading = ref(false);
const notFound = ref(false);
const errorMessage = ref('');

async function load() {
  if (loading.value) return;
  loading.value = true;
  detail.value = null;
  notFound.value = false;
  errorMessage.value = '';
  try {
    detail.value = await fetchMyAchievementRecord(String(route.params.recordId ?? ''));
  } catch (error) {
    notFound.value = error instanceof ApiError && error.status === 404;
    errorMessage.value = notFound.value
      ? '这条成就记录不存在或不属于当前账号'
      : '请稍后重试';
  } finally {
    loading.value = false;
  }
}

function backToList() {
  void router.push('/student/achievements');
}

async function copyCode() {
  if (!detail.value) return;
  try {
    await navigator.clipboard.writeText(detail.value.verificationCode);
    ElMessage.success('验证码已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}

function openVerification() {
  if (detail.value) {
    void router.push(`/achievements/verify/${detail.value.verificationCode}`);
  }
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.achievement-detail {
  display: grid;
  gap: 18px;
}
.detail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.detail-heading h1 {
  margin: 0;
  font-size: 25px;
}
.detail-heading p {
  margin: 7px 0 0;
  color: var(--el-text-color-secondary);
}
.verification-code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  overflow-wrap: anywhere;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
@media (max-width: 768px) {
  .detail-heading {
    align-items: flex-start;
  }
  :deep(.el-descriptions__body) {
    overflow-x: auto;
  }
}
</style>
