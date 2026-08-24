<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listAssignedActivities } from '../api/activityParticipants';
import { studentNavigation as navigation } from '../router/studentNavigation';
import type { ActivityListItem } from '../types/activity';
import type { PageResponse } from '../types/schoolGovernance';

const items = ref<ActivityListItem[]>([]);
const result = ref<PageResponse<ActivityListItem> | null>(null);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listAssignedActivities();
    items.value = result.value.items;
  } catch (value) {
    items.value = [];
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有有效的学生身份。'
      : '我的活动加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <WorkspaceShell
    role-label="学生"
    workspace-title="学生个人工作台"
    page-title="我的活动"
    description="查看学校管理员为你分配的活动。"
    home-path="/student"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="student-activity-panel">
      <header class="student-score-toolbar">
        <div><p class="eyebrow">MY ACTIVITIES</p><h2>已分配活动</h2><span>共 {{ result?.totalElements ?? 0 }} 项活动</span></div>
      </header>
      <div v-if="loading" class="project-state" role="status">正在加载我的活动...</div>
      <div v-else-if="error" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <div v-else-if="!items.length" class="project-state"><strong>暂时没有已分配活动</strong><p>学校管理员分配活动后，会显示在这里。</p></div>
      <div v-else class="activity-card-grid participant-activity-grid">
        <RouterLink v-for="activity in items" :key="activity.id" class="activity-card" :to="`/student/activities/${activity.id}`">
          <div class="activity-card-meta"><span>{{ activity.executionStatus }}</span><span>{{ activity.schoolName }}</span></div>
          <h2>{{ activity.title }}</h2>
          <p>{{ activity.startTime ? new Date(activity.startTime).toLocaleString() : '时间待定' }}</p>
          <p v-if="activity.location" class="activity-card-location">{{ activity.location }}</p>
          <span class="project-card-link">查看活动详情</span>
        </RouterLink>
      </div>
    </section>
  </WorkspaceShell>
</template>
