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
    <el-table :data="roles" style="width: 100%" v-loading="loading" empty-text="暂无记录">
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
    <div style="display: flex; justify-content: flex-end; margin-top: 16px">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="pagination.total"
        :current-page="pagination.current"
        :page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onPageSizeChange"
      />
    </div>
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
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const addFormRef = ref();
const editFormRef = ref();
const rules = {
  name: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '角色编码不能为空', trigger: 'blur' }]
};
async function fetchRoles() {
  loading.value = true;
  try {
    const res = await request.get('/role/page', {
      params: {
        ...query.value,
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
      }
    });
    roles.value = res?.list || [];
    pagination.value.total = Number(res?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

function onPageChange(page) {
  pagination.value.current = page;
  fetchRoles();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchRoles();
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
      pagination.value.current = 1;
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
    const { value: confirmPassword } = await ElMessageBox.prompt('请输入当前治理管理员密码以确认删除角色', '敏感操作二次校验', {
      inputType: 'password',
      inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await request.post('/role/delete', { id, confirmPassword });
    ElMessage.success('删除成功');
    pagination.value.current = 1;
    fetchRoles();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchRoles();
</script>
