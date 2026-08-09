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
        <span>校园吉尼斯</span>
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
      <p class="eyebrow">登录成功</p>
      <h1>当前身份</h1>

      <dl class="identity-list">
        <div>
          <dt>用户名</dt>
          <dd>{{ user.username }}</dd>
        </div>
        <div>
          <dt>账号状态</dt>
          <dd>{{ user.accountStatus }}</dd>
        </div>
        <div>
          <dt>身份</dt>
          <dd>
            <span v-for="role in roles" :key="role" class="role-badge">{{ role }}</span>
          </dd>
        </div>
        <div>
          <dt>学校</dt>
          <dd v-if="user.schoolMemberships.length" class="membership-list">
            <span
              v-for="membership in user.schoolMemberships"
              :key="membership.membershipId"
              class="membership-chip"
            >
              {{ membershipLabel(membership) }}
            </span>
          </dd>
          <dd v-else class="muted">无学校身份</dd>
        </div>
      </dl>

      <p v-if="logoutError" class="message message-error" role="status">
        {{ logoutError }}
      </p>

      <details class="debug-panel">
        <summary>JSON debug</summary>
        <pre>{{ JSON.stringify(user, null, 2) }}</pre>
      </details>
    </section>
  </main>
</template>
