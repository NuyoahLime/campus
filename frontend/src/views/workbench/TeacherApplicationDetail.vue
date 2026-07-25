<template>
  <div class="detail-page">
    <el-page-header @back="$router.push('/teacher/applications')" title="返回申请列表" />
    <div v-if="loading"><el-skeleton :rows="6" animated /></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="申请不存在或无权查看" /></div>
    <template v-else-if="app">
      <h1>{{ app.title }}</h1>
      <el-tag :type="appStatusTagType(app.status)" size="large">{{ appStatusLabel(app.status) }}</el-tag>
      <el-divider />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请ID">{{ app.applicationId }}</el-descriptions-item>
        <el-descriptions-item label="所属学校">{{ app.schoolName || app.schoolId }}</el-descriptions-item>
        <el-descriptions-item label="申请版本">v{{ app.applicationVersion }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmt(app.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ fmt(app.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="app.reviewedAt" label="审核时间">{{ fmt(app.reviewedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="app.createdActivityId" label="生成的活动">{{ app.createdActivityId }}</el-descriptions-item>
      </el-descriptions>
      <section class="desc-section">
        <h3>活动说明</h3>
        <p class="text">{{ app.description || '暂无说明' }}</p>
      </section>
      <section v-if="app.reviewComment || app.rejectReason" class="review-section">
        <h3>审核信息</h3>
        <el-alert v-if="app.reviewComment" title="审核意见" :description="app.reviewComment" type="success" show-icon :closable="false" />
        <el-alert v-if="app.rejectReason" title="驳回原因" :description="app.rejectReason" type="error" show-icon :closable="false" class="mt-8" />
      </section>
      <div class="actions">
        <template v-if="app.status === 'DRAFT'">
          <el-button type="primary" @click="$router.push(`/teacher/applications/${app.applicationId}/edit`)">编辑</el-button>
          <el-button type="success" @click="handleResubmit">提交审核</el-button>
        </template>
        <template v-else-if="app.status === 'SUBMITTED'">
          <el-button type="warning" @click="handleWithdraw">撤回申请</el-button>
        </template>
        <template v-else-if="app.status === 'REJECTED'">
          <el-button type="primary" @click="handleRevise">修改并重新提交</el-button>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getMyApplication, withdrawApplication, returnToDraft, resubmitApplication } from '@/api/teacher-application';
import { ApiError } from '@/api/http';
import { appStatusLabel, appStatusTagType } from '@/utils/application-status';
import { ElMessageBox } from 'element-plus';
import type { TeacherActivityApplicationItem } from '@/types/teacher-application';

const route = useRoute(); const router = useRouter();
const app = ref<TeacherActivityApplicationItem | null>(null);
const loading = ref(true); const notFound = ref(false);

async function load() {
  loading.value = true; notFound.value = false;
  try { app.value = await getMyApplication(route.params.applicationId as string); }
  catch (e) { if (e instanceof ApiError && e.status === 404) notFound.value = true; }
  finally { loading.value = false; }
}

async function handleWithdraw() {
  try { await ElMessageBox.confirm('撤回后该申请将结束，不能再次提交。', '确认撤回', { type: 'warning' }); } catch { return; }
  try { app.value = await withdrawApplication(route.params.applicationId as string); } catch { /* keep state */ }
}

async function handleRevise() {
  try { await ElMessageBox.confirm('将退回草稿状态，修改后可重新提交。', '确认', { type: 'info' }); } catch { return; }
  try { app.value = await returnToDraft(route.params.applicationId as string); router.push(`/teacher/applications/${app.value!.applicationId}/edit`); } catch { /* keep state */ }
}

async function handleResubmit() {
  try { await ElMessageBox.confirm('确认提交审核？', '确认', { type: 'warning' }); } catch { return; }
  try { app.value = await resubmitApplication(route.params.applicationId as string); } catch { /* keep state */ }
}

function fmt(iso: string | null) { return iso ? new Date(iso).toLocaleString('zh-CN') : '-'; }

onMounted(() => load());
</script>

<style scoped>
.detail-page h1 { font-size: 24px; margin: 16px 0 12px; }
.actions { margin-top: 24px; display: flex; gap: 12px; }
.desc-section, .review-section { margin-top: 20px; }
.desc-section h3, .review-section h3 { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.text { color: #606266; line-height: 1.8; white-space: pre-wrap; }
.mt-8 { margin-top: 8px; }
</style>
