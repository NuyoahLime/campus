<script setup lang="ts">
import WorkspaceShell from '../components/WorkspaceShell.vue';
import type { WorkspaceNavigationItem } from '../types/workspace';

const navigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/school-admin' },
  { label: '学生身份审核', to: '/school-admin/student-applications' },
  { label: '学生管理', disabled: true },
  { label: '活动管理', disabled: true },
  { label: '成绩管理', disabled: true },
  { label: '排行榜与成果', disabled: true }
];
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
