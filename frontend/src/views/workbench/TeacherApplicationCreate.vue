<template>
  <div class="create-page">
    <h2>新建活动申请</h2>
    <el-card>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="所属学校" prop="schoolId">
          <el-select v-model="form.schoolId" placeholder="请选择学校" style="width:100%">
            <el-option v-for="m in teacherSchools" :key="m.schoolId" :label="m.schoolId" :value="m.schoolId" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动名称" prop="title">
          <el-input v-model="form.title" placeholder="请输入活动名称" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="活动说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请输入活动说明（选填）" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" :disabled="submitting" @click="handleSubmit">提交审核</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { createApplication } from '@/api/teacher-application';
import { ApiError } from '@/api/http';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessageBox } from 'element-plus';

const router = useRouter();
const auth = useAuthStore();
const formRef = ref<FormInstance>();
const submitting = ref(false);

const teacherSchools = computed(() =>
  auth.schoolMemberships.filter((m) => m.roleInSchool === 'TEACHER'),
);

const form = reactive({ schoolId: '', title: '', description: '' });
const rules: FormRules = {
  schoolId: [{ required: true, message: '请选择学校', trigger: 'blur' }],
  title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }, { max: 200, message: '最多200字', trigger: 'blur' }],
};

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  try {
    await ElMessageBox.confirm('提交后将进入平台审核，审核期间不能修改。', '确认提交', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' });
  } catch { return; }
  submitting.value = true;
  try {
    const r = await createApplication({ schoolId: form.schoolId, title: form.title, description: form.description || undefined });
    router.replace(`/teacher/applications/${r.applicationId}`);
  } catch (e) {
    if (e instanceof ApiError) { /* keep form data */ return; }
  } finally { submitting.value = false; }
}

// Auto-select single school
if (teacherSchools.value.length === 1) {
  form.schoolId = teacherSchools.value[0].schoolId;
}
</script>

<style scoped>
.create-page { max-width: 640px; }
.create-page h2 { margin-bottom: 20px; }
</style>
