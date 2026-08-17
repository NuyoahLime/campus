<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { getPublicProject } from '../api/challengeProjects';
import type { ChallengeProjectDetail } from '../types/challengeProject';
import {
  labelForCategory,
  labelForComparisonDirection,
  labelForEffectiveScoreRule,
  labelForScoreIndicatorType,
  labelForScoreStorageType
} from '../utils/challengeProjectLabels';

const route = useRoute();
const project = ref<ChallengeProjectDetail | null>(null);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    project.value = await getPublicProject(String(route.params.id));
  } catch {
    project.value = null;
    error.value = '项目不存在或尚未对外发布。';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <main class="project-public-page">
    <header class="project-public-header"><div><RouterLink class="project-brand" to="/projects">校园吉尼斯</RouterLink><p>挑战项目资源库</p></div><RouterLink class="secondary-button" to="/login">登录</RouterLink></header>
    <section class="project-public-content project-detail-page">
      <RouterLink class="project-back-link" to="/projects">返回项目资源库</RouterLink>
      <div v-if="loading" class="project-state">正在加载项目详情...</div>
      <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><RouterLink class="secondary-button" to="/projects">返回列表</RouterLink></div>
      <template v-else-if="project">
        <div class="project-detail-heading"><p class="eyebrow">{{ labelForCategory(project.category) }}</p><h1>{{ project.name }}</h1><p>{{ project.description || '暂无项目说明。' }}</p></div>
        <div class="project-detail-grid">
          <section class="project-detail-section"><h2>项目要求</h2><dl><div><dt>场地要求</dt><dd>{{ project.venueRequirements || '未填写' }}</dd></div><div><dt>器材要求</dt><dd>{{ project.equipmentRequirements || '未填写' }}</dd></div></dl></section>
          <section class="project-detail-section"><h2>成绩规则</h2><dl><div><dt>成绩类型</dt><dd>{{ labelForScoreStorageType(project.scoreStorageType) }}</dd></div><div><dt>成绩指标</dt><dd>{{ labelForScoreIndicatorType(project.scoreIndicatorType) }}</dd></div><div><dt>成绩单位</dt><dd>{{ project.scoreUnit || '未填写' }}</dd></div><div><dt>比较方向</dt><dd>{{ labelForComparisonDirection(project.comparisonDirection) }}</dd></div><div><dt>有效规则</dt><dd>{{ labelForEffectiveScoreRule(project.effectiveScoreRule) }}</dd></div><div><dt>允许并列</dt><dd>{{ project.allowTie ? '允许' : '不允许' }}</dd></div></dl></section>
          <section class="project-detail-section project-rule-section"><h2>比赛规则</h2><p class="project-rule-text">{{ project.rulesText || '暂无比赛规则。' }}</p></section>
        </div>
        <p v-if="project.currentRuleVersionNumber" class="project-version-note">当前公开规则版本 V{{ project.currentRuleVersionNumber }}</p>
      </template>
    </section>
  </main>
</template>
