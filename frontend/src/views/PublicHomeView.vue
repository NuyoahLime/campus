<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import PublicShell from '../components/PublicShell.vue';
import { listPublicActivities } from '../api/activities';
import type { ActivityListItem } from '../types/activity';
import { activityTimeRange, labelForActivityStatus } from '../utils/activityLabels';

const activities = ref<ActivityListItem[]>([]);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    activities.value = (await listPublicActivities(0, 3)).items;
  } catch {
    activities.value = [];
    error.value = '公开活动暂时无法加载。';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <PublicShell active="home">
    <main class="public-home-page">
      <section class="public-home-hero" aria-labelledby="home-title">
        <div>
          <p class="eyebrow">CAMPUS GUINNESS</p>
          <h1 id="home-title">发现校园挑战，记录真实成果</h1>
          <p>浏览已公开的挑战项目与学校活动，了解每一场校园挑战的时间、地点和公开规则。</p>
          <div class="public-home-actions">
            <RouterLink class="primary-button" to="/activities">浏览学校活动</RouterLink>
            <RouterLink class="secondary-button" to="/projects">查看挑战项目</RouterLink>
          </div>
        </div>
        <div class="public-home-mark" aria-hidden="true"><span>G</span><small>OPEN CAMPUS</small></div>
      </section>

      <section class="public-home-section" aria-labelledby="latest-activities-title">
        <div class="public-home-section-heading">
          <div><p class="eyebrow">PUBLIC ACTIVITIES</p><h2 id="latest-activities-title">近期公开活动</h2></div>
          <RouterLink class="project-back-link" to="/activities">查看全部活动</RouterLink>
        </div>
        <div v-if="loading" class="project-state" role="status">正在加载公开活动...</div>
        <div v-else-if="error" class="project-state project-state-error" role="alert">
          <strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button>
        </div>
        <div v-else-if="activities.length === 0" class="project-state"><strong>暂时没有公开活动</strong><p>新的公开活动将在审核完成后显示。</p></div>
        <div v-else class="activity-card-grid">
          <RouterLink v-for="activity in activities" :key="activity.id" class="activity-card" :to="`/activities/${activity.id}`">
            <div class="activity-card-meta"><span>{{ labelForActivityStatus(activity.executionStatus) }}</span><span>{{ activity.schoolName }}</span></div>
            <h3>{{ activity.title }}</h3>
            <p>{{ activityTimeRange(activity.startTime, activity.endTime) }}</p>
          </RouterLink>
        </div>
      </section>
    </main>
  </PublicShell>
</template>
