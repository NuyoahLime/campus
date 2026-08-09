<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { ApiError } from '../api/http';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

const username = ref('');
const password = ref('');
const showPassword = ref(false);
const errorMessage = ref('');

const submitting = computed(() => auth.loading);

const messageByCode: Record<string, string> = {
  AUTHENTICATION_FAILED: '用户名或密码错误',
  ACCOUNT_LOCKED: '账号暂时被锁定，请稍后再试',
  ACCOUNT_DISABLED: '账号已被停用，请联系管理员',
  STUDENT_APPROVAL_PENDING: '学生身份申请正在审核中',
  STUDENT_APPLICATION_REJECTED: '学生身份申请未通过审核',
  SCHOOL_ADMIN_ACTIVATION_PENDING: '学校管理员账号尚未激活',
  SCHOOL_ADMIN_ACTIVATION_REQUIRED: '学校管理员账号需要完成激活',
  ACCOUNT_ACTIVATION_REQUIRED: '账号需要完成激活',
  ACCOUNT_ROLE_NOT_READY: '当前账号身份尚未准备完成'
};

function friendlyError(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.code;
    if (code && messageByCode[code]) return messageByCode[code];
  }
  return '登录失败，请稍后重试';
}

async function submit() {
  if (submitting.value) return;
  errorMessage.value = '';

  try {
    await auth.login({
      username: username.value.trim(),
      password: password.value
    });
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    await router.push(redirect);
  } catch (error) {
    errorMessage.value = friendlyError(error);
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="brand-panel" aria-label="校园吉尼斯品牌介绍">
      <div class="brand-mark" aria-hidden="true">G</div>
      <p class="eyebrow">Campus Guinness</p>
      <h1>校园吉尼斯</h1>
      <p class="brand-copy">
        发现校园记录，挑战校园极限，记录属于每个人的高光时刻。
      </p>
    </section>

    <section class="login-card" aria-labelledby="login-title">
      <div>
        <p class="eyebrow">欢迎回来</p>
        <h2 id="login-title">登录校园吉尼斯</h2>
      </div>

      <form class="form-stack" @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input
            v-model="username"
            autocomplete="username"
            :disabled="submitting"
            required
          />
        </label>

        <label class="field">
          <span>密码</span>
          <div class="password-field">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              :disabled="submitting"
              required
            />
            <button
              class="ghost-button"
              type="button"
              :disabled="submitting"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>

        <p v-if="errorMessage" class="message message-error" role="status">
          {{ errorMessage }}
        </p>

        <button class="primary-button" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>
