<template>
  <div class="alist"><h2>活动申请审核</h2>
    <el-card class="fc"><el-form :inline="true">
      <el-form-item label="状态"><el-select v-model="f.status" placeholder="全部" clearable @change="search"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
      <el-form-item label="学校"><el-select v-model="f.schoolId" placeholder="全部" clearable @change="search"><el-option v-for="s in schools" :key="s.schoolId" :label="s.schoolName" :value="s.schoolId" /></el-select></el-form-item>
      <el-form-item label="创建时间"><el-date-picker v-model="f.createdFrom" type="date" placeholder="开始" @change="search" value-format="YYYY-MM-DD" style="width:140px" /></el-form-item>
      <el-form-item label="至"><el-date-picker v-model="f.createdTo" type="date" placeholder="结束" @change="search" value-format="YYYY-MM-DD" style="width:140px" /></el-form-item>
      <el-form-item label="关键词"><el-input v-model="f.keyword" placeholder="标题/学校/申请人" clearable @change="search" /></el-form-item>
      <el-form-item><el-select v-model="f.sort" @change="search"><el-option v-for="o in sortOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
      <div v-if="schoolsErr" style="width:100%"><el-alert :title="schoolsErr" type="warning" show-icon :closable="false" /><el-button size="small" @click="loadSchools">重试</el-button></div>
    </el-form></el-card>
    <div v-if="loading"><el-skeleton :rows="5" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <div v-else-if="items.length===0"><el-empty description="暂无申请" /></div>
    <template v-else>
      <el-table :data="items" @row-click="(row: AdminApplicationItem) => $router.push(`/admin/applications/${row.applicationId}`)" style="cursor:pointer">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="schoolName" label="学校" />
        <el-table-column label="申请人"><template #default="{row}">{{ row.applicantName || '未知' }}</template></el-table-column>
        <el-table-column label="状态"><template #default="{row}"><el-tag :type="appStatusTagType(row.status)" size="small">{{ appStatusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="时间"><template #default="{row}">{{ fmt(row.createdAt) }}</template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination layout="total,prev,pager,next" :total="total" :page-size="20" v-model:current-page="page" @current-change="handlePageChange" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchAdminApplications, fetchAdminSchools } from '@/api/admin-application';
import { ApiError } from '@/api/http';
import { appStatusLabel, appStatusTagType } from '@/utils/application-status';
import { validateStatus, validateSort, validatePage, validateDate, validateSchoolId } from '@/utils/admin-application-filter';
import type { AdminApplicationItem, AdminSchoolOption } from '@/types/admin-application';

const route=useRoute(); const router=useRouter();
const items=ref<AdminApplicationItem[]>([]); const loading=ref(true); const error=ref<string|null>(null);
const page=ref(1); const total=ref(0); const schools=ref<AdminSchoolOption[]>([]); const schoolsErr=ref<string|null>(null);
const f=reactive({status:'',schoolId:'',keyword:'',sort:'updated_desc',createdFrom:'',createdTo:''});
const statusOpts=[{v:'SUBMITTED',l:'待审核'},{v:'DRAFT',l:'草稿'},{v:'APPROVED',l:'已通过'},{v:'REJECTED',l:'已驳回'},{v:'WITHDRAWN',l:'已撤回'}];
const sortOpts=[{v:'updated_desc',l:'最新更新'},{v:'updated_asc',l:'最早更新'},{v:'created_desc',l:'最新创建'},{v:'created_asc',l:'最早创建'}];
let requestSeq=0;

async function loadSchools(){try{schools.value=await fetchAdminSchools()}catch{schoolsErr.value='加载学校失败'}}

onMounted(async()=>{
  await loadSchools();
  // Use filter utilities to validate URL query params
  f.status = validateStatus(route.query.status) || '';
  f.schoolId = validateSchoolId(route.query.schoolId, new Set(schools.value.map(s=>s.schoolId))) || '';
  f.keyword = String(route.query.keyword || '').trim();
  f.sort = validateSort(route.query.sort) || 'updated_desc';
  f.createdFrom = validateDate(route.query.createdFrom) || '';
  f.createdTo = validateDate(route.query.createdTo) || '';
  page.value = validatePage(route.query.page);
  // Clean invalid params from URL
  const clean: Record<string,string> = {};
  if (f.status) clean.status = f.status;
  if (f.schoolId) clean.schoolId = f.schoolId;
  if (f.keyword) clean.keyword = f.keyword;
  if (f.sort !== 'updated_desc') clean.sort = f.sort;
  if (f.createdFrom) clean.createdFrom = f.createdFrom;
  if (f.createdTo) clean.createdTo = f.createdTo;
  if (page.value > 1) clean.page = String(page.value);
  router.replace({ query: clean });
  load();
});

function handlePageChange(p: number) {
  page.value = p;
  const q: Record<string,string> = {};
  if (f.status) q.status = f.status;
  if (f.schoolId) q.schoolId = f.schoolId;
  if (f.keyword) q.keyword = f.keyword;
  if (f.sort !== 'updated_desc') q.sort = f.sort;
  if (f.createdFrom) q.createdFrom = f.createdFrom;
  if (f.createdTo) q.createdTo = f.createdTo;
  if (p > 1) q.page = String(p);
  router.replace({ query: q });
  load();
}

function search() {
  // Validate date range before requesting
  if (f.createdFrom && f.createdTo && f.createdFrom > f.createdTo) {
    error.value = '开始日期不能晚于结束日期';
    return;
  }
  page.value = 1;
  const q: Record<string,string> = {};
  if (f.status) q.status = f.status;
  if (f.schoolId) q.schoolId = f.schoolId;
  if (f.keyword) q.keyword = f.keyword;
  if (f.sort !== 'updated_desc') q.sort = f.sort;
  if (f.createdFrom) q.createdFrom = f.createdFrom;
  if (f.createdTo) q.createdTo = f.createdTo;
  router.replace({ query: q });
  load();
}


async function load(){
  const seq=++requestSeq;loading.value=true;error.value=null;
  try{
    const r=await fetchAdminApplications({status:f.status||undefined,schoolId:f.schoolId||undefined,keyword:f.keyword||undefined,sort:f.sort,page:page.value-1,size:20,createdFrom:f.createdFrom||undefined,createdTo:f.createdTo||undefined});
    if(seq!==requestSeq)return;
    items.value=r.items;total.value=r.totalElements;loading.value=false
  } catch(e){if(seq!==requestSeq)return;error.value=e instanceof ApiError?e.message:'加载失败';}
}
function fmt(iso:string|null){return iso?new Date(iso).toLocaleDateString('zh-CN'):''}
</script>

<style scoped>.alist h2{margin-bottom:16px}.fc{margin-bottom:16px}.pager{display:flex;justify-content:center;margin-top:24px}</style>
