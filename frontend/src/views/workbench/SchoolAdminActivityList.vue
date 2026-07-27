<template>
  <div>
    <div class="header"><h2>活动管理</h2><el-button type="primary" @click="$router.push('/school-admin/activities/new')">创建活动</el-button></div>
    <el-card class="filter"><el-form :inline="true">
      <el-form-item label="执行状态"><el-select v-model="f.executionStatus" placeholder="全部" clearable @change="search"><el-option v-for="o in execOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
      <el-form-item label="公开状态"><el-select v-model="f.publicStatus" placeholder="全部" clearable @change="search"><el-option v-for="o in pubOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
      <el-form-item label="关键词"><el-input v-model="f.keyword" placeholder="活动标题" clearable @change="search" /></el-form-item>
    </el-form></el-card>
    <div v-if="loading"><el-skeleton :rows="5" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <div v-else-if="items.length===0"><el-empty description="暂无活动" /></div>
    <template v-else>
      <el-table :data="items" @row-click="(r: Item) => $router.push(`/school-admin/activities/${r.id}`)" style="cursor:pointer">
        <el-table-column prop="title" label="标题" />
        <el-table-column label="执行状态"><template #default="{row}"><el-tag :type="execTagType(row.executionStatus)" size="small">{{ executionLabel(row.executionStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="公开状态"><template #default="{row}"><el-tag :type="publicTagType(row.publicStatus)" size="small">{{ publicLabel(row.publicStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="时间"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination layout="total,prev,pager,next" :total="total" :page-size="20" v-model:current-page="page" @current-change="handlePage" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'; import { useRoute } from 'vue-router';
import { fetchActivities } from '@/api/school-admin-activity'; import { ApiError } from '@/api/http';
import { executionLabel, publicLabel, execTagType, publicTagType } from '@/utils/activity-status';
import type { SchoolAdminActivityItem as Item } from '@/types/school-admin-activity';

const route=useRoute();
const items=ref<Item[]>([]); const loading=ref(true); const error=ref<string|null>(null); const total=ref(0); const page=ref(1);
const f=reactive({executionStatus:'',publicStatus:'',keyword:''});
const execOpts=[{v:'DRAFT',l:'草稿'},{v:'PUBLISHED',l:'已发布'},{v:'IN_PROGRESS',l:'进行中'},{v:'ENDED',l:'已结束'},{v:'CANCELLED',l:'已取消'}];
const pubOpts=[{v:'NOT_SUBMITTED',l:'未提交'},{v:'PENDING_PLATFORM_REVIEW',l:'审核中'},{v:'PLATFORM_APPROVED',l:'平台批准'},{v:'PLATFORM_REJECTED',l:'平台驳回'},{v:'PUBLIC',l:'已公开'},{v:'SCHOOL_WITHDRAWN',l:'学校撤回'},{v:'PLATFORM_TAKEDOWN',l:'平台下架'}];

async function load() { loading.value=true;error.value=null;try{const flt={executionStatus:f.executionStatus||undefined,publicStatus:f.publicStatus||undefined,keyword:f.keyword?.trim()||undefined};const r=await fetchActivities(flt,page.value-1);items.value=r.items;total.value=r.totalElements}catch(e){error.value=e instanceof ApiError?e.message:'加载失败'}finally{loading.value=false} }
function search() { page.value=1; load(); }
function handlePage(p:number) { page.value=p; load(); }
function fmt(iso:string|null) { return iso ? new Date(iso).toLocaleDateString('zh-CN') : ''; }
onMounted(() => { f.executionStatus=(route.query.executionStatus as string)||''; f.publicStatus=(route.query.publicStatus as string)||''; f.keyword=(route.query.keyword as string)||''; page.value=Number(route.query.page)||1; load(); });
</script>

<style scoped>.header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.header h2{margin:0}.filter{margin-bottom:16px}.pager{display:flex;justify-content:center;margin-top:24px}</style>
