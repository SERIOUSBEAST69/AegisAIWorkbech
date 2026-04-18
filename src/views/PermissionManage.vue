<template>
  <el-card class="mgmt-table-exempt">
    <h2>权限管理</h2>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="权限列表维护" name="list">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="权限名称">
            <el-input v-model="query.name" placeholder="输入权限名称" />
          </el-form-item>
          <el-form-item label="权限编码">
            <el-input v-model="query.code" placeholder="如 user:manage" clearable />
          </el-form-item>
          <el-form-item label="权限类型">
            <el-select v-model="query.type" clearable placeholder="全部类型" style="width: 140px">
              <el-option label="菜单" value="menu" />
              <el-option label="按钮" value="button" />
            </el-select>
          </el-form-item>
          <el-form-item label="父级权限">
            <el-select v-model="query.parentId" clearable placeholder="全部父级" style="width: 260px">
              <el-option :label="ROOT_PARENT_LABEL" :value="ROOT_PARENT_VALUE" />
              <el-option v-for="item in queryParentOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="fetchPermissions">查询</el-button>
            <el-button :disabled="!canWritePermission" @click="openAdd">新增权限</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="permissions" style="width: 100%" v-loading="loading" @sort-change="onSortChange">
          <el-table-column prop="id" label="ID" width="250">
            <template #default="scope">
              <div class="cell nowrap">{{ scope.row.id }}</div>
            </template>
          </el-table-column>
          <el-table-column label="权限名称">
            <template #default="scope">
              <span>{{ permissionDisplayName(scope.row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="code" label="权限编码" sortable="custom" />
          <el-table-column prop="type" label="类型" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.type === 'menu' ? 'primary' : 'info'">{{ typeLabel(scope.row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="parentId" label="父级权限" sortable="custom">
            <template #default="scope">
              <span>{{ resolveParentLabel(scope.row.parentId) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120">
            <template #default="scope">
              <el-tag :type="normalizeStatus(scope.row.status) === 'active' ? 'success' : 'danger'">
                {{ normalizeStatus(scope.row.status) === 'active' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" :disabled="!canWritePermission" @click="togglePermissionStatus(scope.row)">
                {{ normalizeStatus(scope.row.status) === 'active' ? '禁用' : '启用' }}
              </el-button>
              <el-button size="small" :disabled="!canWritePermission" @click="editPermission(scope.row)">编辑</el-button>
              <el-button size="small" type="danger" :disabled="!canWritePermission" @click="deletePermission(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: flex-end; margin-top: 16px">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="pagination.total"
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="onPageChange"
            @size-change="onPageSizeChange"
          />
        </div>
      </el-tab-pane>
      <el-tab-pane label="公司权限树" name="tree">
        <el-tree
          v-loading="treeLoading"
          :data="permissionTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          empty-text="暂无权限树"
        >
          <template #default="{ data }">
            <span>{{ permissionDisplayName(data) }}（{{ data.code || '-' }} / {{ data.type || '-' }}）</span>
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
      <el-tab-pane label="职责分离规则" name="sod">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="场景">
            <el-input v-model="sodQuery.scenario" placeholder="例如 PRIVILEGE_CHANGE_REVIEW" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-select v-model="sodQuery.enabled" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="sodLoading" @click="fetchSodRules">查询</el-button>
            <el-button :disabled="!canManageSod" @click="openAddSodRule">新增规则</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="sodRules" v-loading="sodLoading" style="width: 100%" empty-text="暂无记录">
          <el-table-column prop="id" label="ID" width="100" />
          <el-table-column prop="scenario" label="场景" min-width="180" />
          <el-table-column prop="roleCodeA" label="角色A" width="140" />
          <el-table-column prop="roleCodeB" label="角色B" width="140" />
          <el-table-column label="启用" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button size="small" :disabled="!canManageSod" @click="editSodRule(scope.row)">编辑</el-button>
              <el-button size="small" type="danger" :disabled="!canManageSod" @click="deleteSodRule(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="display: flex; justify-content: flex-end; margin-top: 16px">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="sodPagination.total"
            v-model:current-page="sodPagination.current"
            v-model:page-size="sodPagination.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="onSodPageChange"
            @size-change="onSodPageSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="showAdd" title="新增权限">
        <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-form-item label="权限名称" prop="name"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="权限编码" prop="code"><el-input v-model="addForm.code" /></el-form-item>
          <el-form-item label="类型" prop="type">
            <el-select v-model="addForm.type" placeholder="请选择类型">
              <el-option v-for="item in PERMISSION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        <el-form-item label="父级权限">
            <el-select v-model="addForm.parentId" clearable placeholder="请选择父级权限">
              <el-option :label="ROOT_PARENT_LABEL" :value="null" />
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
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
          <el-form-item label="类型" prop="type">
            <el-select v-model="editForm.type" placeholder="请选择类型">
              <el-option v-for="item in PERMISSION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        <el-form-item label="父级权限">
          <el-select v-model="editForm.parentId" clearable placeholder="请选择父级权限">
              <el-option :label="ROOT_PARENT_LABEL" :value="null" />
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="editForm.status" active-value="active" inactive-value="disabled" active-text="启用" inactive-text="禁用" />
          </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updatePermission">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSodEdit" :title="sodEditForm.id ? '编辑 SoD 规则' : '新增 SoD 规则'" width="580px">
      <el-form :model="sodEditForm" :rules="sodValidationRules" ref="sodEditFormRef" label-width="90px">
        <el-form-item label="场景" prop="scenario">
          <el-input v-model="sodEditForm.scenario" placeholder="例如 PRIVILEGE_CHANGE_REVIEW" />
        </el-form-item>
        <el-form-item label="角色A" prop="roleCodeA">
          <el-input v-model="sodEditForm.roleCodeA" placeholder="例如 ADMIN" />
        </el-form-item>
        <el-form-item label="角色B" prop="roleCodeB">
          <el-input v-model="sodEditForm.roleCodeB" placeholder="例如 SECOPS" />
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="sodEnabledSwitch" />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="sodEditForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSodEdit = false">取消</el-button>
        <el-button type="primary" :loading="sodSaving" @click="saveSodRule">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { getSession } from '../utils/auth';
import { canManageSodRule } from '../utils/roleBoundary';

const currentUsername = String(getSession()?.user?.username || '').trim().toLowerCase();
const canWritePermission = currentUsername === 'admin';
const ROOT_PARENT_VALUE = '__ROOT__';
const ROOT_PARENT_LABEL = '无（根节点）';
const PERMISSION_TYPE_OPTIONS = Object.freeze([
  { label: '菜单', value: 'menu' },
  { label: '按钮', value: 'button' },
]);
const PERMISSION_CODE_REGEX = /^[a-z][a-z0-9_-]*(?::[a-z][a-z0-9_-]*)+$/;
const MODULE_NAME_MAP = Object.freeze({
  user: '用户',
  role: '角色',
  permission: '权限',
  approval: '审批',
  policy: '策略',
  audit: '审计',
  risk: '风险',
  security: '安全',
  data: '数据',
  model: '模型',
  ai: 'AI',
  subject: '数据主体',
  sod: '职责分离',
});
const ACTION_NAME_MAP = Object.freeze({
  view: '查看',
  list: '列表',
  page: '分页',
  manage: '管理',
  add: '新增',
  create: '创建',
  update: '更新',
  edit: '编辑',
  delete: '删除',
  remove: '移除',
  approve: '审批',
  reject: '驳回',
  operate: '操作',
  assign: '分配',
  export: '导出',
  import: '导入',
});

async function promptOperatorConfirm(actionText) {
  const operatorPrompt = await ElMessageBox.prompt(`请输入当前账号密码确认${actionText}`, '治理变更发起确认', {
    inputType: 'password',
    inputPlaceholder: '请输入当前账号密码',
    inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  });
  return {
    confirmPassword: operatorPrompt.value,
  };
}
const activeTab = ref('list');
const permissions = ref([]);
const loading = ref(false);
const treeLoading = ref(false);
const matrixLoading = ref(false);
const sodLoading = ref(false);
const sodSaving = ref(false);
const permissionTree = ref([]);
const parentPermissionPool = ref([]);
const matrixRows = ref([]);
const sodRules = ref([]);
const permissionNameMap = ref(new Map());
const showAdd = ref(false);
const showEdit = ref(false);
const showSodEdit = ref(false);
const saving = ref(false);
const addForm = ref({ name: '', code: '', type: 'menu', parentId: null, status: 'active' });
const editForm = ref({});
const sodEditForm = ref({ id: null, scenario: '', roleCodeA: '', roleCodeB: '', enabled: 1, description: '' });
const sodEnabledSwitch = ref(true);
const query = ref({ name: '', code: '', type: '', parentId: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const sodQuery = ref({ scenario: '', enabled: null });
const sodPagination = ref({ current: 1, pageSize: 10, total: 0 });
const sortState = ref({ prop: '', order: '' });
const addFormRef = ref();
const editFormRef = ref();
const sodEditFormRef = ref();
const rules = {
  name: [{ required: true, message: '权限名称不能为空', trigger: 'blur' }],
  code: [
    { required: true, message: '权限编码不能为空', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const code = String(value || '').trim();
        if (!PERMISSION_CODE_REGEX.test(code)) {
          callback(new Error('权限编码格式需为 模块:操作，例如 user:manage'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
  type: [{ required: true, message: '类型不能为空', trigger: 'blur' }]
};
const sodValidationRules = {
  scenario: [{ required: true, message: '场景不能为空', trigger: 'blur' }],
  roleCodeA: [{ required: true, message: '角色A不能为空', trigger: 'blur' }],
  roleCodeB: [{ required: true, message: '角色B不能为空', trigger: 'blur' }],
};
const canManageSod = computed(() => canManageSodRule(getSession()?.user));

watch(sodEnabledSwitch, value => {
  sodEditForm.value.enabled = value ? 1 : 0;
});

const queryParentOptions = computed(() => {
  const source = parentPermissionPool.value.length > 0 ? parentPermissionPool.value : permissions.value;
  return source
    .filter(item => item?.id != null)
    .map(item => ({
      id: Number(item.id),
      label: `${permissionDisplayName(item)}（${item.code || '-'}，ID:${item.id}）`,
    }));
});

const parentOptions = computed(() => {
  const currentEditId = Number(editForm.value?.id || 0);
  const source = parentPermissionPool.value.length > 0 ? parentPermissionPool.value : permissions.value;
  const rows = source
    .filter(item => item?.id != null)
    .filter(item => Number(item.id) !== currentEditId)
    .map(item => ({
      id: Number(item.id),
      label: `${permissionDisplayName(item)}（${item.code || '-'}，ID:${item.id}）`,
    }));

  const selectedParentId = Number(editForm.value?.parentId || 0);
  const parentExists = selectedParentId > 0 && rows.some(item => item.id === selectedParentId);
  if (selectedParentId > 0 && !parentExists) {
    rows.unshift({
      id: selectedParentId,
      label: `父级权限已失效（ID:${selectedParentId}）`,
    });
  }
  return rows;
});

function typeLabel(type) {
  return type === 'menu' ? '菜单' : type === 'button' ? '按钮' : '-';
}

function normalizeStatus(status) {
  const normalized = String(status || '').trim().toLowerCase();
  return normalized === 'disabled' ? 'disabled' : 'active';
}

function prettifyToken(token) {
  const raw = String(token || '').trim().toLowerCase();
  if (!raw) return '';
  if (MODULE_NAME_MAP[raw]) return MODULE_NAME_MAP[raw];
  if (ACTION_NAME_MAP[raw]) return ACTION_NAME_MAP[raw];
  return raw.replace(/[_-]+/g, ' ').replace(/\b\w/g, ch => ch.toUpperCase());
}

function permissionDisplayName(permission) {
  const explicitName = String(permission?.name || '').trim();
  if (explicitName) {
    return explicitName;
  }
  const code = String(permission?.code || '').trim();
  if (!code) {
    return '未命名权限';
  }
  const parts = code.split(':').map(part => part.trim()).filter(Boolean);
  if (parts.length === 0) {
    return code;
  }
  if (parts.length === 1) {
    return `${prettifyToken(parts[0])}权限`;
  }
  const modulePart = prettifyToken(parts[0]);
  const actionPart = prettifyToken(parts[parts.length - 1]);
  return `${modulePart}${actionPart}`;
}

async function fetchParentPermissionPool() {
  try {
    const data = await request.get('/permission/list');
    parentPermissionPool.value = Array.isArray(data) ? data : [];
  } catch {
    parentPermissionPool.value = [];
  }
}

async function ensureParentPermissionPoolReady() {
  if (parentPermissionPool.value.length > 0) {
    return;
  }
  await fetchParentPermissionPool();
}

function resolveParentLabel(parentId) {
  if (parentId == null) return '-';
  const matched = parentPermissionPool.value.find(item => Number(item?.id) === Number(parentId))
    || permissions.value.find(item => Number(item?.id) === Number(parentId));
  if (!matched) return `未知父级（ID:${parentId}）`;
  return `${permissionDisplayName(matched)}（ID:${matched.id}）`;
}

function normalizePermissionPayload(form) {
  const payload = {
    id: form.id == null ? null : Number(form.id),
    name: form.name,
    code: String(form.code || '').trim().toLowerCase(),
    type: String(form.type || '').trim().toLowerCase(),
    status: normalizeStatus(form.status),
  };
  payload.parentId = form.parentId == null || form.parentId === '' ? null : Number(form.parentId);
  return payload;
}

function normalizeMatrixRoleName(roleCode, roleName) {
  const normalizedCode = String(roleCode || '').trim().toUpperCase();
  if (normalizedCode === 'ADMIN') {
    return '治理管理员';
  }
  return roleName || '-';
}
async function fetchPermissions() {
  loading.value = true;
  try {
    const rootSelected = query.value.parentId === ROOT_PARENT_VALUE;
    const numericParentId = Number(query.value.parentId);
    const res = await request.get('/permission/page', {
      params: {
        name: query.value.name,
        code: query.value.code,
        type: query.value.type,
        parentId: !rootSelected && Number.isFinite(numericParentId) && numericParentId > 0 ? numericParentId : undefined,
        rootOnly: rootSelected ? true : undefined,
        sortBy: sortState.value.prop || undefined,
        sortOrder: sortState.value.order || undefined,
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
        map.set(String(item.id), `${permissionDisplayName(item)}(${item.code || '-'})`);
      }
    });
    permissionNameMap.value = map;

    matrixRows.value = rolesRaw.map(role => {
      const ids = Array.isArray(role?.permissionIds) ? role.permissionIds : [];
      return {
        roleId: role?.roleId,
        roleCode: role?.roleCode || '-',
        roleName: normalizeMatrixRoleName(role?.roleCode, role?.roleName),
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
  if (name === 'sod' && sodRules.value.length === 0) {
    fetchSodRules();
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

function onSortChange({ prop, order }) {
  sortState.value = {
    prop: prop || '',
    order: order || '',
  };
  pagination.value.current = 1;
  fetchPermissions();
}

function onSodPageChange(page) {
  sodPagination.value.current = page;
  fetchSodRules();
}

function onSodPageSizeChange(size) {
  sodPagination.value.pageSize = size;
  sodPagination.value.current = 1;
  fetchSodRules();
}

function openAddSodRule() {
  if (!canManageSod.value) {
    ElMessage.error('当前身份无 SoD 规则编辑权限');
    return;
  }
  sodEditForm.value = { id: null, scenario: '', roleCodeA: '', roleCodeB: '', enabled: 1, description: '' };
  sodEnabledSwitch.value = true;
  showSodEdit.value = true;
}

function editSodRule(row) {
  if (!canManageSod.value) {
    ElMessage.error('当前身份无 SoD 规则编辑权限');
    return;
  }
  sodEditForm.value = {
    id: row.id,
    scenario: row.scenario || '',
    roleCodeA: row.roleCodeA || '',
    roleCodeB: row.roleCodeB || '',
    enabled: Number(row.enabled || 0) > 0 ? 1 : 0,
    description: row.description || '',
  };
  sodEnabledSwitch.value = sodEditForm.value.enabled > 0;
  showSodEdit.value = true;
}

async function fetchSodRules() {
  sodLoading.value = true;
  try {
    const res = await request.get('/sod-rules/page', {
      params: {
        scenario: sodQuery.value.scenario,
        enabled: sodQuery.value.enabled,
        page: sodPagination.value.current,
        pageSize: sodPagination.value.pageSize,
      },
    });
    sodRules.value = res?.list || [];
    sodPagination.value.total = Number(res?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载 SoD 规则失败');
  } finally {
    sodLoading.value = false;
  }
}

async function saveSodRule() {
  if (!canManageSod.value) {
    ElMessage.error('当前身份无 SoD 规则编辑权限');
    return;
  }
  if (!sodEditFormRef.value) return;
  sodEditFormRef.value.validate(async valid => {
    if (!valid) return;
    sodSaving.value = true;
    try {
      await request.post('/sod-rules/save', {
        ...sodEditForm.value,
        enabled: sodEnabledSwitch.value ? 1 : 0,
      });
      ElMessage.success('SoD 规则保存成功');
      showSodEdit.value = false;
      sodPagination.value.current = 1;
      fetchSodRules();
    } catch (err) {
      ElMessage.error(err?.message || 'SoD 规则保存失败');
    } finally {
      sodSaving.value = false;
    }
  });
}

async function deleteSodRule(id) {
  if (!canManageSod.value) {
    ElMessage.error('当前身份无 SoD 规则删除权限');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该 SoD 规则吗？', '提示', { type: 'warning' });
    const pwdPrompt = await ElMessageBox.prompt('请输入当前治理管理员密码确认删除', '敏感操作二次校验', {
      inputType: 'password',
      inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await request.post('/sod-rules/delete', {
      id,
      confirmPassword: pwdPrompt.value,
    });
    ElMessage.success('SoD 规则删除成功');
    sodPagination.value.current = 1;
    fetchSodRules();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || 'SoD 规则删除失败');
    }
  }
}

function openAdd() {
  if (!canWritePermission) {
    ElMessage.error('仅主治理管理员(admin)可发起权限变更');
    return;
  }
  ensureParentPermissionPoolReady().finally(() => {
    addForm.value = { name: '', code: '', type: 'menu', parentId: null, status: 'active' };
    showAdd.value = true;
  });
}
async function addPermission() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const reviewPayload = await promptOperatorConfirm('新增权限');
      await request.post('/governance-change/submit', {
        module: 'PERMISSION',
        action: 'ADD',
        targetId: null,
        payloadJson: JSON.stringify(normalizePermissionPayload(addForm.value)),
        confirmPassword: reviewPayload.confirmPassword,
      });
      ElMessage.success('已提交待复核，治理复核通过后生效');
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
async function editPermission(row) {
  if (!canWritePermission) {
    ElMessage.error('仅主治理管理员(admin)可发起权限变更');
    return;
  }
  await ensureParentPermissionPoolReady();
  editForm.value = {
    ...row,
    type: String(row?.type || '').toLowerCase(),
    status: normalizeStatus(row?.status),
    parentId: row?.parentId == null ? null : Number(row.parentId),
  };
  showEdit.value = true;
}

async function togglePermissionStatus(row) {
  if (!canWritePermission) {
    ElMessage.error('仅主治理管理员(admin)可发起权限变更');
    return;
  }
  const current = normalizeStatus(row.status);
  const nextStatus = current === 'active' ? 'disabled' : 'active';
  try {
    await ElMessageBox.confirm(`确认将权限「${row.name}」设为${nextStatus === 'active' ? '启用' : '禁用'}吗？`, '提示', { type: 'warning' });
    const reviewPayload = await promptOperatorConfirm('更新权限状态');
    await request.post('/governance-change/submit', {
      module: 'PERMISSION',
      action: 'UPDATE',
      targetId: row.id,
      payloadJson: JSON.stringify({ id: Number(row.id), status: nextStatus }),
      confirmPassword: reviewPayload.confirmPassword,
    });
    ElMessage.success('状态变更申请已提交待复核');
    fetchPermissions();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '状态更新失败');
    }
  }
}

async function updatePermission() {
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const reviewPayload = await promptOperatorConfirm('更新权限');
      await request.post('/governance-change/submit', {
        module: 'PERMISSION',
        action: 'UPDATE',
        targetId: editForm.value.id,
        payloadJson: JSON.stringify(normalizePermissionPayload(editForm.value)),
        confirmPassword: reviewPayload.confirmPassword,
      });
      ElMessage.success('更新申请已提交待复核');
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
  if (!canWritePermission) {
    ElMessage.error('仅主治理管理员(admin)可发起权限变更');
    return;
  }
  try {
    const childCount = permissions.value.filter(item => Number(item?.parentId) === Number(id)).length;
    if (childCount > 0) {
      ElMessage.error(`该权限下还有 ${childCount} 个子权限，不能删除`);
      return;
    }
    await ElMessageBox.confirm('确认删除该权限吗？', '提示', { type: 'warning' });
    const reviewPayload = await promptOperatorConfirm('删除权限');
    await request.post('/governance-change/submit', {
      module: 'PERMISSION',
      action: 'DELETE',
      targetId: id,
      payloadJson: JSON.stringify({ id }),
      confirmPassword: reviewPayload.confirmPassword,
    });
    ElMessage.success('删除申请已提交待复核');
    pagination.value.current = 1;
    fetchPermissions();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchPermissions();
fetchPermissionTree();
fetchMatrix();
fetchParentPermissionPool();
fetchSodRules();
</script>
