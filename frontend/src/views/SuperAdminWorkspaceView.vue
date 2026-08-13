<script setup lang="ts">
import WorkspaceShell from '../components/WorkspaceShell.vue';
import type { WorkspaceNavigationItem } from '../types/workspace';

const navigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/super-admin' },
  { label: '学校治理', to: '/super-admin/school-registrations' },
  { label: '学校管理员', disabled: true },
  { label: '挑战项目', disabled: true },
  { label: '平台运营', disabled: true }
];
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
