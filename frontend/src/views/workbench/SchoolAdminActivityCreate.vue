<template>
  <div class="create"><h2>创建活动</h2>
    <el-card><el-form ref="fRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
      <el-form-item label="活动名称" prop="title"><el-input v-model="form.title" placeholder="请输入活动名称" maxlength="200" show-word-limit /></el-form-item>
      <el-form-item label="活动说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="4" placeholder="选填" maxlength="2000" show-word-limit /></el-form-item>
      <el-row :gutter="16"><el-col :span="12"><el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col><el-col :span="12"><el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col></el-row>
      <el-form-item label="地点" prop="location"><el-input v-model="form.location" placeholder="选填" /></el-form-item>
      <el-form-item>
        <el-alert v-if="submitErr" :title="submitErr" type="error" show-icon :closable="false" style="margin-bottom:12px" />
        <el-button native-type="submit" type="primary" :loading="submitting" :disabled="submitting">创建</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form></el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'; import { useRouter } from 'vue-router';
import { createActivity } from '@/api/school-admin-activity'; import { ApiError } from '@/api/http'; import { toISO } from '@/types/school-admin-activity';
import type { FormInstance, FormRules } from 'element-plus';

const router=useRouter(); const fRef=ref<FormInstance>(); const submitting=ref(false); const submitErr=ref<string|null>(null);
const form=reactive({title:'',description:'',startTime:'',endTime:'',location:''});
const rules:FormRules={title:[{required:true,message:'请输入活动名称'},{max:200}],startTime:[],endTime:[]};

async function handleSubmit() {
  if (submitting.value) return; submitting.value=true; submitErr.value=null;
  const valid = await fRef.value?.validate().catch(() => false); if (!valid) { submitting.value=false; return; }
  try { const r = await createActivity({title:form.title,description:form.description||undefined,startTime:toISO(form.startTime),endTime:toISO(form.endTime),location:form.location||undefined}); router.replace(`/school-admin/activities/${r.activityId}`); }
  catch(e) { submitErr.value = e instanceof ApiError ? e.message : '创建失败'; }
  finally { submitting.value = false; }
}
</script>

<style scoped>.create{max-width:640px}.create h2{margin-bottom:20px}</style>
