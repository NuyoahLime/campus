<template>
  <div class="ad"><el-page-header @back="$router.push('/admin/applications')" title="返回审核列表" />
    <div v-if="invalidId"><el-result icon="error" title="地址无效"><template #extra><el-button @click="$router.push('/admin/applications')">返回列表</el-button></template></el-result></div>
    <div v-else-if="loading"><el-skeleton :rows="8" animated /></div>
    <div v-else-if="loadErr"><el-result icon="error" title="加载失败" :sub-title="loadErr"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="申请不存在" /></div>
    <template v-else-if="app">
      <h1>{{ app.title }}</h1>
      <el-tag :type="appStatusTagType(app.status)" size="large">{{ appStatusLabel(app.status) }}</el-tag>
      <el-divider />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学校">{{ app.schoolName || app.schoolId }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ app.applicantName || app.applicantUserId }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ app.applicationVersion }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmt(app.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ fmt(app.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="app.reviewedAt" label="审核时间">{{ fmt(app.reviewedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="app.createdActivityId" label="已创建活动">{{ app.createdActivityId }}</el-descriptions-item>
      </el-descriptions>
      <section class="sec"><h3>活动说明</h3><p class="text">{{ app.description || '暂无说明' }}</p></section>
      <section v-if="app.reviewComment || app.rejectReason" class="sec"><h3>审核信息</h3>
        <el-alert v-if="app.reviewComment" title="审核意见" :description="app.reviewComment" type="success" :closable="false" />
        <el-alert v-if="app.rejectReason" title="驳回原因" :description="app.rejectReason" type="error" :closable="false" class="mt8" />
      </section>
      <div v-if="app.status==='SUBMITTED'" class="actions">
        <el-alert v-if="actionErr" :title="actionErr" type="error" show-icon :closable="false" style="margin-bottom:12px" />
        <el-button type="success" :loading="approving" :disabled="approving" @click="handleApprove">批准</el-button>
        <el-button type="danger" :loading="rejecting" :disabled="rejecting" @click="showRejectDialog=true">驳回</el-button>
      </div>

      <!-- Reject Dialog -->
      <el-dialog v-model="showRejectDialog" title="驳回申请" width="500px">
        <p>活动：<strong>{{ app.title }}</strong></p><p>学校：{{ app.schoolName || app.schoolId }}</p>
        <el-input v-model="rejectReason" type="textarea" :rows="4" placeholder="请输入驳回原因" maxlength="500" show-word-limit />
        <template #footer><el-button @click="showRejectDialog=false">取消</el-button><el-button type="danger" :loading="rejecting" :disabled="rejecting||!rejectReason.trim()" @click="handleReject">确认驳回</el-button></template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { fetchAdminApplicationById, approveApplication, rejectApplication } from '@/api/admin-application';
import { ApiError } from '@/api/http';
import { appStatusLabel, appStatusTagType } from '@/utils/application-status';
import { resolveUuidParam } from '@/utils/route-param';
import { ElMessageBox } from 'element-plus';
import type { AdminApplicationDetail } from '@/types/admin-application';

const route=useRoute();
const appId=resolveUuidParam(route.params.applicationId);
const invalidId=appId===null;
const app=ref<AdminApplicationDetail|null>(null);
const loading=ref(true);const notFound=ref(false);const loadErr=ref<string|null>(null);
const approving=ref(false);const rejecting=ref(false);const actionErr=ref<string|null>(null);
const showRejectDialog=ref(false);const rejectReason=ref('');

async function load(){
  if(invalidId){loading.value=false;return}loading.value=true;notFound.value=false;loadErr.value=null;
  try{app.value=await fetchAdminApplicationById(appId!)}catch(e){if(e instanceof ApiError&&e.status===404)notFound.value=true;else loadErr.value=e instanceof ApiError?e.message:'加载失败'}finally{loading.value=false}
}

async function handleApprove(){
  try{await ElMessageBox.confirm('批准后将创建正式活动，申请不能再修改。确认批准？','确认批准',{type:'warning'})}catch{return}
  approving.value=true;actionErr.value=null;
  try{app.value=await approveApplication(appId!)}catch(e){actionErr.value=e instanceof ApiError?e.message:'批准失败'}finally{approving.value=false}
}

async function handleReject(){
  if(!rejectReason.value.trim())return;
  rejecting.value=true;actionErr.value=null;
  try{app.value=await rejectApplication(appId!,rejectReason.value.trim());showRejectDialog.value=false}catch(e){actionErr.value=e instanceof ApiError?e.message:'驳回失败'}finally{rejecting.value=false}
}

function fmt(iso:string|null){return iso?new Date(iso).toLocaleString('zh-CN'):'-'}
onMounted(()=>load());
</script>

<style scoped>.ad h1{font-size:24px;margin:16px 0 12px}.actions{margin-top:24px;display:flex;gap:12px}.sec{margin-top:20px}.sec h3{font-size:16px;font-weight:600;margin-bottom:8px}.text{color:#606266;line-height:1.8;white-space:pre-wrap}.mt8{margin-top:8px}</style>
