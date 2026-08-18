<script setup lang="ts">
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
</script>

<template>
  <WorkspaceShell
    role-label="学校管理员"
    workspace-title="学校管理工作台"
    page-title="欢迎进入学校管理工作台"
    description="处理本校已开放的管理事项，并查看当前学校身份。"
    home-path="/school-admin"
    :navigation="navigation"
  />
</template>
