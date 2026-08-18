<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getStudentScore } from '../api/studentScore';
import { studentNavigation as navigation } from '../router/studentNavigation';
import type { StudentScoreDetail } from '../types/studentScore';
import { formatStudentScore, labelForStudentScoreStatus, labelForStudentScoreStorageType } from '../utils/studentScoreLabels';

const route = useRoute();
const detail = ref<StudentScoreDetail | null>(null);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getStudentScore(String(route.params.id));
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 404
      ? '该成绩不存在或当前不可查看。'
      : '成绩详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="学生" workspace-title="学生个人工作台" page-title="成绩详情" description="查看当前学生成绩及其所属活动的历史规则版本。" home-path="/student" :navigation="navigation" :show-identity="false">
    <RouterLink class="project-back-link" to="/student/scores">返回我的成绩</RouterLink>
    <div v-if="loading" class="project-state">正在加载成绩详情...</div>
    <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/student/scores">返回成绩列表</RouterLink></div>
    <template v-else-if="detail">
      <header class="student-score-detail-heading"><p class="eyebrow">SCORE DETAIL</p><h2>{{ detail.challengeProjectName }}</h2><p>{{ detail.activityName }}</p></header>
      <section class="student-score-detail-grid">
        <div class="student-score-detail-section"><h3>成绩信息</h3><dl><div><dt>成绩</dt><dd>{{ formatStudentScore(detail.scoreValue, detail.scoreStorageType, detail.scoreUnit) }}</dd></div><div><dt>成绩类型</dt><dd>{{ labelForStudentScoreStorageType(detail.scoreStorageType) }}</dd></div><div><dt>尝试次数</dt><dd>第 {{ detail.attemptNumber }} 次</dd></div><div><dt>成绩时间</dt><dd>{{ detail.scoreBusinessTime ? new Date(detail.scoreBusinessTime).toLocaleString() : '未记录' }}</dd></div><div><dt>状态</dt><dd><span class="student-score-status">{{ labelForStudentScoreStatus(detail.status) }}</span></dd></div></dl></div>
        <div class="student-score-detail-section"><h3>历史规则版本</h3><dl><div><dt>规则版本</dt><dd>V{{ detail.ruleVersionNumber }}</dd></div><div><dt>规则版本 ID</dt><dd class="student-score-breakable">{{ detail.ruleVersionId }}</dd></div></dl><p class="student-score-rules">{{ detail.rulesText || '暂无规则说明。' }}</p></div>
      </section>
    </template>
  </WorkspaceShell>
</template>
