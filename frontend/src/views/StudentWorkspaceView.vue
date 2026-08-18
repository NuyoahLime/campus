<script setup lang="ts">
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { RouterLink } from 'vue-router';
import { studentNavigation as navigation } from '../router/studentNavigation';
</script>

<template>
  <WorkspaceShell
    role-label="学生"
    workspace-title="学生个人工作台"
    page-title="欢迎进入学生个人工作台"
    description="查看当前学生身份，浏览公开活动并读取已经确认的个人成绩。"
    home-path="/student"
    :navigation="navigation"
  >
    <section class="student-overview-actions">
      <RouterLink class="student-overview-action" to="/activities"><strong>浏览公开活动</strong><span>查看当前开放的校园挑战活动。</span></RouterLink>
      <RouterLink class="student-overview-action" to="/student/scores"><strong>我的成绩</strong><span>查看已经确认的个人成绩记录。</span></RouterLink>
    </section>
  </WorkspaceShell>
</template>
