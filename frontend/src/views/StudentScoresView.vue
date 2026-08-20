<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listStudentScores } from '../api/studentScore';
import { studentNavigation as navigation } from '../router/studentNavigation';
import type { StudentScore } from '../types/studentScore';
import type { PageResponse } from '../types/schoolGovernance';
import { formatStudentScore, labelForStudentScoreStatus, labelForStudentScoreStorageType } from '../utils/studentScoreLabels';

const items = ref<StudentScore[]>([]);
const result = ref<PageResponse<StudentScore> | null>(null);
const loading = ref(true);
const error = ref('');
const page = ref(0);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listStudentScores(page.value, 20);
    items.value = result.value.items;
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有学生成绩读取权限。'
      : '成绩列表加载失败，请稍后重试。';
    items.value = [];
  } finally {
    loading.value = false;
  }
}

function changePage(next: number) {
  if (next >= 0 && next < (result.value?.totalPages ?? 0)) {
    page.value = next;
    void load();
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell
    role-label="学生"
    workspace-title="学生个人工作台"
    page-title="我的成绩"
    description="查看当前学生已经确认的成绩记录。"
    home-path="/student"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="student-score-panel">
      <header class="student-score-toolbar">
        <div>
          <p class="eyebrow">MY SCORES</p>
          <h2>成绩记录</h2>
          <span>共 {{ result?.totalElements ?? 0 }} 条可查看成绩</span>
        </div>
        <RouterLink class="secondary-button" to="/activities">浏览公开活动</RouterLink>
      </header>

      <div v-if="loading" class="project-state">正在加载成绩...</div>
      <div v-else-if="error" class="project-state project-state-error">
        <strong>{{ error }}</strong>
        <button class="secondary-button" type="button" @click="load">重新加载</button>
      </div>
      <div v-else-if="!items.length" class="project-state">
        <strong>暂无可查看成绩</strong>
        <p>已经确认的学生成绩会显示在这里。</p>
        <RouterLink class="primary-button" to="/activities">浏览公开活动</RouterLink>
      </div>
      <div v-else class="student-score-table-wrap">
        <table class="student-score-table">
          <thead>
            <tr>
              <th>活动</th>
              <th>挑战项目</th>
              <th>成绩</th>
              <th>尝试次数</th>
              <th>成绩时间</th>
              <th>状态</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.scoreAttemptId">
              <td><strong>{{ item.activityName }}</strong></td>
              <td>{{ item.challengeProjectName }}</td>
              <td>
                <strong>{{ formatStudentScore(item.scoreValue, item.scoreStorageType, item.scoreUnit) }}</strong>
                <small>{{ labelForStudentScoreStorageType(item.scoreStorageType) }}</small>
              </td>
              <td>第 {{ item.attemptNumber }} 次</td>
              <td>{{ item.scoreBusinessTime ? new Date(item.scoreBusinessTime).toLocaleString() : '未记录' }}</td>
              <td><span class="student-score-status">{{ labelForStudentScoreStatus(item.status) }}</span></td>
              <td><RouterLink class="registration-detail-link" :to="`/student/scores/${item.scoreAttemptId}`">查看详情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="result && result.totalPages > 1" class="project-pagination">
        <button class="secondary-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
        <span>{{ page + 1 }} / {{ result.totalPages }}</span>
        <button class="secondary-button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">下一页</button>
      </div>
    </section>
  </WorkspaceShell>
</template>
