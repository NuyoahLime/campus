<template>
  <div class="wb-home">
    <h2>欢迎，{{ auth.user?.username }}</h2>
    <p class="wb-subtitle">学校管理员工作台</p>
    <el-row :gutter="16" class="feature-grid">
      <el-col v-for="item in features" :key="item.path" :span="8">
        <el-card shadow="hover" class="feature-card" @click="$router.push(item.path)">
          <div class="card-content">
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
  { path: '/school-admin/activities', label: '活动管理' },
  { path: '/school-admin/participants', label: '参赛名册' },
  { path: '/school-admin/projects', label: '项目配置' },
  { path: '/school-admin/teachers', label: '教师分配' },
  { path: '/school-admin/scores', label: '成绩管理' },
  { path: '/school-admin/rankings', label: '排名管理' },
];
</script>

<style scoped>
.wb-home h2 { margin-bottom: 8px; }
.wb-subtitle { color: #909399; margin-bottom: 32px; }
.feature-card { cursor: pointer; }
.card-content { text-align: center; padding: 20px 0; }
.card-content h4 { margin: 0 0 4px 0; }
.card-status { color: #c0c4cc; font-size: 13px; margin: 0; }
</style>
