<template>
  <PublicLayout>
    <div class="not-found">
      <el-result icon="error" title="404" sub-title="页面不存在">
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
.not-found {
  padding: 80px 0;
}
</style>
