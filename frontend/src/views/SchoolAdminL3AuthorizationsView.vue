<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getPublicProject, listPublicProjects } from '../api/challengeProjects';
import {
  createL3Authorization,
  getSchoolL3Authorization,
  listSchoolL3Authorizations,
  returnL3AuthorizationToDraft,
  submitL3Authorization,
  updateL3Authorization,
  withdrawL3Authorization
} from '../api/l3Authorization';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { ChallengeProjectDetail, ChallengeProjectListItem } from '../types/challengeProject';
import type { L3Authorization, L3AuthorizationPage, L3AuthorizationStatus } from '../types/l3Authorization';

const statuses: Array<L3AuthorizationStatus | ''> = ['', 'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED', 'WITHDRAWN'];

const items = ref<L3Authorization[]>([]);
const result = ref<L3AuthorizationPage | null>(null);
const selected = ref<L3Authorization | null>(null);
const projects = ref<ChallengeProjectListItem[]>([]);
const projectDetail = ref<ChallengeProjectDetail | null>(null);
const loading = ref(true);
const saving = ref(false);
const page = ref(0);
const status = ref<L3AuthorizationStatus | ''>('');
const pageError = ref('');
const actionMessage = ref('');
const actionError = ref('');
const withdrawReason = ref('');
const form = ref({
  projectId: '',
  activityIds: '',
  grades: '',
  classNames: '',
  activityPeriodStart: '',
  activityPeriodEnd: '',
  allowSchoolName: true,
  allowStudentName: false
});

const ruleVersionId = computed(() => projectDetail.value?.currentRuleVersionId ?? '');
const canSave = computed(() => Boolean(form.value.projectId && ruleVersionId.value && !saving.value));

function describeError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'This account cannot manage L3 data authorization.';
    if (error.status === 404) return 'The authorization was not found.';
    if (error.status === 409) return 'The authorization state or scope conflicts with current data.';
    if (error.status === 400) return 'Check the form values and try again.';
  }
  return 'The request failed. Try again shortly.';
}

function splitValues(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function localDateTimeToIso(value: string) {
  return value ? new Date(value).toISOString() : null;
}

function buildScope() {
  const scope: Record<string, unknown> = {};
  const activityIds = splitValues(form.value.activityIds);
  const grades = splitValues(form.value.grades);
  const classNames = splitValues(form.value.classNames);
  if (activityIds.length) scope.activityIds = activityIds;
  if (grades.length) scope.grades = grades;
  if (classNames.length) scope.classNames = classNames;
  const start = localDateTimeToIso(form.value.activityPeriodStart);
  const end = localDateTimeToIso(form.value.activityPeriodEnd);
  if (start) scope.activityPeriodStart = start;
  if (end) scope.activityPeriodEnd = end;
  return scope;
}

function scopeSummary(value: string | null) {
  if (!value) return '{}';
  try {
    return JSON.stringify(JSON.parse(value));
  } catch {
    return value;
  }
}

function dateLabel(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

async function load(selectId = selected.value?.id ?? '') {
  loading.value = true;
  pageError.value = '';
  try {
    const [authorizations, publicProjects] = await Promise.all([
      listSchoolL3Authorizations(page.value, 20, status.value),
      listPublicProjects(0, 100)
    ]);
    result.value = authorizations;
    items.value = authorizations.items;
    projects.value = publicProjects.items;
    const next = items.value.find((item) => item.id === selectId) ?? items.value[0] ?? null;
    selected.value = next ? await getSchoolL3Authorization(next.id) : null;
  } catch (error) {
    items.value = [];
    result.value = null;
    selected.value = null;
    pageError.value = describeError(error);
  } finally {
    loading.value = false;
  }
}

async function loadProjectDetail() {
  projectDetail.value = null;
  if (!form.value.projectId) return;
  try {
    projectDetail.value = await getPublicProject(form.value.projectId);
  } catch (error) {
    actionError.value = describeError(error);
  }
}

async function selectAuthorization(item: L3Authorization) {
  actionError.value = '';
  actionMessage.value = '';
  selected.value = await getSchoolL3Authorization(item.id);
}

async function createAuthorization() {
  if (!canSave.value) return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const created = await createL3Authorization({
      projectId: form.value.projectId,
      ruleVersionId: ruleVersionId.value,
      dataScope: buildScope(),
      allowSchoolName: form.value.allowSchoolName,
      allowStudentName: form.value.allowStudentName
    });
    actionMessage.value = 'L3 authorization draft created.';
    await load(created.id);
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    saving.value = false;
  }
}

async function editSelected() {
  if (!selected.value || selected.value.status !== 'DRAFT') return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    await updateL3Authorization(selected.value.id, {
      dataScope: buildScope(),
      allowSchoolName: form.value.allowSchoolName,
      allowStudentName: form.value.allowStudentName
    });
    actionMessage.value = 'Draft updated.';
    await load(selected.value.id);
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    saving.value = false;
  }
}

async function runAction(action: 'submit' | 'return' | 'withdraw') {
  if (!selected.value) return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    if (action === 'submit') await submitL3Authorization(selected.value.id);
    if (action === 'return') await returnL3AuthorizationToDraft(selected.value.id);
    if (action === 'withdraw') await withdrawL3Authorization(selected.value.id, withdrawReason.value.trim());
    actionMessage.value = 'Authorization updated.';
    withdrawReason.value = '';
    await load(selected.value.id);
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    saving.value = false;
  }
}

function changePage(next: number) {
  if (next < 0 || next >= (result.value?.totalPages ?? 0)) return;
  page.value = next;
  void load();
}

watch(status, () => { page.value = 0; void load(); });
watch(() => form.value.projectId, () => void loadProjectDetail());
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="School Admin" workspace-title="School Workspace" page-title="L3 Data Authorization" description="Manage school data authorization for future L3 ranking." home-path="/school-admin" :navigation="navigation">
    <section class="project-admin-panel l3-auth-panel">
      <div class="project-admin-toolbar">
        <div><p class="eyebrow">L3 AUTHORIZATION</p><h2>School Authorizations</h2><span>{{ result?.totalElements ?? 0 }} records</span></div>
        <button class="secondary-button" type="button" :disabled="loading" @click="load()">Reload</button>
      </div>
      <form class="project-filter-bar project-admin-filter" @submit.prevent="load()">
        <label>Status<select v-model="status" data-testid="l3-school-status-filter"><option v-for="item in statuses" :key="item || 'ALL'" :value="item">{{ item || 'ALL' }}</option></select></label>
      </form>
      <div v-if="loading" class="project-state">Loading authorizations...</div>
      <div v-else-if="pageError" class="project-state project-state-error"><strong>{{ pageError }}</strong><button class="secondary-button" type="button" @click="load()">Retry</button></div>
      <div v-else class="l3-auth-grid">
        <div class="l3-auth-list">
          <table class="project-admin-table">
            <thead><tr><th>Project</th><th>Status</th><th>Submitted</th><th></th></tr></thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td><strong>{{ item.projectName }}</strong><span>V{{ item.ruleVersionNumber }}</span></td>
                <td><span class="project-status" :data-status="item.status">{{ item.status }}</span></td>
                <td>{{ dateLabel(item.submittedAt) }}</td>
                <td><button class="secondary-button" type="button" @click="selectAuthorization(item)">Open</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="!items.length" class="project-state"><strong>No authorizations yet.</strong></div>
          <div v-if="result && result.totalPages > 1" class="project-pagination">
            <button class="secondary-button" type="button" :disabled="page === 0 || loading" @click="changePage(page - 1)">Previous</button>
            <span>{{ page + 1 }} / {{ result.totalPages }}</span>
            <button class="secondary-button" type="button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">Next</button>
          </div>
        </div>
        <form class="l3-auth-form" data-testid="l3-school-form" @submit.prevent="createAuthorization">
          <h3>Create Draft</h3>
          <label>Challenge Project<select v-model="form.projectId" data-testid="l3-project-select"><option value="">Select project</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option></select></label>
          <label>Activity IDs<input v-model="form.activityIds" data-testid="l3-activity-ids" type="text"></label>
          <label>Grades<input v-model="form.grades" data-testid="l3-grades" type="text"></label>
          <label>Classes<input v-model="form.classNames" data-testid="l3-classes" type="text"></label>
          <div class="l3-auth-two">
            <label>Period Start<input v-model="form.activityPeriodStart" data-testid="l3-period-start" type="datetime-local"></label>
            <label>Period End<input v-model="form.activityPeriodEnd" data-testid="l3-period-end" type="datetime-local"></label>
          </div>
          <label class="l3-auth-check"><input v-model="form.allowSchoolName" type="checkbox"> Allow school display name</label>
          <label class="l3-auth-check"><input v-model="form.allowStudentName" type="checkbox"> Allow masked student display name</label>
          <div class="project-form-actions">
            <button class="primary-button" data-testid="l3-create" type="submit" :disabled="!canSave">Create Draft</button>
            <button class="secondary-button" data-testid="l3-edit" type="button" :disabled="!selected || selected.status !== 'DRAFT' || saving" @click="editSelected">Update Draft</button>
          </div>
        </form>
        <section class="l3-auth-detail" data-testid="l3-school-detail">
          <h3>Selected Authorization</h3>
          <div v-if="!selected" class="project-state"><strong>No record selected.</strong></div>
          <template v-else>
            <dl>
              <div><dt>Status</dt><dd data-testid="l3-selected-status">{{ selected.status }}</dd></div>
              <div><dt>ID</dt><dd>{{ selected.id }}</dd></div>
              <div><dt>Project</dt><dd>{{ selected.projectName }}</dd></div>
              <div><dt>Scope</dt><dd><code>{{ scopeSummary(selected.dataScope) }}</code></dd></div>
              <div><dt>Privacy</dt><dd>School {{ selected.allowSchoolName ? 'allowed' : 'hidden' }} / Student {{ selected.allowStudentName ? 'masked name allowed' : 'hidden' }}</dd></div>
              <div v-if="selected.rejectReason"><dt>Reject Reason</dt><dd data-testid="l3-reject-reason">{{ selected.rejectReason }}</dd></div>
            </dl>
            <div class="project-form-actions">
              <button class="primary-button" data-testid="l3-submit" type="button" :disabled="selected.status !== 'DRAFT' || saving" @click="runAction('submit')">Submit</button>
              <button class="secondary-button" data-testid="l3-return" type="button" :disabled="selected.status !== 'REJECTED' || saving" @click="runAction('return')">Return to Draft</button>
            </div>
            <div class="l3-auth-withdraw">
              <input v-model="withdrawReason" data-testid="l3-withdraw-reason" type="text" placeholder="Withdraw reason">
              <button class="secondary-button danger-outline" type="button" :disabled="!withdrawReason.trim() || !['DRAFT','APPROVED','SUSPENDED'].includes(selected.status) || saving" @click="runAction('withdraw')">Withdraw</button>
            </div>
          </template>
        </section>
      </div>
      <p v-if="actionMessage" class="message message-success" data-testid="l3-action-message">{{ actionMessage }}</p>
      <p v-if="actionError" class="message message-error" data-testid="l3-action-error">{{ actionError }}</p>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.l3-auth-panel { padding: 20px; }
.l3-auth-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr); gap: 18px; align-items: start; }
.l3-auth-list { grid-column: 1; min-width: 0; }
.l3-auth-form, .l3-auth-detail { border: 1px solid #dde3ea; border-radius: 8px; padding: 16px; background: #fff; }
.l3-auth-form { grid-column: 2; }
.l3-auth-detail { grid-column: 1 / -1; }
.l3-auth-form h3, .l3-auth-detail h3 { margin: 0 0 12px; font-size: 16px; }
.l3-auth-form label { display: grid; gap: 6px; margin-bottom: 10px; color: #354155; font-size: 13px; font-weight: 700; }
.l3-auth-form input, .l3-auth-form select, .l3-auth-withdraw input { min-height: 38px; border: 1px solid #d6dce4; border-radius: 7px; padding: 0 10px; font: inherit; }
.l3-auth-two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.l3-auth-check { display: flex !important; grid-template-columns: none !important; align-items: center; gap: 8px !important; }
.l3-auth-check input { min-height: auto; }
.l3-auth-detail dl { display: grid; gap: 10px; margin: 0 0 14px; }
.l3-auth-detail div { min-width: 0; }
.l3-auth-detail dt { color: #687384; font-size: 12px; font-weight: 800; text-transform: uppercase; }
.l3-auth-detail dd { margin: 3px 0 0; color: #1d2736; overflow-wrap: anywhere; }
.l3-auth-withdraw { display: flex; gap: 10px; margin-top: 12px; }
.l3-auth-withdraw input { flex: 1; min-width: 0; }
@media (max-width: 900px) {
  .l3-auth-grid, .l3-auth-two { grid-template-columns: 1fr; }
  .l3-auth-list, .l3-auth-form, .l3-auth-detail { grid-column: 1; }
  .l3-auth-withdraw { flex-direction: column; }
}
</style>
