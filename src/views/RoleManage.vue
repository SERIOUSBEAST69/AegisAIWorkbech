<template>
  <el-card>
    <h2>角色管理</h2>

    <el-form :inline="true" @submit.prevent>
      <el-form-item label="角色关键词">
        <el-input v-model="query.keyword" placeholder="输入角色名称或编码" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchRoles">查询</el-button>
        <el-button v-if="canWriteRole()" @click="openCreate">新增角色</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="roles" v-loading="loading" style="width: 100%" empty-text="暂无角色">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="name" label="角色名称" min-width="140" />
      <el-table-column prop="code" label="角色编码" min-width="140" />
      <el-table-column label="开放注册" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.allowSelfRegister ? 'success' : 'info'">
            {{ scope.row.allowSelfRegister ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="系统预设" width="110">
        <template #default="scope">
          <el-tag :type="isSystemRole(scope.row) ? 'warning' : 'info'">
            {{ isSystemRole(scope.row) ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="260" :fixed="isNarrowScreen ? false : 'right'">
        <template #default="scope">
          <el-button size="small" :disabled="isSystemRole(scope.row) || !canWriteRole()" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="primary" plain :disabled="!canOpenPermissionEditor(scope.row)" @click="openPermissions(scope.row)">权限</el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="isSystemRole(scope.row) || !canWriteRole()"
            @click="deleteRole(scope.row)"
          >
            删除
          </el-button>
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

    <el-dialog v-model="showEditor" :title="editing ? '编辑角色' : '新增角色'" width="720px">
      <el-form ref="editorRef" :model="editor" :rules="rules" label-width="110px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="editor.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="editor.code" :disabled="editing" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="开放注册">
          <el-switch v-model="editor.allowSelfRegister" active-text="允许" inactive-text="不允许" />
        </el-form-item>
        <el-form-item v-if="editor.allowSelfRegister" label="审批说明">
          <el-input v-model="editor.reviewNote" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editor.description" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="权限树">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            node-key="code"
            show-checkbox
            check-strictly
            default-expand-all
            :props="treeProps"
          >
            <template #default="{ data }">
              <span>{{ data.name }}（{{ data.code }}）</span>
            </template>
          </el-tree>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditor = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPermissionOnly" title="更新角色权限" width="640px">
      <el-tree
        ref="permissionOnlyTreeRef"
        :data="permissionTree"
        node-key="code"
        show-checkbox
        check-strictly
        default-expand-all
        :props="treeProps"
      >
        <template #default="{ data }">
          <span>{{ data.name }}（{{ data.code }}）</span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="showPermissionOnly = false">取消</el-button>
        <el-button type="primary" :loading="savingPermission" @click="saveRolePermissions">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { getSession } from '../utils/auth';
import { hasPermissionByUser } from '../utils/permission';

function currentUserSnapshot() {
  return getSession()?.user || {};
}

function normalizeCompanyId(value) {
  if (value == null || value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function roleCompanyId(role) {
  return normalizeCompanyId(role?.companyId ?? role?.company_id);
}

function currentCompanyId() {
  return normalizeCompanyId(currentUserSnapshot()?.companyId);
}

function isPlatformAdmin() {
  const user = currentUserSnapshot();
  const currentUsername = String(user?.username || '').trim().toLowerCase();
  const currentRoleCode = String(user?.roleCode || '').trim().toUpperCase();
  return currentRoleCode === 'ADMIN' || currentUsername === 'admin';
}

function canWriteRole() {
  const user = currentUserSnapshot();
  return isPlatformAdmin() || hasPermissionByUser(user, 'role:manage');
}

function canAssignRolePermission() {
  const user = currentUserSnapshot();
  return canWriteRole() || hasPermissionByUser(user, 'role:permission:assign');
}

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

const loading = ref(false);
const saving = ref(false);
const savingPermission = ref(false);
const roles = ref([]);
const permissionTree = ref([]);
const showEditor = ref(false);
const showPermissionOnly = ref(false);
const editing = ref(false);
const currentRole = ref(null);
const isNarrowScreen = ref(false);

const query = ref({ keyword: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });

const editorRef = ref();
const permissionTreeRef = ref();
const permissionOnlyTreeRef = ref();

const treeProps = {
  label: 'name',
  children: 'children'
};

const editor = ref({
  id: null,
  name: '',
  code: '',
  description: '',
  allowSelfRegister: false,
  reviewNote: '',
});

const rules = {
  name: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '角色编码不能为空', trigger: 'blur' }]
};

function syncViewport() {
  isNarrowScreen.value = typeof window !== 'undefined' ? window.innerWidth < 992 : false;
}

function normalizeRoleDisplayName(role) {
  const code = String(role?.code || '').trim().toUpperCase();
  const displayNameMap = {
    ADMIN: '治理管理员',
    ADMIN_REVIEWER: '治理复核员',
    ADMIN_OPS: '治理运维员',
    EXECUTIVE_OVERVIEW: '管理层总览岗',
    EXECUTIVE_COMPLIANCE: '管理层合规岗',
    SECOPS_TRIAGE: '安全运维复核岗',
    SECOPS_RESPONDER: '安全运维阻断岗',
    DATA_ADMIN_MAINTAINER: '数据管理员维护岗',
    DATA_ADMIN_APPROVER: '数据管理员审批岗',
    AI_BUILDER_PROMPT: 'AI开发提示岗',
    AI_BUILDER_AUDITOR: 'AI开发审计岗',
    BUSINESS_OWNER_APPROVER: '业务负责人放行岗',
    BUSINESS_OWNER_REVIEWER: '业务负责人复核岗',
    EMPLOYEE_REQUESTER_FULL: '普通员工申请岗',
    EMPLOYEE_REQUESTER_LIMITED: '普通员工受限申请岗',
    EMPLOYEE_OBSERVER: '普通员工观察岗',
  };
  if (displayNameMap[code]) {
    return { ...role, name: displayNameMap[code] };
  }
  return role;
}

function flattenTreeCodes(nodes, acc = []) {
  for (const node of nodes || []) {
    if (node?.code) {
      acc.push(node.code);
    }
    flattenTreeCodes(node?.children || [], acc);
  }
  return acc;
}

function isSystemRole(role) {
  const flag = role?.isSystem;
  if (flag === true || flag === 1) return true;
  if (typeof flag === 'string') {
    const normalized = flag.trim().toLowerCase();
    return normalized === 'true' || normalized === '1' || normalized === 'y' || normalized === 'yes';
  }
  return false;
}

function isRoleInCurrentCompany(role) {
  const boundCompanyId = currentCompanyId();
  if (boundCompanyId == null) {
    return true;
  }
  const boundRoleCompanyId = roleCompanyId(role);
  if (boundRoleCompanyId == null) {
    return true;
  }
  return boundRoleCompanyId === boundCompanyId;
}

function canOpenPermissionEditor(role) {
  return canAssignRolePermission() && !isSystemRole(role) && isRoleInCurrentCompany(role);
}

async function fetchPermissionTree() {
  const data = await request.get('/permissions/tree');
  permissionTree.value = Array.isArray(data) ? data : [];
}

async function fetchRoles() {
  loading.value = true;
  try {
    const data = await request.get('/roles', {
      params: {
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
        keyword: query.value.keyword || undefined,
      },
    });
    roles.value = Array.isArray(data?.list) ? data.list : [];
    roles.value = roles.value.map(normalizeRoleDisplayName);
    pagination.value.total = Number(data?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '角色加载失败');
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

function resetEditor() {
  editor.value = {
    id: null,
    name: '',
    code: '',
    description: '',
    allowSelfRegister: false,
    reviewNote: '',
  };
}

function getCheckedPermissionCodes(treeRef) {
  const checked = treeRef?.getCheckedKeys?.(false) || [];
  const halfChecked = treeRef?.getHalfCheckedKeys?.() || [];
  return Array.from(new Set([...checked, ...halfChecked])).filter(Boolean);
}

async function openCreate() {
  if (!canWriteRole()) {
    ElMessage.error('仅主治理管理员(admin)可发起角色变更');
    return;
  }
  editing.value = false;
  currentRole.value = null;
  resetEditor();
  await fetchPermissionTree();
  showEditor.value = true;
  await nextTick();
  permissionTreeRef.value?.setCheckedKeys([]);
}

async function openEdit(role) {
  if (!canWriteRole()) {
    ElMessage.error('仅主治理管理员(admin)可发起角色变更');
    return;
  }
  if (isSystemRole(role)) {
    ElMessage.warning('系统预设角色不允许编辑');
    return;
  }
  try {
    editing.value = true;
    currentRole.value = role;
    editor.value = {
      id: role.id,
      name: role.name,
      code: role.code,
      description: role.description || '',
      allowSelfRegister: Boolean(role.allowSelfRegister),
      reviewNote: '',
    };
    await fetchPermissionTree();
    const currentCodes = await request.get(`/roles/${role.id}/permissions`);
    showEditor.value = true;
    await nextTick();
    permissionTreeRef.value?.setCheckedKeys(Array.isArray(currentCodes) ? currentCodes : []);
  } catch (err) {
    ElMessage.error(err?.message || '角色详情加载失败');
  }
}

async function saveRole() {
  editorRef.value?.validate?.(async valid => {
    if (!valid) {
      return;
    }
    saving.value = true;
    try {
      const reviewPayload = await promptOperatorConfirm(editing.value ? '更新角色' : '新增角色');
      const permissionCodes = getCheckedPermissionCodes(permissionTreeRef.value);
      const payload = {
        name: editor.value.name,
        code: String(editor.value.code || '').trim().toUpperCase(),
        description: editor.value.description,
        allowSelfRegister: Boolean(editor.value.allowSelfRegister),
        permissionCodes,
        reviewNote: editor.value.reviewNote || undefined,
      };

      await request.post('/governance-change/submit', {
        module: 'ROLE',
        action: editing.value && editor.value.id ? 'UPDATE' : 'ADD',
        targetId: editing.value && editor.value.id ? editor.value.id : null,
        payloadJson: JSON.stringify(payload),
        confirmPassword: reviewPayload.confirmPassword,
      });
      ElMessage.success('已提交待复核，治理复核通过后生效');
      showEditor.value = false;
      await fetchRoles();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}

async function openPermissions(role) {
  if (!canAssignRolePermission()) {
    ElMessage.error('当前账号无权限分配角色权限');
    return;
  }
  if (isSystemRole(role)) {
    ElMessage.warning('系统预设角色不允许编辑');
    return;
  }
  if (!isRoleInCurrentCompany(role)) {
    ElMessage.error('角色不属于当前公司');
    return;
  }
  try {
    currentRole.value = role;
    await fetchPermissionTree();
    const currentCodes = await request.get(`/roles/${role.id}/permissions`);
    showPermissionOnly.value = true;
    await nextTick();
    permissionOnlyTreeRef.value?.setCheckedKeys(Array.isArray(currentCodes) ? currentCodes : []);
  } catch (err) {
    ElMessage.error(err?.message || '角色权限加载失败');
  }
}

async function saveRolePermissions() {
  if (!currentRole.value?.id) {
    return;
  }
  savingPermission.value = true;
  try {
    const reviewPayload = await promptOperatorConfirm('更新角色权限');
    const allCodes = new Set(flattenTreeCodes(permissionTree.value).map(code => String(code).trim().toLowerCase()));
    const selectedCodes = getCheckedPermissionCodes(permissionOnlyTreeRef.value)
      .map(code => String(code).trim())
      .filter(code => allCodes.has(code.toLowerCase()));
    await request.post('/governance-change/submit', {
      module: 'ROLE',
      action: 'UPDATE',
      targetId: currentRole.value.id,
      payloadJson: JSON.stringify({ permissionCodes: selectedCodes }),
      confirmPassword: reviewPayload.confirmPassword,
    });
    ElMessage.success('权限变更已提交待复核');
    showPermissionOnly.value = false;
  } catch (err) {
    ElMessage.error(err?.message || '权限更新失败');
  } finally {
    savingPermission.value = false;
  }
}

async function deleteRole(role) {
  if (!role?.id) {
    return;
  }
  if (isSystemRole(role)) {
    ElMessage.warning('系统预设角色不允许删除');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除角色「${role.name}」吗？`, '提示', { type: 'warning' });
    const reviewPayload = await promptOperatorConfirm('删除角色');
    await request.post('/governance-change/submit', {
      module: 'ROLE',
      action: 'DELETE',
      targetId: role.id,
      payloadJson: JSON.stringify({ roleId: role.id }),
      confirmPassword: reviewPayload.confirmPassword,
    });
    ElMessage.success('删除申请已提交待复核');
    if (roles.value.length === 1 && pagination.value.current > 1) {
      pagination.value.current -= 1;
    }
    await fetchRoles();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '删除失败');
    }
  }
}

onMounted(async () => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
  await Promise.all([fetchPermissionTree(), fetchRoles()]);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>
