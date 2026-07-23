<template>
  <div class="public-layout">
    <el-container>
      <el-header class="app-header" height="64px">
        <div class="header-content">
          <router-link to="/" class="logo">Campus Guinness</router-link>
          <el-menu
            :default-active="activeMenu"
            mode="horizontal"
            :ellipsis="false"
            class="nav-menu"
            router
          >
            <el-menu-item index="/projects">
              <span>项目资源库</span>
            </el-menu-item>
            <el-menu-item index="/activities">
              <span>校园活动</span>
            </el-menu-item>
          </el-menu>
          <div class="header-actions">
            <template v-if="auth.authenticated">
              <span class="header-username">{{ auth.user?.username }}</span>
              <el-button size="small" @click="goWorkspace">进入工作台</el-button>
              <el-button size="small" text @click="handleLogout">退出</el-button>
            </template>
            <el-button v-else type="primary" size="small" @click="$router.push('/login')">
              登录
            </el-button>
          </div>
        </div>
      </el-header>
      <el-main class="app-main">
        <slot />
      </el-main>
      <el-footer class="app-footer" height="60px">
        <div class="footer-content">
          <span>&copy; {{ currentYear }} Campus Guinness</span>
        </div>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const currentYear = new Date().getFullYear();

const activeMenu = computed(() => {
  const path = route.path;
  if (path.startsWith('/projects')) return '/projects';
  if (path.startsWith('/activities')) return '/activities';
  return path;
});

function goWorkspace() {
  const roles = auth.roles;
  const count = roles.filter((r) => ['STUDENT', 'TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(r)).length;
  if (count > 1) {
    router.push('/workspaces');
  } else {
    router.push(auth.defaultWorkspaceRoute());
  }
}

async function handleLogout() {
  await auth.logout();
}
</script>

<style scoped>
.public-layout {
  min-height: 100vh;
}

.app-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
}

.logo {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  text-decoration: none;
  margin-right: 40px;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

.header-actions {
  margin-left: auto;
}

.app-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
  min-height: calc(100vh - 124px);
}

.app-footer {
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
}

.footer-content {
  color: #909399;
  font-size: 13px;
}
</style>
