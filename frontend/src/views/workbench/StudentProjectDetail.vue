<template>
  <div class="page-container">
    <el-page-header @back="$router.push('/student/projects')" title="返回项目列表" />
    <div v-if="loading"><el-skeleton :rows="6" animated /></div>
    <div v-else-if="notFound"><el-result icon="error" title="404" sub-title="项目不存在" /></div>
    <template v-else-if="detail">
      <h1>{{ detail.projectName }}</h1>
      <el-tag>{{ detail.category }}</el-tag>
      <el-divider />
      <el-row :gutter="24">
        <el-col :span="12"><section><h3>比赛规则</h3><p class="text">{{ detail.rulesText || '暂无说明' }}</p></section></el-col>
        <el-col :span="12"><section><h3>场地要求</h3><p class="text">{{ detail.venueRequirements || '暂无要求' }}</p></section></el-col>
      </el-row>
      <el-row :gutter="24">
        <el-col :span="12"><section><h3>器材要求</h3><p class="text">{{ detail.equipmentRequirements || '暂无要求' }}</p></section></el-col>
        <el-col :span="12"><section><h3>计分规则</h3><p class="text">{{ detail.effectiveScoreRule || '暂无' }} · {{ scoreTypeLabel(detail.scoreStorageType) }}<span v-if="detail.scoreUnit"> · {{ detail.scoreUnit }}</span></p></section></el-col>
      </el-row>
      <el-divider />
      <section>
        <h3>所属活动</h3>
        <p class="text">{{ detail.activityTitle }}</p>
        <p class="text" v-if="detail.activityDescription">{{ detail.activityDescription }}</p>
        <p class="text" v-if="detail.location">{{ detail.location }}</p>
      </section>
      <section>
        <h3>我的成绩</h3>
        <el-space wrap>
          <el-button type="primary" @click="$router.push(`/student/scores?projectId=${detail.projectId}`)">查看成绩记录</el-button>
          <el-button
            data-testid="project-ranking-button"
            @click="$router.push(`/student/rankings/${detail.activityProjectId}`)"
          >
            查看项目排名
          </el-button>
        </el-space>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { fetchMyProjectById } from '@/api/student-project';
import { ApiError } from '@/api/http';
import type { StudentProjectDetail } from '@/types/student-project';

const route = useRoute();
const detail = ref<StudentProjectDetail | null>(null);
const loading = ref(true);
const notFound = ref(false);

async function load() {
  loading.value = true; notFound.value = false;
  try { detail.value = await fetchMyProjectById(route.params.activityProjectId as string); }
  catch (e) { if (e instanceof ApiError && e.status===404) notFound.value = true; }
  finally { loading.value = false; }
}
function scoreTypeLabel(t: string) { const m: Record<string,string>={INTEGER:'整数',DECIMAL:'小数',DURATION:'时长',GRADE:'等级'}; return m[t]||t; }

onMounted(() => load());
</script>

<style scoped>
.page-container h1 { font-size: 24px; margin: 16px 0 12px; }
.text { color: #606266; line-height: 1.8; white-space: pre-wrap; }
section { margin-bottom: 16px; }
section h3 { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
</style>
