<template>
  <el-card>
    <h2>权限管理</h2>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="权限列表维护" name="list">
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
      </el-tab-pane>
      <el-tab-pane label="公司权限树" name="tree">
        <el-alert type="info" :closable="false" show-icon title="展示当前公司真实权限层级，非纯数据库平铺结果。" style="margin-bottom: 12px;" />
        <el-tree
          v-loading="treeLoading"
          :data="permissionTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          empty-text="暂无权限树"
        >
          <template #default="{ data }">
            <span>{{ data.name }}（{{ data.code || '-' }} / {{ data.type || '-' }}）</span>
          </template>
        </el-tree>
      </el-tab-pane>
      <el-tab-pane label="角色权限矩阵" name="matrix">
        <el-alert type="info" :closable="false" show-icon title="展示当前公司角色绑定的真实权限明细。" style="margin-bottom: 12px;" />
        <el-table :data="matrixRows" v-loading="matrixLoading" style="width: 100%" empty-text="暂无角色权限数据">
          <el-table-column prop="roleCode" label="角色编码" width="180" />
          <el-table-column prop="roleName" label="角色名称" width="180" />
          <el-table-column label="权限数量" width="100">
            <template #default="scope">{{ scope.row.permissionNames.length }}</template>
          </el-table-column>
          <el-table-column label="权限明细" min-width="520">
            <template #default="scope">
              <div style="display:flex;flex-wrap:wrap;gap:6px;">
                <el-tag v-for="name in scope.row.permissionNames" :key="scope.row.roleId + '-' + name" size="small">{{ name }}</el-tag>
                <span v-if="scope.row.permissionNames.length === 0" style="color:#909399;">暂无绑定</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
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
const activeTab = ref('list');
const permissions = ref([]);
const loading = ref(false);
const treeLoading = ref(false);
const matrixLoading = ref(false);
const permissionTree = ref([]);
const matrixRows = ref([]);
const permissionNameMap = ref(new Map());
const showAdd = ref(false);
const showEdit = ref(false);
const saving = ref(false);
const addForm = ref({ name: '', code: '', type: '', parentId: '' });
const editForm = ref({});
const query = ref({ name: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
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
    const res = await request.get('/permission/page', {
      params: {
        ...query.value,
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
      }
    });
    permissions.value = res?.list || [];
    pagination.value.total = Number(res?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function fetchPermissionTree() {
  treeLoading.value = true;
  try {
    const data = await request.get('/permissions/tree');
    permissionTree.value = Array.isArray(data) ? data : [];
  } catch (err) {
    permissionTree.value = [];
    ElMessage.error(err?.message || '加载权限树失败');
  } finally {
    treeLoading.value = false;
  }
}

async function fetchMatrix() {
  matrixLoading.value = true;
  try {
    const data = await request.get('/permission/matrix');
    const permissionsRaw = Array.isArray(data?.permissions) ? data.permissions : [];
    const rolesRaw = Array.isArray(data?.roles) ? data.roles : [];

    const map = new Map();
    permissionsRaw.forEach(item => {
      if (item?.id != null) {
        map.set(String(item.id), `${item.name || '-'}(${item.code || '-'})`);
      }
    });
    permissionNameMap.value = map;

    matrixRows.value = rolesRaw.map(role => {
      const ids = Array.isArray(role?.permissionIds) ? role.permissionIds : [];
      return {
        roleId: role?.roleId,
        roleCode: role?.roleCode || '-',
        roleName: role?.roleName || '-',
        permissionNames: ids.map(id => map.get(String(id))).filter(Boolean)
      };
    });
  } catch (err) {
    matrixRows.value = [];
    ElMessage.error(err?.message || '加载角色权限矩阵失败');
  } finally {
    matrixLoading.value = false;
  }
}

function handleTabChange(name) {
  if (name === 'tree' && permissionTree.value.length === 0) {
    fetchPermissionTree();
  }
  if (name === 'matrix' && matrixRows.value.length === 0) {
    fetchMatrix();
  }
}

function onPageChange(page) {
  pagination.value.current = page;
  fetchPermissions();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchPermissions();
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
      pagination.value.current = 1;
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
    const { value: confirmPassword } = await ElMessageBox.prompt('请输入当前治理管理员密码以确认删除权限', '敏感操作二次校验', {
      inputType: 'password',
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await request.post('/permission/delete', { id, confirmPassword });
    ElMessage.success('删除成功');
    pagination.value.current = 1;
    fetchPermissions();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchPermissions();
fetchPermissionTree();
fetchMatrix();
</script>
