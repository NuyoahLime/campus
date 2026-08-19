<script setup lang="ts">
import { computed, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { submitStudentAppeal } from '../api/studentAppeal';
import { studentNavigation as navigation } from '../router/studentNavigation';

const route = useRoute();
const router = useRouter();
const scoreAttemptId = ref(String(route.query.scoreAttemptId ?? ''));
const appealType = ref('SCORE');
const appealReason = ref('');
const submitting = ref(false);
const error = ref('');
const canSubmit = computed(() => scoreAttemptId.value.trim() && appealReason.value.trim() && !submitting.value);

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const result = await submitStudentAppeal({
      scoreAttemptId: scoreAttemptId.value.trim(),
      appealType: appealType.value,
      appealReason: appealReason.value.trim()
    });
    await router.replace(`/student/appeals/${result.id}`);
  } catch (value) {
    if (value instanceof ApiError && value.status === 404) {
      error.value = '该成绩不存在或当前不可申诉。';
    } else if (value instanceof ApiError && value.status === 409) {
      error.value = '当前状态暂不允许提交申诉。';
    } else {
      error.value = '申诉提交失败，请稍后重试。';
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <WorkspaceShell role-label="学生" workspace-title="学生个人工作台" page-title="发起申诉" description="针对自己可查看的已确认成绩提交申诉。" home-path="/student" :navigation="navigation" :show-identity="false">
    <RouterLink class="project-back-link" to="/student/appeals">返回我的申诉</RouterLink>
    <section class="student-score-panel">
      <form class="project-form" @submit.prevent="submit">
        <fieldset>
          <legend>申诉信息</legend>
          <label>
            成绩 ID
            <input v-model="scoreAttemptId" autocomplete="off" required />
          </label>
          <label>
            申诉类型
            <select v-model="appealType">
              <option value="SCORE">成绩申诉</option>
              <option value="RANKING">排名申诉</option>
            </select>
          </label>
          <label class="project-form-wide">
            申诉原因
            <textarea v-model="appealReason" rows="6" required />
          </label>
        </fieldset>
        <p v-if="error" class="project-inline-error">{{ error }}</p>
        <div class="project-form-actions">
          <RouterLink class="secondary-button" to="/student/appeals">取消</RouterLink>
          <button class="primary-button" type="submit" :disabled="!canSubmit">
            {{ submitting ? '提交中...' : '提交申诉' }}
          </button>
        </div>
      </form>
    </section>
  </WorkspaceShell>
</template>
