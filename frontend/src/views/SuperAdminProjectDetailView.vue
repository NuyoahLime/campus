<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { createProject, getGovernanceProject, publishProject, updateProject, archiveProject } from '../api/challengeProjects';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type { ChallengeProjectDetail, GovernanceProjectDetail, ProjectForm, ProjectStatus, RuleVersion } from '../types/challengeProject';

const route = useRoute();
const router = useRouter();
const isNew = computed(() => route.params.id === 'new');
const project = ref<ChallengeProjectDetail | null>(null);
const versions = ref<RuleVersion[]>([]);
const loading = ref(!isNew.value);
const saving = ref(false);
const error = ref('');
const message = ref('');
const dialogAction = ref<'publish' | 'archive' | null>(null);
const reason = ref('');

const form = ref<ProjectForm>(emptyForm());
const scoreStorageOptions = ['INTEGER', 'DECIMAL', 'DURATION', 'GRADE'];
const indicatorOptions = ['NUMERIC', 'TIME', 'GRADE'];
const directionOptions = ['HIGHER_BETTER', 'LOWER_BETTER', 'GRADE_ORDER', 'NO_RANKING'];
const effectiveOptions = ['BEST', 'LAST', 'ADMIN_DESIGNATED'];

function emptyForm(): ProjectForm {
  return { name: '', category: '', description: '', venueRequirements: '', equipmentRequirements: '', rulesText: '', scoreStorageType: 'INTEGER', scoreIndicatorType: 'NUMERIC', comparisonDirection: 'HIGHER_BETTER', scoreUnit: '', decimalPlaces: null, gradeOrder: '', allowTie: true, effectiveScoreRule: 'BEST' };
}

function fill(value: ChallengeProjectDetail) {
  form.value = { name: value.name, category: value.category, description: value.description || '', venueRequirements: value.venueRequirements || '', equipmentRequirements: value.equipmentRequirements || '', rulesText: value.rulesText || '', scoreStorageType: value.scoreStorageType, scoreIndicatorType: value.scoreIndicatorType, comparisonDirection: value.comparisonDirection, scoreUnit: value.scoreUnit || '', decimalPlaces: value.decimalPlaces, gradeOrder: value.gradeOrder || '', allowTie: value.allowTie, effectiveScoreRule: value.effectiveScoreRule };
}

async function load() {
  if (isNew.value) { form.value = emptyForm(); loading.value = false; return; }
  loading.value = true;
  error.value = '';
  try {
    const response: GovernanceProjectDetail = await getGovernanceProject(String(route.params.id));
    project.value = response.project;
    versions.value = response.ruleVersions;
    fill(response.project);
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403 ? '当前账号没有项目治理权限。' : '项目详情加载失败。';
  } finally { loading.value = false; }
}

async function save() {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    if (isNew.value) {
      const created = await createProject(form.value);
      await router.push(`/super-admin/projects/${created.id}`);
    } else {
      await updateProject(String(route.params.id), form.value);
      message.value = '项目草稿已保存。';
      await load();
    }
  } catch (value) { error.value = value instanceof ApiError && value.status === 409 ? '项目状态已被更新，请刷新后重试。' : '项目保存失败，请检查表单内容。'; }
  finally { saving.value = false; }
}

function openAction(action: 'publish' | 'archive') { dialogAction.value = action; reason.value = ''; }
function closeAction() { if (!saving.value) dialogAction.value = null; }

async function runAction() {
  if (!dialogAction.value || reason.value.trim().length < 2) return;
  saving.value = true;
  error.value = '';
  try {
    const id = String(route.params.id);
    if (dialogAction.value === 'publish') await publishProject(id, reason.value.trim());
    else await archiveProject(id, reason.value.trim());
    dialogAction.value = null;
    message.value = '生命周期操作已完成。';
    await load();
  } catch { error.value = '生命周期操作失败，项目可能已被其他管理员更新。'; }
  finally { saving.value = false; }
}

function statusLabel(value: ProjectStatus | undefined) { return value === 'DRAFT' ? '草稿' : value === 'PUBLISHED' ? '已上架' : value === 'ARCHIVED' ? '已下架' : ''; }
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="超级管理员" workspace-title="平台管理工作台" :page-title="isNew ? '新建挑战项目' : '挑战项目详情'" description="编辑项目资料和成绩规则，并管理项目公开状态。" home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel project-editor">
      <div class="project-detail-toolbar"><RouterLink class="project-back-link" to="/super-admin/projects">返回项目管理</RouterLink><span v-if="project" class="project-status" :data-status="project.status">{{ statusLabel(project.status) }}</span></div>
      <div v-if="loading" class="project-state">正在加载项目...</div>
      <div v-else-if="error && !project" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" @click="load">重新加载</button></div>
      <template v-else>
        <div v-if="error" class="project-inline-error" role="alert">{{ error }}</div>
        <div v-if="message" class="project-inline-success" role="status">{{ message }}</div>
        <form class="project-form" @submit.prevent="save">
          <fieldset><legend>基本信息</legend><div class="project-form-grid"><label>项目名称<input v-model="form.name" required maxlength="200"></label><label>分类<input v-model="form.category" required maxlength="64" placeholder="ATHLETICS"></label><label class="project-form-wide">项目说明<textarea v-model="form.description" rows="3"></textarea></label></div></fieldset>
          <fieldset><legend>项目要求</legend><div class="project-form-grid"><label>场地要求<textarea v-model="form.venueRequirements" rows="3"></textarea></label><label>器材要求<textarea v-model="form.equipmentRequirements" rows="3"></textarea></label></div></fieldset>
          <fieldset><legend>比赛规则</legend><label>规则正文<textarea v-model="form.rulesText" rows="7" placeholder="填写参与条件、流程和判定规则"></textarea></label></fieldset>
          <fieldset><legend>成绩规则</legend><div class="project-form-grid"><label>成绩存储类型<select v-model="form.scoreStorageType"><option v-for="value in scoreStorageOptions" :key="value" :value="value">{{ value }}</option></select></label><label>成绩指标<select v-model="form.scoreIndicatorType"><option v-for="value in indicatorOptions" :key="value" :value="value">{{ value }}</option></select></label><label>比较方向<select v-model="form.comparisonDirection"><option v-for="value in directionOptions" :key="value" :value="value">{{ value }}</option></select></label><label>有效成绩规则<select v-model="form.effectiveScoreRule"><option v-for="value in effectiveOptions" :key="value" :value="value">{{ value }}</option></select></label><label>成绩单位<input v-model="form.scoreUnit"></label><label>小数位<input v-model.number="form.decimalPlaces" type="number" min="0" max="6"></label><label class="project-form-wide">年级顺序<textarea v-model="form.gradeOrder" rows="2"></textarea></label><label class="project-checkbox"><input v-model="form.allowTie" type="checkbox">允许并列</label></div></fieldset>
          <div class="project-form-actions"><button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存项目' }}</button><button v-if="project && project.status === 'DRAFT'" class="secondary-button" type="button" :disabled="saving" @click="openAction('publish')">上架项目</button><button v-if="project && project.status === 'PUBLISHED'" class="secondary-button danger-outline" type="button" :disabled="saving" @click="openAction('archive')">下架项目</button><button v-if="project && project.status === 'ARCHIVED'" class="secondary-button" type="button" :disabled="saving" @click="openAction('publish')">重新上架</button></div>
        </form>
        <section v-if="versions.length" class="project-version-history"><div class="project-section-heading"><div><p class="eyebrow">RULE HISTORY</p><h2>规则版本</h2></div><span>当前版本 V{{ project?.currentRuleVersionNumber }}</span></div><div v-for="version in versions" :key="version.id" class="project-version-row"><strong>V{{ version.versionNumber }}</strong><span>{{ version.changeReason || '初始版本' }}</span><time>{{ new Date(version.createdAt).toLocaleString() }}</time></div></section>
      </template>
      <div v-if="dialogAction" class="project-modal-backdrop" @click.self="closeAction"><section class="project-modal" role="dialog" aria-modal="true"><p class="eyebrow">{{ dialogAction === 'publish' ? 'PUBLISH PROJECT' : 'ARCHIVE PROJECT' }}</p><h2>{{ dialogAction === 'publish' ? (project?.status === 'ARCHIVED' ? '重新上架项目' : '上架项目') : '下架项目' }}</h2><p>项目：{{ project?.name || form.name }}</p><label>操作原因<textarea v-model="reason" rows="4" minlength="2" maxlength="500" required></textarea></label><div class="project-modal-actions"><button class="secondary-button" type="button" :disabled="saving" @click="closeAction">取消</button><button class="primary-button" type="button" :disabled="saving || reason.trim().length < 2" @click="runAction">确认</button></div></section></div>
    </section>
  </WorkspaceShell>
</template>
