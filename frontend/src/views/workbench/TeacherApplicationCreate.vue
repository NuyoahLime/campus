<template>
  <div class="create-page">
    <h2>新建活动申请</h2>
    <el-card>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="所属学校" prop="schoolId">
          <div v-if="schoolsLoading"><el-skeleton :rows="1" /></div>
          <div v-else-if="schoolsError">{{ schoolsError }} <el-button size="small" @click="onMounted">重试</el-button></div>
          <div v-else-if="schools.length===0">当前账号没有可提交申请的教师学校身份。</div>
          <el-select v-else v-model="form.schoolId" placeholder="请选择学校" style="width:100%">
            <el-option v-for="s in schools" :key="s.schoolId" :label="s.schoolName || '学校名称暂不可用'" :value="s.schoolId" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动名称" prop="title">
          <el-input v-model="form.title" placeholder="请输入活动名称" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="活动说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请输入活动说明（选填）" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-alert v-if="submitError" :title="submitError" type="error" show-icon :closable="false" style="margin-bottom:12px" />
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit()" @click="handleSubmit">提交审核</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { createApplication, fetchTeacherSchools, type TeacherSchoolItem } from '@/api/teacher-application';
import { ApiError } from '@/api/http';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessageBox } from 'element-plus';

const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const schools = ref<TeacherSchoolItem[]>([]);
const schoolsLoading = ref(true);
const schoolsError = ref<string | null>(null);
const submitError = ref<string | null>(null);

const form = reactive({ schoolId: '', title: '', description: '' });
const rules: FormRules = {
  schoolId: [{ required: true, message: '请选择学校', trigger: 'blur' }],
  title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }, { max: 200, message: '最多200字', trigger: 'blur' }],
};

async function loadSchools() {
  schoolsLoading.value = true; schoolsError.value = null;
  try {
    schools.value = await fetchTeacherSchools();
    if (schools.value.length === 1) form.schoolId = schools.value[0].schoolId;
    else if (!schools.value.some(s => s.schoolId === form.schoolId)) form.schoolId = '';
  } catch { schoolsError.value = '加载学校列表失败，请重试'; }
  finally { schoolsLoading.value = false; }
}

function canSubmit() { return !schoolsLoading.value && schools.value.length > 0 && !submitting.value; }

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!schools.value.some(s => s.schoolId === form.schoolId)) { submitError.value = '选择的学校无效，请重新选择'; return; }
  try {
    await ElMessageBox.confirm('提交后将进入平台审核，审核期间不能修改。', '确认提交', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' });
  } catch { return; }
  submitting.value = true; submitError.value = null;
  try {
    const r = await createApplication({ schoolId: form.schoolId, title: form.title, description: form.description || undefined });
    router.replace(`/teacher/applications/${r.applicationId}`);
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 403) router.push('/forbidden');
      else if (e.status === 400) submitError.value = '表单内容有误，请检查后重试';
      else submitError.value = e.message;
    } else { submitError.value = '提交失败，请重试'; }
  } finally { submitting.value = false; }
}

onMounted(() => loadSchools());
</script>

<style scoped>
.create-page { max-width: 640px; }
.create-page h2 { margin-bottom: 20px; }
</style>
