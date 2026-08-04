<template>
  <PublicLayout>
    <div class="pending-page">
      <el-card class="pending-card" shadow="always">
        <h2>请检查邮箱</h2>
        <p v-if="username" class="intro">账号 {{ username }} 的注册信息已提交。</p>
        <p class="intro">请打开邮箱中的验证链接。邮箱验证成功后，再返回登录页面。</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleResend"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="form.email"
              autocomplete="email"
              placeholder="重新输入邮箱以发送验证邮件"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              native-type="submit"
              type="primary"
              :loading="submitting"
              :disabled="submitting || cooldown > 0"
              style="width: 100%"
            >
              {{ cooldown > 0 ? `${cooldown} 秒后可重新发送` : '重新发送验证邮件' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="message" aria-live="polite">
          <el-alert
            v-if="message"
            :title="message"
            type="success"
            show-icon
            :closable="false"
          />
          <el-alert
            v-if="errorMsg"
            :title="errorMsg"
            type="error"
            show-icon
            :closable="false"
          />
        </div>

        <div class="pending-actions">
          <el-button text type="primary" @click="router.push('/login')">返回登录</el-button>
        </div>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { ApiError } from '@/api/http';
import { resendVerification } from '@/api/registration';
import PublicLayout from '@/layouts/PublicLayout.vue';

const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const message = ref<string | null>(null);
const errorMsg = ref<string | null>(null);
const cooldown = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;

const username = computed(() => {
  const state = (history.state ?? {}) as { username?: unknown };
  return typeof state.username === 'string' ? state.username : '';
});

const form = reactive({ email: '' });

const rules: FormRules = {
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
};

async function handleResend() {
  if (submitting.value || cooldown.value > 0) return;
  submitting.value = true;
  message.value = null;
  errorMsg.value = null;

  const email = form.email.trim();
  if (!email) {
    submitting.value = false;
    return;
  }

  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    submitting.value = false;
    return;
  }

  try {
    const result = await resendVerification(email);
    message.value = result.message || '如果存在未验证账号，验证邮件将会发送。';
    startCooldown();
  } catch (e) {
    errorMsg.value = e instanceof ApiError ? e.message : '发送失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

function startCooldown() {
  cooldown.value = 60;
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0 && timer) {
      clearInterval(timer);
      timer = null;
    }
  }, 1000);
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.pending-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.pending-card {
  width: 100%;
  max-width: 480px;
}

.pending-card h2 {
  text-align: center;
  margin-bottom: 16px;
}

.intro {
  color: #606266;
  line-height: 1.7;
  text-align: center;
}

.message {
  min-height: 1px;
  margin-bottom: 12px;
}

.pending-actions {
  text-align: center;
}
</style>
