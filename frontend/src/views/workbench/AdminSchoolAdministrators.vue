<template>
  <div><h2>学校管理员</h2>
    <div v-if="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="error"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result></div>
    <el-table v-else :data="items" style="width:100%"><el-table-column prop="username" label="用户名" /><el-table-column label="角色"><template #default><el-tag size="small">学校管理员</el-tag></template></el-table-column><el-table-column label="状态"><template #default="{row}">{{ statusLabel(row.accountStatus) }}</template></el-table-column></el-table>
    <el-button type="primary" @click="openCreate" style="margin-top:16px">创建学校管理员</el-button>

    <!-- Create form dialog -->
    <el-dialog v-model="showCreate" title="创建学校管理员" width="420px">
      <el-form ref="cf" :model="c" :rules="cr" label-position="top"><el-form-item label="用户名" prop="username"><el-input v-model="c.username" placeholder="登录用户名" maxlength="100" /></el-form-item></el-form>
      <div v-if="createErr" class="e"><el-alert :title="createErr" type="error" show-icon /></div>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" :loading="creating" :disabled="creating" @click="handleCreate">创建</el-button></template>
    </el-dialog>

    <!-- Credential result dialog — shown once after creation, then cleared -->
    <el-dialog v-model="showCredential" title="创建成功" width="460px" :close-on-click-modal="false" @closed="clearCredential">
      <el-alert type="success" title="学校管理员账号已创建" :closable="false" style="margin-bottom:16px" />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ credential.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">学校管理员</el-descriptions-item>
        <el-descriptions-item label="临时密码">
          <el-input :model-value="credential.temporaryPassword" readonly type="text" style="font-family:monospace">
            <template #append><el-button @click="copyPassword">复制</el-button></template>
          </el-input>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert type="warning" title="请立即将临时密码交给管理员。该密码将在 72 小时内有效，关闭此对话框后将无法再次查看。" :closable="false" style="margin-top:16px" />
      <template #footer><el-button type="primary" @click="showCredential=false">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'; import { useRoute } from 'vue-router'; import http from '@/api/http'; import { ApiError } from '@/api/http'; import { ElMessage } from 'element-plus'; import type { FormRules } from 'element-plus';

const route=useRoute(); const schoolId=route.params.schoolId as string;
interface AdminItem { userId: string; username: string; role: string; schoolName: string; accountStatus: string; createdAt: string }
const items=ref<AdminItem[]>([]); const loading=ref(true); const error=ref<string|null>(null);
const showCreate=ref(false); const creating=ref(false); const createErr=ref<string|null>(null);
const c=reactive({username:''});
const cr:FormRules={username:[{required:true,message:'请输入用户名',trigger:'blur'}]};

// Credential display — cleared on dialog close
const showCredential=ref(false);
const credential=reactive({username:'',temporaryPassword:''});

function statusLabel(s:string){const m:Record<string,string>={PENDING_ACTIVATION:'待激活',NORMAL:'正常',LOCKED:'已锁定',DISABLED:'已停用'};return m[s]||s}

function openCreate(){c.username='';createErr.value=null;showCreate.value=true}

async function load(){loading.value=true;error.value=null;try{const r=await http.get(`/v1/admin/schools/${schoolId}/administrators`);items.value=r.data}catch(e){error.value=e instanceof ApiError?e.message:'加载失败'}finally{loading.value=false}}

async function handleCreate(){
  creating.value=true;createErr.value=null;
  try{
    const r=await http.post(`/v1/admin/schools/${schoolId}/administrators`,{username:c.username});
    showCreate.value=false;
    // Show credential result once
    credential.username=r.data.username;
    credential.temporaryPassword=r.data.temporaryPassword;
    showCredential.value=true;
    load();
  }catch(e: unknown){createErr.value=e instanceof ApiError?e.message:'创建失败'}
  finally{creating.value=false}
}

function clearCredential(){
  credential.username='';
  credential.temporaryPassword='';
}

async function copyPassword(){
  try{await navigator.clipboard.writeText(credential.temporaryPassword);ElMessage.success('已复制')}catch{ElMessage.warning('复制失败，请手动复制')}
}

onMounted(load);
</script>
<style scoped>.e{margin-bottom:12px}</style>
