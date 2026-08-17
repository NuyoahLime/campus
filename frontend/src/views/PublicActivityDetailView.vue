<script setup lang="ts">
import { ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import PublicShell from '../components/PublicShell.vue';
import { getPublicActivity } from '../api/activities';
import { ApiError } from '../api/http';
import type { ActivityDetail } from '../types/activity';
import { activityTimeRange, labelForActivityStatus } from '../utils/activityLabels';
import { labelForCategory, labelForComparisonDirection, labelForScoreIndicatorType, labelForScoreStorageType } from '../utils/challengeProjectLabels';

const route = useRoute();
const activity = ref<ActivityDetail | null>(null);
const loading = ref(true);
const error = ref('');
const notFound = ref(false);

async function load(id: string) {
  loading.value = true;
  error.value = '';
  notFound.value = false;
  try {
    activity.value = await getPublicActivity(id);
  } catch (value) {
    activity.value = null;
    notFound.value = value instanceof ApiError && value.status === 404;
    error.value = notFound.value ? '活动不存在或尚未公开。' : '活动详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

watch(() => String(route.params.id), id => void load(id), { immediate: true });
</script>

<template>
  <PublicShell active="activities">
    <main class="project-public-page">
      <section class="project-public-content project-detail-page">
        <RouterLink class="project-back-link" to="/activities">返回学校公开活动</RouterLink>
        <div v-if="loading" class="project-state" role="status">正在加载活动详情...</div>
        <div v-else-if="error" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/activities">返回活动列表</RouterLink></div>
        <template v-else-if="activity">
          <div class="project-detail-heading">
            <div class="activity-detail-status"><span>{{ labelForActivityStatus(activity.executionStatus) }}</span><span>{{ activity.schoolName }}</span></div>
            <h1>{{ activity.title }}</h1>
            <p>{{ activity.description || '暂无活动说明。' }}</p>
          </div>
          <div class="project-detail-grid">
            <section class="project-detail-section"><h2>活动信息</h2><dl><div><dt>举办学校</dt><dd>{{ activity.schoolName }}</dd></div><div><dt>地区</dt><dd>{{ activity.schoolRegion }}</dd></div><div><dt>活动时间</dt><dd>{{ activityTimeRange(activity.startTime, activity.endTime) }}</dd></div><div><dt>活动地点</dt><dd>{{ activity.location || '地点待定' }}</dd></div></dl></section>
            <section class="project-detail-section"><h2>挑战项目</h2><div v-if="activity.projects.length === 0" class="activity-empty-note">当前活动未提供公开项目规则。</div><div v-for="project in activity.projects" :key="project.projectId" class="activity-project-block"><h3>{{ project.projectName }}</h3><p>{{ labelForCategory(project.category) }}</p><dl><div><dt>规则版本</dt><dd>V{{ project.ruleVersionNumber }}</dd></div><div><dt>成绩类型</dt><dd>{{ labelForScoreStorageType(project.scoreStorageType) }}</dd></div><div><dt>成绩指标</dt><dd>{{ labelForScoreIndicatorType(project.scoreIndicatorType) }}</dd></div><div><dt>比较方向</dt><dd>{{ labelForComparisonDirection(project.comparisonDirection) }}</dd></div><div><dt>成绩单位</dt><dd>{{ project.scoreUnit || '未填写' }}</dd></div></dl><p class="project-rule-text">{{ project.rulesText || '暂无规则说明。' }}</p></div></section>
          </div>
        </template>
      </section>
    </main>
  </PublicShell>
</template>
