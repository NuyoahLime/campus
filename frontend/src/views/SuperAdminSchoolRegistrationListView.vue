<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listSchoolRegistrations } from '../api/schoolRegistrations';
import type { WorkspaceNavigationItem } from '../types/workspace';
import type {
  PageResponse,
  SchoolRegistrationListItem,
  SchoolRegistrationStatus
} from '../types/schoolRegistration';

const PAGE_SIZE = 20;

const navigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/super-admin' },
  { label: '学校治理', to: '/super-admin/school-registrations' },
  { label: '学校管理员', disabled: true },
  { label: '挑战项目', disabled: true },
  { label: '平台运营', disabled: true }
];

const statusOptions: Array<{ value: SchoolRegistrationStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'NEED_SUPPLEMENT', label: '需补充材料' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'WITHDRAWN', label: '已撤回' }
];

const items = ref<SchoolRegistrationListItem[]>([]);
const result = ref<PageResponse<SchoolRegistrationListItem> | null>(null);
const selectedStatus = ref<SchoolRegistrationStatus | ''>('SUBMITTED');
const page = ref(0);
const loading = ref(true);
const loadError = ref('');
const totalLabel = computed(() => loading.value ? '正在统计...' : `共 ${result.value?.totalElements ?? 0} 条申请`);

function statusLabel(status: SchoolRegistrationStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function formatDate(value: string): string {
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

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return '当前账号已无平台治理权限，请重新登录或联系管理员。';
  }
  return '加载学校入驻申请失败，请稍后重试。';
}

async function loadRegistrations() {
  loading.value = true;
  loadError.value = '';
  try {
    const response = await listSchoolRegistrations(
      page.value,
      PAGE_SIZE,
      selectedStatus.value || null
    );
    result.value = response;
    items.value = response.items;
  } catch (error) {
    result.value = null;
    items.value = [];
    loadError.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function changePage(nextPage: number) {
  if (loading.value || nextPage < 0 || nextPage >= (result.value?.totalPages ?? 0)) return;
  page.value = nextPage;
  await loadRegistrations();
}

watch(selectedStatus, () => {
  page.value = 0;
  void loadRegistrations();
});

onMounted(() => {
  void loadRegistrations();
});
</script>

<template>
  <WorkspaceShell
    role-label="超级管理员"
    workspace-title="平台管理工作台"
    page-title="学校入驻申请"
    description="查看学校提交的入驻资料和当前处理状态。"
    home-path="/super-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="registration-list-panel" aria-labelledby="registration-list-title">
      <div class="registration-toolbar">
        <div>
          <h2 id="registration-list-title">申请列表</h2>
          <p>{{ totalLabel }}</p>
        </div>
        <label class="registration-filter">
          <span>申请状态</span>
          <select v-model="selectedStatus" :disabled="loading">
            <option value="">全部状态</option>
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
      </div>

      <div v-if="loading" class="registration-state" role="status">
        <span class="registration-spinner" aria-hidden="true"></span>
        <strong>正在加载学校入驻申请...</strong>
      </div>

      <div v-else-if="loadError" class="registration-state registration-state-error" role="alert">
        <strong>{{ loadError }}</strong>
        <button class="secondary-button" type="button" @click="loadRegistrations">重新加载</button>
      </div>

      <div v-else-if="items.length === 0" class="registration-state">
        <strong>暂无符合条件的申请</strong>
        <p>调整状态筛选后可以查看其他学校入驻申请。</p>
      </div>

      <template v-else>
        <div class="registration-table-wrap">
          <table class="registration-table">
            <thead>
              <tr>
                <th scope="col">学校名称</th>
                <th scope="col">地区</th>
                <th scope="col">学校类型</th>
                <th scope="col">联系人</th>
                <th scope="col">状态</th>
                <th scope="col">提交时间</th>
                <th scope="col"><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td><strong>{{ item.schoolName }}</strong></td>
                <td>{{ item.region }}</td>
                <td>{{ item.schoolType }}</td>
                <td>{{ item.contactName }}</td>
                <td>
                  <span class="registration-status" :data-status="item.status">
                    {{ statusLabel(item.status) }}
                  </span>
                </td>
                <td><time :datetime="item.createdAt">{{ formatDate(item.createdAt) }}</time></td>
                <td>
                  <RouterLink
                    class="registration-detail-link"
                    :to="`/super-admin/school-registrations/${item.id}`"
                  >
                    查看详情
                  </RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="registration-card-list">
          <article v-for="item in items" :key="item.id" class="registration-card">
            <div class="registration-card-heading">
              <strong>{{ item.schoolName }}</strong>
              <span class="registration-status" :data-status="item.status">
                {{ statusLabel(item.status) }}
              </span>
            </div>
            <dl>
              <div><dt>地区</dt><dd>{{ item.region }}</dd></div>
              <div><dt>学校类型</dt><dd>{{ item.schoolType }}</dd></div>
              <div><dt>联系人</dt><dd>{{ item.contactName }}</dd></div>
              <div><dt>提交时间</dt><dd>{{ formatDate(item.createdAt) }}</dd></div>
            </dl>
            <RouterLink
              class="secondary-button registration-card-link"
              :to="`/super-admin/school-registrations/${item.id}`"
            >
              查看详情
            </RouterLink>
          </article>
        </div>

        <nav v-if="(result?.totalPages ?? 0) > 1" class="registration-pagination" aria-label="学校入驻申请分页">
          <button
            class="secondary-button"
            type="button"
            :disabled="loading || page === 0"
            @click="changePage(page - 1)"
          >
            上一页
          </button>
          <span>第 {{ page + 1 }} / {{ result?.totalPages }} 页</span>
          <button
            class="secondary-button"
            type="button"
            :disabled="loading || !result?.hasNext"
            @click="changePage(page + 1)"
          >
            下一页
          </button>
        </nav>
      </template>
    </section>
  </WorkspaceShell>
</template>
