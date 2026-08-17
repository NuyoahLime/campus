<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { roleHomeLocation } from '../router/roleHome';
import { useAuthStore } from '../stores/auth';

defineProps<{
  active?: 'projects';
}>();

const auth = useAuthStore();
const accountLink = computed(() => auth.isAuthenticated ? roleHomeLocation(auth.currentUser) : { name: 'login' });
const accountLabel = computed(() => auth.isAuthenticated ? '进入工作台' : '登录');
</script>

<template>
  <div class="public-site">
    <header class="public-site-header">
      <RouterLink class="public-site-brand" to="/projects" aria-label="校园吉尼斯项目资源库">
        <span class="brand-mark brand-mark-small" aria-hidden="true">G</span>
        <span>
          <strong>校园吉尼斯</strong>
          <small>Campus Guinness</small>
        </span>
      </RouterLink>
      <nav class="public-site-navigation" aria-label="公共导航">
        <RouterLink :class="{ 'public-site-link-active': active === 'projects' }" to="/projects">挑战项目</RouterLink>
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
