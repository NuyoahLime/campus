<template>
  <PublicLayout>
    <div class="login-page">
      <el-card class="login-card" shadow="always">
        <h2>登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
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
            <el-button native-type="submit" type="primary" :loading="submitting" :disabled="submitting" style="width: 100%">
              {{ submitting ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
        <div v-if="errorMsg" class="login-error">
          <el-alert :title="errorMsg" type="error" show-icon :closable="false" />
        </div>
        <div class="login-footer">
          <p class="note">学生和老师账号由所在学校管理员创建。<br>学校管理员账号由平台管理员创建。</p>
          <el-button text type="primary" @click="$router.push('/activate-account')">首次使用？激活账号</el-button>
          <el-button text @click="$router.push('/')">返回首页</el-button>
        </div>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { ApiError } from '@/api/http';
import type { FormInstance, FormRules } from 'element-plus';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const errorMsg = ref<string | null>(null);

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
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) { submitting.value = false; return; }

  submitting.value = true;
  errorMsg.value = null;

  try {
    await auth.login(form.username, form.password);
    const redirect = (route.query.redirect as string) || auth.defaultWorkspaceRoute();
    await router.replace(redirect);
  } catch (e) {
    if (e instanceof ApiError) {
      errorMsg.value = e.status === 401 ? '用户名或密码错误' : e.message;
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
  max-width: 400px;
}

.login-card h2 {
  text-align: center;
  margin-bottom: 24px;
}

.login-error {
  margin-bottom: 16px;
}

.login-footer {
  text-align: center;
  margin-top: 8px;
}
</style>
