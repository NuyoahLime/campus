<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import {
  getActivityResultEditor,
  getActivityResultHistory,
  makeActivityResultPublic,
  publishActivityResultInternally,
  returnActivityResultToDraft,
  saveActivityResult,
  submitActivityResultForReview,
  withdrawActivityResultInternally
} from '../api/activityResult';
import type { ActivityResultEditor, ActivityResultHistoryEntry } from '../types/activityResult';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import { activityResultActions, labelForActivityResultAction, labelForActivityResultStatus, resultMutationErrorMessage } from '../utils/activityResultLabels';

const route = useRoute();
const editor = ref<ActivityResultEditor | null>(null);
const history = ref<ActivityResultHistoryEntry[]>([]);
const loading = ref(true);
const saving = ref(false);
const editing = ref(false);
const error = ref('');
const message = ref('');
const form = ref({ title: '', summaryText: '', scoreHighlightsText: '', mediaRefsText: '' });

const activityId = computed(() => String(route.params.id));
const candidate = computed(() => editor.value?.candidateProjection ?? null);
const latestRejection = computed(() => [...history.value].reverse().find(entry => entry.action === 'REJECTED'));
const availableActions = computed(() => activityResultActions(
  editor.value?.internalStatus ?? 'DRAFT',
  editor.value?.publicStatus ?? 'NOT_SUBMITTED',
  Boolean(editor.value?.currentCandidateVersionId)
));
const canEdit = computed(() => {
  return availableActions.value.edit;
});

function fillForm(value: ActivityResultEditor) {
  const content = value.candidateProjection;
  form.value = {
    title: content?.title ?? '',
    summaryText: content?.summaryText ?? '',
    scoreHighlightsText: content?.scoreHighlights.join('\n') ?? '',
    mediaRefsText: content?.mediaRefs.join('\n') ?? ''
  };
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [result, resultHistory] = await Promise.all([
      getActivityResultEditor(activityId.value),
      getActivityResultHistory(activityId.value)
    ]);
    editor.value = result;
    history.value = resultHistory;
    fillForm(result);
  } catch (value) {
    editor.value = null;
    history.value = [];
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有管理该活动成果的权限。'
      : '活动成果加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function splitLines(value: string) {
  return value.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
}

async function save() {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    await saveActivityResult(activityId.value, {
      title: form.value.title,
      summaryText: form.value.summaryText,
      scoreHighlights: splitLines(form.value.scoreHighlightsText),
      mediaRefs: splitLines(form.value.mediaRefsText)
    });
    editing.value = false;
    message.value = '成果草稿已保存。';
    await load();
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 409
      ? '成果状态已变化，请重新读取后再保存。'
      : '成果保存失败，请检查标题、摘要和引用格式。';
  } finally {
    saving.value = false;
  }
}

async function run(action: 'publish' | 'withdraw' | 'draft' | 'submit' | 'public') {
  const resultId = editor.value?.resultId;
  if (!resultId) return;
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    if (action === 'publish') await publishActivityResultInternally(resultId);
    if (action === 'withdraw') await withdrawActivityResultInternally(resultId);
    if (action === 'draft') await returnActivityResultToDraft(resultId);
    if (action === 'submit') await submitActivityResultForReview(resultId);
    if (action === 'public') await makeActivityResultPublic(resultId);
    message.value = '操作已完成，页面已重新读取服务端状态。';
    await load();
  } catch (value) {
    error.value = resultMutationErrorMessage(value instanceof ApiError ? value.status : undefined);
    await load();
  } finally {
    saving.value = false;
  }
}

function startEditing() {
  if (!canEdit.value) return;
  editing.value = true;
  message.value = '';
}

watch(() => String(route.params.id), () => { editing.value = false; void load(); });
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" page-title="活动成果" description="保存成果内容、发布校内版本并提交平台审核。" home-path="/school-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel activity-editor">
      <div class="project-detail-toolbar">
        <RouterLink class="project-back-link" :to="`/school-admin/activities/${activityId}`">返回活动详情</RouterLink>
        <div v-if="editor" class="activity-status-group">
          <span class="activity-status">{{ labelForActivityResultStatus(editor.internalStatus) }}</span>
          <span class="activity-status">{{ labelForActivityResultStatus(editor.publicStatus) }}</span>
        </div>
      </div>
      <div v-if="loading" class="project-state" role="status">正在加载活动成果...</div>
      <div v-else-if="error && !editor" class="project-state project-state-error" role="alert"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load">重新加载</button></div>
      <template v-else-if="editor">
        <div v-if="error" class="project-inline-error" role="alert">{{ error }}</div>
        <div v-if="message" class="project-inline-success" role="status">{{ message }}</div>
        <div v-if="latestRejection" class="registration-review-notice" role="status">
          <div><strong>平台驳回原因</strong><p>{{ latestRejection.reason || '审核记录未提供文字原因。' }}</p></div>
        </div>
        <section v-if="!editor.resultId && !editing" class="registration-detail-state">
          <strong>该活动尚未创建活动成果</strong>
          <p>保存后会由服务端一次性创建成果和第一个不可变版本。</p>
          <button class="primary-button" type="button" @click="editing = true">创建活动成果</button>
        </section>
        <section v-else-if="!editing" class="registration-detail-section">
          <header><p class="eyebrow">CURRENT CANDIDATE</p><h2>{{ candidate?.title || '暂无候选版本' }}</h2></header>
          <div class="registration-detail-grid">
            <div><dt>成果状态</dt><dd>{{ labelForActivityResultStatus(editor.internalStatus) }}</dd></div>
            <div><dt>平台状态</dt><dd>{{ labelForActivityResultStatus(editor.publicStatus) }}</dd></div>
            <div class="registration-detail-grid-wide"><dt>摘要</dt><dd>{{ candidate?.summaryText || '暂无摘要' }}</dd></div>
            <div class="registration-detail-grid-wide"><dt>亮点</dt><dd>{{ candidate?.scoreHighlights.join('、') || '暂无亮点' }}</dd></div>
            <div><dt>候选版本</dt><dd>V{{ candidate?.versionNumber ?? '-' }}</dd></div>
            <div><dt>公开可见性</dt><dd>{{ editor.publicVisibilityBlocked ? '已阻断' : '未阻断' }}</dd></div>
          </div>
          <div class="project-form-actions">
            <button v-if="canEdit" class="secondary-button" type="button" @click="startEditing">编辑成果</button>
            <button v-if="availableActions.publish" class="primary-button" type="button" :disabled="saving" @click="run('publish')">内部发布</button>
            <button v-if="availableActions.submit" class="primary-button" type="button" :disabled="saving" @click="run('submit')">提交平台审核</button>
            <button v-if="availableActions.makePublic" class="primary-button" type="button" :disabled="saving" @click="run('public')">正式公开</button>
            <button v-if="availableActions.withdraw" class="secondary-button danger-outline" type="button" :disabled="saving" @click="run('withdraw')">内部撤回</button>
            <button v-if="availableActions.returnToDraft" class="secondary-button" type="button" :disabled="saving" @click="run('draft')">恢复草稿</button>
          </div>
        </section>
        <form v-else class="project-form" @submit.prevent="save">
          <fieldset><legend>成果内容</legend><div class="project-form-grid"><label class="project-form-wide">标题<input v-model="form.title" maxlength="200" required></label><label class="project-form-wide">成果摘要<textarea v-model="form.summaryText" rows="7" maxlength="10000" required></textarea></label><label>成绩亮点<small>每行一条，最多 20 条</small><textarea v-model="form.scoreHighlightsText" rows="5"></textarea></label><label>媒体引用 UUID<small>每行一个；当前后端只接受可验证的媒体引用</small><textarea v-model="form.mediaRefsText" rows="5"></textarea></label></div></fieldset>
          <div class="project-form-actions"><button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存成果' }}</button><button class="secondary-button" type="button" :disabled="saving" @click="editing = false; editor && fillForm(editor)">取消</button></div>
        </form>
        <section v-if="history.length" class="registration-detail-section"><header><h2>审核记录</h2></header><div v-for="entry in history" :key="entry.id" class="registration-detail-grid"><div><dt>动作</dt><dd>{{ labelForActivityResultAction(entry.action) }}</dd></div><div><dt>时间</dt><dd>{{ new Date(entry.occurredAt).toLocaleString() }}</dd></div><div class="registration-detail-grid-wide" v-if="entry.reason"><dt>原因</dt><dd>{{ entry.reason }}</dd></div></div></section>
      </template>
    </section>
  </WorkspaceShell>
</template>
