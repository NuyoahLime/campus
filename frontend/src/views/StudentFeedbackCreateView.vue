<script setup lang="ts">
import { computed, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { submitStudentFeedback } from '../api/studentFeedback';
import { studentNavigation as navigation } from '../router/studentNavigation';

const router = useRouter();
const feedbackType = ref('GENERAL');
const content = ref('');
const submitting = ref(false);
const error = ref('');
const canSubmit = computed(() => content.value.trim() && !submitting.value);

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const result = await submitStudentFeedback({
      feedbackType: feedbackType.value,
      content: content.value.trim()
    });
    await router.replace(`/student/feedback/${result.id}`);
  } catch (value) {
    error.value = value instanceof ApiError && value.status === 403
      ? '当前账号没有意见反馈权限。'
      : '反馈提交失败，请稍后重试。';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <WorkspaceShell role-label="学生" workspace-title="学生个人工作台" page-title="提交反馈" description="向学校提交当前账号自己的意见反馈。" home-path="/student" :navigation="navigation" :show-identity="false">
    <RouterLink class="project-back-link" to="/student/feedback">返回意见反馈</RouterLink>
    <section class="student-score-panel">
      <form class="project-form" @submit.prevent="submit">
        <fieldset>
          <legend>反馈内容</legend>
          <label>
            反馈类型
            <select v-model="feedbackType">
              <option value="GENERAL">一般反馈</option>
              <option value="SCORE_PROBLEM">成绩问题</option>
              <option value="RANKING_PROBLEM">排名问题</option>
            </select>
          </label>
          <label class="project-form-wide">
            反馈说明
            <textarea v-model="content" rows="7" required />
          </label>
        </fieldset>
        <p v-if="error" class="project-inline-error">{{ error }}</p>
        <div class="project-form-actions">
          <RouterLink class="secondary-button" to="/student/feedback">取消</RouterLink>
          <button class="primary-button" type="submit" :disabled="!canSubmit">
            {{ submitting ? '提交中...' : '提交反馈' }}
          </button>
        </div>
      </form>
    </section>
  </WorkspaceShell>
</template>
