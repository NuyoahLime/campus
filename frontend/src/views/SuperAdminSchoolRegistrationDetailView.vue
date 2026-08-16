<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import {
  approveSchoolRegistration,
  getSchoolRegistration,
  rejectSchoolRegistration,
  requestSchoolRegistrationSupplement
} from '../api/schoolRegistrations';
import type { SchoolRegistrationDetail, SchoolRegistrationStatus } from '../types/schoolRegistration';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';

const REVIEW_TEXT_MAX_LENGTH = 2000;

const statusLabels: Record<SchoolRegistrationStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  NEED_SUPPLEMENT: '需补充材料',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回'
};

type ReviewAction = 'supplement' | 'approve' | 'reject';

const route = useRoute();
const registration = ref<SchoolRegistrationDetail | null>(null);
const loading = ref(true);
const loadError = ref('');
const notFound = ref(false);
const successMessage = ref('');
const actionError = ref('');
const activeDialog = ref<ReviewAction | null>(null);
const submittingAction = ref<ReviewAction | null>(null);
const supplementComment = ref('');
const approveComment = ref('');
const rejectReason = ref('');
const fieldError = ref('');
const registrationId = computed(() => String(route.params.id ?? ''));
const canReview = computed(() => registration.value?.status === 'SUBMITTED');
const isSubmitting = computed(() => submittingAction.value !== null);

function displayValue(value: string | null | undefined): string {
  return value?.trim() || '未提供';
}

function formatDate(value: string | null): string {
  if (!value) return '未发生';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '未提供';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date);
}

async function loadDetail(options: { preserveContent?: boolean } = {}) {
  if (!options.preserveContent) loading.value = true;
  loadError.value = '';
  notFound.value = false;
  try {
    registration.value = await getSchoolRegistration(registrationId.value);
  } catch (error) {
    if (!options.preserveContent) registration.value = null;
    notFound.value = error instanceof ApiError && error.status === 404;
    loadError.value = notFound.value
      ? '未找到该学校入驻申请。'
      : error instanceof ApiError && error.status === 403
        ? '当前账号已无平台治理权限，请重新登录或联系管理员。'
        : '加载学校入驻申请详情失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function openDialog(action: ReviewAction) {
  if (!canReview.value || isSubmitting.value) return;
  activeDialog.value = action;
  actionError.value = '';
  fieldError.value = '';
  supplementComment.value = '';
  approveComment.value = '';
  rejectReason.value = '';
}

function closeDialog() {
  if (isSubmitting.value) return;
  activeDialog.value = null;
  actionError.value = '';
  fieldError.value = '';
}

function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return '操作失败，请稍后重试。';
  if (error.status === 400) return '提交内容不符合要求，请检查后重试。';
  if (error.status === 401) return '登录状态已失效，请重新登录。';
  if (error.status === 403) return '当前账号已无平台治理权限，请重新登录或联系管理员。';
  if (error.status === 404) return '该申请已不存在，请返回列表重新确认。';
  if (error.status === 409 && error.code === 'SCHOOL_UNIFIED_CODE_CONFLICT') {
    return '相同统一识别编码的学校已存在，请核对申请资料。页面已刷新。';
  }
  if (error.status === 409) return '申请状态可能已被其他管理员更新，页面已刷新。';
  return '操作失败，请稍后重试。';
}

async function submitAction(action: ReviewAction, payload: string) {
  if (isSubmitting.value) return;
  actionError.value = '';
  successMessage.value = '';
  submittingAction.value = action;
  try {
    if (action === 'supplement') {
      await requestSchoolRegistrationSupplement(registrationId.value, payload);
    } else if (action === 'approve') {
      await approveSchoolRegistration(registrationId.value, payload || undefined);
    } else {
      await rejectSchoolRegistration(registrationId.value, payload);
    }
    activeDialog.value = null;
    successMessage.value = action === 'supplement'
      ? '已要求申请人补充材料。'
      : action === 'approve'
        ? '审核已通过，学校记录已创建，当前为待启用状态。'
        : '学校入驻申请已驳回。';
    await loadDetail({ preserveContent: true });
  } catch (error) {
    actionError.value = errorMessage(error);
    if (error instanceof ApiError && error.status === 409) {
      await loadDetail({ preserveContent: true });
    }
  } finally {
    submittingAction.value = null;
  }
}

async function confirmSupplement() {
  const comment = supplementComment.value.trim();
  fieldError.value = !comment ? '请输入补充说明。' : '';
  if (!fieldError.value) await submitAction('supplement', comment);
}

async function confirmApprove() {
  const comment = approveComment.value.trim();
  if (comment.length > REVIEW_TEXT_MAX_LENGTH) {
    fieldError.value = `审核意见不能超过 ${REVIEW_TEXT_MAX_LENGTH} 个字符。`;
    return;
  }
  await submitAction('approve', comment);
}

async function confirmReject() {
  const reason = rejectReason.value.trim();
  fieldError.value = !reason ? '请输入驳回原因。' : '';
  if (!fieldError.value) await submitAction('reject', reason);
}

onMounted(() => { void loadDetail(); });
</script>

<template>
  <WorkspaceShell
    role-label="超级管理员"
    workspace-title="平台管理工作台"
    :page-title="registration?.schoolName ?? '学校入驻申请详情'"
    description="查看学校入驻申请提交的完整资料和处理记录。"
    home-path="/super-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <RouterLink class="registration-back-link" to="/super-admin/school-registrations">返回学校入驻申请</RouterLink>

    <div v-if="loading" class="registration-detail-state" role="status">
      <span class="registration-spinner" aria-hidden="true"></span>
      <strong>正在加载申请详情...</strong>
    </div>

    <div v-else-if="loadError && !registration" class="registration-detail-state registration-state-error" role="alert">
      <strong>{{ loadError }}</strong>
      <RouterLink v-if="notFound" class="secondary-button" to="/super-admin/school-registrations">返回申请列表</RouterLink>
      <button v-else class="secondary-button" type="button" @click="loadDetail()">重新加载</button>
    </div>

    <template v-else-if="registration">
      <p v-if="successMessage" class="message message-success registration-review-message" role="status">{{ successMessage }}</p>
      <p v-if="loadError" class="message message-error registration-review-message" role="alert">{{ loadError }}</p>

      <div class="registration-detail-summary">
        <div><span>申请状态</span><strong>{{ statusLabels[registration.status] }}</strong></div>
        <span class="registration-status" :data-status="registration.status">{{ statusLabels[registration.status] }}</span>
      </div>

      <section v-if="canReview" class="registration-review-panel" aria-labelledby="registration-review-title">
        <div>
          <h2 id="registration-review-title">审核操作</h2>
          <p>请根据当前已提交资料作出审核决定。</p>
        </div>
        <div class="registration-review-actions">
          <button class="secondary-button" type="button" @click="openDialog('supplement')">要求补充材料</button>
          <button class="primary-button" type="button" @click="openDialog('approve')">审核通过</button>
          <button class="registration-danger-button" type="button" @click="openDialog('reject')">驳回申请</button>
        </div>
      </section>

      <section v-else-if="registration.status === 'NEED_SUPPLEMENT'" class="registration-review-notice" aria-label="补充材料状态">
        <strong>需要补充材料</strong><p>{{ displayValue(registration.reviewComment) }}</p>
      </section>
      <section v-else-if="registration.status === 'APPROVED'" class="registration-review-notice registration-review-approved" aria-label="审核通过状态">
        <strong>学校记录已创建</strong><p>学校当前状态：待启用</p>
      </section>

      <section class="registration-detail-section" aria-labelledby="registration-basic-title">
        <header><h2 id="registration-basic-title">基本信息</h2></header>
        <dl class="registration-detail-grid">
          <div><dt>学校名称</dt><dd>{{ registration.schoolName }}</dd></div>
          <div><dt>学校类型</dt><dd>{{ registration.schoolType }}</dd></div>
          <div><dt>地区</dt><dd>{{ registration.region }}</dd></div>
          <div><dt>地址</dt><dd>{{ registration.address }}</dd></div>
          <div><dt>统一识别类型</dt><dd>{{ registration.unifiedCodeType }}</dd></div>
          <div><dt>统一识别编码</dt><dd>{{ displayValue(registration.unifiedCode) }}</dd></div>
        </dl>
      </section>

      <section class="registration-detail-section" aria-labelledby="registration-contact-title">
        <header><h2 id="registration-contact-title">联系人信息</h2></header>
        <dl class="registration-detail-grid">
          <div><dt>联系人</dt><dd>{{ registration.contactName }}</dd></div>
          <div><dt>联系电话</dt><dd>{{ registration.contactPhone }}</dd></div>
          <div><dt>联系邮箱</dt><dd>{{ registration.contactEmail }}</dd></div>
        </dl>
      </section>

      <section class="registration-detail-section" aria-labelledby="registration-application-title">
        <header><h2 id="registration-application-title">申请信息</h2></header>
        <dl class="registration-detail-grid registration-detail-grid-wide">
          <div><dt>申请说明</dt><dd>{{ displayValue(registration.description) }}</dd></div>
          <div><dt>证明材料</dt><dd>{{ registration.evidenceSubmitted ? '已提交证明材料' : '未提交证明材料' }}</dd></div>
        </dl>
      </section>

      <section class="registration-detail-section" aria-labelledby="registration-system-title">
        <header><h2 id="registration-system-title">系统信息</h2></header>
        <dl class="registration-detail-grid">
          <div><dt>申请 ID</dt><dd>{{ registration.id }}</dd></div>
          <div><dt>当前状态</dt><dd>{{ statusLabels[registration.status] }}</dd></div>
          <div><dt>提交时间</dt><dd>{{ formatDate(registration.createdAt) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatDate(registration.updatedAt) }}</dd></div>
          <div><dt>创建学校 ID</dt><dd>{{ displayValue(registration.createdSchoolId) }}</dd></div>
          <div><dt>审核人 ID</dt><dd>{{ displayValue(registration.reviewedBy) }}</dd></div>
          <div><dt>审核时间</dt><dd>{{ formatDate(registration.reviewedAt) }}</dd></div>
          <div><dt>审核意见</dt><dd>{{ displayValue(registration.reviewComment) }}</dd></div>
          <div v-if="registration.rejectReason"><dt>驳回原因</dt><dd>{{ registration.rejectReason }}</dd></div>
        </dl>
      </section>
    </template>

    <div v-if="activeDialog" class="modal-backdrop" @click.self="closeDialog">
      <section class="review-modal registration-review-modal" role="dialog" aria-modal="true" :aria-labelledby="`${activeDialog}-dialog-title`">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">学校入驻审核</p>
            <h2 :id="`${activeDialog}-dialog-title`">
              {{ activeDialog === 'supplement' ? '要求补充材料' : activeDialog === 'approve' ? '审核通过' : '驳回学校入驻申请' }}
            </h2>
          </div>
          <button class="modal-close" type="button" title="关闭" :disabled="isSubmitting" @click="closeDialog">×</button>
        </div>

        <template v-if="activeDialog === 'supplement'">
          <p class="modal-copy">请明确说明申请人需要补充或修改的材料。</p>
          <label class="reject-reason-field"><span>补充说明</span><textarea v-model="supplementComment" :maxlength="REVIEW_TEXT_MAX_LENGTH" :disabled="isSubmitting" rows="5"></textarea><span class="character-count">{{ supplementComment.length }} / {{ REVIEW_TEXT_MAX_LENGTH }}</span></label>
        </template>

        <template v-else-if="activeDialog === 'approve'">
          <p class="modal-copy">审核通过后系统将创建学校记录，初始状态为“待启用”。</p>
          <dl class="registration-confirmation-list">
            <div><dt>学校名称</dt><dd>{{ registration?.schoolName }}</dd></div>
            <div><dt>统一识别类型</dt><dd>{{ registration?.unifiedCodeType }}</dd></div>
            <div><dt>统一识别编码</dt><dd>{{ displayValue(registration?.unifiedCode) }}</dd></div>
            <div><dt>地区</dt><dd>{{ registration?.region }}</dd></div>
          </dl>
          <label class="reject-reason-field"><span>审核意见（选填）</span><textarea v-model="approveComment" :maxlength="REVIEW_TEXT_MAX_LENGTH" :disabled="isSubmitting" rows="4"></textarea><span class="character-count">{{ approveComment.length }} / {{ REVIEW_TEXT_MAX_LENGTH }}</span></label>
        </template>

        <template v-else>
          <p class="modal-copy">该操作会使当前申请进入“已拒绝”状态，且不能在此页面恢复。</p>
          <label class="reject-reason-field"><span>驳回原因</span><textarea v-model="rejectReason" :maxlength="REVIEW_TEXT_MAX_LENGTH" :disabled="isSubmitting" rows="5"></textarea><span class="character-count">{{ rejectReason.length }} / {{ REVIEW_TEXT_MAX_LENGTH }}</span></label>
        </template>

        <small v-if="fieldError" class="field-error registration-dialog-error">{{ fieldError }}</small>
        <p v-if="actionError" class="message message-error" role="alert">{{ actionError }}</p>
        <div class="modal-actions">
          <button class="secondary-button" type="button" :disabled="isSubmitting" @click="closeDialog">取消</button>
          <button v-if="activeDialog === 'supplement'" class="primary-button" type="button" :disabled="isSubmitting" @click="confirmSupplement"><span v-if="isSubmitting" class="button-spinner" aria-hidden="true"></span>{{ isSubmitting ? '提交中...' : '确认要求补充' }}</button>
          <button v-else-if="activeDialog === 'approve'" class="primary-button" type="button" :disabled="isSubmitting" @click="confirmApprove"><span v-if="isSubmitting" class="button-spinner" aria-hidden="true"></span>{{ isSubmitting ? '审核中...' : '确认通过' }}</button>
          <button v-else class="registration-danger-button" type="button" :disabled="isSubmitting" @click="confirmReject"><span v-if="isSubmitting" class="button-spinner" aria-hidden="true"></span>{{ isSubmitting ? '驳回中...' : '确认驳回' }}</button>
        </div>
      </section>
    </div>
  </WorkspaceShell>
</template>
