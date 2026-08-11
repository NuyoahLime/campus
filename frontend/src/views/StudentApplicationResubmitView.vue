<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError } from '../api/http';
import { resubmitStudentApplication } from '../api/registration';
import type { StudentRegistrationResponse } from '../types/registration';

const route = useRoute();
const router = useRouter();

const submitting = ref(false);
const submitError = ref('');
const validationErrors = ref<Record<string, string>>({});
const submitted = ref<StudentRegistrationResponse | null>(null);

const initialUsername = computed(() => {
  const value = route.query.username;
  return typeof value === 'string' ? value : '';
});

const form = reactive({
  username: initialUsername.value,
  password: '',
  realName: '',
  studentNumber: '',
  grade: '',
  className: ''
});

const canSubmit = computed(() => !submitting.value);

const errorMessages: Record<string, string> = {
  AUTHENTICATION_FAILED: '用户名或密码错误',
  ACCOUNT_LOCKED: '账号暂时被锁定，请稍后再试。',
  ACCOUNT_DISABLED: '账号已被停用，请联系管理员。',
  STUDENT_APPLICATION_NOT_RESUBMITTABLE: '当前学生身份申请状态不支持重新提交。',
  INVALID_STUDENT_RESUBMISSION_DATA: '请检查学生身份信息后再提交。',
  PROOF_ATTACHMENT_NOT_SUPPORTED: '当前暂不支持上传证明材料。',
  VALIDATION_FAILED: '请检查表单中的必填项。',
  MALFORMED_REQUEST: '提交内容格式不正确，请检查后再试。'
};

const fieldLabels: Record<string, string> = {
  username: '用户名',
  password: '当前账号密码',
  realName: '真实姓名',
  studentNumber: '学号',
  grade: '年级',
  className: '班级'
};

function trimForm() {
  form.username = form.username.trim();
  form.realName = form.realName.trim();
  form.studentNumber = form.studentNumber.trim();
  form.grade = form.grade.trim();
  form.className = form.className.trim();
}

function validate() {
  validationErrors.value = {};
  trimForm();

  const errors: Record<string, string> = {};
  if (!form.username) errors.username = '请输入用户名。';
  if (!form.password) errors.password = '请输入当前账号密码。';
  if (!form.realName) errors.realName = '请输入真实姓名。';
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
    if (error.status === 401 || error.status === 403) return '用户名或密码错误';
  }
  return '重新提交失败，请稍后重试。';
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

async function submit() {
  if (submitting.value || !validate()) return;
  submitError.value = '';
  submitting.value = true;

  try {
    submitted.value = await resubmitStudentApplication({
      username: form.username,
      password: form.password,
      realName: form.realName,
      studentNumber: form.studentNumber,
      grade: form.grade,
      className: form.className,
      proofFileKeys: []
    });
    form.password = '';
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
</script>

<template>
  <main class="auth-page registration-page">
    <section class="brand-panel" aria-label="校园吉尼斯品牌介绍">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true">G</span>
        <span>校园吉尼斯挑战赛</span>
      </div>
      <p class="eyebrow">Student Resubmission</p>
      <h1>
        <span class="headline-blue">修改身份资料</span>
        <span class="headline-orange">重新提交审核</span>
      </h1>
      <p class="brand-copy">
        为确认由本人重新提交，请输入当前账号密码，并重新填写学生身份信息。
      </p>
      <div class="brand-visual" aria-hidden="true">
        <span class="trophy"></span>
        <span class="runner runner-secondary"></span>
        <span class="runner runner-main"></span>
        <span class="runner runner-third"></span>
      </div>
    </section>

    <section class="login-card registration-card" aria-labelledby="resubmit-title">
      <template v-if="submitted">
        <div class="submitted-state">
          <span class="submitted-icon" aria-hidden="true">✓</span>
          <p class="eyebrow">申请已重新提交</p>
          <h2 id="resubmit-title">等待学校审核</h2>
          <p class="login-subtitle">
            你的学生身份信息已经重新提交，正在等待学校管理员审核。
          </p>
          <p class="message message-success">
            审核通过后即可正常登录校园吉尼斯。
          </p>
          <dl class="submitted-summary">
            <div>
              <dt>用户名</dt>
              <dd>{{ submitted.username }}</dd>
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
          <p class="eyebrow">学生重新申请</p>
          <h2 id="resubmit-title">修改资料并重新申请</h2>
          <p class="login-subtitle">
            请重新确认并填写学生身份信息。
          </p>
        </div>

        <form class="form-stack registration-form" novalidate @submit.prevent="submit">
          <fieldset class="form-section">
            <legend>账号确认</legend>
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

            <label class="field">
              <span>当前账号密码</span>
              <input
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                placeholder="用于确认由本人重新提交"
                :disabled="submitting"
                :aria-invalid="Boolean(validationErrors.password)"
                required
              />
              <small v-if="validationErrors.password" class="field-error">
                {{ validationErrors.password }}
              </small>
            </label>
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
            {{ submitting ? '提交中...' : '重新提交申请' }}
          </button>
        </form>

        <p class="auth-switch">
          暂不修改？
          <RouterLink to="/login">返回登录</RouterLink>
        </p>
      </template>
    </section>
  </main>
</template>
