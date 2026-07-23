<template>
  <PublicLayout>
    <el-result icon="warning" title="无可用角色" sub-title="你的账号尚未分配任何业务角色，请联系管理员">
      <template #extra>
        <el-button type="primary" @click="handleLogout">切换账号</el-button>
        <el-button @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </PublicLayout>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import { useRouter } from 'vue-router';
import PublicLayout from '@/layouts/PublicLayout.vue';

const auth = useAuthStore();
const router = useRouter();

async function handleLogout() {
  await auth.logout();
  router.push('/login');
}
</script>
