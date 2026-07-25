<template>
  <div class="edit-page">
    <h2>修改活动申请</h2>
    <div v-if="invalidId"><el-result icon="error" title="申请地址无效"><template #extra><el-button @click="$router.push('/teacher/applications')">返回申请列表</el-button></template></el-result></div>
    <div v-else-if="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="loadError"><el-result icon="error" title="加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="loadData">重试</el-button></template></el-result></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="申请不存在或无权查看" /></div>
    <div v-else-if="!canEdit"><el-result icon="warning" title="无法编辑" :sub-title="`当前状态为 ${appStatusLabel(app?.status || '')}，仅草稿状态可编辑`"><template #extra><el-button type="primary" @click="$router.push('/teacher/applications/' + applicationId)">返回详情</el-button></template></el-result></div>
    <template v-else>
      <el-card>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="所属学校"><el-input :model-value="app?.schoolName || '学校名称暂不可用'" disabled /></el-form-item>
          <el-form-item label="活动名称" prop="title">
            <el-input v-model="form.title" placeholder="请输入活动名称" maxlength="200" show-word-limit @input="checkDirty" />
          </el-form-item>
          <el-form-item label="活动说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请输入活动说明（选填，可留空）" maxlength="2000" show-word-limit @input="checkDirty" />
          </el-form-item>
          <el-form-item>
            <el-alert v-if="saveError" :title="saveError" type="error" show-icon :closable="false" style="margin-bottom:12px" />
            <el-button type="primary" :loading="saving" :disabled="saving" @click="handleSave">保存草稿</el-button>
            <el-button @click="handleCancel">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { getMyApplication, updateDraft } from '@/api/teacher-application';
import { ApiError } from '@/api/http';
import { appStatusLabel } from '@/utils/application-status';
import { resolveUuidParam } from '@/utils/route-param';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessageBox } from 'element-plus';

const route = useRoute(); const router = useRouter();
const applicationId = resolveUuidParam(route.params.applicationId);
const invalidId = ref(applicationId === null);
const formRef = ref<FormInstance>();
const saving = ref(false); const loading = ref(true);
const notFound = ref(false); const loadError = ref<string|null>(null);
const saveError = ref<string|null>(null);
const app = ref<Awaited<ReturnType<typeof getMyApplication>> | null>(null);
const canEdit = ref(false);

const form = reactive({ title: '', description: '' });
let initialTitle = ''; let initialDescription = '';

const formDirty = computed(() =>
  form.title !== initialTitle || form.description !== initialDescription
);

const rules: FormRules = {
  title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }, { max: 200 }],
};

async function loadData() {
  if (invalidId.value) return;
  loading.value = true; notFound.value = false; loadError.value = null;
  try {
    app.value = await getMyApplication(applicationId!);
    if (app.value.status !== 'DRAFT') { canEdit.value = false; return; }
    canEdit.value = true;
    form.title = app.value.title;
    form.description = app.value.description || '';
    initialTitle = form.title;
    initialDescription = form.description;
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true;
    else loadError.value = e instanceof ApiError ? e.message : '加载失败';
  }
  finally { loading.value = false; }
}

function checkDirty() { /* computed handles this */ }

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true; saveError.value = null;
  try {
    await updateDraft(applicationId!, { title: form.title, description: form.description });
    initialTitle = form.title; initialDescription = form.description;
    router.push(`/teacher/applications/${applicationId}`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 400) saveError.value = '表单内容有误，请检查后重试';
    else if (e instanceof ApiError && e.status === 409) saveError.value = '当前申请状态不能修改';
    else saveError.value = e instanceof ApiError ? e.message : '保存失败，请重试';
  }
  finally { saving.value = false; }
}

async function handleCancel() {
  if (formDirty.value) {
    try { await ElMessageBox.confirm('有未保存的修改，确定离开吗？', '确认离开', { type: 'warning' }); }
    catch { return; }
  }
  router.push(`/teacher/applications/${applicationId}`);
}

onMounted(() => loadData());

onBeforeRouteLeave(() => { return formDirty.value ? '有未保存的修改，确定离开吗？' : true; });
</script>

<style scoped>
.edit-page { max-width: 640px; }
.edit-page h2 { margin-bottom: 20px; }
</style>
