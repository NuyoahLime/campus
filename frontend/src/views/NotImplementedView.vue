<template>
  <el-result icon="info" title="功能开发中" sub-title="此功能正在开发，敬请期待">
    <template #extra>
      <el-button type="primary" @click="$router.back()">返回</el-button>
    </template>
  </el-result>
</template>
