<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import type { WorkspaceNavigationItem } from '../types/workspace';

const props = withDefaults(defineProps<{
  roleLabel: string;
  workspaceTitle: string;
  pageTitle: string;
  description: string;
  homePath: string;
  navigation: WorkspaceNavigationItem[];
  showIdentity?: boolean;
}>(), {
  showIdentity: true
});

const auth = useAuthStore();
const router = useRouter();
const logoutError = ref('');
const user = computed(() => auth.currentUser);
const schoolIds = computed(() =>
  Array.from(new Set(user.value?.schoolMemberships.map((membership) => membership.schoolId) ?? []))
);
const initial = computed(() => user.value?.username.slice(0, 1).toUpperCase() || 'U');

async function handleLogout() {
  logoutError.value = '';
  try {
    await auth.logout();
    await router.replace('/login');
  } catch {
    logoutError.value = '退出登录失败，请稍后再试';
  }
}
</script>

<template>
  <div class="workspace-layout">
    <aside class="workspace-sidebar">
      <RouterLink class="workspace-brand" :to="props.homePath">
        <span class="brand-mark brand-mark-small" aria-hidden="true">G</span>
        <span>
          <strong>校园吉尼斯</strong>
          <small>Campus Guinness</small>
        </span>
      </RouterLink>

      <div class="workspace-role-block">
        <span>当前工作台</span>
        <strong>{{ props.workspaceTitle }}</strong>
      </div>

      <nav class="workspace-navigation" aria-label="工作台导航">
        <template v-for="item in props.navigation" :key="item.label">
          <RouterLink
            v-if="item.to && !item.disabled"
            class="workspace-nav-item"
            :to="item.to"
          >
            <span>{{ item.label }}</span>
          </RouterLink>
          <span v-else class="workspace-nav-item workspace-nav-item-disabled" aria-disabled="true">
            <span>{{ item.label }}</span>
            <small>待开发</small>
          </span>
        </template>
      </nav>
    </aside>

    <div class="workspace-main">
      <header class="workspace-topbar">
        <div class="workspace-topbar-context">
          <span>{{ props.roleLabel }}</span>
          <strong>{{ props.workspaceTitle }}</strong>
        </div>
        <div v-if="user" class="workspace-account">
          <span class="workspace-avatar" aria-hidden="true">{{ initial }}</span>
          <span class="workspace-account-copy">
            <strong>{{ user.username }}</strong>
            <small>{{ props.roleLabel }}</small>
          </span>
          <button
            class="workspace-logout-button"
            type="button"
            :disabled="auth.loading"
            @click="handleLogout"
          >
            {{ auth.loading ? '退出中...' : '退出登录' }}
          </button>
        </div>
      </header>

      <main class="workspace-content">
        <header class="workspace-page-header">
          <p>{{ props.workspaceTitle }}</p>
          <h1>{{ props.pageTitle }}</h1>
          <span>{{ props.description }}</span>
        </header>

        <p v-if="logoutError" class="message message-error workspace-message" role="status">
          {{ logoutError }}
        </p>

        <section
          v-if="user && props.showIdentity !== false"
          class="workspace-identity"
          aria-labelledby="workspace-identity-title"
        >
          <div class="workspace-section-heading">
            <div>
              <p>SESSION IDENTITY</p>
              <h2 id="workspace-identity-title">当前身份</h2>
            </div>
            <span class="workspace-status">{{ user.accountStatus }}</span>
          </div>

          <dl class="workspace-identity-list">
            <div>
              <dt>用户名</dt>
              <dd>{{ user.username }}</dd>
            </div>
            <div>
              <dt>角色</dt>
              <dd>{{ props.roleLabel }}</dd>
            </div>
            <div>
              <dt>学校</dt>
              <dd>{{ schoolIds.length ? schoolIds.join('、') : '不适用' }}</dd>
            </div>
          </dl>
        </section>

        <slot />
      </main>
    </div>
  </div>
</template>
