<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getManagedActivity } from '../api/activityManagement';
import {
  getActivityResultEditor,
  getActivityResultHistory,
  getResultFormatHistory,
  makeActivityResultPublic,
  publishActivityResultInternally,
  returnActivityResultToDraft,
  saveActivityResult,
  saveResultFormatEdit,
  submitActivityResultForReview,
  withdrawActivityResultInternally
} from '../api/activityResult';
import type { ActivityResultEditor, ActivityResultHistoryEntry, ResultFormatHistoryEntry, ResultFormatPresentation, ResultFormatStyle } from '../types/activityResult';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import { activityResultActions, labelForActivityResultAction, labelForActivityResultStatus, resultMutationErrorMessage } from '../utils/activityResultLabels';
import { clonePresentation, defaultPresentation, formatBlocks as buildFormatBlocks, paragraphRangesWithSelection, selectionToCodePointRange, toggleEmphasis } from '../utils/resultFormat';

const route = useRoute();
const editor = ref<ActivityResultEditor | null>(null);
const history = ref<ActivityResultHistoryEntry[]>([]);
const loading = ref(true);
const saving = ref(false);
const editing = ref(false);
const error = ref('');
const message = ref('');
const form = ref({ title: '', summaryText: '', scoreHighlightsText: '' });
const activityExecutionStatus = ref<string | null>(null);
const formatHistory = ref<ResultFormatHistoryEntry[]>([]);
const selectedFormatVersionId = ref('');
const formatDraft = ref<ResultFormatPresentation | null>(null);
const formatReason = ref('');
const formatError = ref('');
const formatSaving = ref(false);
const formatSurface = ref<HTMLElement | null>(null);

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
const formatTargets = computed(() => {
  if (!['PUBLISHED', 'IN_PROGRESS', 'ENDED'].includes(activityExecutionStatus.value ?? '') || !editor.value) return [];
  const result = editor.value;
  const targets: Array<{ versionId: string; label: string; version: NonNullable<ActivityResultEditor['candidateProjection']> }> = [];
  const seen = new Set<string>();
  const add = (versionId: string | null, label: string, version: ActivityResultEditor['candidateProjection']) => {
    if (!versionId || !version || seen.has(versionId)) return;
    seen.add(versionId);
    targets.push({ versionId, label, version });
  };
  if (result.internalStatus === 'INTERNAL_PUBLISHED' && result.currentInternalVersionId === result.internalProjection?.versionId) {
    add(result.currentInternalVersionId, '校内成果', result.internalProjection);
  }
  if (!result.publicVisibilityBlocked && result.currentPublicVersionId === result.publicProjection?.versionId) {
    add(result.currentPublicVersionId, '公开成果', result.publicProjection);
  }
  return targets;
});
const selectedFormatTarget = computed(() => formatTargets.value.find(target => target.versionId === selectedFormatVersionId.value) ?? null);
const formatBlocks = computed(() => selectedFormatTarget.value && formatDraft.value
  ? buildFormatBlocks(selectedFormatTarget.value.version.summaryText, formatDraft.value)
  : []);

function fillForm(value: ActivityResultEditor) {
  const content = value.candidateProjection;
  form.value = {
    title: content?.title ?? '',
    summaryText: content?.summaryText ?? '',
    scoreHighlightsText: content?.scoreHighlights.join('\n') ?? ''
  };
}

function resetFormatDraft(target = selectedFormatTarget.value) {
  formatDraft.value = target
    ? clonePresentation(target.version.presentation ?? defaultPresentation(target.version.summaryText))
    : null;
  formatReason.value = '';
  formatError.value = '';
}

async function refreshFormatSelection() {
  const targets = formatTargets.value;
  if (!targets.length) {
    selectedFormatVersionId.value = '';
    formatDraft.value = null;
    formatHistory.value = [];
    return;
  }
  const target = targets.find(item => item.versionId === selectedFormatVersionId.value) ?? targets[0];
  selectedFormatVersionId.value = target.versionId;
  resetFormatDraft(target);
  try {
    formatHistory.value = editor.value?.resultId
      ? await getResultFormatHistory(editor.value.resultId, target.versionId)
      : [];
  } catch (value) {
    formatHistory.value = [];
    formatError.value = value instanceof ApiError && value.status === 404
      ? '该版本的展示样式历史不可用。'
      : '展示样式历史加载失败，请稍后重试。';
  }
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [result, resultHistory, activity] = await Promise.all([
      getActivityResultEditor(activityId.value),
      getActivityResultHistory(activityId.value),
      getManagedActivity(activityId.value)
    ]);
    editor.value = result;
    history.value = resultHistory;
    activityExecutionStatus.value = activity.executionStatus;
    fillForm(result);
    await refreshFormatSelection();
  } catch (value) {
    editor.value = null;
    history.value = [];
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有管理该活动成果的权限。'
      : '活动成果加载失败，请稍后重试。';
    activityExecutionStatus.value = null;
    formatHistory.value = [];
    formatDraft.value = null;
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
      mediaRefs: candidate.value?.mediaRefs ?? []
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

async function selectFormatTarget(versionId: string) {
  selectedFormatVersionId.value = versionId;
  const target = formatTargets.value.find(item => item.versionId === versionId);
  resetFormatDraft(target);
  if (!target || !editor.value?.resultId) return;
  try {
    formatHistory.value = await getResultFormatHistory(editor.value.resultId, versionId);
  } catch (value) {
    formatHistory.value = [];
    formatError.value = value instanceof ApiError && value.status === 404
      ? '该版本的展示样式历史不可用。'
      : '展示样式历史加载失败，请稍后重试。';
  }
}

function onFormatTargetChange(event: Event) {
  void selectFormatTarget((event.target as HTMLSelectElement).value);
}

function selectedRange() {
  if (!formatSurface.value || !selectedFormatTarget.value) return null;
  return selectionToCodePointRange(formatSurface.value, selectedFormatTarget.value.version.summaryText);
}

function applyEmphasis(style: ResultFormatStyle) {
  if (!formatDraft.value || !selectedFormatTarget.value) return;
  const range = selectedRange();
  if (!range) {
    formatError.value = '请先在摘要预览中选择一段文字。';
    return;
  }
  const paragraph = formatDraft.value.paragraphs.find(item => range.start >= item.start && range.end <= item.end);
  if (!paragraph) {
    formatError.value = '加粗或斜体不能跨越段落，请重新选择。';
    return;
  }
  formatDraft.value = {
    ...formatDraft.value,
    emphasisRanges: toggleEmphasis(formatDraft.value.emphasisRanges, range.start, range.end, style)
  };
  formatError.value = '';
}

function splitParagraph() {
  if (!formatDraft.value || !selectedFormatTarget.value) return;
  const range = selectedRange();
  if (!range) {
    formatError.value = '请先选择一段文字作为独立段落。';
    return;
  }
  formatDraft.value = {
    ...formatDraft.value,
    paragraphs: paragraphRangesWithSelection(selectedFormatTarget.value.version.summaryText, formatDraft.value, range.start, range.end)
  };
  formatError.value = '';
}

async function saveFormat() {
  if (!editor.value?.resultId || !selectedFormatTarget.value || !formatDraft.value) return;
  if (!formatReason.value.trim()) {
    formatError.value = '请填写本次展示样式调整原因。';
    return;
  }
  formatSaving.value = true;
  formatError.value = '';
  message.value = '';
  try {
    await saveResultFormatEdit(editor.value.resultId, selectedFormatTarget.value.versionId, formatReason.value, formatDraft.value);
    message.value = '展示样式已保存，页面已重新读取服务端状态。';
    await load();
  } catch (value) {
    const status = value instanceof ApiError ? value.status : undefined;
    formatError.value = status === 409
      ? '该版本状态已变化或展示样式发生并发冲突，请重新读取后重试。'
      : status === 403
        ? '当前账号没有该版本展示样式的编辑权限。'
        : '展示样式保存失败，请检查选择范围和调整原因。';
  } finally {
    formatSaving.value = false;
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
          <fieldset><legend>成果内容</legend><div class="project-form-grid"><label class="project-form-wide">标题<input v-model="form.title" maxlength="200" required></label><label class="project-form-wide">成果摘要<textarea v-model="form.summaryText" rows="7" maxlength="10000" required></textarea></label><label>成绩亮点<small>每行一条，最多 20 条</small><textarea v-model="form.scoreHighlightsText" rows="5"></textarea></label></div></fieldset>
          <div class="project-form-actions"><button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存成果' }}</button><button class="secondary-button" type="button" :disabled="saving" @click="editing = false; editor && fillForm(editor)">取消</button></div>
        </form>
        <section v-if="formatTargets.length" class="registration-detail-section result-format-section">
          <header><p class="eyebrow">PRESENTATION ONLY</p><h2>展示样式</h2><p class="section-description">只调整摘要的展示层样式，不改变成果内容、不创建新版本，也不会重新进入平台审核。</p></header>
          <div class="result-format-content">
            <label class="result-format-target">编辑目标<select :value="selectedFormatVersionId" @change="onFormatTargetChange"><option v-for="target in formatTargets" :key="target.versionId" :value="target.versionId">{{ target.label }} · V{{ target.version.versionNumber }}</option></select></label>
            <div v-if="formatError" class="project-inline-error" role="alert">{{ formatError }}</div>
            <div class="result-format-toolbar" aria-label="展示样式工具"><button class="secondary-button" type="button" @mousedown.prevent @click="applyEmphasis('BOLD')"><strong>B</strong> 加粗</button><button class="secondary-button" type="button" @mousedown.prevent @click="applyEmphasis('ITALIC')"><em>I</em> 斜体</button><button class="secondary-button" type="button" @mousedown.prevent @click="splitParagraph">设为独立段落</button></div>
            <div ref="formatSurface" class="result-format-surface" tabindex="0" aria-label="可选择的展示样式预览">
              <p v-for="block in formatBlocks" :key="`${block.start}-${block.end}`" class="result-format-paragraph"><span v-for="(segment, index) in block.segments" :key="`${block.start}-${index}`" :class="['result-format-segment', { 'result-format-bold': segment.style.includes('BOLD'), 'result-format-italic': segment.style.includes('ITALIC') }]">{{ segment.text }}</span></p>
            </div>
            <label class="project-form-wide">调整原因<textarea v-model="formatReason" rows="3" maxlength="2000" required placeholder="说明为什么需要调整展示样式"></textarea></label>
            <div class="project-form-actions"><button class="primary-button" type="button" :disabled="formatSaving" @click="saveFormat">{{ formatSaving ? '保存中...' : '保存展示样式' }}</button></div>
            <div v-if="formatHistory.length" class="result-format-history"><h3>展示样式历史</h3><article v-for="entry in [...formatHistory].reverse()" :key="entry.formatEditId" class="result-format-history-item"><div><strong>修订 {{ entry.revision }}</strong><span>{{ new Date(entry.editedAt).toLocaleString() }}</span></div><p>{{ entry.reason }}</p><small>目标版本 V{{ selectedFormatTarget?.version.versionNumber }} · 操作者 {{ entry.editedBy || '系统记录' }}</small></article></div>
          </div>
        </section>
        <section v-else-if="editor.resultId" class="registration-detail-section result-format-section"><header><p class="eyebrow">PRESENTATION ONLY</p><h2>展示样式</h2><p class="section-description">当前活动状态或成果状态不允许普通展示样式调整，服务端仍是最终权限判断。</p></header></section>
        <section v-if="history.length" class="registration-detail-section"><header><h2>审核记录</h2></header><div v-for="entry in history" :key="entry.id" class="registration-detail-grid"><div><dt>动作</dt><dd>{{ labelForActivityResultAction(entry.action) }}</dd></div><div><dt>时间</dt><dd>{{ new Date(entry.occurredAt).toLocaleString() }}</dd></div><div class="registration-detail-grid-wide" v-if="entry.reason"><dt>原因</dt><dd>{{ entry.reason }}</dd></div></div></section>
      </template>
    </section>
  </WorkspaceShell>
</template>
