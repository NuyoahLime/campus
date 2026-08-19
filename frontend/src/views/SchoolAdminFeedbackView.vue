<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listSchoolAdminFeedback } from '../api/schoolAdminFeedback';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { StudentFeedback } from '../types/studentFeedback';
import type { PageResponse } from '../types/schoolGovernance';
import { labelForFeedbackStatus, labelForFeedbackType } from '../utils/studentFeedbackLabels';

const items = ref<StudentFeedback[]>([]);
const result = ref<PageResponse<StudentFeedback> | null>(null);
const loading = ref(true);
const error = ref('');
const page = ref(0);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listSchoolAdminFeedback(page.value, 20);
    items.value = result.value.items;
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有本校反馈读取权限。'
      : '反馈列表加载失败，请稍后重试。';
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
  <WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" page-title="意见反馈" description="查看和处理本校学生提交的意见反馈。" home-path="/school-admin" :navigation="navigation">
    <section class="student-score-panel">
      <header class="student-score-toolbar">
        <div>
          <p class="eyebrow">SCHOOL FEEDBACK</p>
          <h2>本校反馈</h2>
          <span>共 {{ result?.totalElements ?? 0 }} 条记录</span>
        </div>
      </header>
      <div v-if="loading" class="project-state">正在加载反馈...</div>
      <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <div v-else-if="!items.length" class="project-state"><strong>暂无本校反馈</strong><p>学生提交的反馈会显示在这里。</p></div>
      <div v-else class="student-score-table-wrap">
        <table class="student-score-table">
          <thead><tr><th>类型</th><th>状态</th><th>提交时间</th><th>更新时间</th><th></th></tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.feedbackId">
              <td>{{ labelForFeedbackType(item.feedbackType) }}</td>
              <td><span class="student-score-status">{{ labelForFeedbackStatus(item.status) }}</span></td>
              <td>{{ new Date(item.createdAt).toLocaleString() }}</td>
              <td>{{ new Date(item.updatedAt).toLocaleString() }}</td>
              <td><RouterLink class="registration-detail-link" :to="`/school-admin/feedback/${item.feedbackId}`">查看详情</RouterLink></td>
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
