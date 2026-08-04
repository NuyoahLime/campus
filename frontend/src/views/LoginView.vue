<template>
  <PublicLayout>
    <div class="login-page">
      <el-card class="login-card" shadow="always">
        <h2>登录</h2>

        <el-alert
          v-if="verifiedBanner"
          class="login-banner"
          title="邮箱验证成功，请登录。"
          type="success"
          show-icon
          :closable="false"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button
              native-type="submit"
              type="primary"
              :loading="submitting"
              :disabled="submitting"
              style="width: 100%"
            >
              {{ submitting ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div v-if="errorMsg" class="login-error" aria-live="polite">
          <el-alert :title="errorMsg" type="error" show-icon :closable="false" />
          <div v-if="verificationRequired" class="verification-action">
            <el-button text type="primary" @click="router.push('/verify-email-pending')">
              重新发送验证邮件
            </el-button>
          </div>
        </div>

        <div class="login-footer">
          <p class="note">
            学生可先公开注册账号；教师账号仍由学校管理员创建。
          </p>
          <el-button text type="primary" @click="router.push('/register')">
            没有账号？公开注册
          </el-button>
          <el-button text type="primary" @click="router.push('/activate-account')">
            已有临时密码？首次激活
          </el-button>
          <el-button text @click="router.push('/')">返回首页</el-button>
        </div>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { ApiError } from '@/api/http';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { useAuthStore } from '@/stores/auth';
import { safeRedirect } from '@/router';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const errorMsg = ref<string | null>(null);
const verificationRequired = ref(false);

const verifiedBanner = computed(() => route.query.verified === '1');

const form = reactive({
  username: '',
  password: '',
});

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function handleLogin() {
  if (submitting.value) return;
  submitting.value = true;
  errorMsg.value = null;
  verificationRequired.value = false;

  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    submitting.value = false;
    return;
  }

  try {
    const context = await auth.login(form.username, form.password);
    const destination = context.primaryRole === 'REGISTERED_USER'
      ? '/onboarding'
      : safeRedirect(route.query.redirect, auth.defaultWorkspaceRoute());
    await router.replace(destination);
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 401) {
        errorMsg.value = '用户名或密码错误';
      } else if (e.status === 403 && e.code === 'EMAIL_VERIFICATION_REQUIRED') {
        errorMsg.value = '该账号需要先完成邮箱验证。';
        verificationRequired.value = true;
      } else {
        errorMsg.value = e.message;
      }
    } else {
      errorMsg.value = '登录失败，请稍后重试';
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.login-card {
  width: 100%;
  max-width: 420px;
}

.login-card h2 {
  text-align: center;
  margin-bottom: 24px;
}

.login-banner,
.login-error {
  margin-bottom: 16px;
}

.verification-action {
  margin-top: 8px;
  text-align: center;
}

.login-footer {
  text-align: center;
  margin-top: 8px;
}

.note {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
</style>
