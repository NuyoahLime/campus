<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getGovernanceSchool, updateSchoolLifecycle } from '../api/schoolGovernance';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type {
  GovernanceSchoolDetail,
  SchoolLifecycleAction,
  SchoolStatus
} from '../types/schoolGovernance';

interface LifecycleOption {
  action: SchoolLifecycleAction;
  label: string;
  confirmLabel: string;
  targetStatus: SchoolStatus;
  impact: string;
  danger?: boolean;
}

const route = useRoute();
const school = ref<GovernanceSchoolDetail | null>(null);
const loading = ref(true);
const loadError = ref('');
const actionSuccess = ref('');
const activeAction = ref<SchoolLifecycleAction | null>(null);
const reason = ref('');
const fieldError = ref('');
const submitting = ref(false);
const schoolId = computed(() => String(route.params.id ?? ''));

const statusLabels: Record<SchoolStatus, string> = {
  PENDING_ENABLE: '待启用',
  NORMAL: '正常',
  SUSPENDED: '已暂停',
  DISABLED: '已停用'
};

const lifecycleOptions: Record<SchoolStatus, LifecycleOption[]> = {
  PENDING_ENABLE: [{
    action: 'activate',
    label: '启用学校',
    confirmLabel: '确认启用',
    targetStatus: 'NORMAL',
    impact: '启用后学校进入正常状态。此操作要求至少两名正常且有效的学校管理员。'
  }],
  NORMAL: [
    {
      action: 'suspend',
      label: '暂停学校',
      confirmLabel: '确认暂停',
      targetStatus: 'SUSPENDED',
      impact: '暂停后学校进入暂停状态。其他业务模块的运行限制仍以各模块当前实现为准。'
    },
    {
      action: 'disable',
      label: '停用学校',
      confirmLabel: '确认停用',
      targetStatus: 'DISABLED',
      impact: '停用后学校进入停用状态，历史数据会保留。其他业务模块的访问限制仍以各模块当前实现为准。',
      danger: true
    }
  ],
  SUSPENDED: [
    {
      action: 'restore',
      label: '恢复学校',
      confirmLabel: '确认恢复',
      targetStatus: 'NORMAL',
      impact: '恢复后学校重新进入正常状态，并再次校验至少两名正常且有效的学校管理员。'
    },
    {
      action: 'disable',
      label: '停用学校',
      confirmLabel: '确认停用',
      targetStatus: 'DISABLED',
      impact: '停用后学校进入停用状态，历史数据会保留。其他业务模块的访问限制仍以各模块当前实现为准。',
      danger: true
    }
  ],
  DISABLED: [{
    action: 're-enable',
    label: '重新启用',
    confirmLabel: '确认重新启用',
    targetStatus: 'PENDING_ENABLE',
    impact: '重新启用后学校回到待启用状态，不会直接进入正常状态。'
  }]
};

const availableActions = computed(() => school.value ? lifecycleOptions[school.value.status] : []);
const selectedAction = computed(() => {
  if (!school.value || !activeAction.value) return null;
  return lifecycleOptions[school.value.status].find((item) => item.action === activeAction.value) ?? null;
});
const adminRequirementMet = computed(() => (school.value?.normalActiveSchoolAdminCount ?? 0) >= 2);

function value(value: string | null | undefined): string {
  return value?.trim() || '未提供';
}

function formatDate(valueToFormat: string): string {
  const date = new Date(valueToFormat);
  if (Number.isNaN(date.getTime())) return '未提供';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date);
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'SCHOOL_NOT_FOUND') return '未找到该学校。';
  if (error instanceof ApiError && error.status === 403) return '当前账号无平台学校管理权限。';
  return '加载学校详情失败，请稍后重试。';
}

function lifecycleErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'SCHOOL_ADMIN_CONFIGURATION_INSUFFICIENT') {
      return '学校管理员配置不足，至少需要两名正常且有效的学校管理员。';
    }
    if (error.code === 'INVALID_SCHOOL_STATE_TRANSITION') {
      return '学校状态已发生变化，当前操作不再适用。请关闭弹窗并刷新后重试。';
    }
    if (error.code === 'SCHOOL_LIFECYCLE_CONFLICT') {
      return '其他操作正在更新该学校状态，请稍后关闭弹窗并刷新后重试。';
    }
    if (error.code === 'SCHOOL_NOT_FOUND' || error.status === 404) return '未找到该学校。';
    if (error.status === 403) return '当前账号无平台学校管理权限。';
    if (error.code === 'SCHOOL_LIFECYCLE_REASON_INVALID' || error.status === 400) {
      return '请输入 2 至 500 个字符的操作原因。';
    }
    if (error.status === 409) return '学校状态更新发生冲突，请关闭弹窗并刷新后重试。';
  }
  return '学校状态更新失败，请稍后重试。';
}

function openLifecycleDialog(action: SchoolLifecycleAction) {
  activeAction.value = action;
  reason.value = '';
  fieldError.value = '';
  actionSuccess.value = '';
}

function closeLifecycleDialog() {
  if (submitting.value) return;
  activeAction.value = null;
  reason.value = '';
  fieldError.value = '';
}

async function submitLifecycleAction() {
  if (!activeAction.value || !selectedAction.value) return;
  const normalizedReason = reason.value.trim();
  if (normalizedReason.length < 2 || normalizedReason.length > 500) {
    fieldError.value = '请输入 2 至 500 个字符的操作原因。';
    return;
  }

  const successMessage = `${selectedAction.value.label}操作已完成。`;
  submitting.value = true;
  fieldError.value = '';
  try {
    await updateSchoolLifecycle(schoolId.value, activeAction.value, normalizedReason);
    activeAction.value = null;
    reason.value = '';
    await loadSchool();
    actionSuccess.value = successMessage;
  } catch (error) {
    fieldError.value = lifecycleErrorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function loadSchool() {
  loading.value = true;
  loadError.value = '';
  try {
    school.value = await getGovernanceSchool(schoolId.value);
  } catch (error) {
    school.value = null;
    loadError.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

onMounted(() => void loadSchool());
</script>

<template>
  <WorkspaceShell
    role-label="超级管理员"
    workspace-title="平台管理工作台"
    :page-title="school?.name || '学校详情'"
    description="查看学校主数据、联系信息和学校管理员配置情况。"
    home-path="/super-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <RouterLink class="registration-back-link" to="/super-admin/schools">返回学校管理</RouterLink>

    <div v-if="loading" class="registration-detail-state" role="status">
      <span class="registration-spinner" aria-hidden="true"></span>
      <strong>正在加载学校详情...</strong>
    </div>
    <div v-else-if="loadError" class="registration-detail-state registration-state-error" role="alert">
      <strong>{{ loadError }}</strong>
      <button class="secondary-button" type="button" @click="loadSchool">重新加载</button>
    </div>

    <template v-else-if="school">
      <section class="governance-detail-summary">
        <div>
          <span>学校状态</span>
          <strong>{{ statusLabels[school.status] }}</strong>
        </div>
        <span class="registration-status" :data-status="school.status">{{ statusLabels[school.status] }}</span>
      </section>

      <p v-if="actionSuccess" class="message message-success governance-action-message" role="status">
        {{ actionSuccess }}
      </p>

      <section class="governance-lifecycle-panel" aria-labelledby="school-lifecycle-title">
        <div class="governance-section-heading governance-section-actions">
          <div>
            <h2 id="school-lifecycle-title">学校状态管理</h2>
            <p>状态变更需要填写原因，并记录平台治理审计。</p>
          </div>
          <RouterLink class="secondary-button governance-inline-action" :to="`/super-admin/schools/${school.id}/admins`">
            管理学校管理员
          </RouterLink>
        </div>

        <dl class="governance-lifecycle-summary">
          <div>
            <dt>当前状态</dt>
            <dd><span class="registration-status" :data-status="school.status">{{ statusLabels[school.status] }}</span></dd>
          </div>
          <div>
            <dt>正常学校管理员</dt>
            <dd><strong>{{ school.normalActiveSchoolAdminCount }} / 2</strong></dd>
          </div>
          <div>
            <dt>正常启用条件</dt>
            <dd :class="adminRequirementMet ? 'governance-condition-met' : 'governance-condition-unmet'">
              {{ adminRequirementMet ? '已满足' : '尚未满足' }}
            </dd>
          </div>
        </dl>

        <p v-if="!adminRequirementMet" class="message message-warning governance-count-warning">
          至少需要两名正常且有效的学校管理员，才能启用或恢复学校。
        </p>

        <div class="governance-lifecycle-actions">
          <button
            v-for="option in availableActions"
            :key="option.action"
            :class="option.danger ? 'registration-danger-button' : 'primary-button'"
            type="button"
            :disabled="option.action === 'activate' && !adminRequirementMet"
            @click="openLifecycleDialog(option.action)"
          >
            {{ option.label }}
          </button>
        </div>
      </section>

      <section class="registration-detail-section" aria-labelledby="school-basic-title">
        <header><h2 id="school-basic-title">基本信息</h2></header>
        <dl class="registration-detail-grid">
          <div><dt>学校名称</dt><dd>{{ school.name }}</dd></div>
          <div><dt>学校状态</dt><dd>{{ statusLabels[school.status] }}</dd></div>
          <div><dt>内部编码</dt><dd>{{ school.internalCode }}</dd></div>
          <div><dt>学校类型</dt><dd>{{ school.schoolType }}</dd></div>
          <div><dt>地区</dt><dd>{{ school.region }}</dd></div>
          <div><dt>地址</dt><dd>{{ school.address }}</dd></div>
          <div><dt>统一识别类型</dt><dd>{{ school.unifiedCodeType }}</dd></div>
          <div><dt>统一识别编码</dt><dd>{{ value(school.unifiedCode) }}</dd></div>
        </dl>
      </section>

      <section class="registration-detail-section" aria-labelledby="school-contact-title">
        <header><h2 id="school-contact-title">联系信息</h2></header>
        <dl class="registration-detail-grid">
          <div><dt>联系人</dt><dd>{{ school.contactName }}</dd></div>
          <div><dt>联系电话</dt><dd>{{ school.contactPhone }}</dd></div>
          <div><dt>联系邮箱</dt><dd>{{ school.contactEmail }}</dd></div>
          <div><dt>正常学校管理员</dt><dd>{{ school.normalActiveSchoolAdminCount }} 人</dd></div>
        </dl>
      </section>

      <section class="registration-detail-section" aria-labelledby="school-system-title">
        <header><h2 id="school-system-title">系统信息</h2></header>
        <dl class="registration-detail-grid">
          <div><dt>学校 ID</dt><dd>{{ school.id }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatDate(school.createdAt) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatDate(school.updatedAt) }}</dd></div>
        </dl>
      </section>
    </template>

    <div v-if="activeAction && school && selectedAction" class="modal-backdrop" @click.self="closeLifecycleDialog">
      <section
        class="review-modal governance-dialog governance-lifecycle-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="school-lifecycle-dialog-title"
      >
        <div class="modal-heading">
          <div>
            <p>SCHOOL LIFECYCLE</p>
            <h2 id="school-lifecycle-dialog-title">{{ selectedAction.label }}</h2>
          </div>
          <button class="modal-close" type="button" title="关闭" :disabled="submitting" @click="closeLifecycleDialog">×</button>
        </div>

        <dl class="governance-lifecycle-dialog-summary">
          <div><dt>学校</dt><dd>{{ school.name }}</dd></div>
          <div><dt>当前状态</dt><dd>{{ statusLabels[school.status] }}</dd></div>
          <div><dt>目标状态</dt><dd>{{ statusLabels[selectedAction.targetStatus] }}</dd></div>
        </dl>
        <p :class="selectedAction.danger ? 'message message-warning governance-lifecycle-impact' : 'modal-copy'">
          {{ selectedAction.impact }}
        </p>

        <form class="governance-dialog-form" @submit.prevent="submitLifecycleAction">
          <label class="field-group">
            <span>操作原因</span>
            <textarea
              v-model="reason"
              rows="4"
              minlength="2"
              maxlength="500"
              :disabled="submitting"
              placeholder="请输入本次状态变更的业务原因"
            ></textarea>
            <small class="field-hint">{{ reason.trim().length }} / 500，至少 2 个字符</small>
          </label>
          <small v-if="fieldError" class="field-error" role="alert">{{ fieldError }}</small>
          <div class="modal-actions">
            <button class="secondary-button" type="button" :disabled="submitting" @click="closeLifecycleDialog">取消</button>
            <button
              :class="selectedAction.danger ? 'registration-danger-button' : 'primary-button'"
              type="submit"
              :disabled="submitting"
            >
              {{ submitting ? '提交中...' : selectedAction.confirmLabel }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </WorkspaceShell>
</template>
