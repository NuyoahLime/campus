<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { roleHomeLocation } from '../router/roleHome';
import { useAuthStore } from '../stores/auth';

defineProps<{
  active?: 'home' | 'projects' | 'activities' | 'registration' | 'rankings';
}>();

const auth = useAuthStore();
const accountLink = computed(() => auth.isAuthenticated ? roleHomeLocation(auth.currentUser) : { name: 'login' });
const accountLabel = computed(() => auth.isAuthenticated ? '进入工作台' : '登录');
</script>

<template>
  <div class="public-site">
    <header class="public-site-header">
      <RouterLink class="public-site-brand" to="/" aria-label="校园吉尼斯">
        <span class="brand-mark brand-mark-small" aria-hidden="true">G</span>
        <span>
          <strong>校园吉尼斯</strong>
          <small>Campus Guinness</small>
        </span>
      </RouterLink>
      <nav class="public-site-navigation" aria-label="公共导航">
        <RouterLink :class="{ 'public-site-link-active': active === 'home' }" to="/">首页</RouterLink>
        <RouterLink :class="{ 'public-site-link-active': active === 'projects' }" to="/projects">挑战项目</RouterLink>
        <RouterLink :class="{ 'public-site-link-active': active === 'activities' }" to="/activities">学校活动</RouterLink>
        <RouterLink :class="{ 'public-site-link-active': active === 'registration' }" to="/school-registration">学校入驻</RouterLink>
        <RouterLink :class="{ 'public-site-link-active': active === 'rankings' }" to="/rankings">排行榜</RouterLink>
      </nav>
      <RouterLink class="secondary-button public-site-login" :to="accountLink">{{ accountLabel }}</RouterLink>
    </header>
    <slot />
    <footer class="public-site-footer">
      <span>校园吉尼斯</span>
      <span>公开项目与规则资源</span>
    </footer>
  </div>
</template>
