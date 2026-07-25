<template>
  <div><h2>本校账号管理</h2>
    <el-card class="fc"><el-form :inline="true">
      <el-form-item label="角色"><el-select v-model="f.role" placeholder="全部" clearable @change="search"><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="f.status" placeholder="全部" clearable @change="search"><el-option v-for="s in statusOpts" :key="s.v" :label="s.l" :value="s.v" /></el-select></el-form-item>
      <el-form-item label="关键词"><el-input v-model="f.keyword" placeholder="用户名" clearable @change="search" /></el-form-item>
      <el-form-item><el-button type="primary" @click="showCreate=true">创建账号</el-button></el-form-item>
    </el-form></el-card>
    <div v-if="loading"><el-skeleton :rows="5" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <div v-else-if="items.length===0"><el-empty description="暂无账号" /></div>
    <el-table v-else :data="items" style="width:100%"><el-table-column prop="username" label="用户名" /><el-table-column label="角色"><template #default="{row}">{{ roleLabel(row.role) }}</template></el-table-column><el-table-column label="状态"><template #default="{row}">{{ statusLabel(row.accountStatus) }}</template></el-table-column><el-table-column label="创建时间"><template #default="{row}">{{ fmt(row.createdAt) }}</template></el-table-column></el-table>

    <el-dialog v-model="showCreate" title="创建账号" width="420px">
      <el-form ref="cf" :model="c" :rules="cr" label-position="top"><el-form-item label="角色" prop="role"><el-select v-model="c.role" style="width:100%"><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" /></el-select></el-form-item>
      <el-form-item label="用户名" prop="username"><el-input v-model="c.username" placeholder="登录用户名" maxlength="100" /></el-form-item>
      <el-form-item label="临时密码" prop="temporaryPassword"><el-input v-model="c.temporaryPassword" type="password" show-password placeholder="8位以上" /></el-form-item></el-form>
      <div v-if="createErr" class="e"><el-alert :title="createErr" type="error" show-icon /></div>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" :loading="creating" :disabled="creating" @click="handleCreate">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'; import http from '@/api/http'; import { ApiError } from '@/api/http'; import { ElMessageBox } from 'element-plus'; import type { FormRules } from 'element-plus';

interface AccountItem { userId: string; username: string; role: string; schoolName: string; accountStatus: string; createdAt: string }
const items=ref<AccountItem[]>([]); const loading=ref(true); const error=ref<string|null>(null);
const showCreate=ref(false); const creating=ref(false); const createErr=ref<string|null>(null);
const f=reactive({role:'',status:'',keyword:''});
const c=reactive({role:'TEACHER',username:'',temporaryPassword:''});
const cr:FormRules={role:[{required:true}],username:[{required:true}],temporaryPassword:[{required:true,min:8}]};
const statusOpts=[{v:'PENDING_ACTIVATION',l:'待激活'},{v:'NORMAL',l:'正常'},{v:'LOCKED',l:'已锁定'},{v:'DISABLED',l:'已停用'}];

function roleLabel(r:string){return r==='TEACHER'?'教师':'学生'}
function statusLabel(s:string){const m:Record<string,string>={PENDING_ACTIVATION:'待激活',NORMAL:'正常',LOCKED:'已锁定',DISABLED:'已停用'};return m[s]||s}
function fmt(iso:string){return iso?new Date(iso).toLocaleDateString('zh-CN'):''}

async function load(){loading.value=true;error.value=null;try{const resp=await http.get('/v1/school-admin/accounts',{params:{role:f.role||undefined,status:f.status||undefined,keyword:f.keyword||undefined}});items.value=resp.data}catch(e){error.value=e instanceof ApiError?e.message:'加载失败'}finally{loading.value=false}}
async function handleCreate(){creating.value=true;createErr.value=null;try{await http.post('/v1/school-admin/accounts',{role:c.role,username:c.username,temporaryPassword:c.temporaryPassword});showCreate.value=false;c.username='';c.temporaryPassword='';ElMessageBox.alert('账号创建成功！请将临时密码交给用户。','创建成功');load()}catch(e: unknown){createErr.value=e instanceof ApiError?e.message:'创建失败'}finally{creating.value=false}}
function search(){load()}
onMounted(load);
</script>
<style scoped>.fc{margin-bottom:16px}.e{margin-bottom:12px}</style>
