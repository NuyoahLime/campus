<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getGovernanceSchool } from '../api/schoolGovernance';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type { GovernanceSchoolDetail, SchoolStatus } from '../types/schoolGovernance';

const route = useRoute();
const school = ref<GovernanceSchoolDetail | null>(null);
const loading = ref(true);
const loadError = ref('');
const schoolId = computed(() => String(route.params.id ?? ''));

const statusLabels: Record<SchoolStatus, string> = {
  PENDING_ENABLE: '待启用',
  NORMAL: '正常',
  SUSPENDED: '已暂停',
  DISABLED: '已停用'
};

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

      <section class="governance-readonly-notice" aria-label="只读配置说明">
        <div>
          <strong>学校生命周期配置为只读</strong>
          <p>本阶段仅提供学校与管理员治理信息，不在此处变更学校状态。</p>
        </div>
        <RouterLink class="primary-button governance-inline-action" :to="`/super-admin/schools/${school.id}/admins`">
          管理学校管理员
        </RouterLink>
      </section>

      <p v-if="school.normalActiveSchoolAdminCount < 2" class="message message-warning governance-count-warning">
        学校管理员配置尚未满足正常启用条件。
      </p>

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
  </WorkspaceShell>
</template>
