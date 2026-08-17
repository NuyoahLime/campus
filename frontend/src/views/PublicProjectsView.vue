<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { ApiError } from '../api/http';
import { listPublicProjects } from '../api/challengeProjects';
import type { ChallengeProjectListItem, ProjectPage } from '../types/challengeProject';
import {
  labelForCategory,
  labelForComparisonDirection,
  labelForScoreStorageType
} from '../utils/challengeProjectLabels';

const items = ref<ChallengeProjectListItem[]>([]);
const result = ref<ProjectPage<ChallengeProjectListItem> | null>(null);
const category = ref('');
const search = ref('');
const loading = ref(true);
const error = ref('');

function message(value: unknown) {
  return value instanceof ApiError ? '项目资源库暂时无法加载，请稍后重试。' : '项目资源库加载失败。';
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const response = await listPublicProjects(0, 20, category.value, search.value);
    result.value = response;
    items.value = response.items;
  } catch (value) {
    items.value = [];
    result.value = null;
    error.value = message(value);
  } finally {
    loading.value = false;
  }
}

function submit() {
  void load();
}

onMounted(() => void load());
</script>

<template>
  <main class="project-public-page">
    <header class="project-public-header">
      <div>
        <RouterLink class="project-brand" to="/">校园吉尼斯</RouterLink>
        <p>挑战项目资源库</p>
      </div>
      <RouterLink class="secondary-button" to="/login">登录</RouterLink>
    </header>
    <section class="project-public-content">
      <div class="project-page-heading">
        <p class="eyebrow">PROJECT LIBRARY</p>
        <h1>挑战项目资源库</h1>
        <span>浏览已上架的校园挑战项目和公开规则。</span>
      </div>
      <form class="project-filter-bar" @submit.prevent="submit">
        <label>关键词<input v-model="search" type="search" placeholder="项目名称或说明"></label>
        <label>分类<input v-model="category" type="search" placeholder="输入项目分类"></label>
        <button class="primary-button" type="submit" :disabled="loading">查询</button>
      </form>
      <div v-if="loading" class="project-state" role="status">正在加载项目...</div>
      <div v-else-if="error" class="project-state project-state-error" role="alert">
        <strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button>
      </div>
      <div v-else-if="items.length === 0" class="project-state"><strong>暂无已上架项目</strong><p>当前没有符合条件的公开项目。</p></div>
      <div v-else class="project-card-grid">
        <RouterLink v-for="item in items" :key="item.id" class="project-card" :to="`/projects/${item.id}`">
          <div class="project-card-meta"><span>{{ labelForCategory(item.category) }}</span><span>{{ labelForScoreStorageType(item.scoreStorageType) }}</span></div>
          <h2>{{ item.name }}</h2>
          <p>成绩方向：{{ labelForComparisonDirection(item.comparisonDirection) }}</p>
          <span class="project-card-link">查看项目详情</span>
        </RouterLink>
      </div>
      <p v-if="result" class="project-result-count">共 {{ result.totalElements }} 个已上架项目</p>
    </section>
  </main>
</template>
