<template>
  <el-card>
    <h2>角色管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="角色名称">
        <el-input v-model="query.name" placeholder="输入角色名称" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchRoles">查询</el-button>
        <el-button @click="openAdd">新增角色</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="roles" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="角色名称" />
      <el-table-column prop="code" label="角色编码" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editRole(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteRole(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="新增角色">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-form-item label="角色名称" prop="name"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="角色编码" prop="code"><el-input v-model="addForm.code" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="addForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addRole">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showEdit" title="编辑角色">
      <el-form :model="editForm" :rules="rules" ref="editFormRef">
        <el-form-item label="角色名称" prop="name"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="角色编码" prop="code"><el-input v-model="editForm.code" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updateRole">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
const roles = ref([]);
const loading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const saving = ref(false);
const addForm = ref({ name: '', code: '', description: '' });
const editForm = ref({});
const query = ref({ name: '' });
const addFormRef = ref();
const editFormRef = ref();
const rules = {
  name: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '角色编码不能为空', trigger: 'blur' }]
};
async function fetchRoles() {
  loading.value = true;
  try {
    const res = await request.get('/role/list', { params: query.value });
    roles.value = res || [];
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}
function openAdd() {
  addForm.value = { name: '', code: '', description: '' };
  showAdd.value = true;
}
async function addRole() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/role/add', addForm.value);
      ElMessage.success('保存成功');
      showAdd.value = false;
      fetchRoles();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}
function editRole(row) {
  editForm.value = { ...row };
  showEdit.value = true;
}
async function updateRole() {
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/role/update', editForm.value);
      ElMessage.success('更新成功');
      showEdit.value = false;
      fetchRoles();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}
async function deleteRole(id) {
  try {
    await ElMessageBox.confirm('确认删除该角色吗？', '提示', { type: 'warning' });
    await request.post('/role/delete', { id });
    ElMessage.success('删除成功');
    fetchRoles();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchRoles();
</script>
