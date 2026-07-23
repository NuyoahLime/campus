<template>
  <div class="wb-home">
    <h2>欢迎，{{ auth.user?.username }}</h2>
    <p class="wb-subtitle">学生工作台 — 管理你的校园吉尼斯活动</p>
    <el-row :gutter="16" class="feature-grid">
      <el-col v-for="item in features" :key="item.path" :span="8">
        <el-card shadow="hover" class="feature-card" @click="$router.push(item.path)">
          <div class="card-content">
            <el-icon :size="32"><component :is="item.icon" /></el-icon>
            <h4>{{ item.label }}</h4>
            <p class="card-status">功能开发中</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
const auth = useAuthStore();
const features = [
  { path: '/student/activities', label: '我的活动', icon: 'Tickets' },
  { path: '/student/projects', label: '我的参赛项目', icon: 'Collection' },
  { path: '/student/scores', label: '我的成绩', icon: 'DataLine' },
  { path: '/student/rankings', label: '我的排名', icon: 'Trophy' },
  { path: '/student/appeals', label: '我的申诉', icon: 'Warning' },
  { path: '/student/achievements', label: '我的成就', icon: 'Medal' },
];
</script>

<style scoped>
.wb-home h2 { margin-bottom: 8px; }
.wb-subtitle { color: #909399; margin-bottom: 32px; }
.feature-card { cursor: pointer; }
.card-content { text-align: center; padding: 20px 0; }
.card-content h4 { margin: 12px 0 4px 0; }
.card-status { color: #c0c4cc; font-size: 13px; margin: 0; }
</style>
