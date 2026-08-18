<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { createManagedActivity, getManagedActivity, updateManagedActivity, publishManagedActivity, cancelManagedActivity } from '../api/activityManagement';
import { listPublicProjects } from '../api/challengeProjects';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { ChallengeProjectListItem } from '../types/challengeProject';
import type { ActivityManagementDetail, ActivityManagementForm } from '../types/activityManagement';
import { labelForActivityExecution, labelForActivityPublic } from '../utils/activityManagementLabels';
import { labelForCategory } from '../utils/challengeProjectLabels';

const route = useRoute();
const router = useRouter();
const isNew = computed(() => route.name === 'school-admin-activity-new' || route.params.id === 'new');
const isEditing = computed(() => isNew.value || route.name === 'school-admin-activity-edit');
const detail = ref<ActivityManagementDetail | null>(null);
const projects = ref<ChallengeProjectListItem[]>([]);
const loading = ref(!isNew.value);
const saving = ref(false);
const error = ref('');
const message = ref('');
const form = ref<ActivityManagementForm>({ projectId: '', title: '', description: '', startTime: '', endTime: '', location: '' });

function localTime(value: string | null) {
  if (!value) return '';
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function fill(value: ActivityManagementDetail) {
  detail.value = value;
  form.value = { projectId: value.projects[0]?.projectId || '', title: value.title, description: value.description || '', startTime: localTime(value.startTime), endTime: localTime(value.endTime), location: value.location || '' };
}

async function load() {
  loading.value = true; error.value = '';
  try {
    const projectPage = await listPublicProjects(0, 100);
    projects.value = projectPage.items.filter(item => item.projectStatus === 'PUBLISHED');
    if (!isNew.value) fill(await getManagedActivity(String(route.params.id)));
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有本校活动管理权限。' : '活动详情加载失败，请稍后重试。';
  } finally { loading.value = false; }
}

async function save() {
  if (!form.value.projectId && isNew.value) { error.value = '请选择一个可用的挑战项目。'; return; }
  saving.value = true; error.value = ''; message.value = '';
  try {
    if (isNew.value) {
      const created = await createManagedActivity(form.value);
      await router.push(`/school-admin/activities/${created.id}`);
    } else {
      await updateManagedActivity(String(route.params.id), form.value);
      message.value = '活动资料已保存。'; await load();
    }
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 409 ? '活动当前状态或学校状态不允许此操作。' : '活动保存失败，请检查表单内容。';
  } finally { saving.value = false; }
}

async function publish() { await action(publishManagedActivity, '活动已发布。'); }
async function cancel() { await action(cancelManagedActivity, '活动已取消。'); }
async function action(fn: (id: string) => Promise<unknown>, success: string) {
  saving.value = true; error.value = ''; message.value = '';
  try { await fn(String(route.params.id)); message.value = success; await load(); }
  catch (value) { error.value = value instanceof ApiError && value.status === 409 ? '活动当前状态或学校状态不允许此操作。' : '生命周期操作失败，请稍后重试。'; }
  finally { saving.value = false; }
}

onMounted(() => void load());
watch(() => route.fullPath, () => void load());
</script>

<template>
  <WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" :page-title="isNew ? '创建活动' : '活动详情'" description="维护本校活动资料，并保留创建时绑定的挑战项目规则版本。" home-path="/school-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel activity-editor">
      <div class="project-detail-toolbar"><RouterLink class="project-back-link" to="/school-admin/activities">返回活动管理</RouterLink><div v-if="detail" class="activity-status-group"><span class="activity-status" :data-status="detail.executionStatus">{{ labelForActivityExecution(detail.executionStatus) }}</span><span>{{ labelForActivityPublic(detail.publicStatus) }}</span></div></div>
      <div v-if="loading" class="project-state">正在加载活动...</div>
      <div v-else-if="error && !detail" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <template v-else>
        <div v-if="error" class="project-inline-error" role="alert">{{ error }}</div><div v-if="message" class="project-inline-success" role="status">{{ message }}</div>
        <form class="project-form activity-form" @submit.prevent="save">
          <fieldset><legend>基本信息</legend><div class="project-form-grid"><label class="project-form-wide">活动名称<input v-model="form.title" maxlength="200" required :readonly="!isEditing"></label><label class="project-form-wide">挑战项目<select v-model="form.projectId" :disabled="!isNew" required><option value="" disabled>请选择已上架项目</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }} · {{ labelForCategory(project.category) }}</option></select><small>{{ isNew ? '只显示当前可用于新活动的已上架项目。' : '活动创建时绑定的挑战项目和规则版本不可在编辑中替换。' }}</small></label></div></fieldset>
          <fieldset><legend>时间地点</legend><div class="project-form-grid"><label>开始时间<input v-model="form.startTime" type="datetime-local" :readonly="!isEditing"></label><label>结束时间<input v-model="form.endTime" type="datetime-local" :readonly="!isEditing"></label><label class="project-form-wide">活动地点<input v-model="form.location" maxlength="300" :readonly="!isEditing"></label></div></fieldset>
          <fieldset><legend>活动说明</legend><label>描述<textarea v-model="form.description" rows="6" :readonly="!isEditing"></textarea></label></fieldset>
          <div class="project-form-actions"><button v-if="isEditing" class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存活动' }}</button><RouterLink v-if="detail?.executionStatus === 'DRAFT' && !isEditing" class="secondary-button" :to="`/school-admin/activities/${detail.id}/edit`">编辑活动</RouterLink><button v-if="detail?.executionStatus === 'DRAFT'" class="secondary-button" type="button" :disabled="saving" @click="publish">发布活动</button><button v-if="detail && (detail.executionStatus === 'DRAFT' || detail.executionStatus === 'PUBLISHED')" class="secondary-button danger-outline" type="button" :disabled="saving" @click="cancel">取消活动</button><RouterLink v-if="detail?.publicStatus === 'PUBLIC'" class="secondary-button" :to="`/activities/${detail.id}`">查看公开页面</RouterLink></div>
        </form>
        <section v-if="detail" class="registration-detail-section activity-snapshot"><header><p class="eyebrow">RULE SNAPSHOT</p><h2>挑战项目规则版本</h2></header><div v-for="project in detail.projects" :key="project.projectId" class="registration-detail-grid"><div><dt>挑战项目</dt><dd>{{ project.projectName }}</dd></div><div><dt>规则版本</dt><dd>V{{ project.ruleVersionNumber }}</dd></div><div class="registration-detail-grid-wide"><dt>规则正文</dt><dd>{{ project.rulesText || '未提供' }}</dd></div></div></section>
        <section v-if="detail" class="registration-detail-section"><header><p class="eyebrow">LIFECYCLE</p><h2>生命周期信息</h2></header><div class="registration-detail-grid"><div><dt>活动 ID</dt><dd>{{ detail.id }}</dd></div><div><dt>所属学校</dt><dd>{{ detail.schoolName }}</dd></div><div><dt>创建时间</dt><dd>{{ new Date(detail.createdAt).toLocaleString() }}</dd></div><div><dt>更新时间</dt><dd>{{ new Date(detail.updatedAt).toLocaleString() }}</dd></div></div></section>
      </template>
    </section>
  </WorkspaceShell>
</template>
