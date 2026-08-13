<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  approveStudentApplication,
  getStudentApplicationDetail,
  listPendingStudentApplications,
  rejectStudentApplication
} from '../api/studentReview';
import { ApiError } from '../api/http';
import { useAuthStore } from '../stores/auth';
import type { AuthenticatedSchoolMembership } from '../types/auth';
import type {
  PageResponse,
  StudentIdentityApplicationDetail,
  StudentIdentityApplicationSummary
} from '../types/studentReview';

const PAGE_SIZE = 20;
const REJECTION_REASON_MAX_LENGTH = 2000;

const router = useRouter();
const auth = useAuthStore();
const applications = ref<StudentIdentityApplicationSummary[]>([]);
const page = ref(0);
const pageResult = ref<PageResponse<StudentIdentityApplicationSummary> | null>(null);
const loading = ref(true);
const loadError = ref('');
const successMessage = ref('');
const logoutError = ref('');

const selectedDetail = ref<StudentIdentityApplicationDetail | null>(null);
const detailTarget = ref<StudentIdentityApplicationSummary | null>(null);
const detailLoading = ref(false);
const detailError = ref('');

const approveTarget = ref<StudentIdentityApplicationSummary | null>(null);
const rejectTarget = ref<StudentIdentityApplicationSummary | null>(null);
const rejectReason = ref('');
const rejectReasonError = ref('');
const actionError = ref('');
const submittingAction = ref<'approve' | 'reject' | null>(null);

const schoolAdminMemberships = computed(() =>
  (auth.currentUser?.schoolMemberships ?? []).filter(
    (membership) => membership.roleInSchool === 'SCHOOL_ADMIN'
  )
);
const selectedSchoolId = ref(schoolAdminMemberships.value[0]?.schoolId ?? '');
const currentSchoolId = computed(() => selectedSchoolId.value || schoolAdminMemberships.value[0]?.schoolId || '');
const totalPending = computed(() => pageResult.value?.totalElements ?? applications.value.length);
const isSubmitting = computed(() => submittingAction.value !== null);

watch(selectedSchoolId, () => {
  page.value = 0;
  void loadApplications();
});

function schoolMembershipLabel(membership: AuthenticatedSchoolMembership): string {
  return `学校 ${membership.schoolId}`;
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '未提供';
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

function isStaleAuthorizationError(error: unknown): boolean {
  return error instanceof ApiError
    && error.status === 403
    && ['SCHOOL_ADMIN_SCOPE_DENIED', 'ACCESS_DENIED'].includes(error.code ?? '');
}

function isAlreadyReviewedError(error: unknown): boolean {
  return error instanceof ApiError
    && (error.status === 409 || ['STUDENT_APPLICATION_NOT_FOUND'].includes(error.code ?? ''));
}

function authorizationMessage(): string {
  return '当前账号已无权执行此操作，请重新登录或联系管理员。';
}

async function loadApplications() {
  const schoolId = currentSchoolId.value;
  if (!schoolId) {
    loading.value = false;
    applications.value = [];
    pageResult.value = null;
    loadError.value = '当前账号没有可审核的学校身份。';
    return;
  }

  loading.value = true;
  loadError.value = '';
  try {
    const result = await listPendingStudentApplications(schoolId, page.value, PAGE_SIZE);
    pageResult.value = result;
    applications.value = result.items;
  } catch (error) {
    applications.value = [];
    pageResult.value = null;
    loadError.value = isStaleAuthorizationError(error)
      ? authorizationMessage()
      : '加载学生申请失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function changePage(nextPage: number) {
  if (loading.value || nextPage < 0 || nextPage >= (pageResult.value?.totalPages ?? 0)) return;
  page.value = nextPage;
  await loadApplications();
}

async function openDetail(application: StudentIdentityApplicationSummary) {
  if (detailLoading.value) return;
  detailTarget.value = application;
  selectedDetail.value = null;
  detailError.value = '';
  detailLoading.value = true;
  try {
    selectedDetail.value = await getStudentApplicationDetail(currentSchoolId.value, application.applicationId);
  } catch (error) {
    detailError.value = isStaleAuthorizationError(error)
      ? authorizationMessage()
      : isAlreadyReviewedError(error)
        ? '该申请已被处理，请刷新列表。'
        : '加载申请详情失败，请稍后重试。';
  } finally {
    detailLoading.value = false;
  }
}

function closeDetail() {
  if (detailLoading.value) return;
  detailTarget.value = null;
  selectedDetail.value = null;
  detailError.value = '';
}

function retryDetail() {
  if (detailTarget.value) {
    void openDetail(detailTarget.value);
  }
}

function openApprove(application: StudentIdentityApplicationSummary) {
  if (isSubmitting.value) return;
  actionError.value = '';
  approveTarget.value = application;
}

function closeApprove() {
  if (isSubmitting.value) return;
  approveTarget.value = null;
  actionError.value = '';
}

function openReject(application: StudentIdentityApplicationSummary) {
  if (isSubmitting.value) return;
  actionError.value = '';
  rejectReason.value = '';
  rejectReasonError.value = '';
  rejectTarget.value = application;
}

function closeReject() {
  if (isSubmitting.value) return;
  rejectTarget.value = null;
  rejectReason.value = '';
  rejectReasonError.value = '';
  actionError.value = '';
}

async function refreshAfterReview(message: string) {
  successMessage.value = message;
  if (applications.value.length === 1 && page.value > 0) {
    page.value -= 1;
  }
  await loadApplications();
}

async function handleActionError(error: unknown) {
  if (isStaleAuthorizationError(error)) {
    actionError.value = authorizationMessage();
    return;
  }
  if (isAlreadyReviewedError(error)) {
    actionError.value = '该申请已被处理，请刷新列表。';
    await loadApplications();
    return;
  }
  actionError.value = '操作失败，请稍后重试。';
}

async function confirmApprove() {
  if (!approveTarget.value || isSubmitting.value) return;
  const target = approveTarget.value;
  actionError.value = '';
  successMessage.value = '';
  submittingAction.value = 'approve';
  try {
    await approveStudentApplication(currentSchoolId.value, target.applicationId);
    approveTarget.value = null;
    await refreshAfterReview('已批准学生身份申请');
  } catch (error) {
    await handleActionError(error);
  } finally {
    submittingAction.value = null;
  }
}

async function confirmReject() {
  if (!rejectTarget.value || isSubmitting.value) return;
  const normalizedReason = rejectReason.value.trim();
  rejectReasonError.value = '';
  actionError.value = '';
  if (!normalizedReason) {
    rejectReasonError.value = '请输入拒绝原因';
    return;
  }
  if (normalizedReason.length > REJECTION_REASON_MAX_LENGTH) {
    rejectReasonError.value = `拒绝原因不能超过 ${REJECTION_REASON_MAX_LENGTH} 个字符`;
    return;
  }

  const target = rejectTarget.value;
  successMessage.value = '';
  submittingAction.value = 'reject';
  try {
    await rejectStudentApplication(currentSchoolId.value, target.applicationId, normalizedReason);
    rejectTarget.value = null;
    rejectReason.value = '';
    await refreshAfterReview('已拒绝学生身份申请');
  } catch (error) {
    await handleActionError(error);
  } finally {
    submittingAction.value = null;
  }
}

async function handleLogout() {
  logoutError.value = '';
  try {
    await auth.logout();
    await router.push('/login');
  } catch {
    logoutError.value = '退出登录失败，请稍后再试';
  }
}

onMounted(() => {
  void loadApplications();
});
</script>

<template>
  <main class="shell-page review-page">
    <header class="topbar">
      <RouterLink class="topbar-brand topbar-brand-link" to="/school-admin">
        <span class="brand-mark brand-mark-small" aria-hidden="true">G</span>
        <span>校园吉尼斯挑战赛 <span class="topbar-brand-subtitle">学生身份审核</span></span>
      </RouterLink>
      <div v-if="auth.currentUser" class="topbar-user">
        <span>{{ auth.currentUser.username }}</span>
        <span class="role-badge">学校管理员</span>
        <button class="secondary-button" type="button" :disabled="auth.loading" @click="handleLogout">
          {{ auth.loading ? '退出中...' : '退出登录' }}
        </button>
      </div>
    </header>

    <section class="review-content" aria-labelledby="review-page-title">
      <div class="review-heading-row">
        <div>
          <p class="eyebrow">School Administration</p>
          <h1 id="review-page-title">学生身份审核</h1>
          <p>审核本校学生提交的身份申请。</p>
        </div>
        <RouterLink class="secondary-button back-home-link" to="/school-admin">返回工作台</RouterLink>
      </div>

      <p v-if="logoutError" class="message message-error" role="status">{{ logoutError }}</p>
      <p v-if="successMessage" class="message message-success" role="status">{{ successMessage }}</p>

      <section class="review-list-panel" aria-labelledby="pending-list-title">
        <div class="review-list-heading">
          <div>
            <h2 id="pending-list-title">待审核申请</h2>
            <p>{{ loading ? '正在统计...' : `共 ${totalPending} 条待审核申请` }}</p>
          </div>
          <label v-if="schoolAdminMemberships.length > 1" class="school-select">
            <span>审核学校</span>
            <select v-model="selectedSchoolId" :disabled="loading || isSubmitting">
              <option
                v-for="membership in schoolAdminMemberships"
                :key="membership.membershipId"
                :value="membership.schoolId"
              >
                {{ schoolMembershipLabel(membership) }}
              </option>
            </select>
          </label>
          <span v-else-if="currentSchoolId" class="school-scope-label">学校 {{ currentSchoolId }}</span>
        </div>

        <div v-if="loading" class="review-state" role="status">
          <span class="review-spinner" aria-hidden="true"></span>
          <strong>正在加载待审核申请...</strong>
        </div>

        <div v-else-if="loadError" class="review-state review-state-error" role="alert">
          <strong>{{ loadError }}</strong>
          <button class="secondary-button" type="button" @click="loadApplications">重新加载</button>
        </div>

        <div v-else-if="applications.length === 0" class="review-state">
          <span class="empty-state-mark" aria-hidden="true">✓</span>
          <strong>暂无待审核申请</strong>
          <p>新的学生身份申请会显示在这里。</p>
        </div>

        <template v-else>
          <div class="review-table-wrap">
            <table class="review-table">
              <thead>
                <tr>
                  <th scope="col">学生</th>
                  <th scope="col">学号</th>
                  <th scope="col">年级 / 班级</th>
                  <th scope="col">提交时间</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="application in applications" :key="application.applicationId">
                  <td>
                    <strong>{{ application.realName }}</strong>
                    <span>{{ application.username }}</span>
                  </td>
                  <td>{{ application.studentNumber }}</td>
                  <td>{{ application.grade }} / {{ application.className }}</td>
                  <td><time :datetime="application.submittedAt">{{ formatDate(application.submittedAt) }}</time></td>
                  <td>
                    <div class="review-actions">
                      <button class="text-action" type="button" :disabled="isSubmitting" @click="openDetail(application)">
                        查看
                      </button>
                      <button class="text-action text-action-approve" type="button" :disabled="isSubmitting" @click="openApprove(application)">
                        批准
                      </button>
                      <button class="text-action text-action-reject" type="button" :disabled="isSubmitting" @click="openReject(application)">
                        拒绝
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="review-card-list">
            <article v-for="application in applications" :key="application.applicationId" class="review-application-card">
              <div class="review-card-heading">
                <div>
                  <strong>{{ application.realName }}</strong>
                  <span>{{ application.username }}</span>
                </div>
                <span class="pending-pill">待审核</span>
              </div>
              <dl>
                <div><dt>学号</dt><dd>{{ application.studentNumber }}</dd></div>
                <div><dt>年级 / 班级</dt><dd>{{ application.grade }} / {{ application.className }}</dd></div>
                <div><dt>提交时间</dt><dd>{{ formatDate(application.submittedAt) }}</dd></div>
              </dl>
              <div class="review-actions review-card-actions">
                <button class="secondary-button" type="button" :disabled="isSubmitting" @click="openDetail(application)">查看</button>
                <button class="approve-button" type="button" :disabled="isSubmitting" @click="openApprove(application)">批准</button>
                <button class="reject-button" type="button" :disabled="isSubmitting" @click="openReject(application)">拒绝</button>
              </div>
            </article>
          </div>

          <nav v-if="(pageResult?.totalPages ?? 0) > 1" class="review-pagination" aria-label="待审核申请分页">
            <button class="secondary-button" type="button" :disabled="loading || page === 0" @click="changePage(page - 1)">
              上一页
            </button>
            <span>第 {{ page + 1 }} / {{ pageResult?.totalPages }} 页</span>
            <button class="secondary-button" type="button" :disabled="loading || !pageResult?.hasNext" @click="changePage(page + 1)">
              下一页
            </button>
          </nav>
        </template>
      </section>
    </section>

    <div v-if="detailTarget" class="modal-backdrop" @click.self="closeDetail">
      <section class="review-modal detail-modal" role="dialog" aria-modal="true" aria-labelledby="detail-modal-title">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">申请资料</p>
            <h2 id="detail-modal-title">{{ detailTarget.realName }}</h2>
          </div>
          <button class="modal-close" type="button" title="关闭" :disabled="detailLoading" @click="closeDetail">×</button>
        </div>
        <div v-if="detailLoading" class="review-state compact-review-state" role="status">
          <span class="review-spinner" aria-hidden="true"></span>
          <strong>正在加载申请资料...</strong>
        </div>
        <div v-else-if="detailError" class="review-state review-state-error compact-review-state" role="alert">
          <strong>{{ detailError }}</strong>
          <button class="secondary-button" type="button" @click="retryDetail">重新加载</button>
        </div>
        <template v-else-if="selectedDetail">
          <dl class="application-detail-list">
            <div><dt>用户名</dt><dd>{{ selectedDetail.username }}</dd></div>
            <div><dt>姓名</dt><dd>{{ selectedDetail.realName }}</dd></div>
            <div><dt>学号</dt><dd>{{ selectedDetail.studentNumber }}</dd></div>
            <div><dt>年级</dt><dd>{{ selectedDetail.grade }}</dd></div>
            <div><dt>班级</dt><dd>{{ selectedDetail.className }}</dd></div>
            <div><dt>学校</dt><dd>{{ selectedDetail.schoolId }}</dd></div>
            <div><dt>提交时间</dt><dd>{{ formatDate(selectedDetail.submittedAt) }}</dd></div>
            <div><dt>证明材料</dt><dd>{{ selectedDetail.proofFileCount }} 项</dd></div>
          </dl>
          <div v-if="selectedDetail.proofFileKeys.length" class="proof-list">
            <strong>证明材料引用</strong>
            <ul>
              <li v-for="fileKey in selectedDetail.proofFileKeys" :key="fileKey">{{ fileKey }}</li>
            </ul>
          </div>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="closeDetail">关闭</button>
          </div>
        </template>
      </section>
    </div>

    <div v-if="approveTarget" class="modal-backdrop" @click.self="closeApprove">
      <section class="review-modal confirmation-modal" role="dialog" aria-modal="true" aria-labelledby="approve-modal-title">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">身份授权确认</p>
            <h2 id="approve-modal-title">确认批准该学生身份申请？</h2>
          </div>
          <button class="modal-close" type="button" title="关闭" :disabled="isSubmitting" @click="closeApprove">×</button>
        </div>
        <p class="modal-copy">批准后，{{ approveTarget.realName }} 将获得正式学生身份。</p>
        <p v-if="actionError" class="message message-error" role="alert">{{ actionError }}</p>
        <div class="modal-actions">
          <button class="secondary-button" type="button" :disabled="isSubmitting" @click="closeApprove">取消</button>
          <button class="approve-button" type="button" :disabled="isSubmitting" @click="confirmApprove">
            <span v-if="submittingAction === 'approve'" class="button-spinner" aria-hidden="true"></span>
            {{ submittingAction === 'approve' ? '批准中...' : '确认批准' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="rejectTarget" class="modal-backdrop" @click.self="closeReject">
      <section class="review-modal confirmation-modal" role="dialog" aria-modal="true" aria-labelledby="reject-modal-title">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">审核决定</p>
            <h2 id="reject-modal-title">拒绝学生身份申请</h2>
          </div>
          <button class="modal-close" type="button" title="关闭" :disabled="isSubmitting" @click="closeReject">×</button>
        </div>
        <p class="modal-copy">请输入拒绝原因。</p>
        <label class="reject-reason-field">
          <span>拒绝原因</span>
          <textarea
            v-model="rejectReason"
            :maxlength="REJECTION_REASON_MAX_LENGTH"
            :disabled="isSubmitting"
            :aria-invalid="Boolean(rejectReasonError)"
            rows="5"
            placeholder="请说明需要学生重新确认的资料"
          ></textarea>
          <span class="character-count">{{ rejectReason.length }} / {{ REJECTION_REASON_MAX_LENGTH }}</span>
          <small v-if="rejectReasonError" class="field-error">{{ rejectReasonError }}</small>
        </label>
        <p v-if="actionError" class="message message-error" role="alert">{{ actionError }}</p>
        <div class="modal-actions">
          <button class="secondary-button" type="button" :disabled="isSubmitting" @click="closeReject">取消</button>
          <button class="reject-button" type="button" :disabled="isSubmitting" @click="confirmReject">
            <span v-if="submittingAction === 'reject'" class="button-spinner" aria-hidden="true"></span>
            {{ submittingAction === 'reject' ? '拒绝中...' : '确认拒绝' }}
          </button>
        </div>
      </section>
    </div>
  </main>
</template>
