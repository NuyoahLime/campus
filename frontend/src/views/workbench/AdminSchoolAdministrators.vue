<template>
  <div><h2>学校管理员</h2>
    <div v-if="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <el-table v-else :data="items" style="width:100%"><el-table-column prop="username" label="用户名" /><el-table-column label="角色"><template #default><el-tag size="small">学校管理员</el-tag></template></el-table-column><el-table-column label="状态"><template #default="{row}">{{ statusLabel(row.accountStatus) }}</template></el-table-column></el-table>
    <el-button type="primary" @click="showCreate=true" style="margin-top:16px">创建学校管理员</el-button>

    <el-dialog v-model="showCreate" title="创建学校管理员" width="420px">
      <el-form ref="cf" :model="c" :rules="cr" label-position="top"><el-form-item label="用户名" prop="username"><el-input v-model="c.username" placeholder="登录用户名" maxlength="100" /></el-form-item>
      <el-form-item label="临时密码" prop="temporaryPassword"><el-input v-model="c.temporaryPassword" type="password" show-password placeholder="8位以上" /></el-form-item></el-form>
      <div v-if="createErr" class="e"><el-alert :title="createErr" type="error" show-icon /></div>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" :loading="creating" :disabled="creating" @click="handleCreate">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'; import { useRoute } from 'vue-router'; import http from '@/api/http'; import { ApiError } from '@/api/http'; import { ElMessageBox } from 'element-plus'; import type { FormRules } from 'element-plus';

const route=useRoute(); const schoolId=route.params.schoolId as string;
interface AdminItem { userId: string; username: string; role: string; schoolName: string; accountStatus: string; createdAt: string }
const items=ref<AdminItem[]>([]); const loading=ref(true); const error=ref<string|null>(null);
const showCreate=ref(false); const creating=ref(false); const createErr=ref<string|null>(null);
const c=reactive({username:'',temporaryPassword:''});
const cr:FormRules={username:[{required:true}],temporaryPassword:[{required:true,min:8}]};

function statusLabel(s:string){const m:Record<string,string>={PENDING_ACTIVATION:'待激活',NORMAL:'正常',LOCKED:'已锁定',DISABLED:'已停用'};return m[s]||s}

async function load(){loading.value=true;error.value=null;try{const r=await http.get(`/v1/admin/schools/${schoolId}/administrators`);items.value=r.data}catch(e){error.value=e instanceof ApiError?e.message:'加载失败'}finally{loading.value=false}}
async function handleCreate(){creating.value=true;createErr.value=null;try{await http.post(`/v1/admin/schools/${schoolId}/administrators`,{username:c.username,temporaryPassword:c.temporaryPassword});showCreate.value=false;c.username='';c.temporaryPassword='';ElMessageBox.alert('创建成功！请将临时密码交给管理员。','创建成功');load()}catch(e: unknown){createErr.value=e instanceof ApiError?e.message:'创建失败'}finally{creating.value=false}}
onMounted(load);
</script>
<style scoped>.e{margin-bottom:12px}</style>
