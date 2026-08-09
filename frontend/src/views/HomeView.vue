<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import type { AuthenticatedSchoolMembership } from '../types/auth';

const router = useRouter();
const auth = useAuthStore();
const logoutError = ref('');

const user = computed(() => auth.currentUser);

const roleLabels: Record<string, string> = {
  ROLE_STUDENT: '学生',
  ROLE_SCHOOL_ADMIN: '学校管理员',
  ROLE_SUPER_ADMIN: '超级管理员'
};

const roles = computed(() =>
  (user.value?.authorities ?? []).map((authority) => roleLabels[authority] ?? authority)
);

function membershipLabel(membership: AuthenticatedSchoolMembership): string {
  const role = membership.roleInSchool === 'STUDENT'
    ? '学生'
    : membership.roleInSchool === 'SCHOOL_ADMIN'
      ? '学校管理员'
      : membership.roleInSchool;
  return `${role} / 学校 ${membership.schoolId}`;
}

async function handleLogout() {
  logoutError.value = '';
  try {
    await auth.logout();
    await router.push('/login');
  } catch {
    logoutError.value = '退出登录失败，请稍后再试';
  }
}
</script>

<template>
  <main class="shell-page">
    <header class="topbar">
      <div class="topbar-brand">
        <span class="brand-mark brand-mark-small" aria-hidden="true">G</span>
        <span>校园吉尼斯挑战赛 <span class="topbar-brand-subtitle">资源管理平台</span></span>
      </div>
      <div class="topbar-user" v-if="user">
        <span>{{ user.username }}</span>
        <span v-for="role in roles" :key="role" class="role-badge">{{ role }}</span>
        <button class="secondary-button" type="button" :disabled="auth.loading" @click="handleLogout">
          {{ auth.loading ? '退出中...' : '退出登录' }}
        </button>
      </div>
    </header>

    <section class="home-panel" v-if="user">
      <div class="identity-hero">
        <div class="identity-hero-copy">
          <p class="eyebrow">当前会话身份</p>
          <h1>{{ user.username }}</h1>
          <div class="identity-hero-meta">
            <span v-for="role in roles" :key="role" class="role-badge">{{ role }}</span>
            <span class="status-pill">{{ user.accountStatus }}</span>
          </div>
        </div>
        <div class="identity-hero-art" aria-hidden="true">
          <span class="identity-avatar">{{ user.username.slice(0, 1).toUpperCase() }}</span>
        </div>
      </div>

      <div class="identity-grid">
        <section class="identity-card" aria-labelledby="identity-details-title">
          <div class="identity-card-heading">
            <h2 id="identity-details-title">身份信息</h2>
            <span class="trust-icon" aria-hidden="true">✓</span>
          </div>
          <dl class="identity-list">
            <div>
              <dt>用户 ID</dt>
              <dd>{{ user.userId }}</dd>
            </div>
            <div>
              <dt>用户名</dt>
              <dd>{{ user.username }}</dd>
            </div>
            <div>
              <dt>账号状态</dt>
              <dd>{{ user.accountStatus }}</dd>
            </div>
            <div>
              <dt>平台角色</dt>
              <dd class="membership-list">
                <span v-for="role in roles" :key="role" class="role-badge">{{ role }}</span>
              </dd>
            </div>
          </dl>
        </section>

        <section class="identity-card" aria-labelledby="membership-title">
          <div class="identity-card-heading">
            <h2 id="membership-title">学校身份</h2>
            <span class="trust-icon" aria-hidden="true">◆</span>
          </div>
          <div v-if="user.schoolMemberships.length" class="membership-list membership-list-panel">
            <span
              v-for="membership in user.schoolMemberships"
              :key="membership.membershipId"
              class="membership-chip"
            >
              {{ membershipLabel(membership) }}
            </span>
          </div>
          <p v-else class="muted membership-empty">当前账号暂无学校身份</p>
        </section>
      </div>

      <p v-if="logoutError" class="message message-error" role="status">
        {{ logoutError }}
      </p>

      <details class="debug-panel">
        <summary>查看会话 JSON</summary>
        <pre>{{ JSON.stringify(user, null, 2) }}</pre>
      </details>
    </section>
  </main>
</template>
