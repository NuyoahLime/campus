<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ApiError } from '../api/http';
import { getPublicSchools, registerStudent } from '../api/registration';
import type { PublicSchoolSummary, StudentRegistrationResponse } from '../types/registration';

const router = useRouter();

const schools = ref<PublicSchoolSummary[]>([]);
const schoolsLoading = ref(false);
const schoolsError = ref('');
const submitting = ref(false);
const submitError = ref('');
const validationErrors = ref<Record<string, string>>({});
const submitted = ref<StudentRegistrationResponse | null>(null);

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  schoolId: '',
  studentNumber: '',
  grade: '',
  className: ''
});

const selectedSchoolName = computed(() => {
  const selected = schools.value.find((school) => school.id === submitted.value?.schoolId || school.id === form.schoolId);
  return selected?.name ?? '已选择学校';
});

const canSubmit = computed(() =>
  !submitting.value
  && !schoolsLoading.value
  && schools.value.length > 0
  && form.schoolId.trim().length > 0
);

const errorMessages: Record<string, string> = {
  USERNAME_ALREADY_EXISTS: '该用户名已被使用，请更换后再提交。',
  STUDENT_APPROVAL_CONFLICT: '该学生身份申请已存在，请返回登录查看当前审核状态。',
  PASSWORD_CONFIRMATION_MISMATCH: '两次输入的密码不一致。',
  PASSWORD_POLICY_VIOLATION: '密码至少需要 8 位，且不能超过安全长度限制。',
  INVALID_STUDENT_REGISTRATION_DATA: '请检查学生注册信息后再提交。',
  SCHOOL_NOT_FOUND: '选择的学校不可用，请重新选择。',
  SCHOOL_NOT_OPEN_FOR_REGISTRATION: '选择的学校暂不开放学生注册，请重新选择。',
  PROOF_ATTACHMENT_NOT_SUPPORTED: '当前暂不支持上传证明材料。',
  VALIDATION_FAILED: '请检查表单中的必填项。',
  MALFORMED_REQUEST: '提交内容格式不正确，请检查后再试。',
  CONFLICT: '当前信息已被使用或存在冲突，请检查后再提交。'
};

const fieldLabels: Record<string, string> = {
  username: '用户名',
  password: '密码',
  confirmPassword: '确认密码',
  realName: '真实姓名',
  schoolId: '学校',
  studentNumber: '学号',
  grade: '年级',
  className: '班级'
};

function trimForm() {
  form.username = form.username.trim();
  form.realName = form.realName.trim();
  form.schoolId = form.schoolId.trim();
  form.studentNumber = form.studentNumber.trim();
  form.grade = form.grade.trim();
  form.className = form.className.trim();
}

function validate() {
  validationErrors.value = {};
  trimForm();

  const errors: Record<string, string> = {};
  if (!form.username) errors.username = '请输入用户名。';
  if (!form.password) errors.password = '请输入密码。';
  if (!form.confirmPassword) errors.confirmPassword = '请再次输入密码。';
  if (form.password && form.password.length < 8) errors.password = '密码至少需要 8 位。';
  if (form.password !== form.confirmPassword) errors.confirmPassword = '两次输入的密码不一致。';
  if (!form.realName) errors.realName = '请输入真实姓名。';
  if (!form.schoolId) errors.schoolId = '请选择学校。';
  if (!form.studentNumber) errors.studentNumber = '请输入学号。';
  if (!form.grade) errors.grade = '请输入年级。';
  if (!form.className) errors.className = '请输入班级。';

  validationErrors.value = errors;
  return Object.keys(errors).length === 0;
}

function mapError(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.code;
    if (code && errorMessages[code]) return errorMessages[code];
    if (error.status === 403) return '页面安全校验已失效，请刷新后重试。';
    if (error.status === 409) return errorMessages.CONFLICT;
  }
  return '注册提交失败，请稍后重试。';
}

function applyBackendFieldErrors(error: unknown) {
  if (!(error instanceof ApiError)) return;
  const body = error.body;
  if (!body || typeof body !== 'object' || !('details' in body)) return;
  const details = (body as { details?: unknown }).details;
  if (!Array.isArray(details)) return;

  const errors: Record<string, string> = {};
  for (const detail of details) {
    if (!detail || typeof detail !== 'object') continue;
    const field = 'field' in detail ? String(detail.field) : '';
    const message = 'message' in detail ? String(detail.message) : '';
    if (!field || !(field in fieldLabels)) continue;
    errors[field] = fieldMessage(field, message);
  }
  validationErrors.value = { ...validationErrors.value, ...errors };
}

function fieldMessage(field: string, message: string): string {
  const label = fieldLabels[field] ?? '该字段';
  if (message.includes('must not be blank') || message.includes('must not be null')) {
    return `${label}为必填项。`;
  }
  if (message.includes('size must be between') || message.includes('length')) {
    return `${label}长度不符合要求。`;
  }
  return `${label}填写不正确。`;
}

async function loadSchools() {
  schoolsLoading.value = true;
  schoolsError.value = '';
  try {
    const result = await getPublicSchools();
    schools.value = result.items;
    if (result.items.length === 0) {
      form.schoolId = '';
      return;
    }
    if (!form.schoolId || !result.items.some((school) => school.id === form.schoolId)) {
      form.schoolId = result.items[0].id;
    }
  } catch {
    schools.value = [];
    form.schoolId = '';
    schoolsError.value = '学校列表加载失败，请稍后重试。';
  } finally {
    schoolsLoading.value = false;
  }
}

async function submit() {
  if (submitting.value || !validate()) return;
  submitError.value = '';
  submitting.value = true;

  try {
    submitted.value = await registerStudent({
      username: form.username,
      password: form.password,
      confirmPassword: form.confirmPassword,
      realName: form.realName,
      schoolId: form.schoolId,
      studentNumber: form.studentNumber,
      grade: form.grade,
      className: form.className,
      proofFileKeys: []
    });
    form.password = '';
    form.confirmPassword = '';
  } catch (error) {
    applyBackendFieldErrors(error);
    submitError.value = mapError(error);
  } finally {
    submitting.value = false;
  }
}

function backToLogin() {
  router.push({ name: 'login' });
}

onMounted(loadSchools);
</script>

<template>
  <main class="auth-page registration-page">
    <section class="brand-panel" aria-label="校园吉尼斯品牌介绍">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true">G</span>
        <span>校园吉尼斯挑战赛</span>
      </div>
      <p class="eyebrow">Student Registration</p>
      <h1>
        <span class="headline-blue">创建学生账号</span>
        <span class="headline-orange">等待学校审核</span>
      </h1>
      <p class="brand-copy">
        提交学生身份信息后，学校管理员将进行审核。审核通过后即可登录校园吉尼斯。
      </p>
      <div class="brand-visual" aria-hidden="true">
        <span class="trophy"></span>
        <span class="runner runner-secondary"></span>
        <span class="runner runner-main"></span>
        <span class="runner runner-third"></span>
      </div>
    </section>

    <section class="login-card registration-card" aria-labelledby="register-title">
      <template v-if="submitted">
        <div class="submitted-state">
          <span class="submitted-icon" aria-hidden="true">✓</span>
          <p class="eyebrow">申请已提交</p>
          <h2 id="register-title">等待学校审核</h2>
          <p class="login-subtitle">
            你的学生账号已经创建，学生身份信息正在等待学校管理员审核。
          </p>
          <p class="message message-success">
            审核通过后即可使用该账号登录校园吉尼斯。
          </p>
          <dl class="submitted-summary">
            <div>
              <dt>用户名</dt>
              <dd>{{ submitted.username }}</dd>
            </div>
            <div>
              <dt>学校</dt>
              <dd>{{ selectedSchoolName }}</dd>
            </div>
            <div>
              <dt>审核状态</dt>
              <dd>等待学校审核</dd>
            </div>
          </dl>
          <button class="primary-button" type="button" @click="backToLogin">
            返回登录
          </button>
        </div>
      </template>

      <template v-else>
        <div>
          <p class="eyebrow">学生首次注册</p>
          <h2 id="register-title">创建学生账号</h2>
          <p class="login-subtitle">
            提交学生身份信息后，将由学校管理员进行审核。
          </p>
        </div>

        <form class="form-stack registration-form" novalidate @submit.prevent="submit">
          <fieldset class="form-section">
            <legend>账号信息</legend>
            <label class="field">
              <span>用户名</span>
              <input
                v-model="form.username"
                autocomplete="username"
                maxlength="100"
                placeholder="请输入用户名"
                :disabled="submitting"
                :aria-invalid="Boolean(validationErrors.username)"
                required
              />
              <small v-if="validationErrors.username" class="field-error">
                {{ validationErrors.username }}
              </small>
            </label>

            <div class="form-row">
              <label class="field">
                <span>密码</span>
                <input
                  v-model="form.password"
                  type="password"
                  autocomplete="new-password"
                  placeholder="至少 8 位"
                  :disabled="submitting"
                  :aria-invalid="Boolean(validationErrors.password)"
                  required
                />
                <small v-if="validationErrors.password" class="field-error">
                  {{ validationErrors.password }}
                </small>
              </label>

              <label class="field">
                <span>确认密码</span>
                <input
                  v-model="form.confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="再次输入密码"
                  :disabled="submitting"
                  :aria-invalid="Boolean(validationErrors.confirmPassword)"
                  required
                />
                <small v-if="validationErrors.confirmPassword" class="field-error">
                  {{ validationErrors.confirmPassword }}
                </small>
              </label>
            </div>
          </fieldset>

          <fieldset class="form-section">
            <legend>学生身份</legend>
            <label class="field">
              <span>真实姓名</span>
              <input
                v-model="form.realName"
                maxlength="100"
                placeholder="请输入真实姓名"
                :disabled="submitting"
                :aria-invalid="Boolean(validationErrors.realName)"
                required
              />
              <small v-if="validationErrors.realName" class="field-error">
                {{ validationErrors.realName }}
              </small>
            </label>

            <label class="field">
              <span>学校</span>
              <select
                v-model="form.schoolId"
                :disabled="schoolsLoading || submitting || schools.length === 0"
                :aria-invalid="Boolean(validationErrors.schoolId)"
                required
              >
                <option value="" disabled>
                  {{ schoolsLoading ? '正在加载学校...' : '请选择学校' }}
                </option>
                <option v-for="school in schools" :key="school.id" :value="school.id">
                  {{ school.name }}
                </option>
              </select>
              <small v-if="validationErrors.schoolId" class="field-error">
                {{ validationErrors.schoolId }}
              </small>
            </label>

            <p v-if="schoolsError" class="message message-error" role="status">
              {{ schoolsError }}
              <button class="inline-button" type="button" @click="loadSchools">重新加载</button>
            </p>
            <p v-else-if="!schoolsLoading && schools.length === 0" class="message message-warning" role="status">
              当前暂无可选择的学校
            </p>

            <div class="form-row">
              <label class="field">
                <span>学号</span>
                <input
                  v-model="form.studentNumber"
                  maxlength="64"
                  placeholder="请输入学号"
                  :disabled="submitting"
                  :aria-invalid="Boolean(validationErrors.studentNumber)"
                  required
                />
                <small v-if="validationErrors.studentNumber" class="field-error">
                  {{ validationErrors.studentNumber }}
                </small>
              </label>

              <label class="field">
                <span>年级</span>
                <input
                  v-model="form.grade"
                  maxlength="32"
                  placeholder="例如：高一年级"
                  :disabled="submitting"
                  :aria-invalid="Boolean(validationErrors.grade)"
                  required
                />
                <small v-if="validationErrors.grade" class="field-error">
                  {{ validationErrors.grade }}
                </small>
              </label>
            </div>

            <label class="field">
              <span>班级</span>
              <input
                v-model="form.className"
                maxlength="64"
                placeholder="例如：1 班"
                :disabled="submitting"
                :aria-invalid="Boolean(validationErrors.className)"
                required
              />
              <small v-if="validationErrors.className" class="field-error">
                {{ validationErrors.className }}
              </small>
            </label>
          </fieldset>

          <p v-if="submitError" class="message message-error" role="status">
            {{ submitError }}
          </p>

          <button class="primary-button" type="submit" :disabled="!canSubmit">
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            {{ submitting ? '提交中...' : '提交注册申请' }}
          </button>
        </form>

        <p class="auth-switch">
          已有账号？
          <RouterLink to="/login">返回登录</RouterLink>
        </p>
      </template>
    </section>
  </main>
</template>
