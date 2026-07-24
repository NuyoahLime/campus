<template>
  <div class="swb-layout">
    <!-- Desktop sidebar -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="swb-sidebar desktop-only">
      <div class="sidebar-header">
        <span v-if="!collapsed" class="sidebar-logo">Campus Guinness</span>
        <el-button text @click="collapsed = !collapsed"><el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon></el-button>
      </div>
      <el-menu :default-active="activeMenu" :collapse="collapsed" router class="sidebar-menu" @select="closeMobile">
        <el-menu-item index="/student"><el-icon><HomeFilled /></el-icon><span>首页</span></el-menu-item>
        <el-menu-item index="/student/activities"><el-icon><Tickets /></el-icon><span>我的活动</span></el-menu-item>
        <el-menu-item index="/student/projects"><el-icon><Collection /></el-icon><span>参赛项目</span></el-menu-item>
        <el-menu-item index="/student/scores"><el-icon><DataLine /></el-icon><span>我的成绩</span></el-menu-item>
        <el-menu-item index="/student/rankings"><el-icon><Trophy /></el-icon><span>排名</span></el-menu-item>
        <el-menu-item index="/student/appeals"><el-icon><Warning /></el-icon><span>申诉</span></el-menu-item>
        <el-menu-item index="/student/achievements"><el-icon><Medal /></el-icon><span>成就</span></el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Mobile drawer -->
    <el-drawer v-model="mobileOpen" direction="ltr" size="260px" :with-header="false">
      <el-menu :default-active="activeMenu" router class="mobile-menu" @select="mobileOpen = false">
        <el-menu-item index="/student"><el-icon><HomeFilled /></el-icon><span>首页</span></el-menu-item>
        <el-menu-item index="/student/activities"><el-icon><Tickets /></el-icon><span>我的活动</span></el-menu-item>
        <el-menu-item index="/student/projects"><el-icon><Collection /></el-icon><span>参赛项目</span></el-menu-item>
        <el-menu-item index="/student/scores"><el-icon><DataLine /></el-icon><span>我的成绩</span></el-menu-item>
        <el-menu-item index="/student/rankings"><el-icon><Trophy /></el-icon><span>排名</span></el-menu-item>
        <el-menu-item index="/student/appeals"><el-icon><Warning /></el-icon><span>申诉</span></el-menu-item>
        <el-menu-item index="/student/achievements"><el-icon><Medal /></el-icon><span>成就</span></el-menu-item>
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="swb-header" height="56px">
        <div class="header-left">
          <el-button class="mobile-only" text @click="mobileOpen = true"><el-icon><Menu /></el-icon></el-button>
          <el-tag>学生</el-tag>
          <span class="user-name">{{ auth.user?.username }}</span>
        </div>
        <div class="header-right">
          <el-button text @click="$router.push('/')">门户</el-button>
          <el-button text type="danger" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="swb-main"><router-view /></el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
const router = useRouter(); const route = useRoute(); const auth = useAuthStore();
const collapsed = ref(false); const mobileOpen = ref(false);
const activeMenu = computed(() => route.path);
function closeMobile() { mobileOpen.value = false; }
async function handleLogout() { await auth.logout(); router.push('/'); }
</script>

<style scoped>
.swb-layout { min-height: 100vh; display: flex; }
.swb-sidebar { background: #ffffff; border-right: 1px solid #e8ecf1; flex-shrink: 0; }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; padding: 16px; color: #303133; }
.sidebar-logo { font-size: 16px; font-weight: 700; white-space: nowrap; color: #409eff; }
.sidebar-menu, .mobile-menu { border-right: none; }
.sidebar-menu .el-menu-item, .mobile-menu .el-menu-item { color: #606266; }
.sidebar-menu .el-menu-item.is-active, .mobile-menu .el-menu-item.is-active { color: #409eff; background: #ecf5ff; }
.swb-header { background: #fff; border-bottom: 1px solid #e8ecf1; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; flex-shrink: 0; }
.header-left { display: flex; align-items: center; gap: 8px; }
.user-name { font-weight: 500; font-size: 14px; }
.swb-main { background: #eef4fb; padding: 20px; flex: 1; min-width: 0; }

.desktop-only { display: block; }
.mobile-only { display: none; }
@media (max-width: 767px) {
  .desktop-only { display: none; }
  .mobile-only { display: inline-flex; }
  .swb-main { padding: 12px; }
}
</style>
