<template>
  <div class="edit-page">
    <h2>修改活动申请</h2>
    <div v-if="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="申请不存在或无权查看" /></div>
    <div v-else-if="!canEdit"><el-result icon="warning" title="无法编辑" :sub-title="`当前状态为 ${appStatusLabel(app?.status || '')}，仅草稿状态可编辑`"><template #extra><el-button type="primary" @click="$router.push(`/teacher/applications/${applicationId}`)">返回详情</el-button></template></el-result></div>
    <template v-else>
      <el-card>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="所属学校">
            <el-input :model-value="app?.schoolName || app?.schoolId" disabled />
          </el-form-item>
          <el-form-item label="活动名称" prop="title">
            <el-input v-model="form.title" placeholder="请输入活动名称" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="活动说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请输入活动说明（选填，可留空）" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" :disabled="saving" @click="handleSave">保存草稿</el-button>
            <el-button @click="$router.push(`/teacher/applications/${applicationId}`)">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { getMyApplication, updateDraft } from '@/api/teacher-application';
import { ApiError } from '@/api/http';
import { appStatusLabel } from '@/utils/application-status';
import type { FormInstance, FormRules } from 'element-plus';

const route = useRoute(); const router = useRouter();
const applicationId = route.params.applicationId as string;
const formRef = ref<FormInstance>(); const saving = ref(false);
const loading = ref(true); const notFound = ref(false);
const app = ref<Awaited<ReturnType<typeof getMyApplication>> | null>(null);
const canEdit = ref(false);
const formDirty = ref(false);

const form = reactive({ title: '', description: '' });
const rules: FormRules = {
  title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }, { max: 200 }],
};

async function load() {
  loading.value = true; notFound.value = false;
  try {
    app.value = await getMyApplication(applicationId);
    if (app.value.status !== 'DRAFT') { canEdit.value = false; return; }
    canEdit.value = true;
    form.title = app.value.title;
    form.description = app.value.description || '';
  } catch (e) { if (e instanceof ApiError && e.status === 404) notFound.value = true; }
  finally { loading.value = false; }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    await updateDraft(applicationId, { title: form.title, description: form.description });
    formDirty.value = false;
    router.push(`/teacher/applications/${applicationId}`);
  } catch { /* keep form */ }
  finally { saving.value = false; }
}

onMounted(() => load());

onBeforeRouteLeave(() => { return formDirty.value ? '有未保存的修改，确定离开吗？' : true; });
</script>

<style scoped>
.edit-page { max-width: 640px; }
.edit-page h2 { margin-bottom: 20px; }
</style>
