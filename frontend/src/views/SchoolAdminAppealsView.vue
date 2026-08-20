<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listSchoolAdminAppeals } from '../api/schoolAdminAppeal';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { StudentAppeal } from '../types/studentAppeal';
import type { PageResponse } from '../types/schoolGovernance';
import { labelForAppealStatus, labelForAppealType } from '../utils/studentAppealLabels';

const items = ref<StudentAppeal[]>([]);
const result = ref<PageResponse<StudentAppeal> | null>(null);
const loading = ref(true);
const error = ref('');
const page = ref(0);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listSchoolAdminAppeals(page.value, 20);
    items.value = result.value.items;
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有本校申诉读取权限。'
      : '申诉列表加载失败，请稍后重试。';
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
  <WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" page-title="成绩申诉" description="查看和处理本校学生提交的成绩申诉。" home-path="/school-admin" :navigation="navigation">
    <section class="student-score-panel">
      <header class="student-score-toolbar">
        <div>
          <p class="eyebrow">SCHOOL APPEALS</p>
          <h2>本校申诉</h2>
          <span>共 {{ result?.totalElements ?? 0 }} 条记录</span>
        </div>
      </header>
      <div v-if="loading" class="project-state">正在加载申诉...</div>
      <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <div v-else-if="!items.length" class="project-state"><strong>暂无本校申诉</strong><p>学生提交的申诉会显示在这里。</p></div>
      <div v-else class="student-score-table-wrap">
        <table class="student-score-table">
          <thead><tr><th>活动</th><th>挑战项目</th><th>类型</th><th>状态</th><th>提交时间</th><th></th></tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.appealId">
              <td><strong>{{ item.activityName }}</strong></td>
              <td>{{ item.challengeProjectName }}</td>
              <td>{{ labelForAppealType(item.appealType) }}</td>
              <td><span class="student-score-status">{{ labelForAppealStatus(item.status) }}</span></td>
              <td>{{ new Date(item.createdAt).toLocaleString() }}</td>
              <td><RouterLink class="registration-detail-link" :to="`/school-admin/appeals/${item.appealId}`">查看详情</RouterLink></td>
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
