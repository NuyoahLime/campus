<template>
  <main class="verification-page">
    <el-card shadow="never" class="verification-card">
      <header>
        <h1>成就记录验真</h1>
        <p>输入 32 位验证码，核验成就记录及其当前状态。</p>
      </header>

      <el-form label-position="top" @submit.prevent="verify">
        <el-form-item label="验证码">
          <el-input
            v-model="inputCode"
            maxlength="32"
            clearable
            autocomplete="off"
            placeholder="请输入 32 位十六进制验证码"
            data-testid="verification-code-input"
            @input="sanitizeInput"
            @keyup.enter="verify"
          />
        </el-form-item>
        <div class="form-actions">
          <el-button
            type="primary"
            :loading="loading"
            :disabled="loading"
            data-testid="verify-achievement"
            @click="verify"
          >
            验证
          </el-button>
          <el-button data-testid="reset-verification" @click="reset">重置</el-button>
        </div>
      </el-form>

      <el-skeleton v-if="loading" :rows="7" animated />

      <el-result
        v-else-if="notFound"
        icon="error"
        title="未找到对应成就记录"
        sub-title="请检查验证码后重试"
      />

      <section v-else-if="result" class="verification-result">
        <el-alert
          v-if="result.status === 'ACTIVE'"
          title="成就记录有效"
          type="success"
          :closable="false"
          show-icon
        />
        <el-alert
          v-else
          title="成就记录已撤销"
          type="error"
          :closable="false"
          show-icon
        />

        <el-descriptions :column="2" border>
          <el-descriptions-item label="成就标题">{{ result.recordTitle }}</el-descriptions-item>
          <el-descriptions-item label="学校">{{ result.schoolName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ result.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ result.projectName }}</el-descriptions-item>
          <el-descriptions-item label="排名版本">
            V{{ result.rankingVersionNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="名次">第{{ result.rankPosition }}名</el-descriptions-item>
          <el-descriptions-item label="成绩">{{ result.scoreDisplayValue }}</el-descriptions-item>
          <el-descriptions-item label="签发时间">
            {{ formatTime(result.issuedAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="result.status === 'REVOKED'" label="撤销时间">
            {{ formatTime(result.revokedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </section>
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError } from '@/api/http';
import { verifyAchievementRecord } from '@/api/student-achievement';
import type { PublicAchievementVerification } from '@/types/student-achievement';

const route = useRoute();
const router = useRouter();
const inputCode = ref('');
const loading = ref(false);
const notFound = ref(false);
const result = ref<PublicAchievementVerification | null>(null);

function normalizedCode() {
  return inputCode.value.trim().toLowerCase();
}

function sanitizeInput() {
  inputCode.value = inputCode.value.trim().toLowerCase().replace(/[^0-9a-f]/g, '');
}

async function verify() {
  if (loading.value) return;
  const code = normalizedCode();
  inputCode.value = code;
  result.value = null;
  notFound.value = false;
  if (!/^[0-9a-f]{32}$/.test(code)) {
    notFound.value = true;
    return;
  }

  loading.value = true;
  try {
    result.value = await verifyAchievementRecord(code);
    if (route.params.verificationCode !== code) {
      await router.replace(`/achievements/verify/${code}`);
    }
  } catch (error) {
    notFound.value = error instanceof ApiError
      ? error.status === 404
      : true;
  } finally {
    loading.value = false;
  }
}

function reset() {
  inputCode.value = '';
  result.value = null;
  notFound.value = false;
  void router.replace('/achievements/verify');
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

onMounted(() => {
  const routeCode = String(route.params.verificationCode ?? '');
  if (routeCode) {
    inputCode.value = routeCode.trim().toLowerCase();
    void verify();
  }
});
</script>

<style scoped>
.verification-page {
  min-height: calc(100vh - 80px);
  display: grid;
  place-items: start center;
  padding: 48px 18px;
  background: var(--el-fill-color-lighter);
}
.verification-card {
  width: min(760px, 100%);
}
header {
  margin-bottom: 24px;
}
header h1 {
  margin: 0;
  font-size: 28px;
}
header p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}
.form-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 24px;
}
.verification-result {
  display: grid;
  gap: 18px;
}
@media (max-width: 640px) {
  .verification-page {
    padding: 20px 12px;
  }
  :deep(.el-descriptions__body) {
    overflow-x: auto;
  }
}
</style>
