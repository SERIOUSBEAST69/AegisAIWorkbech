<template>
  <el-card>
    <h2>审批流管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="申请人ID">
        <el-input v-model="query.applicantId" placeholder="申请人ID" />
      </el-form-item>
      <el-form-item label="资产ID">
        <el-input v-model="query.assetId" placeholder="资产ID" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchApprovals">查询</el-button>
        <el-button :loading="loading" @click="fetchTodo">我的待办</el-button>
        <el-button @click="openAdd">新建申请</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="approvals" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="applicantId" label="申请人ID" />
      <el-table-column prop="assetId" label="资产ID" />
      <el-table-column prop="reason" label="理由" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="approverId" label="审批人ID" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="approve(scope.row, '通过')">通过</el-button>
          <el-button size="small" type="danger" @click="approve(scope.row, '拒绝')">拒绝</el-button>
          <el-button size="small" type="warning" @click="remove(scope.row.id)" style="margin-left:6px">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="新建审批申请">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-alert title="申请人将自动绑定为当前登录账号" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form-item label="资产ID" prop="assetId"><el-input v-model="addForm.assetId" /></el-form-item>
        <el-form-item label="理由" prop="reason"><el-input v-model="addForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addApproval">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
const approvals = ref([]);
const loading = ref(false);
const showAdd = ref(false);
const saving = ref(false);
const addForm = ref({ assetId: '', reason: '' });
const query = ref({ applicantId: '', assetId: '' });
const addFormRef = ref();
const rules = {
  assetId: [{ required: true, message: '资产ID不能为空', trigger: 'blur' }],
  reason: [{ required: true, message: '理由不能为空', trigger: 'blur' }]
};
async function fetchApprovals() {
  loading.value = true;
  try {
    const res = await request.get('/approval/list', { params: query.value });
    approvals.value = res || [];
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function fetchTodo() {
  loading.value = true;
  try {
    approvals.value = await request.get('/approval/todo');
  } catch (err) {
    ElMessage.error(err?.message || '加载待办失败');
  } finally {
    loading.value = false;
  }
}
function openAdd() {
  addForm.value = { assetId: '', reason: '' };
  showAdd.value = true;
}
async function addApproval() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/approval/apply', addForm.value);
      ElMessage.success('提交成功');
      showAdd.value = false;
      fetchApprovals();
    } catch (err) {
      ElMessage.error(err?.message || '提交失败');
    } finally {
      saving.value = false;
    }
  });
}
async function approve(row, status) {
  try {
    const endpoint = status === '拒绝' ? '/approval/reject' : '/approval/approve';
    await request.post(endpoint, { requestId: row.id, status });
    ElMessage.success('处理成功');
    fetchApprovals();
  } catch (err) {
    ElMessage.error(err?.message || '处理失败');
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该申请吗？', '提示', { type: 'warning' });
    await request.post('/approval/delete', { id });
    ElMessage.success('删除成功');
    fetchApprovals();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchApprovals();
</script>
