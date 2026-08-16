<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { listGovernanceSchools } from '../api/schoolGovernance';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type {
  GovernanceSchoolListItem,
  PageResponse,
  SchoolStatus
} from '../types/schoolGovernance';

const PAGE_SIZE = 20;

const statusOptions: Array<{ value: SchoolStatus; label: string }> = [
  { value: 'PENDING_ENABLE', label: '待启用' },
  { value: 'NORMAL', label: '正常' },
  { value: 'SUSPENDED', label: '已暂停' },
  { value: 'DISABLED', label: '已停用' }
];

const items = ref<GovernanceSchoolListItem[]>([]);
const result = ref<PageResponse<GovernanceSchoolListItem> | null>(null);
const selectedStatus = ref<SchoolStatus | ''>('');
const searchInput = ref('');
const appliedSearch = ref('');
const page = ref(0);
const loading = ref(true);
const loadError = ref('');

const totalLabel = computed(() => loading.value
  ? '正在统计...'
  : `共 ${result.value?.totalElements ?? 0} 所学校`);

function statusLabel(status: SchoolStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return '当前账号无平台学校管理权限，请重新登录。';
  }
  return '加载学校列表失败，请稍后重试。';
}

async function loadSchools() {
  loading.value = true;
  loadError.value = '';
  try {
    const response = await listGovernanceSchools(
      page.value,
      PAGE_SIZE,
      selectedStatus.value || null,
      appliedSearch.value
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

function applySearch() {
  appliedSearch.value = searchInput.value.trim();
  page.value = 0;
  void loadSchools();
}

function clearSearch() {
  searchInput.value = '';
  if (!appliedSearch.value) return;
  appliedSearch.value = '';
  page.value = 0;
  void loadSchools();
}

async function changePage(nextPage: number) {
  if (loading.value || nextPage < 0 || nextPage >= (result.value?.totalPages ?? 0)) return;
  page.value = nextPage;
  await loadSchools();
}

watch(selectedStatus, () => {
  page.value = 0;
  void loadSchools();
});

onMounted(() => void loadSchools());
</script>

<template>
  <WorkspaceShell
    role-label="超级管理员"
    workspace-title="平台管理工作台"
    page-title="学校管理"
    description="查看学校主数据、当前状态和已配置的正常学校管理员数量。"
    home-path="/super-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="governance-panel" aria-labelledby="governance-school-list-title">
      <div class="governance-toolbar">
        <div>
          <h2 id="governance-school-list-title">学校列表</h2>
          <p>{{ totalLabel }}</p>
        </div>
        <div class="governance-filters">
          <form class="governance-search" role="search" @submit.prevent="applySearch">
            <label for="school-governance-search">搜索学校</label>
            <div>
              <input
                id="school-governance-search"
                v-model="searchInput"
                type="search"
                maxlength="200"
                placeholder="学校名称、内部编码或统一识别编码"
                :disabled="loading"
              >
              <button class="secondary-button" type="submit" :disabled="loading">搜索</button>
              <button
                v-if="searchInput || appliedSearch"
                class="ghost-button"
                type="button"
                :disabled="loading"
                @click="clearSearch"
              >
                清除
              </button>
            </div>
          </form>
          <label class="governance-filter">
            <span>学校状态</span>
            <select v-model="selectedStatus" :disabled="loading">
              <option value="">全部状态</option>
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
      </div>

      <div v-if="loading" class="registration-state" role="status">
        <span class="registration-spinner" aria-hidden="true"></span>
        <strong>正在加载学校列表...</strong>
      </div>
      <div v-else-if="loadError" class="registration-state registration-state-error" role="alert">
        <strong>{{ loadError }}</strong>
        <button class="secondary-button" type="button" @click="loadSchools">重新加载</button>
      </div>
      <div v-else-if="items.length === 0" class="registration-state">
        <strong>暂无符合条件的学校</strong>
        <p>可以调整状态筛选或搜索条件。</p>
      </div>

      <template v-else>
        <div class="governance-table-wrap">
          <table class="governance-table">
            <thead>
              <tr>
                <th scope="col">学校名称</th>
                <th scope="col">内部编码</th>
                <th scope="col">学校类型</th>
                <th scope="col">地区</th>
                <th scope="col">状态</th>
                <th scope="col">正常管理员</th>
                <th scope="col"><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td><strong>{{ item.name }}</strong></td>
                <td><code>{{ item.internalCode }}</code></td>
                <td>{{ item.schoolType }}</td>
                <td>{{ item.region }}</td>
                <td><span class="registration-status" :data-status="item.status">{{ statusLabel(item.status) }}</span></td>
                <td>{{ item.normalActiveSchoolAdminCount }}</td>
                <td><RouterLink class="registration-detail-link" :to="`/super-admin/schools/${item.id}`">查看详情</RouterLink></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="governance-card-list">
          <article v-for="item in items" :key="item.id" class="governance-card">
            <div class="registration-card-heading">
              <strong>{{ item.name }}</strong>
              <span class="registration-status" :data-status="item.status">{{ statusLabel(item.status) }}</span>
            </div>
            <dl>
              <div><dt>内部编码</dt><dd>{{ item.internalCode }}</dd></div>
              <div><dt>学校类型</dt><dd>{{ item.schoolType }}</dd></div>
              <div><dt>地区</dt><dd>{{ item.region }}</dd></div>
              <div><dt>正常管理员</dt><dd>{{ item.normalActiveSchoolAdminCount }}</dd></div>
            </dl>
            <RouterLink class="secondary-button governance-card-link" :to="`/super-admin/schools/${item.id}`">查看详情</RouterLink>
          </article>
        </div>

        <nav v-if="(result?.totalPages ?? 0) > 1" class="registration-pagination" aria-label="学校列表分页">
          <button class="secondary-button" type="button" :disabled="loading || page === 0" @click="changePage(page - 1)">上一页</button>
          <span>第 {{ page + 1 }} / {{ result?.totalPages }} 页</span>
          <button class="secondary-button" type="button" :disabled="loading || !result?.hasNext" @click="changePage(page + 1)">下一页</button>
        </nav>
      </template>
    </section>
  </WorkspaceShell>
</template>
