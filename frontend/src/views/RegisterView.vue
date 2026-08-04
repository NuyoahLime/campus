<template>
  <PublicLayout>
    <div class="register-page">
      <el-card class="register-card" shadow="always">
        <h2>公开注册</h2>
        <p class="intro">注册后需要先完成邮箱验证，再使用用户名和密码登录。</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              maxlength="100"
              placeholder="请输入用户名"
            />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="form.email"
              autocomplete="email"
              placeholder="请输入邮箱"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="至少 8 位"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入密码"
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
              {{ submitting ? '提交中...' : '注册并发送验证邮件' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="submit-state" aria-live="polite">
          <el-alert
            v-if="errorMsg"
            :title="errorMsg"
            type="error"
            show-icon
            :closable="false"
          />
        </div>

        <div class="register-footer">
          <el-button text type="primary" @click="router.push('/login')">
            已有账号？返回登录
          </el-button>
        </div>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { ApiError } from '@/api/http';
import { register } from '@/api/registration';
import PublicLayout from '@/layouts/PublicLayout.vue';

const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const errorMsg = ref<string | null>(null);

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
});

const validateConfirm = (_rule: unknown, value: string, cb: (error?: Error) => void) => {
  cb(value !== form.password ? new Error('两次输入的密码不一致') : undefined);
};

const rules: FormRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { max: 100, message: '用户名最多 100 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
};

async function handleRegister() {
  if (submitting.value) return;
  submitting.value = true;
  errorMsg.value = null;

  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    submitting.value = false;
    return;
  }

  try {
    const result = await register({
      username: form.username,
      email: form.email,
      password: form.password,
      confirmPassword: form.confirmPassword,
    });
    form.password = '';
    form.confirmPassword = '';
    await router.push({
      name: 'verify-email-pending',
      state: { username: result.username },
    });
  } catch (e) {
    if (e instanceof ApiError && e.code === 'REGISTRATION_UNAVAILABLE') {
      errorMsg.value = '当前注册信息不可用，请更换后重试';
    } else if (e instanceof ApiError) {
      errorMsg.value = e.message;
    } else {
      errorMsg.value = '注册失败，请稍后重试';
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.register-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.register-card {
  width: 100%;
  max-width: 460px;
}

.register-card h2 {
  text-align: center;
  margin-bottom: 12px;
}

.intro {
  color: #606266;
  font-size: 14px;
  margin-bottom: 20px;
  text-align: center;
}

.submit-state {
  min-height: 1px;
  margin-bottom: 12px;
}

.register-footer {
  text-align: center;
}
</style>
