<script setup lang="ts">
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
</script>

<template>
  <WorkspaceShell
    role-label="超级管理员"
    workspace-title="平台管理工作台"
    page-title="欢迎进入平台管理工作台"
    description="查看当前平台身份，并从左侧导航进入已开放的管理功能。"
    home-path="/super-admin"
    :navigation="navigation"
  />
</template>
