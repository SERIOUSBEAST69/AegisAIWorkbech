<template>
  <el-card>
    <h2>权限管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="权限名称">
        <el-input v-model="query.name" placeholder="输入权限名称" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchPermissions">查询</el-button>
        <el-button @click="openAdd">新增权限</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="permissions" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="权限名称" />
      <el-table-column prop="code" label="权限编码" />
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="parentId" label="父ID" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editPermission(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deletePermission(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="新增权限">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-form-item label="权限名称" prop="name"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="权限编码" prop="code"><el-input v-model="addForm.code" /></el-form-item>
        <el-form-item label="类型" prop="type"><el-input v-model="addForm.type" /></el-form-item>
        <el-form-item label="父ID"><el-input v-model="addForm.parentId" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addPermission">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showEdit" title="编辑权限">
      <el-form :model="editForm" :rules="rules" ref="editFormRef">
        <el-form-item label="权限名称" prop="name"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="权限编码" prop="code"><el-input v-model="editForm.code" /></el-form-item>
        <el-form-item label="类型" prop="type"><el-input v-model="editForm.type" /></el-form-item>
        <el-form-item label="父ID"><el-input v-model="editForm.parentId" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updatePermission">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
const permissions = ref([]);
const loading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const saving = ref(false);
const addForm = ref({ name: '', code: '', type: '', parentId: '' });
const editForm = ref({});
const query = ref({ name: '' });
const addFormRef = ref();
const editFormRef = ref();
const rules = {
  name: [{ required: true, message: '权限名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '权限编码不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '类型不能为空', trigger: 'blur' }]
};
async function fetchPermissions() {
  loading.value = true;
  try {
    const res = await request.get('/permission/list', { params: query.value });
    permissions.value = res || [];
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}
function openAdd() {
  addForm.value = { name: '', code: '', type: '', parentId: '' };
  showAdd.value = true;
}
async function addPermission() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/permission/add', addForm.value);
      ElMessage.success('保存成功');
      showAdd.value = false;
      fetchPermissions();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}
function editPermission(row) {
  editForm.value = { ...row };
  showEdit.value = true;
}
async function updatePermission() {
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/permission/update', editForm.value);
      ElMessage.success('更新成功');
      showEdit.value = false;
      fetchPermissions();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}
async function deletePermission(id) {
  try {
    await ElMessageBox.confirm('确认删除该权限吗？', '提示', { type: 'warning' });
    await request.post('/permission/delete', { id });
    ElMessage.success('删除成功');
    fetchPermissions();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchPermissions();
</script>
