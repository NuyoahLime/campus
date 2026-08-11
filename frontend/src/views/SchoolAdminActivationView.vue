<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { activateSchoolAdmin } from '../api/auth';
import { ApiError } from '../api/http';

const route = useRoute();
const router = useRouter();

const submitting = ref(false);
const activated = ref(false);
const activatedUsername = ref('');
const submitError = ref('');
const validationErrors = ref<Record<string, string>>({});
const showNewPassword = ref(false);
const showConfirmPassword = ref(false);

const form = reactive({
  username: typeof route.query.username === 'string' ? route.query.username : '',
  invitationCode: '',
  newPassword: '',
  confirmPassword: ''
});

const canSubmit = computed(() => !submitting.value);

const errorMessages: Record<string, string> = {
  INVITATION_ACTIVATION_FAILED: '账号激活失败，请检查用户名和邀请码。',
  INVITATION_EXPIRED: '邀请码已过期，请联系超级管理员获取新的邀请码。',
  PASSWORD_CONFIRMATION_MISMATCH: '两次输入的密码不一致。',
  PASSWORD_POLICY_VIOLATION: '密码至少需要 8 位，且不能超过 72 个 UTF-8 字节。',
  ACCOUNT_NOT_ACTIVATABLE: '当前账号无法激活，请联系超级管理员。',
  SCHOOL_ADMIN_MEMBERSHIP_CONFLICT: '当前账号已有学校管理员身份，无法重复激活。',
  VALIDATION_FAILED: '请检查表单中的必填项。',
  MALFORMED_REQUEST: '提交内容格式不正确，请检查后再试。'
};

const fieldLabels: Record<string, string> = {
  username: '用户名',
  invitationCode: '邀请码',
  newPassword: '新密码',
  confirmPassword: '确认新密码'
};

function passwordByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

function validate() {
  validationErrors.value = {};
  form.username = form.username.trim();
  form.invitationCode = form.invitationCode.trim();

  const errors: Record<string, string> = {};
  if (!form.username) errors.username = '请输入用户名。';
  if (form.username.length > 100) errors.username = '用户名不能超过 100 个字符。';
  if (!form.invitationCode) errors.invitationCode = '请输入邀请码。';
  if (!form.newPassword) {
    errors.newPassword = '请输入新密码。';
  } else if (form.newPassword.length < 8 || passwordByteLength(form.newPassword) > 72) {
    errors.newPassword = '密码至少需要 8 位，且不能超过 72 个 UTF-8 字节。';
  }
  if (!form.confirmPassword) {
    errors.confirmPassword = '请再次输入新密码。';
  } else if (form.newPassword !== form.confirmPassword) {
    errors.confirmPassword = '两次输入的密码不一致。';
  }

  validationErrors.value = errors;
  return Object.keys(errors).length === 0;
}

function mapError(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.code;
    if (code && errorMessages[code]) return errorMessages[code];
    if (error.status === 401) return errorMessages.INVITATION_ACTIVATION_FAILED;
  }
  return '账号激活失败，请稍后重试。';
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
  if (message.includes('size must be between')) {
    return field === 'newPassword' || field === 'confirmPassword'
      ? '密码长度需要在 8 到 72 位之间。'
      : `${label}长度不符合要求。`;
  }
  return `${label}填写不正确。`;
}

async function submit() {
  if (submitting.value || !validate()) return;
  submitError.value = '';
  submitting.value = true;

  try {
    await activateSchoolAdmin({
      username: form.username,
      invitationCode: form.invitationCode,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    });
    activatedUsername.value = form.username;
    form.invitationCode = '';
    form.newPassword = '';
    form.confirmPassword = '';
    activated.value = true;
  } catch (error) {
    applyBackendFieldErrors(error);
    submitError.value = mapError(error);
  } finally {
    submitting.value = false;
  }
}

function goToLogin() {
  router.push({
    name: 'login',
    query: activatedUsername.value ? { username: activatedUsername.value } : undefined
  });
}
</script>

<template>
  <main class="auth-page registration-page">
    <section class="brand-panel" aria-label="校园吉尼斯品牌介绍">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true">G</span>
        <span>校园吉尼斯挑战赛</span>
      </div>
      <p class="eyebrow">School Admin Activation</p>
      <h1>
        <span class="headline-blue">激活管理员账号</span>
        <span class="headline-orange">开始学校管理</span>
      </h1>
      <p class="brand-copy">
        账号已由超级管理员预先创建。请输入用户名和邀请码，并设置你的登录密码。
      </p>
      <div class="brand-visual" aria-hidden="true">
        <span class="trophy"></span>
        <span class="runner runner-secondary"></span>
        <span class="runner runner-main"></span>
        <span class="runner runner-third"></span>
      </div>
    </section>

    <section class="login-card registration-card" aria-labelledby="activation-title">
      <template v-if="activated">
        <div class="submitted-state">
          <span class="submitted-icon" aria-hidden="true">✓</span>
          <p class="eyebrow">学校管理员账号</p>
          <h2 id="activation-title">账号激活成功</h2>
          <p class="login-subtitle">
            你的学校管理员账号已经完成激活。请使用刚刚设置的新密码重新登录。
          </p>
          <p class="message message-success">
            激活不会自动登录，以保护你的账号安全。
          </p>
          <dl class="submitted-summary">
            <div>
              <dt>用户名</dt>
              <dd>{{ activatedUsername }}</dd>
            </div>
            <div>
              <dt>账号状态</dt>
              <dd>已激活</dd>
            </div>
          </dl>
          <button class="primary-button" type="button" @click="goToLogin">
            前往登录
          </button>
        </div>
      </template>

      <template v-else>
        <div>
          <p class="eyebrow">管理员邀请码激活</p>
          <h2 id="activation-title">激活学校管理员账号</h2>
          <p class="login-subtitle">
            请输入超级管理员提供的用户名和邀请码，并设置你的登录密码。
          </p>
        </div>

        <form class="form-stack registration-form" novalidate @submit.prevent="submit">
          <fieldset class="form-section">
            <legend>邀请信息</legend>
            <label class="field">
              <span>用户名</span>
              <input
                v-model="form.username"
                autocomplete="username"
                maxlength="100"
                placeholder="请输入预先创建的用户名"
                :disabled="submitting"
                :aria-invalid="Boolean(validationErrors.username)"
                required
              />
              <small v-if="validationErrors.username" class="field-error">
                {{ validationErrors.username }}
              </small>
            </label>

            <label class="field">
              <span>邀请码</span>
              <input
                v-model="form.invitationCode"
                type="text"
                autocomplete="one-time-code"
                autocapitalize="off"
                spellcheck="false"
                placeholder="请输入或粘贴邀请码"
                :disabled="submitting"
                :aria-invalid="Boolean(validationErrors.invitationCode)"
                required
              />
              <small v-if="validationErrors.invitationCode" class="field-error">
                {{ validationErrors.invitationCode }}
              </small>
            </label>
          </fieldset>

          <fieldset class="form-section">
            <legend>设置登录密码</legend>
            <label class="field">
              <span>新密码</span>
              <div class="password-field">
                <input
                  v-model="form.newPassword"
                  :type="showNewPassword ? 'text' : 'password'"
                  aria-label="新密码"
                  autocomplete="new-password"
                  placeholder="至少 8 位"
                  :disabled="submitting"
                  :aria-invalid="Boolean(validationErrors.newPassword)"
                  required
                />
                <button
                  class="ghost-button"
                  type="button"
                  :disabled="submitting"
                  @click="showNewPassword = !showNewPassword"
                >
                  {{ showNewPassword ? '隐藏' : '显示' }}
                </button>
              </div>
              <small class="field-hint">密码至少 8 位，最多 72 个 UTF-8 字节。</small>
              <small v-if="validationErrors.newPassword" class="field-error">
                {{ validationErrors.newPassword }}
              </small>
            </label>

            <label class="field">
              <span>确认新密码</span>
              <div class="password-field">
                <input
                  v-model="form.confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  aria-label="确认新密码"
                  autocomplete="new-password"
                  placeholder="再次输入新密码"
                  :disabled="submitting"
                  :aria-invalid="Boolean(validationErrors.confirmPassword)"
                  required
                />
                <button
                  class="ghost-button"
                  type="button"
                  :disabled="submitting"
                  @click="showConfirmPassword = !showConfirmPassword"
                >
                  {{ showConfirmPassword ? '隐藏' : '显示' }}
                </button>
              </div>
              <small v-if="validationErrors.confirmPassword" class="field-error">
                {{ validationErrors.confirmPassword }}
              </small>
            </label>
          </fieldset>

          <p v-if="submitError" class="message message-error" role="status">
            {{ submitError }}
          </p>

          <button class="primary-button" type="submit" :disabled="!canSubmit">
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            {{ submitting ? '激活中...' : '激活账号' }}
          </button>
        </form>

        <p class="auth-switch">
          暂不激活？
          <RouterLink to="/login">返回登录</RouterLink>
        </p>
      </template>
    </section>
  </main>
</template>
