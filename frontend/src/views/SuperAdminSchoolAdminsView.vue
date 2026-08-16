<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import {
  createSchoolAdminInvitation,
  getGovernanceSchool,
  listSchoolAdminInvitations,
  listSchoolAdmins,
  regenerateSchoolAdminInvitation,
  revokeSchoolAdminInvitation
} from '../api/schoolGovernance';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type {
  GovernanceSchoolDetail,
  InvitationCommandResponse,
  InvitationStatus,
  PageResponse,
  SchoolAdminAccount,
  SchoolAdminInvitation
} from '../types/schoolGovernance';

type DialogName = 'create' | 'revoke' | 'regenerate';

const route = useRoute();
const schoolId = computed(() => String(route.params.id ?? ''));
const school = ref<GovernanceSchoolDetail | null>(null);
const admins = ref<SchoolAdminAccount[]>([]);
const invitations = ref<PageResponse<SchoolAdminInvitation> | null>(null);
const invitationStatus = ref<InvitationStatus | ''>('');
const loading = ref(true);
const invitationLoading = ref(false);
const loadError = ref('');
const actionError = ref('');
const actionSuccess = ref('');
const activeDialog = ref<DialogName | null>(null);
const targetInvitation = ref<SchoolAdminInvitation | null>(null);
const submitting = ref(false);
const createUsername = ref('');
const createExpiresAt = ref('');
const fieldError = ref('');
const oneTimeResult = ref<InvitationCommandResponse | null>(null);
const copied = ref(false);

const invitationStatusOptions: Array<{ value: InvitationStatus; label: string }> = [
  { value: 'PENDING', label: '待激活' },
  { value: 'ACCEPTED', label: '已接受' },
  { value: 'REVOKED', label: '已撤销' },
  { value: 'EXPIRED', label: '已过期' }
];

function invitationStatusLabel(invitation: SchoolAdminInvitation): string {
  if (invitation.expired) return '已过期';
  return invitationStatusOptions.find((item) => item.value === invitation.status)?.label ?? invitation.status;
}

function accountStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    NORMAL: '正常', PENDING_ACTIVATION: '待激活', LOCKED: '已锁定', DISABLED: '已停用'
  };
  return labels[status] ?? status;
}

function membershipStatusLabel(status: string): string {
  return status === 'ACTIVE' ? '有效' : status === 'ENDED' ? '已结束' : status;
}

function formatDate(value: string | null): string {
  if (!value) return '未发生';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '未提供';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date);
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return '当前账号无平台学校管理权限。';
    if (error.code === 'INVITATION_NOT_PENDING') return '该邀请已不处于待激活状态，请刷新后重试。';
    if (error.code === 'ACCOUNT_ALREADY_ACTIVATED') return '该账号已经完成激活。';
    if (error.code === 'USERNAME_ALREADY_EXISTS') return '该用户名已被使用。';
    if (error.code === 'ACTIVE_INVITATION_ALREADY_EXISTS') return '该账号已有待激活邀请。';
    if (error.code === 'INVALID_INVITATION_EXPIRY') return '邀请有效期需在 15 分钟至 30 天之间。';
    if (error.code === 'SCHOOL_NOT_ELIGIBLE') return '当前学校状态不允许创建管理员邀请。';
  }
  return fallback;
}

async function loadPage() {
  loading.value = true;
  loadError.value = '';
  try {
    const [schoolResult, adminResult, invitationResult] = await Promise.all([
      getGovernanceSchool(schoolId.value),
      listSchoolAdmins(schoolId.value),
      listSchoolAdminInvitations(schoolId.value)
    ]);
    school.value = schoolResult;
    admins.value = adminResult;
    invitations.value = invitationResult;
  } catch (error) {
    loadError.value = errorMessage(error, '加载学校管理员治理信息失败，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

async function loadInvitations() {
  invitationLoading.value = true;
  actionError.value = '';
  try {
    invitations.value = await listSchoolAdminInvitations(
      schoolId.value,
      0,
      20,
      invitationStatus.value || null
    );
  } catch (error) {
    actionError.value = errorMessage(error, '加载邀请记录失败，请稍后重试。');
  } finally {
    invitationLoading.value = false;
  }
}

function openCreate() {
  createUsername.value = '';
  createExpiresAt.value = '';
  fieldError.value = '';
  actionError.value = '';
  activeDialog.value = 'create';
}

function openInvitationAction(dialog: 'revoke' | 'regenerate', invitation: SchoolAdminInvitation) {
  targetInvitation.value = invitation;
  fieldError.value = '';
  actionError.value = '';
  activeDialog.value = dialog;
}

function closeDialog() {
  if (submitting.value) return;
  activeDialog.value = null;
  targetInvitation.value = null;
  fieldError.value = '';
}

function showOneTimeCode(result: InvitationCommandResponse) {
  activeDialog.value = null;
  targetInvitation.value = null;
  oneTimeResult.value = result;
  copied.value = false;
}

function closeOneTimeCode() {
  oneTimeResult.value = null;
  copied.value = false;
}

async function submitCreate() {
  fieldError.value = '';
  if (!createUsername.value.trim()) {
    fieldError.value = '请输入学校管理员用户名。';
    return;
  }
  const expiresAt = createExpiresAt.value
    ? new Date(createExpiresAt.value).toISOString()
    : null;
  submitting.value = true;
  try {
    const result = await createSchoolAdminInvitation(
      schoolId.value,
      createUsername.value,
      expiresAt
    );
    await loadInvitations();
    showOneTimeCode(result);
  } catch (error) {
    fieldError.value = errorMessage(error, '创建邀请失败，请稍后重试。');
  } finally {
    submitting.value = false;
  }
}

async function submitRevoke() {
  if (!targetInvitation.value) return;
  submitting.value = true;
  fieldError.value = '';
  try {
    await revokeSchoolAdminInvitation(targetInvitation.value.invitationId);
    activeDialog.value = null;
    targetInvitation.value = null;
    actionSuccess.value = '学校管理员邀请已撤销。';
    await loadInvitations();
  } catch (error) {
    fieldError.value = errorMessage(error, '撤销邀请失败，请稍后重试。');
  } finally {
    submitting.value = false;
  }
}

async function submitRegenerate() {
  if (!targetInvitation.value) return;
  submitting.value = true;
  fieldError.value = '';
  try {
    const result = await regenerateSchoolAdminInvitation(targetInvitation.value.invitationId);
    await loadInvitations();
    showOneTimeCode(result);
  } catch (error) {
    fieldError.value = errorMessage(error, '重新生成邀请失败，请稍后重试。');
  } finally {
    submitting.value = false;
  }
}

async function copyCode() {
  if (!oneTimeResult.value) return;
  try {
    await navigator.clipboard.writeText(oneTimeResult.value.invitationCode);
    copied.value = true;
  } catch {
    copied.value = false;
  }
}

watch(invitationStatus, () => void loadInvitations());
onMounted(() => void loadPage());
</script>

<template>
  <WorkspaceShell
    role-label="超级管理员"
    workspace-title="平台管理工作台"
    :page-title="school ? `${school.name} · 学校管理员` : '学校管理员'"
    description="分别查看已配置的学校管理员账号和邀请记录。"
    home-path="/super-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <RouterLink class="registration-back-link" :to="`/super-admin/schools/${schoolId}`">返回学校详情</RouterLink>

    <div v-if="loading" class="registration-detail-state" role="status">
      <span class="registration-spinner" aria-hidden="true"></span>
      <strong>正在加载管理员信息...</strong>
    </div>
    <div v-else-if="loadError" class="registration-detail-state registration-state-error" role="alert">
      <strong>{{ loadError }}</strong>
      <button class="secondary-button" type="button" @click="loadPage">重新加载</button>
    </div>

    <template v-else>
      <p v-if="actionSuccess" class="message message-success governance-action-message" role="status">{{ actionSuccess }}</p>
      <p v-if="actionError" class="message message-error governance-action-message" role="alert">{{ actionError }}</p>

      <section class="governance-panel" aria-labelledby="school-admin-account-title">
        <div class="governance-section-heading">
          <div>
            <h2 id="school-admin-account-title">学校管理员账号</h2>
            <p>共 {{ admins.length }} 条学校管理员 membership 记录</p>
          </div>
        </div>
        <div v-if="admins.length === 0" class="governance-empty-state">
          <strong>暂无学校管理员账号</strong>
          <p>激活邀请后，对应账号会显示在此处。</p>
        </div>
        <div v-else class="governance-table-wrap">
          <table class="governance-table governance-admin-table">
            <thead><tr><th>用户名</th><th>账号状态</th><th>成员关系状态</th><th>开始时间</th><th>锁定至</th></tr></thead>
            <tbody>
              <tr v-for="admin in admins" :key="`${admin.userId}-${admin.startedAt}`">
                <td><strong>{{ admin.username }}</strong><small>{{ admin.userId }}</small></td>
                <td>{{ accountStatusLabel(admin.accountStatus) }}</td>
                <td>{{ membershipStatusLabel(admin.membershipStatus) }}</td>
                <td>{{ formatDate(admin.startedAt) }}</td>
                <td>{{ formatDate(admin.lockedUntil) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="admins.length" class="governance-card-list">
          <article v-for="admin in admins" :key="`${admin.userId}-card`" class="governance-card">
            <strong>{{ admin.username }}</strong>
            <dl>
              <div><dt>账号状态</dt><dd>{{ accountStatusLabel(admin.accountStatus) }}</dd></div>
              <div><dt>成员关系</dt><dd>{{ membershipStatusLabel(admin.membershipStatus) }}</dd></div>
              <div><dt>开始时间</dt><dd>{{ formatDate(admin.startedAt) }}</dd></div>
              <div><dt>锁定至</dt><dd>{{ formatDate(admin.lockedUntil) }}</dd></div>
            </dl>
          </article>
        </div>
      </section>

      <section class="governance-panel" aria-labelledby="school-admin-invitation-title">
        <div class="governance-section-heading governance-section-actions">
          <div>
            <h2 id="school-admin-invitation-title">学校管理员邀请</h2>
            <p>邀请与已激活账号采用各自独立的状态。</p>
          </div>
          <div class="governance-invitation-tools">
            <label class="governance-filter">
              <span>邀请状态</span>
              <select v-model="invitationStatus" :disabled="invitationLoading">
                <option value="">全部状态</option>
                <option v-for="option in invitationStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <button class="primary-button" type="button" @click="openCreate">创建邀请</button>
          </div>
        </div>

        <div v-if="invitationLoading" class="governance-empty-state" role="status">
          <span class="registration-spinner" aria-hidden="true"></span>
          <strong>正在加载邀请记录...</strong>
        </div>
        <div v-else-if="!invitations?.items.length" class="governance-empty-state">
          <strong>暂无邀请记录</strong>
          <p>可以为该学校创建学校管理员邀请。</p>
        </div>
        <template v-else>
          <div class="governance-table-wrap">
            <table class="governance-table governance-invitation-table">
              <thead><tr><th>用户名</th><th>状态</th><th>有效期至</th><th>创建时间</th><th>处理时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="invitation in invitations.items" :key="invitation.invitationId">
                  <td><strong>{{ invitation.username }}</strong></td>
                  <td><span class="registration-status" :data-status="invitation.expired ? 'EXPIRED' : invitation.status">{{ invitationStatusLabel(invitation) }}</span></td>
                  <td>{{ formatDate(invitation.expiresAt) }}</td>
                  <td>{{ formatDate(invitation.createdAt) }}</td>
                  <td>{{ formatDate(invitation.acceptedAt || invitation.revokedAt) }}</td>
                  <td>
                    <div v-if="invitation.status === 'PENDING'" class="governance-row-actions">
                      <button class="secondary-button" type="button" @click="openInvitationAction('regenerate', invitation)">重新生成</button>
                      <button class="governance-danger-link" type="button" @click="openInvitationAction('revoke', invitation)">撤销</button>
                    </div>
                    <span v-else class="governance-no-action">无可用操作</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="governance-card-list">
            <article v-for="invitation in invitations.items" :key="`${invitation.invitationId}-card`" class="governance-card">
              <div class="registration-card-heading">
                <strong>{{ invitation.username }}</strong>
                <span class="registration-status" :data-status="invitation.expired ? 'EXPIRED' : invitation.status">{{ invitationStatusLabel(invitation) }}</span>
              </div>
              <dl>
                <div><dt>有效期至</dt><dd>{{ formatDate(invitation.expiresAt) }}</dd></div>
                <div><dt>创建时间</dt><dd>{{ formatDate(invitation.createdAt) }}</dd></div>
                <div><dt>处理时间</dt><dd>{{ formatDate(invitation.acceptedAt || invitation.revokedAt) }}</dd></div>
              </dl>
              <div v-if="invitation.status === 'PENDING'" class="governance-card-actions">
                <button class="secondary-button" type="button" @click="openInvitationAction('regenerate', invitation)">重新生成</button>
                <button class="governance-danger-link" type="button" @click="openInvitationAction('revoke', invitation)">撤销</button>
              </div>
            </article>
          </div>
        </template>
      </section>
    </template>

    <div v-if="activeDialog" class="modal-backdrop" @click.self="closeDialog">
      <section class="review-modal governance-dialog" role="dialog" aria-modal="true" aria-labelledby="governance-dialog-title">
        <div class="modal-heading">
          <div>
            <p>SCHOOL ADMIN INVITATION</p>
            <h2 id="governance-dialog-title">{{ activeDialog === 'create' ? '创建学校管理员邀请' : activeDialog === 'revoke' ? '撤销邀请' : '重新生成邀请' }}</h2>
          </div>
          <button class="modal-close" type="button" title="关闭" :disabled="submitting" @click="closeDialog">×</button>
        </div>

        <form v-if="activeDialog === 'create'" class="governance-dialog-form" @submit.prevent="submitCreate">
          <label class="field-group">
            <span>用户名</span>
            <input v-model="createUsername" type="text" maxlength="100" autocomplete="off" :disabled="submitting">
          </label>
          <label class="field-group">
            <span>有效期至（可选）</span>
            <input v-model="createExpiresAt" type="datetime-local" :disabled="submitting">
            <small class="field-hint">不填写时使用系统默认有效期。</small>
          </label>
          <small v-if="fieldError" class="field-error">{{ fieldError }}</small>
          <div class="modal-actions">
            <button class="secondary-button" type="button" :disabled="submitting" @click="closeDialog">取消</button>
            <button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '创建中...' : '创建邀请' }}</button>
          </div>
        </form>

        <template v-else>
          <p class="modal-copy">
            {{ activeDialog === 'revoke'
              ? `撤销后，${targetInvitation?.username} 将无法使用该邀请完成激活。`
              : `旧邀请将被撤销，并为 ${targetInvitation?.username} 生成新的邀请码。` }}
          </p>
          <small v-if="fieldError" class="field-error">{{ fieldError }}</small>
          <div class="modal-actions">
            <button class="secondary-button" type="button" :disabled="submitting" @click="closeDialog">取消</button>
            <button v-if="activeDialog === 'revoke'" class="registration-danger-button" type="button" :disabled="submitting" @click="submitRevoke">{{ submitting ? '撤销中...' : '确认撤销' }}</button>
            <button v-else class="primary-button" type="button" :disabled="submitting" @click="submitRegenerate">{{ submitting ? '生成中...' : '确认重新生成' }}</button>
          </div>
        </template>
      </section>
    </div>

    <div v-if="oneTimeResult" class="modal-backdrop">
      <section class="review-modal governance-dialog" role="dialog" aria-modal="true" aria-labelledby="one-time-code-title">
        <div class="modal-heading">
          <div><p>ONE-TIME CODE</p><h2 id="one-time-code-title">学校管理员邀请码</h2></div>
        </div>
        <p class="message message-warning governance-code-warning">邀请码仅显示一次。关闭后无法再次查看，如遗失请重新生成。</p>
        <dl class="governance-code-summary">
          <div><dt>用户名</dt><dd>{{ oneTimeResult.username }}</dd></div>
          <div><dt>有效期至</dt><dd>{{ formatDate(oneTimeResult.expiresAt) }}</dd></div>
        </dl>
        <div class="governance-code-value" aria-label="一次性邀请码">{{ oneTimeResult.invitationCode }}</div>
        <p v-if="copied" class="governance-copy-status" role="status">已复制到剪贴板。</p>
        <div class="modal-actions">
          <button class="secondary-button" type="button" @click="copyCode">复制邀请码</button>
          <button class="primary-button" type="button" @click="closeOneTimeCode">我已安全保存</button>
        </div>
      </section>
    </div>
  </WorkspaceShell>
</template>
