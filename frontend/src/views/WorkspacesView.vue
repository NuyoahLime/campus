<template>
  <PublicLayout>
    <div class="workspaces-page">
      <h2>选择工作台</h2>
      <p class="subtitle">你拥有多个角色，请选择要进入的工作台：</p>
      <el-row :gutter="16" class="workspace-grid">
        <el-col v-for="ws in workspaces" :key="ws.path" :span="6">
          <el-card shadow="hover" class="ws-card" @click="$router.push(ws.path)">
            <h3>{{ ws.label }}</h3>
            <p>{{ ws.desc }}</p>
            <el-button type="primary" size="small">进入</el-button>
          </el-card>
        </el-col>
      </el-row>
      <div class="back-link">
        <el-button text @click="$router.push('/')">返回首页</el-button>
      </div>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '@/stores/auth';
import PublicLayout from '@/layouts/PublicLayout.vue';

const auth = useAuthStore();

interface WsItem { path: string; label: string; desc: string }

const workspaces = computed<WsItem[]>(() => {
  const items: WsItem[] = [];
  const r = auth.roles;
  if (r.includes('SUPER_ADMIN')) items.push({ path: '/admin', label: '超级管理员', desc: '平台管理' });
  if (r.includes('SCHOOL_ADMIN')) items.push({ path: '/school-admin', label: '学校管理员', desc: '学校管理' });
  if (r.includes('TEACHER')) items.push({ path: '/teacher', label: '教师工作台', desc: '教学与成绩' });
  if (r.includes('STUDENT')) items.push({ path: '/student', label: '学生工作台', desc: '我的活动' });
  return items;
});
</script>

<style scoped>
.workspaces-page { padding: 40px 0; text-align: center; }
.subtitle { color: #909399; margin-bottom: 32px; }
.ws-card { cursor: pointer; text-align: center; }
.ws-card h3 { margin-bottom: 8px; }
.ws-card p { color: #909399; margin-bottom: 16px; }
.back-link { margin-top: 32px; }
</style>
