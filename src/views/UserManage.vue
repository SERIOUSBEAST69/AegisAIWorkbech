<template>
  <el-card>
    <h2>用户管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="用户名">
        <el-input v-model="query.username" placeholder="输入用户名" />
      </el-form-item>
      <el-form-item label="账号状态">
        <el-select v-model="query.accountStatus" clearable placeholder="全部" style="width: 150px">
          <el-option label="待审批" value="pending" />
          <el-option label="已激活" value="active" />
          <el-option label="已拒绝" value="rejected" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchUsers">查询</el-button>
        <el-button @click="openAdd">新增用户</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="users" style="width: 100%" v-loading="loading" empty-text="暂无记录">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="realName" label="真实姓名" />
      <el-table-column prop="companyId" label="公司ID" width="90" />
      <el-table-column prop="accountType" label="账号类型" width="100" />
      <el-table-column prop="roleId" label="角色ID" />
      <el-table-column prop="department" label="部门" />
      <el-table-column label="账号状态" width="120">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.accountStatus)">{{ scope.row.accountStatus || 'active' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button
            v-if="scope.row.accountStatus === 'pending'"
            size="small"
            type="success"
            @click="approveUser(scope.row.id)"
          >审批通过</el-button>
          <el-button
            v-if="scope.row.accountStatus === 'pending'"
            size="small"
            type="warning"
            @click="rejectUser(scope.row.id)"
          >拒绝</el-button>
          <el-button size="small" @click="editUser(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteUser(scope.row.id)">删除</el-button>
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
    <el-dialog v-model="showAdd" title="新增用户">
      <el-form :model="addForm" :rules="addRules" ref="addFormRef">
        <el-form-item label="用户名" prop="username"><el-input v-model="addForm.username" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="addForm.password" type="password" /></el-form-item>
        <el-form-item label="真实姓名" prop="realName"><el-input v-model="addForm.realName" /></el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="addForm.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="`${role.name} (${role.code})`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="addForm.department" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addUser">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showEdit" title="编辑用户">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef">
        <el-form-item label="真实姓名" prop="realName"><el-input v-model="editForm.realName" /></el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="editForm.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="`${role.name} (${role.code})`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="editForm.department" /></el-form-item>
        <el-form-item label="账号状态" prop="accountStatus">
          <el-select v-model="editForm.accountStatus" placeholder="请选择账号状态" style="width: 100%">
            <el-option label="待审批" value="pending" />
            <el-option label="已激活" value="active" />
            <el-option label="已拒绝" value="rejected" />
            <el-option label="已禁用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updateUser">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
const users = ref([]);
const loading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const saving = ref(false);
const addForm = ref({ username: '', password: '', realName: '', roleId: '', department: '' });
const editForm = ref({});
const roleOptions = ref([]);
const query = ref({ username: '', accountStatus: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const addFormRef = ref();
const editFormRef = ref();
const baseRules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  realName: [{ required: true, message: '真实姓名不能为空', trigger: 'blur' }],
  roleId: [{ required: true, message: '角色ID不能为空', trigger: 'blur' }]
};
const addRules = { ...baseRules, password: [{ required: true, message: '密码不能为空', trigger: 'blur' }] };
const editRules = { ...baseRules, accountStatus: [{ required: true, message: '账号状态不能为空', trigger: 'change' }] };
async function fetchUsers() {
  loading.value = true;
  try {
    const res = await request.get('/user/page', {
      params: {
        ...query.value,
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
      }
    });
    users.value = res?.list || [];
    pagination.value.total = Number(res?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function fetchRoles() {
  try {
    const res = await request.get('/role/list');
    roleOptions.value = Array.isArray(res) ? res : [];
  } catch (err) {
    roleOptions.value = [];
    ElMessage.error(err?.message || '角色加载失败');
  }
}

function onPageChange(page) {
  pagination.value.current = page;
  fetchUsers();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchUsers();
}

function statusTagType(status) {
  if (status === 'pending') return 'warning';
  if (status === 'active') return 'success';
  if (status === 'rejected') return 'danger';
  return 'info';
}

async function approveUser(id) {
  try {
    await request.post('/user/approve', { id });
    ElMessage.success('审批通过');
    pagination.value.current = 1;
    fetchUsers();
  } catch (err) {
    ElMessage.error(err?.message || '审批失败');
  }
}

async function rejectUser(id) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因（可选）', '拒绝注册', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'text',
    });
    await request.post('/user/reject', { id, reason: value || '' });
    ElMessage.success('已拒绝该账号');
    pagination.value.current = 1;
    fetchUsers();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '拒绝失败');
  }
}
function openAdd() {
  addForm.value = { username: '', password: '', realName: '', roleId: null, department: '' };
  showAdd.value = true;
}
async function addUser() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/user/register', addForm.value);
      ElMessage.success('保存成功');
      showAdd.value = false;
      pagination.value.current = 1;
      fetchUsers();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}
function editUser(row) {
  editForm.value = { ...row, roleId: row?.roleId ?? null };
  showEdit.value = true;
}
async function updateUser() {
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/user/update', editForm.value);
      ElMessage.success('更新成功');
      showEdit.value = false;
      fetchUsers();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}
async function deleteUser(id) {
  try {
    await ElMessageBox.confirm('确认删除该用户吗？', '提示', { type: 'warning' });
    const { value: confirmPassword } = await ElMessageBox.prompt('请输入当前治理管理员密码以确认删除', '敏感操作二次校验', {
      inputType: 'password',
      inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await request.post('/user/delete', { id, confirmPassword, deleteReason: 'governance_console_delete' });
    ElMessage.success('删除成功');
    pagination.value.current = 1;
    fetchUsers();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
onMounted(() => {
  fetchRoles();
  fetchUsers();
});
</script>
