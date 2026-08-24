<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import { ApiError } from '../api/http';
import { getSchoolAdminScores } from '../api/schoolAdminScore';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
const route = useRoute(); const loading = ref(true); const error = ref(''); const scores = ref<any[]>([]); const title = ref('');
async function load() { loading.value = true; try { const result = await getSchoolAdminScores(String(route.params.id)); scores.value = result.scores; title.value = result.activityTitle; } catch (e) { error.value = e instanceof ApiError && e.status === 403 ? '当前账号没有本校成绩管理权限。' : '成绩草稿加载失败，请稍后重试。'; } finally { loading.value = false; } }
onMounted(() => void load());
</script>
<template><WorkspaceShell role-label="学校管理员" workspace-title="学校管理工作台" page-title="成绩管理" :description="title || '管理本校活动成绩草稿。'" home-path="/school-admin" :navigation="navigation"><section class="project-admin-panel"><div v-if="loading" class="project-state">正在加载成绩草稿...</div><div v-else-if="error" class="project-state project-state-error"><strong>{{ error }}</strong><button class="secondary-button" @click="load">重新加载</button></div><div v-else-if="!scores.length" class="project-state">当前活动还没有成绩草稿。</div><div v-else class="project-admin-table-wrap"><table class="project-admin-table"><thead><tr><th>学生</th><th>项目</th><th>尝试</th><th>状态</th><th>成绩</th></tr></thead><tbody><tr v-for="score in scores" :key="score.scoreAttemptId"><td>{{ score.studentDisplay || score.studentId }}</td><td>{{ score.activityProjectId }}</td><td>{{ score.attemptNumber }}</td><td>{{ score.status }}</td><td>{{ score.integerValue ?? score.decimalValue ?? score.durationMs ?? score.grade ?? '—' }}</td></tr></tbody></table></div></section></WorkspaceShell></template>
