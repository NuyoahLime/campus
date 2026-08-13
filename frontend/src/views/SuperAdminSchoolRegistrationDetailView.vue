<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getSchoolRegistration } from '../api/schoolRegistrations';
import type { SchoolRegistrationDetail, SchoolRegistrationStatus } from '../types/schoolRegistration';
import type { WorkspaceNavigationItem } from '../types/workspace';

const navigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/super-admin' },
  { label: '学校治理', to: '/super-admin/school-registrations' },
  { label: '学校管理员', disabled: true },
  { label: '挑战项目', disabled: true },
  { label: '平台运营', disabled: true }
];

const statusLabels: Record<SchoolRegistrationStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  NEED_SUPPLEMENT: '需补充材料',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回'
};

const route = useRoute();
const registration = ref<SchoolRegistrationDetail | null>(null);
const loading = ref(true);
const loadError = ref('');
const notFound = ref(false);
const registrationId = computed(() => String(route.params.id ?? ''));

function displayValue(value: string | null | undefined): string {
  return value?.trim() || '未提供';
}

function formatDate(value: string | null): string {
  if (!value) return '未发生';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '未提供';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

async function loadDetail() {
  loading.value = true;
  loadError.value = '';
  notFound.value = false;
  try {
    registration.value = await getSchoolRegistration(registrationId.value);
  } catch (error) {
    registration.value = null;
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

onMounted(() => {
  void loadDetail();
});
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
    <RouterLink class="registration-back-link" to="/super-admin/school-registrations">
      返回学校入驻申请
    </RouterLink>

    <div v-if="loading" class="registration-detail-state" role="status">
      <span class="registration-spinner" aria-hidden="true"></span>
      <strong>正在加载申请详情...</strong>
    </div>

    <div v-else-if="loadError" class="registration-detail-state registration-state-error" role="alert">
      <strong>{{ loadError }}</strong>
      <RouterLink v-if="notFound" class="secondary-button" to="/super-admin/school-registrations">
        返回申请列表
      </RouterLink>
      <button v-else class="secondary-button" type="button" @click="loadDetail">重新加载</button>
    </div>

    <template v-else-if="registration">
      <div class="registration-detail-summary">
        <div>
          <span>申请状态</span>
          <strong>{{ statusLabels[registration.status] }}</strong>
        </div>
        <span class="registration-status" :data-status="registration.status">
          {{ statusLabels[registration.status] }}
        </span>
      </div>

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
          <div v-if="registration.rejectReason"><dt>拒绝原因</dt><dd>{{ registration.rejectReason }}</dd></div>
        </dl>
      </section>
    </template>
  </WorkspaceShell>
</template>
