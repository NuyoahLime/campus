<template>
  <PublicLayout>
    <div class="verify-page">
      <el-card class="verify-card" shadow="always">
        <el-result
          v-if="state === 'VERIFYING'"
          icon="info"
          title="正在验证邮箱..."
          sub-title="请稍候，不要刷新页面。"
        />
        <el-result
          v-else-if="state === 'SUCCESS'"
          icon="success"
          title="邮箱验证成功"
          sub-title="请使用用户名和密码登录。"
        >
          <template #extra>
            <el-button type="primary" @click="router.push('/login?verified=1')">
              前往登录
            </el-button>
          </template>
        </el-result>
        <el-result
          v-else
          icon="error"
          title="验证链接无效或已过期"
          sub-title="请重新发送验证邮件后再试。"
        >
          <template #extra>
            <el-button type="primary" @click="router.push('/verify-email-pending')">
              重新发送验证邮件
            </el-button>
            <el-button @click="router.push('/login')">返回登录</el-button>
          </template>
        </el-result>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { verifyEmail } from '@/api/registration';
import PublicLayout from '@/layouts/PublicLayout.vue';

type VerificationState = 'VERIFYING' | 'SUCCESS' | 'INVALID';

const route = useRoute();
const router = useRouter();
const state = ref<VerificationState>('VERIFYING');
const started = ref(false);

onMounted(async () => {
  if (started.value) return;
  started.value = true;

  const rawToken = typeof route.query.token === 'string' ? route.query.token : null;

  await router.replace({
    path: '/verify-email',
    query: {},
  });

  if (!rawToken) {
    state.value = 'INVALID';
    return;
  }

  try {
    await verifyEmail(rawToken);
    state.value = 'SUCCESS';
  } catch {
    state.value = 'INVALID';
  }
});
</script>

<style scoped>
.verify-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.verify-card {
  width: 100%;
  max-width: 560px;
}
</style>
