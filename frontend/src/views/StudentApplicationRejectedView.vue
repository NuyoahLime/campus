<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const username = computed(() => {
  const value = route.query.username;
  return typeof value === 'string' ? value : '';
});

function goToResubmit() {
  router.push({
    name: 'student-application-resubmit',
    query: username.value ? { username: username.value } : undefined
  });
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
      <p class="eyebrow">Application Review</p>
      <h1>
        <span class="headline-blue">申请未通过</span>
        <span class="headline-orange">可以重新提交</span>
      </h1>
      <p class="brand-copy">
        请重新确认学生身份信息，并使用当前账号密码提交新的审核申请。
      </p>
      <div class="brand-visual" aria-hidden="true">
        <span class="trophy"></span>
        <span class="runner runner-secondary"></span>
        <span class="runner runner-main"></span>
        <span class="runner runner-third"></span>
      </div>
    </section>

    <section class="login-card registration-card" aria-labelledby="rejected-title">
      <div class="submitted-state">
        <span class="submitted-icon submitted-icon-warning" aria-hidden="true">!</span>
        <p class="eyebrow">学生身份申请</p>
        <h2 id="rejected-title">申请未通过审核</h2>
        <p class="login-subtitle">
          你的学生身份申请暂未通过学校审核。
        </p>
        <p class="message message-warning">
          请检查并修改身份信息后重新提交。
        </p>
        <dl v-if="username" class="submitted-summary">
          <div>
            <dt>用户名</dt>
            <dd>{{ username }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>申请未通过</dd>
          </div>
        </dl>
        <div class="button-row">
          <button class="primary-button" type="button" @click="goToResubmit">
            修改资料并重新申请
          </button>
          <button class="secondary-button full-width-button" type="button" @click="backToLogin">
            返回登录
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
