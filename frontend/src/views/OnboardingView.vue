<template>
  <PublicLayout>
    <div class="onboarding-page">
      <el-card class="onboarding-card" shadow="always">
        <h2>完善账号身份</h2>
        <p class="intro">
          {{ auth.user?.username }}，账号已经创建，但尚未获得学校身份。请选择下一步。
        </p>

        <div class="paths" aria-label="身份申请入口">
          <el-card class="path-card" shadow="never">
            <h3>申请加入学校</h3>
            <p>适用于学生账号。下一阶段开放申请提交。</p>
            <el-button type="primary" disabled>下一阶段开放</el-button>
          </el-card>

          <el-card class="path-card" shadow="never">
            <h3>申请学校入驻</h3>
            <p>适用于学校管理员入驻申请。下一阶段开放申请提交。</p>
            <el-button type="primary" disabled>下一阶段开放</el-button>
          </el-card>
        </div>

        <div class="actions">
          <el-button @click="handleLogout">退出登录</el-button>
        </div>
      </el-card>
    </div>
  </PublicLayout>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import PublicLayout from '@/layouts/PublicLayout.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const router = useRouter();

async function handleLogout() {
  await auth.logout();
  await router.push('/login');
}
</script>

<style scoped>
.onboarding-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.onboarding-card {
  width: 100%;
  max-width: 760px;
}

.onboarding-card h2 {
  text-align: center;
  margin-bottom: 12px;
}

.intro {
  color: #606266;
  line-height: 1.7;
  text-align: center;
}

.paths {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 24px 0;
}

.path-card h3 {
  margin-top: 0;
}

.path-card p {
  color: #606266;
  min-height: 44px;
}

.actions {
  text-align: center;
}

@media (max-width: 720px) {
  .paths {
    grid-template-columns: 1fr;
  }
}
</style>
