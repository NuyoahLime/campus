<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import PublicShell from '../components/PublicShell.vue';
import { listPublicActivities } from '../api/activities';
import type { ActivityListItem } from '../types/activity';
import { activityTimeRange, labelForActivityStatus } from '../utils/activityLabels';

const items = ref<ActivityListItem[]>([]);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    items.value = (await listPublicActivities()).items;
  } catch {
    items.value = [];
    error.value = '公开活动加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <PublicShell active="activities">
    <main class="project-public-page">
      <section class="project-public-content">
        <div class="project-page-heading">
          <p class="eyebrow">SCHOOL ACTIVITIES</p>
          <h1>学校公开活动</h1>
          <span>查看已经通过平台公开审核的学校活动。</span>
        </div>
        <div v-if="loading" class="project-state" role="status">正在加载公开活动...</div>
        <div v-else-if="error" class="project-state project-state-error" role="alert">
          <strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button>
        </div>
        <div v-else-if="items.length === 0" class="project-state"><strong>暂时没有公开活动</strong><p>新的公开活动将在审核完成后显示。</p></div>
        <div v-else class="activity-card-grid">
          <RouterLink v-for="activity in items" :key="activity.id" class="activity-card" :to="`/activities/${activity.id}`">
            <div class="activity-card-meta"><span>{{ labelForActivityStatus(activity.executionStatus) }}</span><span>{{ activity.schoolName }}</span></div>
            <h2>{{ activity.title }}</h2>
            <p>{{ activityTimeRange(activity.startTime, activity.endTime) }}</p>
            <p v-if="activity.location" class="activity-card-location">{{ activity.location }}</p>
            <span class="project-card-link">查看活动详情</span>
          </RouterLink>
        </div>
      </section>
    </main>
  </PublicShell>
</template>
