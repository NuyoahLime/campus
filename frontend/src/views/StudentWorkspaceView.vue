<script setup lang="ts">
import WorkspaceShell from '../components/WorkspaceShell.vue';
import type { WorkspaceNavigationItem } from '../types/workspace';

const navigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/student' },
  { label: '活动', disabled: true },
  { label: '我的成绩', disabled: true },
  { label: '排行榜', disabled: true },
  { label: '申诉', disabled: true },
  { label: '个人信息', disabled: true }
];
</script>

<template>
  <WorkspaceShell
    role-label="学生"
    workspace-title="学生个人工作台"
    page-title="欢迎进入学生个人工作台"
    description="查看当前学生身份，并从导航进入后续开放的校园挑战功能。"
    home-path="/student"
    :navigation="navigation"
  />
</template>
