<template>
  <div>
    <div v-if="!isValidId"><el-result icon="error" title="无效的活动ID"><template #extra><el-button type="primary" @click="$router.push('/school-admin/activities')">返回活动列表</el-button></template></el-result></div>
    <div v-else-if="loading"><el-skeleton :rows="6" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <template v-else-if="detail">
      <el-page-header @back="$router.push('/school-admin/activities')" title="返回活动列表" />
      <h1>{{ detail.title }}</h1>
      <div class="tags"><el-tag :type="execTagType(detail.executionStatus)">{{ executionLabel(detail.executionStatus) }}</el-tag><el-tag :type="publicTagType(detail.publicStatus)" style="margin-left:8px">{{ publicLabel(detail.publicStatus) }}</el-tag></div>
      <el-divider />
      <el-descriptions :column="2" border><el-descriptions-item label="开始时间">{{ fmt(detail.startTime) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ fmt(detail.endTime) }}</el-descriptions-item><el-descriptions-item label="地点">{{ detail.location || '未设置' }}</el-descriptions-item><el-descriptions-item label="描述">{{ detail.description || '暂无' }}</el-descriptions-item></el-descriptions>

      <div v-if="detail.executionStatus==='DRAFT'" class="actions">
        <el-button @click="openEdit">编辑</el-button>
        <el-button @click="openAddProject">添加项目</el-button>
        <el-button type="success" :loading="publishing" :disabled="publishing || publishBlocked" @click="handlePublish">发布活动</el-button>
      </div>
      <el-alert v-if="actionErr" :title="actionErr" type="error" show-icon :closable="false" style="margin-top:12px" />

      <el-dialog v-model="showEdit" title="编辑活动" width="520px">
        <el-form ref="efRef" :model="eForm" :rules="editRules" label-position="top" @submit.prevent="handleUpdate">
          <el-form-item label="活动名称" prop="title"><el-input v-model="eForm.title" maxlength="200" /></el-form-item>
          <el-form-item label="活动说明" prop="description"><el-input v-model="eForm.description" type="textarea" :rows="3" /></el-form-item>
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="开始时间" prop="startTime"><el-date-picker v-model="eForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col><el-col :span="12"><el-form-item label="结束时间" prop="endTime"><el-date-picker v-model="eForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col></el-row>
          <el-form-item label="地点" prop="location"><el-input v-model="eForm.location" /></el-form-item>
          <el-alert v-if="editErr" :title="editErr" type="error" show-icon style="margin-bottom:12px" />
        </el-form>
        <template #footer><el-button @click="showEdit=false">取消</el-button><el-button native-type="submit" type="primary" :loading="updating" :disabled="updating" @click="handleUpdate">保存</el-button></template>
      </el-dialog>

      <section class="sec"><h3>已配置项目</h3>
        <div v-if="detail.projects.length===0" class="empty">暂无项目</div>
        <el-table v-else :data="detail.projects"><el-table-column prop="projectId" label="项目ID" width="300" />
          <el-table-column label="负责教师"><template #default="{row}">
            <span v-if="row._teachers && row._teachers.length">{{ row._teachers.map((t:any) => t.username + (t.subject?' ('+t.subject+')':'') + (t.title?' - '+t.title:'')).join(', ') }}</span>
            <span v-else class="empty">未分配负责教师</span>
          </template></el-table-column>
          <el-table-column v-if="detail.executionStatus!=='ENDED' && detail.executionStatus!=='CANCELLED'" label="管理" width="180"><template #default="{row}">
            <el-button size="small" @click="openManageTeachers(row.projectId)">管理教师</el-button>
            <el-button v-if="detail.executionStatus==='DRAFT'" size="small" type="danger" :loading="removingProjectId===row.projectId" :disabled="removingProjectId!==null" @click="handleRemoveProject(row.projectId)">移除</el-button>
          </template></el-table-column></el-table>
        <div v-if="detail.executionStatus==='DRAFT'" style="margin-top:8px"><span v-if="publishBlocked" style="color:#f56c6c;font-size:13px">每个项目至少分配一名负责教师后才能发布</span></div>
      </section>

      <el-dialog v-model="showManageTeachers" :title="'管理教师 — '+manageProjectId" width="560px" @closed="closeManageTeachers">
        <el-alert v-if="teacherErr" :title="teacherErr" type="error" show-icon style="margin-bottom:12px"><template #default><el-button size="small" @click="loadTeacherData" style="margin-top:4px">重新加载</el-button></template></el-alert>
        <div v-if="teacherLoading" style="text-align:center;padding:20px"><el-skeleton :rows="3" animated /></div>
        <template v-else>
          <div style="margin-bottom:12px"><strong>已分配教师</strong></div>
          <div v-if="projectTeachers.length===0" style="color:#c0c4cc;margin-bottom:16px">未分配负责教师</div>
          <el-tag v-for="t in projectTeachers" :key="t.id" :closable="unassigningTeacherId===null" style="margin:0 4px 4px 0" @close="handleUnassignTeacher(t.userId)">{{ t.username }}{{ t.subject?' ('+t.subject+')':'' }}{{ t.title?' - '+t.title:'' }}</el-tag>
          <el-divider />
          <div style="margin-bottom:8px"><strong>添加教师</strong></div>
          <el-input v-model="teacherKeyword" placeholder="搜索教师" clearable @change="searchTeachers" style="margin-bottom:8px" />
          <div v-if="availableTeachers.length===0" style="color:#c0c4cc;text-align:center;padding:8px">暂无可分配教师</div>
          <el-select v-else v-model="selectedTeacherId" placeholder="选择教师" filterable style="width:100%" :loading="teacherListLoading"><el-option v-for="t in availableTeachers" :key="t.userId" :label="t.username + (t.subject?' - '+t.subject:'') + (t.title?' - '+t.title:'')" :value="t.userId" /></el-select>
        </template>
        <template #footer><el-button @click="showManageTeachers=false">关闭</el-button><el-button type="primary" :loading="assigningTeacher" :disabled="assigningTeacher||!selectedTeacherId" @click="handleAssignTeacher">添加</el-button></template>
      </el-dialog>

      <el-dialog v-model="showAddProject" title="添加项目" width="400px">
        <el-alert v-if="projErr" :title="projErr" type="error" show-icon style="margin-bottom:12px"><template #default><el-button size="small" @click="loadProjects" style="margin-top:4px">重新加载</el-button></template></el-alert>
        <div v-if="availableProjects.length===0 && !projLoading" style="color:#909399;text-align:center;padding:20px">暂无可添加项目</div>
        <el-select v-else v-model="selectedProjectId" placeholder="选择挑战项目" filterable style="width:100%" :loading="projLoading"><el-option v-for="p in availableProjects" :key="p.projectId" :label="p.name" :value="p.projectId" /></el-select>
        <template #footer><el-button @click="showAddProject=false">取消</el-button><el-button type="primary" :loading="addingProject" :disabled="addingProject||!selectedProjectId" @click="handleAddProject">添加</el-button></template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { fetchActivity, updateActivity, addProject, removeProject, publishActivity, fetchAvailableProjects, fetchSchoolTeachers, fetchResponsibleTeachers, assignResponsibleTeacher, unassignResponsibleTeacher } from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import { executionLabel, publicLabel, execTagType, publicTagType } from '@/utils/activity-status';
import { localDateTimeToInstant, instantToLocalDateTime } from '@/utils/activity-time';
import { ElMessageBox } from 'element-plus'; import type { FormInstance, FormRules } from 'element-plus';
import type { SchoolAdminActivityDetail } from '@/types/school-admin-activity';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const props = defineProps<{ activityId: string }>();
const id = props.activityId;
const isValidId = computed(() => UUID_PATTERN.test(id));

const detail=ref<SchoolAdminActivityDetail|null>(null); const loading=ref(true); const error=ref<string|null>(null);
const showEdit=ref(false); const showAddProject=ref(false); const updating=ref(false); const publishing=ref(false); const addingProject=ref(false); const efRef=ref<FormInstance>();
const actionErr=ref<string|null>(null); const editErr=ref<string|null>(null); const projErr=ref<string|null>(null);
const selectedProjectId=ref(''); const projects=ref<{projectId:string;name:string}[]>([]);
const removingProjectId=ref<string|null>(null);
const projLoading=ref(false);
const eForm=reactive({title:'',description:'',startTime:'',endTime:'',location:''});
const editRules: FormRules = { title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }, { max: 200 }], description: [{ max: 2000 }], startTime: [], endTime: [] };

const availableProjects = computed(() => {
  if (!detail.value) return projects.value;
  const addedIds = new Set(detail.value.projects.map(p => p.projectId));
  return projects.value.filter(p => !addedIds.has(p.projectId));
});

async function load() { if (!isValidId.value) return; loading.value=true;error.value=null;try{const loaded=await fetchActivity(id); loaded.projects.forEach(p=>{p._teachers=loaded.responsibleTeachers.filter(t=>t.activityProjectId===p.id);}); detail.value=loaded; }catch(err:unknown){error.value=err instanceof ApiError?err.message:'加载失败'}finally{loading.value=false} }
async function loadProjects() { if (projLoading.value) return; projLoading.value=true;projErr.value=null;try{const r=await fetchAvailableProjects();projects.value=r.items}catch(err:unknown){projErr.value=err instanceof ApiError?err.message:'加载项目列表失败'}finally{projLoading.value=false} }

function openEdit() { if(!detail.value)return;eForm.title=detail.value.title||'';eForm.description=detail.value.description||'';eForm.startTime=instantToLocalDateTime(detail.value.startTime);eForm.endTime=instantToLocalDateTime(detail.value.endTime);eForm.location=detail.value.location||'';showEdit.value=true; }

async function handleUpdate() {
  if(updating.value)return; updating.value=true; editErr.value=null;
  if (eForm.startTime && eForm.endTime && new Date(eForm.endTime) < new Date(eForm.startTime)) { editErr.value='结束时间不得早于开始时间'; updating.value=false; return; }
  const valid = await efRef.value?.validate().catch(() => false) ?? false;
  if (!valid) { updating.value = false; return; }
  try {
    await updateActivity(id, {title:eForm.title,description:eForm.description||undefined,startTime:localDateTimeToInstant(eForm.startTime),endTime:localDateTimeToInstant(eForm.endTime),location:eForm.location||undefined});
    showEdit.value=false; await load();
  } catch(err:unknown) { editErr.value = err instanceof ApiError ? err.message : '保存失败'; }
  finally { updating.value = false; }
}

async function openAddProject() { showAddProject.value=true; selectedProjectId.value=''; projErr.value=null; await loadProjects(); }

async function handlePublish() {
  if (publishing.value) return;
  publishing.value = true; actionErr.value = null;
  try {
    await ElMessageBox.confirm('确认发布？','确认',{type:'warning'});
    await publishActivity(id); await load();
  } catch(err:unknown) {
    if (err !== 'cancel' && err !== 'close') actionErr.value = err instanceof ApiError ? err.message : '发布失败';
  } finally { publishing.value = false; }
}

async function handleAddProject() {
  if (addingProject.value || !selectedProjectId.value) return;
  addingProject.value=true; projErr.value=null;
  try { await addProject(id,selectedProjectId.value); showAddProject.value=false; selectedProjectId.value=''; await load(); }
  catch(err:unknown) { projErr.value = err instanceof ApiError ? err.message : '添加失败'; }
  finally { addingProject.value = false; }
}

async function handleRemoveProject(pid:string) {
  if (removingProjectId.value !== null) return;
  removingProjectId.value = pid; actionErr.value = null;
  try {
    await ElMessageBox.confirm('确认移除？','确认',{type:'warning'});
    await removeProject(id,pid); await load();
  } catch(err:unknown) {
    if (err !== 'cancel' && err !== 'close') actionErr.value = err instanceof ApiError ? err.message : '移除失败';
  } finally { removingProjectId.value = null; }
}

const showManageTeachers=ref(false); const manageProjectId=ref(''); const projectTeachers=ref<import('@/types/school-admin-activity').ResponsibleTeacherItem[]>([]);
const teacherKeyword=ref(''); const selectedTeacherId=ref(''); const teacherListLoading=ref(false); const teacherLoading=ref(false); const teacherErr=ref<string|null>(null);
const allTeachers=ref<import('@/types/school-admin-activity').SchoolTeacherItem[]>([]); const assigningTeacher=ref(false);
const availableTeachers = computed(() => { const assignedIds = new Set(projectTeachers.value.map(t => t.userId)); return allTeachers.value.filter(t => !assignedIds.has(t.userId)); });
const unassigningTeacherId=ref<string|null>(null);
const publishBlocked = computed(() => detail.value && detail.value.executionStatus==='DRAFT' && detail.value.projects.some(p => !p._teachers || !p._teachers.some((t:any)=>t.membershipStatus==='ACTIVE' && t.accountStatus==='NORMAL')));

async function loadTeacherData() { teacherLoading.value=true; teacherErr.value=null; try { const [teachers, all] = await Promise.all([fetchResponsibleTeachers(id, manageProjectId.value), fetchSchoolTeachers()]); projectTeachers.value=teachers; allTeachers.value=all.items; selectedTeacherId.value=''; } catch(err:unknown) { teacherErr.value = err instanceof ApiError ? err.message : '加载失败'; } finally { teacherLoading.value=false; } }
async function searchTeachers() { teacherListLoading.value=true;try{const r=await fetchSchoolTeachers(teacherKeyword.value);allTeachers.value=r.items;}catch(err:unknown){teacherErr.value=err instanceof ApiError?err.message:'搜索失败';teacherListLoading.value=false;}finally{if(teacherListLoading.value)teacherListLoading.value=false;} }
async function openManageTeachers(projectId: string) { manageProjectId.value=projectId; teacherKeyword.value=''; showManageTeachers.value=true; await loadTeacherData(); }
function closeManageTeachers() { projectTeachers.value=[]; allTeachers.value=[]; selectedTeacherId.value=''; teacherErr.value=null; }
async function handleAssignTeacher() { if(assigningTeacher.value||!selectedTeacherId.value)return; assigningTeacher.value=true;teacherErr.value=null;try{await assignResponsibleTeacher(id,manageProjectId.value,selectedTeacherId.value);selectedTeacherId.value='';await load();await loadTeacherData();}catch(err:unknown){teacherErr.value=err instanceof ApiError?err.message:'分配失败';}finally{assigningTeacher.value=false;} }
async function handleUnassignTeacher(teacherId:string) { if(unassigningTeacherId.value!==null)return;unassigningTeacherId.value=teacherId;try{await ElMessageBox.confirm('确认取消分配？','确认',{type:'warning'});await unassignResponsibleTeacher(id,manageProjectId.value,teacherId);await load();await loadTeacherData();}catch(err:unknown){if(err!=='cancel'&&err!=='close')teacherErr.value=err instanceof ApiError?err.message:'取消分配失败';}finally{unassigningTeacherId.value=null;} }

function fmt(iso:string|null) { return iso ? new Date(iso).toLocaleDateString('zh-CN') : '-'; }
onMounted(()=>{if(isValidId.value){load();loadProjects();}});
</script>

<style scoped>h1{font-size:24px;margin:16px 0 8px}.tags{margin-bottom:16px}.actions{margin:16px 0;display:flex;gap:12px}.sec{margin-top:24px}.sec h3{font-size:16px;font-weight:600;margin-bottom:12px}.empty{color:#c0c4cc}</style>
