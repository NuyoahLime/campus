<template>
  <div>
    <div v-if="loading"><el-skeleton :rows="6" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <template v-else-if="detail">
      <el-page-header @back="$router.push('/school-admin/activities')" title="返回活动列表" />
      <h1>{{ detail.title }}</h1>
      <div class="tags"><el-tag :type="execTagType(detail.executionStatus)">{{ executionLabel(detail.executionStatus) }}</el-tag><el-tag :type="publicTagType(detail.publicStatus)" style="margin-left:8px">{{ publicLabel(detail.publicStatus) }}</el-tag></div>
      <el-divider />
      <el-descriptions :column="2" border><el-descriptions-item label="开始时间">{{ fmt(detail.startTime) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ fmt(detail.endTime) }}</el-descriptions-item><el-descriptions-item label="地点">{{ detail.location || '未设置' }}</el-descriptions-item><el-descriptions-item label="描述">{{ detail.description || '暂无' }}</el-descriptions-item></el-descriptions>

      <!-- DRAFT actions -->
      <div v-if="detail.executionStatus==='DRAFT'" class="actions">
        <el-button @click="showEdit=true">编辑</el-button>
        <el-button @click="showAddProject=true">添加项目</el-button>
        <el-button type="success" :loading="publishing" :disabled="publishing" @click="handlePublish">发布活动</el-button>
      </div>

      <!-- Edit dialog -->
      <el-dialog v-model="showEdit" title="编辑活动" width="520px"><el-form ref="efRef" :model="eForm" label-position="top" @submit.prevent="handleUpdate">
        <el-form-item label="活动名称"><el-input v-model="eForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="活动说明"><el-input v-model="eForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="开始时间"><el-date-picker v-model="eForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col><el-col :span="12"><el-form-item label="结束时间"><el-date-picker v-model="eForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col></el-row>
        <el-form-item label="地点"><el-input v-model="eForm.location" /></el-form-item>
        <template #footer><el-button @click="showEdit=false">取消</el-button><el-button native-type="submit" type="primary" :loading="updating" :disabled="updating">保存</el-button></template>
      </el-form></el-dialog>

      <!-- Projects -->
      <section class="sec"><h3>已配置项目</h3>
        <div v-if="detail.projects.length===0" class="empty">暂无项目</div>
        <el-table v-else :data="detail.projects"><el-table-column prop="projectId" label="项目ID" /><el-table-column v-if="detail.executionStatus==='DRAFT'" label="操作" width="100"><template #default="{row}"><el-button size="small" type="danger" @click="handleRemoveProject(row.projectId)">移除</el-button></template></el-table-column></el-table>
      </section>

      <!-- Add project dialog -->
      <el-dialog v-model="showAddProject" title="添加项目" width="400px">
        <el-select v-model="selectedProjectId" placeholder="选择挑战项目" filterable style="width:100%"><el-option v-for="p in projects" :key="p.projectId" :label="p.name" :value="p.projectId" /></el-select>
        <template #footer><el-button @click="showAddProject=false">取消</el-button><el-button type="primary" :loading="addingProject" :disabled="addingProject||!selectedProjectId" @click="handleAddProject">添加</el-button></template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'; import { useRoute } from 'vue-router';
import { fetchActivity, updateActivity, addProject, removeProject, publishActivity } from '@/api/school-admin-activity';
import { ApiError } from '@/api/http'; import http from '@/api/http';
import { executionLabel, publicLabel, execTagType, publicTagType } from '@/utils/activity-status';
import { ElMessageBox } from 'element-plus';
import type { SchoolAdminActivityDetail } from '@/types/school-admin-activity';

const route=useRoute(); const id=route.params.activityId as string;
const detail=ref<SchoolAdminActivityDetail|null>(null); const loading=ref(true); const error=ref<string|null>(null);
const showEdit=ref(false); const showAddProject=ref(false); const updating=ref(false); const publishing=ref(false); const addingProject=ref(false);
const selectedProjectId=ref(''); const projects=ref<{projectId:string;name:string}[]>([]);
const eForm=reactive({title:'',description:'',startTime:'',endTime:'',location:''});

async function load() { loading.value=true;error.value=null;try{detail.value=await fetchActivity(id)}catch(err:unknown){error.value=err instanceof ApiError?err.message:'加载失败'}finally{loading.value=false} }
async function handleUpdate() { if(updating.value)return;updating.value=true;try{detail.value=await updateActivity(id,{title:eForm.title,description:eForm.description,startTime:eForm.startTime||undefined,endTime:eForm.endTime||undefined,location:eForm.location||undefined});showEdit.value=false}catch{/* keep dialog */}finally{updating.value=false} }
async function handlePublish() { try{await ElMessageBox.confirm('确认发布？','确认',{type:'warning'})}catch{return}publishing.value=true;try{detail.value=await publishActivity(id)}catch(err:unknown){alert(err instanceof ApiError?err.message:'发布失败')}finally{publishing.value=false} }
async function loadProjects() { try{const r=await http.get<{items:{projectId:string;name:string}[]}>('/v1/public/challenge-projects',{params:{size:200}});projects.value=r.data.items}catch{} }
async function handleAddProject() { if(!selectedProjectId.value)return;addingProject.value=true;try{await addProject(id,selectedProjectId.value);showAddProject.value=false;selectedProjectId.value='';load()}catch{}finally{addingProject.value=false} }
async function handleRemoveProject(pid:string) { try{await ElMessageBox.confirm('确认移除？','确认',{type:'warning'})}catch{return}try{await removeProject(id,pid);load()}catch{} }
function fmt(iso:string|null) { return iso ? new Date(iso).toLocaleDateString('zh-CN') : '-'; }

onMounted(()=>{load();loadProjects();});
watch(showEdit,(v)=>{if(v&&detail.value){eForm.title=detail.value.title||'';eForm.description=detail.value.description||'';eForm.startTime=detail.value.startTime||'';eForm.endTime=detail.value.endTime||'';eForm.location=detail.value.location||'';}});
</script>

<style scoped>h1{font-size:24px;margin:16px 0 8px}.tags{margin-bottom:16px}.actions{margin:16px 0;display:flex;gap:12px}.sec{margin-top:24px}.sec h3{font-size:16px;font-weight:600;margin-bottom:12px}.empty{color:#c0c4cc}</style>
