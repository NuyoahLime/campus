<template>
  <PublicLayout>
    <div class="login-placeholder">
      <el-result icon="info" title="登录功能即将上线" sub-title="认证系统正在开发中，敬请期待">
        <template #extra>
          <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
        </template>
      </el-result>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import PublicLayout from '@/layouts/PublicLayout.vue';
</script>

<style scoped>
.login-placeholder {
  padding: 80px 0;
}
</style>
