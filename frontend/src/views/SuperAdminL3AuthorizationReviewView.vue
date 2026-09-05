<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import {
  approveL3Authorization,
  getReviewL3Authorization,
  listReviewL3Authorizations,
  rejectL3Authorization,
  resumeL3Authorization
} from '../api/l3Authorization';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type { L3Authorization, L3AuthorizationPage, L3AuthorizationStatus } from '../types/l3Authorization';

const statuses: Array<L3AuthorizationStatus | ''> = ['', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED', 'WITHDRAWN', 'DRAFT'];

const items = ref<L3Authorization[]>([]);
const result = ref<L3AuthorizationPage | null>(null);
const selected = ref<L3Authorization | null>(null);
const loading = ref(true);
const saving = ref(false);
const page = ref(0);
const status = ref<L3AuthorizationStatus | ''>('PENDING_REVIEW');
const error = ref('');
const actionMessage = ref('');
const actionError = ref('');
const reviewComment = ref('');
const rejectReason = ref('');

function describeError(value: unknown) {
  if (value instanceof ApiError) {
    if (value.status === 403) return 'This account cannot review L3 authorization.';
    if (value.status === 404) return 'The authorization was not found.';
    if (value.status === 409) return 'The authorization can no longer be reviewed in this state.';
  }
  return 'The request failed. Try again shortly.';
}

function dateLabel(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function scopeSummary(value: string | null) {
  if (!value) return '{}';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

async function load(selectId = selected.value?.id ?? '') {
  loading.value = true;
  error.value = '';
  try {
    result.value = await listReviewL3Authorizations(page.value, 20, status.value);
    items.value = result.value.items;
    const next = items.value.find((item) => item.id === selectId) ?? items.value[0] ?? null;
    selected.value = selectId
      ? await getReviewL3Authorization(selectId)
      : (next ? await getReviewL3Authorization(next.id) : null);
  } catch (value) {
    result.value = null;
    items.value = [];
    selected.value = null;
    error.value = describeError(value);
  } finally {
    loading.value = false;
  }
}

async function selectAuthorization(item: L3Authorization) {
  actionError.value = '';
  actionMessage.value = '';
  selected.value = await getReviewL3Authorization(item.id);
}

async function review(action: 'approve' | 'reject' | 'resume') {
  if (!selected.value) return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    if (action === 'approve') await approveL3Authorization(selected.value.id, reviewComment.value.trim());
    if (action === 'reject') await rejectL3Authorization(selected.value.id, rejectReason.value.trim());
    if (action === 'resume') await resumeL3Authorization(selected.value.id);
    actionMessage.value = 'Review action completed.';
    rejectReason.value = '';
    reviewComment.value = '';
    await load(selected.value.id);
  } catch (value) {
    actionError.value = describeError(value);
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
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell role-label="Super Admin" workspace-title="Platform Workspace" page-title="L3 Authorization Review" description="Review school data authorization for future L3 ranking." home-path="/super-admin" :navigation="navigation" :show-identity="false">
    <section class="project-admin-panel l3-review-panel">
      <div class="project-admin-toolbar">
        <div><p class="eyebrow">L3 REVIEW</p><h2>Authorization Queue</h2><span>{{ result?.totalElements ?? 0 }} records</span></div>
        <button class="secondary-button" type="button" :disabled="loading" @click="load()">Reload</button>
      </div>
      <form class="project-filter-bar project-admin-filter" @submit.prevent="load()">
        <label>Status<select v-model="status" data-testid="l3-review-status-filter"><option v-for="item in statuses" :key="item || 'ALL'" :value="item">{{ item || 'ALL' }}</option></select></label>
      </form>
      <div v-if="loading" class="project-state">Loading review queue...</div>
      <div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" type="button" @click="load()">Retry</button></div>
      <div v-else class="l3-review-grid">
        <div class="l3-review-list">
          <table class="project-admin-table">
            <thead><tr><th>School</th><th>Project</th><th>Status</th><th>Submitted</th><th></th></tr></thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>{{ item.schoolName }}</td>
                <td><strong>{{ item.projectName }}</strong><span>V{{ item.ruleVersionNumber }}</span></td>
                <td><span class="project-status" :data-status="item.status">{{ item.status }}</span></td>
                <td>{{ dateLabel(item.submittedAt) }}</td>
                <td><button class="secondary-button" type="button" @click="selectAuthorization(item)">Open</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="!items.length" class="project-state"><strong>No authorizations in this queue.</strong></div>
          <div v-if="result && result.totalPages > 1" class="project-pagination">
            <button class="secondary-button" type="button" :disabled="page === 0 || loading" @click="changePage(page - 1)">Previous</button>
            <span>{{ page + 1 }} / {{ result.totalPages }}</span>
            <button class="secondary-button" type="button" :disabled="!result.hasNext || loading" @click="changePage(page + 1)">Next</button>
          </div>
        </div>
        <section class="l3-review-detail" data-testid="l3-review-detail">
          <h3>Review Detail</h3>
          <div v-if="!selected" class="project-state"><strong>No record selected.</strong></div>
          <template v-else>
            <dl>
              <div><dt>Status</dt><dd data-testid="l3-review-selected-status">{{ selected.status }}</dd></div>
              <div><dt>ID</dt><dd>{{ selected.id }}</dd></div>
              <div><dt>School</dt><dd>{{ selected.schoolName }}</dd></div>
              <div><dt>Project</dt><dd>{{ selected.projectName }} / V{{ selected.ruleVersionNumber }}</dd></div>
              <div><dt>Scope</dt><dd><pre>{{ scopeSummary(selected.dataScope) }}</pre></dd></div>
              <div><dt>Privacy</dt><dd>School {{ selected.allowSchoolName ? 'allowed' : 'hidden' }} / Student {{ selected.allowStudentName ? 'masked name allowed' : 'hidden' }}</dd></div>
              <div v-if="selected.rejectReason"><dt>Reject Reason</dt><dd>{{ selected.rejectReason }}</dd></div>
            </dl>
            <label class="l3-review-field">Approve Comment<input v-model="reviewComment" data-testid="l3-approve-comment" type="text"></label>
            <button class="primary-button" data-testid="l3-approve" type="button" :disabled="selected.status !== 'PENDING_REVIEW' || saving" @click="review('approve')">Approve</button>
            <label class="l3-review-field">Reject Reason<input v-model="rejectReason" data-testid="l3-reject-input" type="text"></label>
            <button class="secondary-button danger-outline" data-testid="l3-reject" type="button" :disabled="selected.status !== 'PENDING_REVIEW' || !rejectReason.trim() || saving" @click="review('reject')">Reject</button>
            <button class="secondary-button" data-testid="l3-resume" type="button" :disabled="selected.status !== 'SUSPENDED' || saving" @click="review('resume')">Resume</button>
          </template>
        </section>
      </div>
      <p v-if="actionMessage" class="message message-success" data-testid="l3-review-action-message">{{ actionMessage }}</p>
      <p v-if="actionError" class="message message-error" data-testid="l3-review-action-error">{{ actionError }}</p>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.l3-review-panel { padding: 20px; }
.l3-review-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(300px, .8fr); gap: 18px; align-items: start; }
.l3-review-list { min-width: 0; }
.l3-review-detail { border: 1px solid #dde3ea; border-radius: 8px; padding: 16px; background: #fff; }
.l3-review-detail h3 { margin: 0 0 12px; font-size: 16px; }
.l3-review-detail dl { display: grid; gap: 10px; margin: 0 0 14px; }
.l3-review-detail dt { color: #687384; font-size: 12px; font-weight: 800; text-transform: uppercase; }
.l3-review-detail dd { margin: 3px 0 0; color: #1d2736; overflow-wrap: anywhere; }
.l3-review-detail pre { white-space: pre-wrap; margin: 0; font: 12px/1.5 ui-monospace, SFMono-Regular, Consolas, monospace; }
.l3-review-field { display: grid; gap: 6px; margin: 12px 0 8px; color: #354155; font-size: 13px; font-weight: 700; }
.l3-review-field input { min-height: 38px; border: 1px solid #d6dce4; border-radius: 7px; padding: 0 10px; font: inherit; }
.l3-review-detail button { margin: 0 8px 8px 0; }
@media (max-width: 900px) {
  .l3-review-grid { grid-template-columns: 1fr; }
}
</style>
