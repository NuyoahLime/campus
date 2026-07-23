<template>
  <div class="workbench-layout">
    <el-container>
      <!-- Sidebar -->
      <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="wb-sidebar">
        <div class="sidebar-header">
          <span v-if="!sidebarCollapsed" class="sidebar-title">Campus Guinness</span>
          <el-button text @click="sidebarCollapsed = !sidebarCollapsed" class="collapse-btn">
            <el-icon><Expand v-if="sidebarCollapsed" /><Fold v-else /></el-icon>
          </el-button>
        </div>
        <el-menu
          :default-active="activeMenu"
          :collapse="sidebarCollapsed"
          router
          class="sidebar-menu"
        >
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- Main -->
      <el-container>
        <el-header class="wb-header" height="56px">
          <div class="header-left">
            <span class="role-badge">
              <el-tag :type="roleTagType" size="small">{{ roleLabel }}</el-tag>
            </span>
            <span class="user-name">{{ auth.user?.username }}</span>
          </div>
          <div class="header-right">
            <el-button text @click="goPublic">返回公共门户</el-button>
            <el-button text type="danger" @click="handleLogout">退出登录</el-button>
          </div>
        </el-header>

        <el-main class="wb-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { Expand, Fold } from '@element-plus/icons-vue';

const router = useRouter();
const auth = useAuthStore();

const sidebarCollapsed = ref(false);

const roleLabel = computed(() => {
  const r = auth.roles;
  if (r.includes('SUPER_ADMIN')) return '超级管理员';
  if (r.includes('SCHOOL_ADMIN')) return '学校管理员';
  if (r.includes('TEACHER')) return '教师';
  if (r.includes('STUDENT')) return '学生';
  return '用户';
});

const roleTagType = computed(() => {
  const r = auth.roles;
  if (r.includes('SUPER_ADMIN')) return 'danger';
  if (r.includes('SCHOOL_ADMIN')) return 'warning';
  if (r.includes('TEACHER')) return 'success';
  if (r.includes('STUDENT')) return '';
  return 'info';
});

const activeMenu = computed(() => router.currentRoute.value.path);

interface MenuItem {
  path: string;
  label: string;
  icon: string;
}

const menuItems = computed<MenuItem[]>(() => {
  const r = auth.roles;
  // Use string names that map to Element Plus icon components
  if (r.includes('SUPER_ADMIN')) return [
    { path: '/admin', label: '工作台首页', icon: 'HomeFilled' },
    { path: '/admin/projects', label: '项目管理', icon: 'Collection' },
    { path: '/admin/applications', label: '活动申请审核', icon: 'Check' },
    { path: '/admin/public-review', label: '活动公开审核', icon: 'View' },
    { path: '/admin/schools', label: '学校管理', icon: 'School' },
    { path: '/admin/operations', label: '平台运营', icon: 'Setting' },
  ];
  if (r.includes('SCHOOL_ADMIN')) return [
    { path: '/school-admin', label: '工作台首页', icon: 'HomeFilled' },
    { path: '/school-admin/activities', label: '活动管理', icon: 'Tickets' },
    { path: '/school-admin/participants', label: '参赛名册', icon: 'User' },
    { path: '/school-admin/projects', label: '项目配置', icon: 'Collection' },
    { path: '/school-admin/teachers', label: '教师分配', icon: 'Avatar' },
    { path: '/school-admin/scores', label: '成绩管理', icon: 'DataLine' },
    { path: '/school-admin/rankings', label: '排名管理', icon: 'Trophy' },
  ];
  if (r.includes('TEACHER')) return [
    { path: '/teacher', label: '工作台首页', icon: 'HomeFilled' },
    { path: '/teacher/projects', label: '项目资源库', icon: 'Collection' },
    { path: '/teacher/applications', label: '活动申请', icon: 'Document' },
    { path: '/teacher/responsible', label: '负责项目', icon: 'Star' },
    { path: '/teacher/scores', label: '成绩录入', icon: 'Edit' },
    { path: '/teacher/review', label: '成绩审核', icon: 'Check' },
    { path: '/teacher/appeals', label: '申诉处理', icon: 'Warning' },
  ];
  if (r.includes('STUDENT')) return [
    { path: '/student', label: '工作台首页', icon: 'HomeFilled' },
    { path: '/student/activities', label: '我的活动', icon: 'Tickets' },
    { path: '/student/projects', label: '我的参赛项目', icon: 'Collection' },
    { path: '/student/scores', label: '我的成绩', icon: 'DataLine' },
    { path: '/student/rankings', label: '我的排名', icon: 'Trophy' },
    { path: '/student/appeals', label: '我的申诉', icon: 'Warning' },
    { path: '/student/achievements', label: '我的成就', icon: 'Medal' },
  ];
  return [];
});

function goPublic() {
  router.push('/');
}

async function handleLogout() {
  await auth.logout();
  router.push('/');
}
</script>

<style scoped>
.workbench-layout {
  min-height: 100vh;
}

.wb-sidebar {
  background: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  color: #fff;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.collapse-btn {
  color: #fff;
}

.sidebar-menu {
  border-right: none;
}

.wb-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-weight: 500;
}

.wb-main {
  background: #f5f7fa;
  padding: 24px;
}
</style>
