<template>
  <PublicLayout>
    <div class="activate-page">
      <el-card class="activate-card" shadow="always">
        <h2>激活账号</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleActivate">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="临时密码" prop="temporaryPassword">
            <el-input v-model="form.temporaryPassword" type="password" placeholder="管理员提供的临时密码" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="form.newPassword" type="password" placeholder="至少8位" show-password />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" show-password />
          </el-form-item>
          <el-form-item>
            <el-button native-type="submit" type="primary" :loading="loading" :disabled="loading" style="width:100%">{{ loading ? '激活中...' : '激活账号' }}</el-button>
          </el-form-item>
        </el-form>
        <div v-if="errorMsg" class="err"><el-alert :title="errorMsg" type="error" show-icon :closable="false" /></div>
        <div v-if="success" class="suc"><el-alert title="账号激活成功！" type="success" show-icon /><el-button type="primary" @click="$router.push('/login')" style="margin-top:12px">返回登录</el-button></div>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import PublicLayout from '@/layouts/PublicLayout.vue';
import http from '@/api/http';
import { ApiError } from '@/api/http';
import type { FormInstance, FormRules } from 'element-plus';
const formRef = ref<FormInstance>();
const loading = ref(false);
const errorMsg = ref<string|null>(null);
const success = ref(false);

const form = reactive({ username: '', temporaryPassword: '', newPassword: '', confirmPassword: '' });
const validateConfirm = (_rule: unknown, value: string, cb: (e?: Error) => void) => { cb(value !== form.newPassword ? new Error('两次输入的密码不一致') : undefined); };

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名' }],
  temporaryPassword: [{ required: true, message: '请输入临时密码' }],
  newPassword: [{ required: true, min: 8, message: '至少8位' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }],
};

async function handleActivate() {
  if (loading.value) return;
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) { loading.value = false; return; }
  loading.value = true; errorMsg.value = null;
  try {
    await http.post('/v1/auth/activate', { username: form.username, temporaryPassword: form.temporaryPassword, newPassword: form.newPassword, confirmPassword: form.confirmPassword });
    success.value = true;
  } catch (e: unknown) { errorMsg.value = e instanceof ApiError ? e.message : '激活失败，请重试'; }
  finally { loading.value = false; }
}
</script>

<style scoped>
.activate-page { display: flex; justify-content: center; align-items: center; min-height: 60vh; }
.activate-card { width: 100%; max-width: 420px; }
.activate-card h2 { text-align: center; margin-bottom: 24px; }
.err, .suc { margin-top: 16px; }
</style>
