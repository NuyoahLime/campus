<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();
const logoutError = ref('');

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
  <main class="role-unavailable-page">
    <section class="role-unavailable-panel" aria-labelledby="role-unavailable-title">
      <span class="brand-mark" aria-hidden="true">G</span>
      <p>校园吉尼斯</p>
      <h1 id="role-unavailable-title">当前会话身份不可用</h1>
      <span>系统无法为当前账号确定唯一的正式角色。请退出登录后联系管理员。</span>
      <p v-if="logoutError" class="message message-error" role="status">{{ logoutError }}</p>
      <button
        class="primary-button role-unavailable-action"
        type="button"
        :disabled="auth.loading"
        @click="handleLogout"
      >
        {{ auth.loading ? '退出中...' : '退出登录' }}
      </button>
    </section>
  </main>
</template>
