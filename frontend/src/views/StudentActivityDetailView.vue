<script setup lang="ts">
import { ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getAssignedActivity } from '../api/activityParticipants';
import { studentNavigation as navigation } from '../router/studentNavigation';
import type { ActivityDetail } from '../types/activity';

const route = useRoute();
const activity = ref<ActivityDetail | null>(null);
const loading = ref(true);
const error = ref('');

async function load(id: string) {
  loading.value = true;
  error.value = '';
  try {
    activity.value = await getAssignedActivity(id);
  } catch (value) {
    activity.value = null;
    error.value = value instanceof ApiError && value.status === 404
      ? '活动不存在、已结束或当前未分配给你。'
      : '活动详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

watch(() => String(route.params.id), id => void load(id), { immediate: true });
</script>

<template>
  <WorkspaceShell
    role-label="学生"
    workspace-title="学生个人工作台"
    page-title="活动详情"
    description="查看已分配活动的时间、地点和挑战规则。"
    home-path="/student"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="student-activity-panel">
      <RouterLink class="project-back-link" to="/student/activities">返回我的活动</RouterLink>
      <div v-if="loading" class="project-state" role="status">正在加载活动详情...</div>
      <div v-else-if="error" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/student/activities">返回活动列表</RouterLink></div>
      <template v-else-if="activity">
        <div class="project-detail-heading">
          <div class="activity-detail-status"><span>{{ activity.executionStatus }}</span><span>{{ activity.schoolName }}</span></div>
          <h1>{{ activity.title }}</h1>
          <p>{{ activity.description || '暂无活动说明。' }}</p>
        </div>
        <div class="project-detail-grid">
          <section class="project-detail-section"><h2>活动信息</h2><dl><div><dt>举办学校</dt><dd>{{ activity.schoolName }}</dd></div><div><dt>地区</dt><dd>{{ activity.schoolRegion }}</dd></div><div><dt>活动时间</dt><dd>{{ activity.startTime ? new Date(activity.startTime).toLocaleString() : '时间待定' }}</dd></div><div><dt>活动地点</dt><dd>{{ activity.location || '地点待定' }}</dd></div></dl></section>
          <section class="project-detail-section"><h2>挑战项目</h2><div v-if="!activity.projects.length" class="activity-empty-note">当前活动没有提供挑战项目规则。</div><div v-for="project in activity.projects" :key="project.projectId" class="activity-project-block"><h3>{{ project.projectName }}</h3><p class="project-rule-text">{{ project.rulesText || '暂无规则说明。' }}</p></div></section>
        </div>
      </template>
    </section>
  </WorkspaceShell>
</template>
