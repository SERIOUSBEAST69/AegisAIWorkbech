<template>
  <el-card>
    <h2>合规策略管理</h2>
    <el-alert
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="生效说明"
      :description="policyHint"/>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="策略名称">
        <el-input v-model="query.name" placeholder="输入策略名称" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchPolicies">查询</el-button>
        <el-button v-if="canManageStructure" @click="openAdd">新增策略</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="policies" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="策略名称" />
      <el-table-column prop="ruleContent" label="规则内容" />
      <el-table-column prop="scope" label="生效范围" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="isEnabled(scope.row.status) ? 'success' : 'info'">
            {{ formatStatus(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button
            v-if="canManageStructure"
            size="small"
            @click="editPolicy(scope.row)">
            编辑
          </el-button>
          <el-button
            v-if="canManageStructure"
            size="small"
            type="danger"
            @click="deletePolicy(scope.row.id)">
            删除
          </el-button>
          <el-button
            v-if="!canManageStructure && canToggleStatus"
            size="small"
            :type="isEnabled(scope.row.status) ? 'warning' : 'success'"
            @click="togglePolicyStatus(scope.row)">
            {{ isEnabled(scope.row.status) ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="新增策略">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-form-item label="策略名称" prop="name"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="规则内容" prop="ruleContent"><el-input v-model="addForm.ruleContent" /></el-form-item>
        <el-form-item label="生效范围" prop="scope">
          <el-select v-model="addForm.scope" style="width: 220px">
            <el-option label="ai_prompt（提示词拦截）" value="ai_prompt" />
            <el-option label="全局" value="全局" />
            <el-option label="业务部门" value="业务部门" />
            <el-option label="技术部门" value="技术部门" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addPolicy">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showEdit" title="编辑策略">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef">
        <el-form-item label="策略名称" prop="name"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="规则内容" prop="ruleContent"><el-input v-model="editForm.ruleContent" /></el-form-item>
        <el-form-item label="生效范围" prop="scope">
          <el-select v-model="editForm.scope" style="width: 220px">
            <el-option label="ai_prompt（提示词拦截）" value="ai_prompt" />
            <el-option label="全局" value="全局" />
            <el-option label="业务部门" value="业务部门" />
            <el-option label="技术部门" value="技术部门" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updatePolicy">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '../store/user';
import { canManagePolicyStructure, canTogglePolicyStatus } from '../utils/roleBoundary';
import request from '../api/request';

const userStore = useUserStore();
const canManageStructure = computed(() => canManagePolicyStructure(userStore.userInfo));
const canToggleStatus = computed(() => canTogglePolicyStatus(userStore.userInfo));
const policies = ref([]);
const loading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const saving = ref(false);
const policyHint = '当 scope=ai_prompt 且策略启用时，系统会把 ruleContent 中定义的敏感词应用到 AI 提示词拦截链路。推荐 ruleContent 使用 JSON：{"keywords":["身份证号","银行卡号"]}';
const addForm = ref({ name: '', ruleContent: '{"keywords":[]}', scope: 'ai_prompt' });
const editForm = ref({});
const query = ref({ name: '' });
const addFormRef = ref();
const editFormRef = ref();
const rules = {
  name: [{ required: true, message: '策略名称不能为空', trigger: 'blur' }],
  ruleContent: [{ required: true, message: '规则内容不能为空', trigger: 'blur' }],
  scope: [{ required: true, message: '生效范围不能为空', trigger: 'blur' }]
};
const editRules = { ...rules };
async function fetchPolicies() {
  loading.value = true;
  try {
    const res = await request.get('/policy/list', { params: query.value });
    policies.value = res || [];
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}
function openAdd() {
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能新增策略');
    return;
  }
  addForm.value = { name: '', ruleContent: '{"keywords":[]}', scope: 'ai_prompt' };
  showAdd.value = true;
}
async function addPolicy() {
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能新增策略');
    return;
  }
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    let confirmPassword = '';
    try {
      const prompt = await ElMessageBox.prompt('请输入当前账号密码确认保存策略', '敏感操作二次校验', {
        inputType: 'password',
        inputPlaceholder: '请输入密码',
        inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      });
      confirmPassword = prompt.value;
    } catch {
      return;
    }
    saving.value = true;
    try {
      await request.post('/policy/save', { ...addForm.value, confirmPassword });
      ElMessage.success('保存成功');
      showAdd.value = false;
      fetchPolicies();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}
function editPolicy(row) {
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能编辑策略结构');
    return;
  }
  editForm.value = { ...row };
  showEdit.value = true;
}
async function updatePolicy() {
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能编辑策略结构');
    return;
  }
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    let confirmPassword = '';
    try {
      const prompt = await ElMessageBox.prompt('请输入当前账号密码确认更新策略', '敏感操作二次校验', {
        inputType: 'password',
        inputPlaceholder: '请输入密码',
        inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      });
      confirmPassword = prompt.value;
    } catch {
      return;
    }
    saving.value = true;
    try {
      await request.post('/policy/save', { ...editForm.value, confirmPassword });
      ElMessage.success('更新成功');
      showEdit.value = false;
      fetchPolicies();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}
async function deletePolicy(id) {
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能删除策略');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该策略吗？', '提示', { type: 'warning' });
    const prompt = await ElMessageBox.prompt('请输入当前账号密码确认删除策略', '敏感操作二次校验', {
      inputType: 'password',
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    });
    await request.post('/policy/delete', { id, confirmPassword: prompt.value });
    ElMessage.success('删除成功');
    fetchPolicies();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}

function isEnabled(status) {
  const value = String(status ?? '').trim().toLowerCase();
  return ['enabled', 'active', 'true', '1', '启用'].includes(value);
}

function formatStatus(status) {
  return isEnabled(status) ? '启用' : '停用';
}

async function togglePolicyStatus(row) {
  if (!canToggleStatus.value) {
    ElMessage.error('当前身份无策略启停权限');
    return;
  }
  const targetStatus = isEnabled(row?.status) ? 'DISABLED' : 'ENABLED';
  const actionLabel = targetStatus === 'ENABLED' ? '启用' : '停用';
  try {
    const prompt = await ElMessageBox.prompt(`请输入当前账号密码确认${actionLabel}策略`, '敏感操作二次校验', {
      inputType: 'password',
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    });
    await request.post('/policy/toggle-status', {
      id: row.id,
      status: targetStatus,
      confirmPassword: prompt.value
    });
    ElMessage.success(`${actionLabel}成功`);
    fetchPolicies();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || `${actionLabel}失败`);
  }
}

fetchPolicies();
</script>
