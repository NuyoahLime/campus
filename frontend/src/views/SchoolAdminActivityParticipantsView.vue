<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import {
  assignActivityParticipant,
  listActivityParticipants,
  listParticipantCandidates,
  removeActivityParticipant
} from '../api/activityParticipants';
import { getManagedActivity } from '../api/activityManagement';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { ActivityManagementDetail } from '../types/activityManagement';
import type { ActivityParticipant } from '../types/activityParticipant';

const route = useRoute();
const activityId = () => String(route.params.id);
const activity = ref<ActivityManagementDetail | null>(null);
const participants = ref<ActivityParticipant[]>([]);
const candidates = ref<ActivityParticipant[]>([]);
const query = ref('');
const loading = ref(true);
const candidatesLoading = ref(false);
const saving = ref(false);
const error = ref('');
const message = ref('');

function studentLabel(student: ActivityParticipant) {
  return student.studentNumber || student.displayName || student.studentId;
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const id = activityId();
    const [detail, assigned] = await Promise.all([
      getManagedActivity(id),
      listActivityParticipants(id)
    ]);
    activity.value = detail;
    participants.value = assigned;
    await loadCandidates();
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有本校活动参与者管理权限。'
      : '活动参与学生加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function loadCandidates() {
  candidatesLoading.value = true;
  try {
    candidates.value = await listParticipantCandidates(activityId(), query.value);
  } catch {
    candidates.value = [];
    error.value = '可分配学生加载失败，请稍后重试。';
  } finally {
    candidatesLoading.value = false;
  }
}

async function add(student: ActivityParticipant) {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    await assignActivityParticipant(activityId(), student.studentId);
    message.value = '学生已加入本次活动。';
    await load();
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 409
      ? '该学生已经分配到此活动。'
      : '分配学生失败，请检查学生身份和活动归属。';
  } finally {
    saving.value = false;
  }
}

async function remove(student: ActivityParticipant) {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    await removeActivityParticipant(activityId(), student.studentId);
    message.value = '学生已从本次活动移除。';
    await load();
  } catch {
    error.value = '移除学生失败，请稍后重试。';
  } finally {
    saving.value = false;
  }
}

watch(query, () => void loadCandidates());
onMounted(() => void load());
</script>

<template>
  <WorkspaceShell
    role-label="学校管理员"
    workspace-title="学校管理工作台"
    page-title="活动参与学生"
    description="为本校活动配置被授权参与的学生。"
    home-path="/school-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="project-admin-panel participant-admin-panel">
      <div class="project-detail-toolbar">
        <div>
          <RouterLink class="project-back-link" :to="`/school-admin/activities/${activityId()}`">返回活动详情</RouterLink>
          <p v-if="activity" class="eyebrow">ACTIVITY PARTICIPANTS</p>
          <h2>{{ activity?.title || '活动参与学生' }}</h2>
        </div>
      </div>
      <div v-if="loading" class="project-state" role="status">正在加载参与学生...</div>
      <div v-else-if="error && !activity" class="project-state project-state-error" role="alert">
        <strong>{{ error }}</strong>
        <button class="secondary-button" type="button" @click="load">重新加载</button>
      </div>
      <template v-else>
        <div v-if="error" class="project-inline-error" role="alert">{{ error }}</div>
        <div v-if="message" class="project-inline-success" role="status">{{ message }}</div>
        <section class="participant-section">
          <header class="participant-section-heading">
            <div><p class="eyebrow">ASSIGNED STUDENTS</p><h3>已分配学生</h3><span>共 {{ participants.length }} 人</span></div>
          </header>
          <div v-if="!participants.length" class="project-state participant-state">当前活动还没有分配学生。</div>
          <div v-else class="project-admin-table-wrap">
            <table class="project-admin-table participant-table">
              <thead><tr><th>学生</th><th>学号</th><th>年级 / 班级</th><th>分配时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="student in participants" :key="student.studentId">
                  <td><strong>{{ studentLabel(student) }}</strong></td>
                  <td>{{ student.studentNumber || '未提供' }}</td>
                  <td>{{ [student.grade, student.className].filter(Boolean).join(' / ') || '未提供' }}</td>
                  <td>{{ student.assignedAt ? new Date(student.assignedAt).toLocaleString() : '未记录' }}</td>
                  <td><button class="secondary-button danger-outline" type="button" :disabled="saving" @click="remove(student)">移除</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section class="participant-section">
          <header class="participant-section-heading">
            <div><p class="eyebrow">STUDENT CANDIDATES</p><h3>添加学生</h3><span>仅显示本校 active STUDENT</span></div>
            <label class="participant-search">搜索<input v-model="query" type="search" placeholder="学号或账号"></label>
          </header>
          <div v-if="candidatesLoading" class="project-state participant-state" role="status">正在加载可分配学生...</div>
          <div v-else-if="!candidates.length" class="project-state participant-state">没有可添加的本校学生。</div>
          <div v-else class="project-admin-table-wrap">
            <table class="project-admin-table participant-table">
              <thead><tr><th>学生</th><th>学号</th><th>年级 / 班级</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="student in candidates" :key="student.studentId">
                  <td><strong>{{ studentLabel(student) }}</strong></td>
                  <td>{{ student.studentNumber || '未提供' }}</td>
                  <td>{{ [student.grade, student.className].filter(Boolean).join(' / ') || '未提供' }}</td>
                  <td><button class="primary-button" type="button" :disabled="saving" @click="add(student)">添加到活动</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </section>
  </WorkspaceShell>
</template>
