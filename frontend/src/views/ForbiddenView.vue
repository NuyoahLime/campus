<template>
  <el-result icon="error" title="403" sub-title="你没有权限访问此页面">
    <template #extra>
      <el-button type="primary" @click="$router.push(auth.defaultWorkspaceRoute())">进入工作台</el-button>
      <el-button @click="$router.push('/')">返回首页</el-button>
    </template>
  </el-result>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
const auth = useAuthStore();
</script>
