<template>
  <div class="page-container">
    <el-page-header @back="$router.push('/student/scores')" title="返回成绩列表" />
    <div v-if="loading"><el-skeleton :rows="6" animated /></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="成绩记录不存在" /></div>
    <template v-else-if="detail">
      <h1>{{ detail.projectName }} <span class="attempt-num">#{{ detail.attemptNumber }}</span></h1>
      <el-tag :type="scoreTag(detail.status)">{{ scoreLabel(detail.status) }}</el-tag>
      <el-divider />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="成绩值">{{ detail.scoreDisplay }}</el-descriptions-item>
        <el-descriptions-item label="成绩类型">{{ scoreTypeLabel(detail.scoreStorageType) }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
        <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
        <el-descriptions-item label="尝试次数">第 {{ detail.attemptNumber }} 次</el-descriptions-item>
        <el-descriptions-item label="是否当前有效">{{ detail.isCurrentEffective ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="业务时间">{{ fmt(detail.scoreBusinessTime) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ fmt(detail.submittedAt) }}</el-descriptions-item>
        <el-descriptions-item label="时间来源">{{ detail.timeSource || '未指定' }}</el-descriptions-item>
        <el-descriptions-item label="录入者">{{ detail.enteredByDisplayName || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" v-if="detail.reviewComment">{{ detail.reviewComment }}</el-descriptions-item>
        <el-descriptions-item label="驳回原因" v-if="detail.rejectReason">{{ detail.rejectReason }}</el-descriptions-item>
        <el-descriptions-item label="审核时间" v-if="detail.reviewedAt">{{ fmt(detail.reviewedAt) }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions"><el-button @click="$router.push(`/student/projects/${detail.activityProjectId}`)">查看项目</el-button></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { fetchMyScoreById } from '@/api/student-score';
import { ApiError } from '@/api/http';
import type { StudentScoreDetail } from '@/types/student-score';

const route = useRoute();
const detail = ref<StudentScoreDetail | null>(null);
const loading = ref(true);
const notFound = ref(false);

async function load() {
  loading.value = true; notFound.value = false;
  try { detail.value = await fetchMyScoreById(route.params.attemptId as string); }
  catch (e) { if (e instanceof ApiError && e.status===404) notFound.value = true; }
  finally { loading.value = false; }
}
function scoreTypeLabel(t: string) { const m: Record<string,string>={INTEGER:'整数',DECIMAL:'小数',DURATION:'时长',GRADE:'等级'}; return m[t]||t; }
function scoreLabel(s: string) { const m: Record<string,string>={DRAFT:'草稿',PENDING_REVIEW:'待审核',APPROVED:'已通过',REJECTED:'已驳回',INVALIDATED:'已失效'}; return m[s]||s; }
function scoreTag(s: string) { const m: Record<string,string>={APPROVED:'success',PENDING_REVIEW:'warning',REJECTED:'danger'}; return (m[s]||'info') as 'success'|'warning'|'danger'|'info'; }
function fmt(iso: string|null) { if(!iso) return '-'; return new Date(iso).toLocaleString('zh-CN'); }

onMounted(() => load());
</script>

<style scoped>
.page-container h1 { font-size: 24px; margin: 16px 0 12px; }
.attempt-num { color: #909399; font-size: 18px; }
.actions { margin-top: 24px; }
</style>
