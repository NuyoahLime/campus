<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import PublicShell from '../components/PublicShell.vue';
import { ApiError } from '../api/http';
import { submitPublicSchoolRegistration } from '../api/publicSchoolRegistration';
import type {
  PublicSchoolRegistrationRequest,
  PublicSchoolRegistrationResponse
} from '../types/publicSchoolRegistration';

const loading = ref(true);
const submitting = ref(false);
const submitError = ref('');
const validationErrors = ref<Record<string, string>>({});
const submitted = ref<PublicSchoolRegistrationResponse | null>(null);

const form = reactive({
  schoolName: '',
  unifiedCodeType: '',
  unifiedCode: '',
  schoolType: '',
  region: '',
  address: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  description: ''
});

const fieldLabels: Record<string, string> = {
  schoolName: '学校名称',
  unifiedCodeType: '统一识别类型',
  unifiedCode: '统一识别编码',
  schoolType: '学校类型',
  region: '所属地区',
  address: '学校地址',
  contactName: '联系人姓名',
  contactPhone: '联系电话',
  contactEmail: '联系邮箱',
  description: '申请说明'
};

const canSubmit = computed(() => !loading.value && !submitting.value);

function trimForm() {
  for (const key of Object.keys(form) as Array<keyof typeof form>) {
    form[key] = form[key].trim();
  }
}

function validate(): boolean {
  trimForm();
  const errors: Record<string, string> = {};
  const required: Array<keyof typeof form> = [
    'schoolName', 'unifiedCodeType', 'schoolType', 'region', 'address',
    'contactName', 'contactPhone', 'contactEmail'
  ];

  for (const field of required) {
    if (!form[field]) errors[field] = `请输入${fieldLabels[field]}。`;
  }

  if (form.schoolName.length > 200) errors.schoolName = '学校名称不能超过 200 个字符。';
  if (form.unifiedCodeType.length > 32) errors.unifiedCodeType = '统一识别类型不能超过 32 个字符。';
  if (form.unifiedCode.length > 64) errors.unifiedCode = '统一识别编码不能超过 64 个字符。';
  if (form.schoolType.length > 32) errors.schoolType = '学校类型不能超过 32 个字符。';
  if (form.region.length > 128) errors.region = '所属地区不能超过 128 个字符。';
  if (form.contactName.length > 100) errors.contactName = '联系人姓名不能超过 100 个字符。';
  if (form.contactPhone.length > 32) errors.contactPhone = '联系电话不能超过 32 个字符。';
  if (form.contactEmail.length > 200) errors.contactEmail = '联系邮箱不能超过 200 个字符。';
  if (form.contactEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.contactEmail)) {
    errors.contactEmail = '请输入有效的联系邮箱。';
  }

  validationErrors.value = errors;
  return Object.keys(errors).length === 0;
}

function applyBackendFieldErrors(error: ApiError) {
  if (!error.body || typeof error.body !== 'object' || !('details' in error.body)) return;
  const details = (error.body as { details?: unknown }).details;
  if (!Array.isArray(details)) return;

  const errors: Record<string, string> = {};
  for (const detail of details) {
    if (!detail || typeof detail !== 'object') continue;
    const field = 'field' in detail ? String(detail.field) : '';
    if (!fieldLabels[field]) continue;
    errors[field] = `${fieldLabels[field]}填写不正确，请检查后重试。`;
  }
  validationErrors.value = { ...validationErrors.value, ...errors };
}

function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return '网络连接失败，请检查网络后重试。';
  if (error.code === 'VALIDATION_FAILED' || error.status === 400) return '请检查表单内容后重新提交。';
  if (error.code === 'SCHOOL_UNIFIED_CODE_CONFLICT' || error.status === 409) {
    return '该学校识别信息已存在或正在处理中，请核对后再提交。';
  }
  if (error.status === 401 || error.status === 403) return '页面安全校验已失效，请刷新后重试。';
  return '申请提交失败，请稍后重试。';
}

function requestPayload(): PublicSchoolRegistrationRequest {
  return {
    schoolName: form.schoolName,
    unifiedCodeType: form.unifiedCodeType,
    ...(form.unifiedCode ? { unifiedCode: form.unifiedCode } : {}),
    schoolType: form.schoolType,
    region: form.region,
    address: form.address,
    contactName: form.contactName,
    contactPhone: form.contactPhone,
    contactEmail: form.contactEmail,
    ...(form.description ? { description: form.description } : {})
  };
}

async function submit() {
  if (submitting.value || !validate()) return;
  submitting.value = true;
  submitError.value = '';
  validationErrors.value = {};
  try {
    submitted.value = await submitPublicSchoolRegistration(requestPayload());
  } catch (error) {
    if (error instanceof ApiError) applyBackendFieldErrors(error);
    submitError.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

function startAnother() {
  submitted.value = null;
  submitError.value = '';
  validationErrors.value = {};
  for (const key of Object.keys(form) as Array<keyof typeof form>) form[key] = '';
}

onMounted(() => {
  loading.value = false;
});
</script>

<template>
  <PublicShell active="registration">
    <main class="school-registration-page">
      <header class="school-registration-heading">
        <p class="eyebrow">SCHOOL REGISTRATION</p>
        <h1>学校入驻申请</h1>
        <p>提交学校基础资料和联系人信息，由平台管理员进行审核。</p>
      </header>

      <div v-if="loading" class="school-registration-state" role="status">
        正在准备申请表单...
      </div>

      <section v-else-if="submitted" class="school-registration-success" aria-labelledby="submitted-title">
        <span class="school-registration-success-mark" aria-hidden="true">✓</span>
        <div>
          <p class="eyebrow">APPLICATION SUBMITTED</p>
          <h2 id="submitted-title">申请已提交</h2>
          <p>学校入驻资料已进入审核流程，请等待平台管理员审核。</p>
        </div>
        <dl>
          <div><dt>学校名称</dt><dd>{{ submitted.schoolName }}</dd></div>
          <div><dt>申请编号</dt><dd>{{ submitted.id }}</dd></div>
          <div><dt>当前状态</dt><dd>已提交</dd></div>
        </dl>
        <div class="school-registration-success-actions">
          <RouterLink class="secondary-button" to="/">返回首页</RouterLink>
          <button class="primary-button" type="button" @click="startAnother">提交另一所学校</button>
        </div>
      </section>

      <form v-else class="school-registration-form" novalidate @submit.prevent="submit">
        <fieldset>
          <legend>学校信息</legend>
          <div class="school-registration-grid">
            <label class="school-registration-field school-registration-field-wide">
              <span>学校名称</span>
              <input v-model="form.schoolName" maxlength="200" autocomplete="organization" placeholder="请输入学校全称" :disabled="submitting" :aria-invalid="Boolean(validationErrors.schoolName)" required>
              <small v-if="validationErrors.schoolName" class="field-error">{{ validationErrors.schoolName }}</small>
            </label>

            <label class="school-registration-field">
              <span>统一识别类型</span>
              <input v-model="form.unifiedCodeType" list="unified-code-types" maxlength="32" placeholder="例如：USCC" :disabled="submitting" :aria-invalid="Boolean(validationErrors.unifiedCodeType)" required>
              <datalist id="unified-code-types">
                <option value="USCC">统一社会信用代码</option>
                <option value="SCHOOL_IDENTIFIER">学校标识码</option>
                <option value="EDUCATION_REGISTRATION">教育主管部门登记编码</option>
                <option value="MANUAL_REVIEW">其他人工核验标识</option>
              </datalist>
              <small v-if="validationErrors.unifiedCodeType" class="field-error">{{ validationErrors.unifiedCodeType }}</small>
            </label>

            <label class="school-registration-field">
              <span>统一识别编码 <em>选填</em></span>
              <input v-model="form.unifiedCode" maxlength="64" placeholder="无编码时可留空" :disabled="submitting" :aria-invalid="Boolean(validationErrors.unifiedCode)">
              <small v-if="validationErrors.unifiedCode" class="field-error">{{ validationErrors.unifiedCode }}</small>
            </label>

            <label class="school-registration-field">
              <span>学校类型</span>
              <input v-model="form.schoolType" list="school-types" maxlength="32" placeholder="例如：UNIVERSITY" :disabled="submitting" :aria-invalid="Boolean(validationErrors.schoolType)" required>
              <datalist id="school-types">
                <option value="PRIMARY">小学</option>
                <option value="MIDDLE">初中</option>
                <option value="HIGH">高中</option>
                <option value="VOCATIONAL">职业学校</option>
                <option value="UNIVERSITY">高等院校</option>
                <option value="OTHER">其他</option>
              </datalist>
              <small v-if="validationErrors.schoolType" class="field-error">{{ validationErrors.schoolType }}</small>
            </label>

            <label class="school-registration-field">
              <span>所属地区</span>
              <input v-model="form.region" maxlength="128" autocomplete="address-level1" placeholder="省 / 市 / 区县" :disabled="submitting" :aria-invalid="Boolean(validationErrors.region)" required>
              <small v-if="validationErrors.region" class="field-error">{{ validationErrors.region }}</small>
            </label>

            <label class="school-registration-field school-registration-field-wide">
              <span>学校地址</span>
              <textarea v-model="form.address" rows="3" autocomplete="street-address" placeholder="请输入学校详细地址" :disabled="submitting" :aria-invalid="Boolean(validationErrors.address)" required></textarea>
              <small v-if="validationErrors.address" class="field-error">{{ validationErrors.address }}</small>
            </label>
          </div>
        </fieldset>

        <fieldset>
          <legend>联系人信息</legend>
          <div class="school-registration-grid">
            <label class="school-registration-field">
              <span>联系人姓名</span>
              <input v-model="form.contactName" maxlength="100" autocomplete="name" placeholder="请输入联系人姓名" :disabled="submitting" :aria-invalid="Boolean(validationErrors.contactName)" required>
              <small v-if="validationErrors.contactName" class="field-error">{{ validationErrors.contactName }}</small>
            </label>

            <label class="school-registration-field">
              <span>联系电话</span>
              <input v-model="form.contactPhone" type="tel" maxlength="32" autocomplete="tel" placeholder="请输入联系电话" :disabled="submitting" :aria-invalid="Boolean(validationErrors.contactPhone)" required>
              <small v-if="validationErrors.contactPhone" class="field-error">{{ validationErrors.contactPhone }}</small>
            </label>

            <label class="school-registration-field school-registration-field-wide">
              <span>联系邮箱</span>
              <input v-model="form.contactEmail" type="email" maxlength="200" autocomplete="email" placeholder="name@example.com" :disabled="submitting" :aria-invalid="Boolean(validationErrors.contactEmail)" required>
              <small v-if="validationErrors.contactEmail" class="field-error">{{ validationErrors.contactEmail }}</small>
            </label>
          </div>
        </fieldset>

        <fieldset>
          <legend>申请说明</legend>
          <label class="school-registration-field">
            <span>补充说明 <em>选填</em></span>
            <textarea v-model="form.description" rows="5" placeholder="可补充学校情况和入驻用途" :disabled="submitting"></textarea>
          </label>
        </fieldset>

        <p v-if="submitError" class="message message-error" role="alert">{{ submitError }}</p>

        <footer class="school-registration-form-actions">
          <RouterLink class="secondary-button" to="/">取消</RouterLink>
          <button class="primary-button" type="submit" :disabled="!canSubmit">
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            {{ submitting ? '正在提交...' : '提交入驻申请' }}
          </button>
        </footer>
      </form>
    </main>
  </PublicShell>
</template>
