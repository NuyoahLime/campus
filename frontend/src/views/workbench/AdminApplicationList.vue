<template>
  <div class="alist"><h2>活动申请审核</h2>
    <el-card class="fc"><el-form :inline="true">
      <el-form-item label="状态"><el-select v-model="f.status" placeholder="全部" clearable @change="search"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
      <el-form-item label="学校"><el-select v-model="f.schoolId" placeholder="全部" clearable @change="search"><el-option v-for="s in schools" :key="s.schoolId" :label="s.schoolName" :value="s.schoolId" /></el-select></el-form-item>
      <el-form-item label="关键词"><el-input v-model="f.keyword" placeholder="标题/学校/申请人" clearable @change="search" /></el-form-item>
      <el-form-item><el-select v-model="f.sort" @change="search"><el-option v-for="o in sortOpts" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
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
      <div class="pager"><el-pagination layout="total,prev,pager,next" :total="total" :page-size="20" v-model:current-page="page" @current-change="load" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchAdminApplications, fetchAdminSchools } from '@/api/admin-application';
import { ApiError } from '@/api/http';
import { appStatusLabel, appStatusTagType } from '@/utils/application-status';
import type { AdminApplicationItem, AdminSchoolOption } from '@/types/admin-application';

const route=useRoute(); const router=useRouter();
const items=ref<AdminApplicationItem[]>([]); const loading=ref(true); const error=ref<string|null>(null);
const page=ref(1); const total=ref(0); const schools=ref<AdminSchoolOption[]>([]);
const f=reactive({status:'',schoolId:'',keyword:'',sort:'updated_desc'});
const statusOpts=[{v:'SUBMITTED',l:'待审核'},{v:'DRAFT',l:'草稿'},{v:'APPROVED',l:'已通过'},{v:'REJECTED',l:'已驳回'},{v:'WITHDRAWN',l:'已撤回'}];
const sortOpts=[{v:'updated_desc',l:'最新更新'},{v:'updated_asc',l:'最早更新'},{v:'created_desc',l:'最新创建'}];

onMounted(async()=>{
  try{schools.value=await fetchAdminSchools()}catch{}
  f.status=String(route.query.status||'');f.schoolId=String(route.query.schoolId||'');f.keyword=String(route.query.keyword||'');
  f.sort=String(route.query.sort||'updated_desc');page.value=Number(route.query.page)||1;load();
});

function search(){page.value=1;const q:Record<string,string>={};if(f.status)q.status=f.status;if(f.schoolId)q.schoolId=f.schoolId;if(f.keyword)q.keyword=f.keyword;if(f.sort!=='updated_desc')q.sort=f.sort;if(page.value>1)q.page=String(page.value);router.replace({query:q});load();}

async function load(){
  loading.value=true;error.value=null;
  try{const r=await fetchAdminApplications({status:f.status||undefined,schoolId:f.schoolId||undefined,keyword:f.keyword||undefined,sort:f.sort,page:page.value-1,size:20});items.value=r.items;total.value=r.totalElements}
  catch(e){error.value=e instanceof ApiError?e.message:'加载失败'}finally{loading.value=false}
}
function fmt(iso:string|null){return iso?new Date(iso).toLocaleDateString('zh-CN'):''}
</script>

<style scoped>.alist h2{margin-bottom:16px}.fc{margin-bottom:16px}.pager{display:flex;justify-content:center;margin-top:24px}</style>
