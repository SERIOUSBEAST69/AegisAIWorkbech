<template>
  <el-card>
    <h2>用户管理</h2>

    <el-form :inline="true" @submit.prevent>
      <el-form-item label="用户名">
        <el-input v-model="query.username" clearable placeholder="输入用户名" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.accountStatus" clearable placeholder="全部" style="width: 150px">
          <el-option label="待审批" value="pending" />
          <el-option label="已激活" value="active" />
          <el-option label="已拒绝" value="rejected" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchUsers">查询</el-button>
        <el-button v-if="canWriteUser" @click="openAdd">新增用户</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="users" style="width: 100%" v-loading="loading" empty-text="暂无记录">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="真实姓名" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          {{ displayRealName(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="260">
        <template #default="scope">
          <el-space wrap>
            <el-tag v-for="tag in roleLabels(scope.row)" :key="tag" type="info">{{ tag }}</el-tag>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column prop="department" label="部门" min-width="140" />
      <el-table-column label="账号状态" width="120">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.accountStatus)">{{ scope.row.accountStatus || 'active' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" :fixed="isNarrowScreen ? false : 'right'">
        <template #default="scope">
          <el-button v-if="canApproveUser && scope.row.accountStatus === 'pending'" size="small" type="success" @click="approveUser(scope.row.id)">通过</el-button>
          <el-button v-if="canApproveUser && scope.row.accountStatus === 'pending'" size="small" type="warning" @click="rejectUser(scope.row.id)">拒绝</el-button>
          <el-button
            v-if="canWriteUser"
            size="small"
            type="primary"
            plain
            :loading="roleRecommendLoadingUserId === (scope.row.id ? String(scope.row.id) : String(scope.row.username || ''))"
            @click="viewRoleRecommend(scope.row)"
          >角色推荐</el-button>
          <el-button v-if="canWriteUser" size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button v-if="canWriteUser" size="small" type="danger" @click="deleteUser(scope.row.id)">删除</el-button>
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

    <el-dialog v-model="showAdd" title="新增用户" width="680px">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="用户名" prop="username"><el-input v-model="addForm.username" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="addForm.password" type="password" /></el-form-item>
        <el-form-item label="真实姓名" prop="realName"><el-input v-model="addForm.realName" /></el-form-item>
        <el-form-item label="角色分配" prop="roleIds">
          <el-select v-model="addForm.roleIds" multiple filterable collapse-tags style="width: 100%" placeholder="可留空，后续由管理员分配">
            <el-option v-for="role in roleOptions" :key="role.id" :label="`${role.name} (${role.code})`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="addForm.department" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEdit" title="编辑用户" width="680px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="真实姓名" prop="realName"><el-input v-model="editForm.realName" /></el-form-item>
        <el-form-item label="角色分配" prop="roleIds">
          <el-select v-model="editForm.roleIds" multiple filterable collapse-tags style="width: 100%" placeholder="可留空，后续由管理员分配">
            <el-option v-for="role in roleOptions" :key="role.id" :label="`${role.name} (${role.code})`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="editForm.department" /></el-form-item>
        <el-form-item label="账号状态" prop="accountStatus">
          <el-select v-model="editForm.accountStatus" style="width: 100%">
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

    <el-dialog v-model="showRoleRecommend" title="角色推荐结果" width="680px">
      <div v-if="recommendPayload" style="display:grid;gap:10px;">
        <div><strong>用户：</strong>{{ recommendPayload.username || '-' }}</div>
        <div><strong>部门/岗位：</strong>{{ recommendPayload.department || '-' }} / {{ recommendPayload.jobTitle || '-' }}</div>
        <div><strong>样本：</strong>近 {{ recommendPayload.basedOnDays || 90 }} 天，用户 {{ recommendPayload.sampleUsers || 0 }} 人，行为 {{ recommendPayload.sampleOperations || 0 }} 项</div>
        <el-table :data="recommendPayload.recommendedRoles || []" size="small" empty-text="暂无推荐角色">
          <el-table-column prop="roleCode" label="角色编码" min-width="120" />
          <el-table-column prop="roleName" label="角色名称" min-width="140" />
          <el-table-column prop="confidence" label="置信度" width="100" />
          <el-table-column prop="supportUsers" label="支持样本" width="100" />
          <el-table-column prop="score" label="分值" width="100" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showRoleRecommend = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { getSession } from '../utils/auth';

const users = ref([]);
const roleOptions = ref([]);
const roleIndex = ref({});

const loading = ref(false);
const saving = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const showRoleRecommend = ref(false);
const roleRecommendLoadingUserId = ref(null);
const recommendPayload = ref(null);
const isNarrowScreen = ref(false);

const syncViewport = () => {
  isNarrowScreen.value = window.innerWidth < 992;
};

const query = ref({ username: '', accountStatus: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });

const addFormRef = ref();
const editFormRef = ref();
const sessionUser = getSession()?.user || {};
const currentUsername = String(sessionUser?.username || '').trim().toLowerCase();
const currentRoleCode = String(sessionUser?.roleCode || sessionUser?.role || '').trim().toUpperCase();
const canApproveUser = currentRoleCode === 'ADMIN';
const canWriteUser = currentRoleCode === 'ADMIN';

const addForm = ref({
  username: '',
  password: '',
  realName: '',
  roleIds: [],
  department: '',
});

const editForm = ref({
  id: null,
  realName: '',
  roleIds: [],
  department: '',
  accountStatus: 'active',
});

const addRules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }],
  realName: [{ required: true, message: '真实姓名不能为空', trigger: 'blur' }],
};

const editRules = {
  realName: [{ required: true, message: '真实姓名不能为空', trigger: 'blur' }],
  accountStatus: [{ required: true, message: '账号状态不能为空', trigger: 'change' }],
};

function statusTagType(status) {
  if (status === 'pending') return 'warning';
  if (status === 'active') return 'success';
  if (status === 'rejected') return 'danger';
  return 'info';
}

function roleLabels(user) {
  const ids = Array.isArray(user?.roleIds) && user.roleIds.length > 0
    ? user.roleIds
    : (user?.roleId ? [user.roleId] : []);
  const labels = ids
    .map(id => roleIndex.value[id])
    .filter(Boolean)
    .map(role => `${role.name} (${role.code})`);
  return labels.length > 0 ? labels : ['待分配'];
}

function displayRealName(user) {
  const value = String(user?.realName || user?.nickname || user?.username || '').trim();
  return value || '待补全';
}

async function fetchRoles() {
  const data = await request.get('/roles', { params: { page: 1, pageSize: 200 } });
  const list = Array.isArray(data?.list) ? data.list : [];
  roleOptions.value = list;
  roleIndex.value = list.reduce((acc, item) => {
    acc[item.id] = item;
    return acc;
  }, {});
}

async function fetchUsers() {
  loading.value = true;
  try {
    const data = await request.get('/user/page', {
      params: {
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
        username: query.value.username || undefined,
        accountStatus: query.value.accountStatus || undefined,
      }
    });
    users.value = (Array.isArray(data?.list) ? data.list : []).map(item => ({
      ...item,
      realName: String(item?.realName || item?.nickname || item?.username || '').trim(),
      nickname: String(item?.nickname || item?.realName || item?.username || '').trim(),
    }));
    pagination.value.total = Number(data?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '用户加载失败');
  } finally {
    loading.value = false;
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

function openAdd() {
  addForm.value = {
    username: '',
    password: '',
    realName: '',
    roleIds: [],
    department: '',
  };
  showAdd.value = true;
}

function openEdit(user) {
  editForm.value = {
    id: user.id,
    realName: user.realName || '',
    roleIds: Array.isArray(user.roleIds) && user.roleIds.length > 0
      ? [...user.roleIds]
      : (user.roleId ? [user.roleId] : []),
    department: user.department || '',
    accountStatus: user.accountStatus || 'active',
  };
  showEdit.value = true;
}

function buildUserRolePayload(formValue) {
  const roleIds = (formValue.roleIds || []).filter(Boolean);
  const roleId = roleIds[0] || null;
  return {
    roleId,
    roleIds,
  };
}

async function addUser() {
  addFormRef.value?.validate?.(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload = {
        username: addForm.value.username,
        password: addForm.value.password,
        realName: addForm.value.realName,
        department: addForm.value.department,
        ...buildUserRolePayload(addForm.value),
      };
      await request.post('/user/register', payload);
      ElMessage.success('新增成功');
      showAdd.value = false;
      pagination.value.current = 1;
      await fetchUsers();
    } catch (err) {
      ElMessage.error(err?.message || '新增失败');
    } finally {
      saving.value = false;
    }
  });
}

async function updateUser() {
  editFormRef.value?.validate?.(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const pwdPrompt = await ElMessageBox.prompt('请输入当前账号密码确认发起用户更新', '治理变更发起确认', {
        inputType: 'password',
        inputPlaceholder: '请输入当前账号密码',
        inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
        confirmButtonText: '确认发起',
        cancelButtonText: '取消',
      });
      const payload = {
        realName: editForm.value.realName,
        department: editForm.value.department,
        accountStatus: editForm.value.accountStatus,
        ...buildUserRolePayload(editForm.value),
      };
      await request.post('/governance-change/submit', {
        module: 'USER',
        action: 'UPDATE',
        targetId: editForm.value.id,
        payloadJson: JSON.stringify(payload),
        confirmPassword: pwdPrompt.value,
      });
      ElMessage.success('用户更新申请已提交待复核');
      showEdit.value = false;
      await fetchUsers();
    } catch (err) {
      if (err === 'cancel' || err === 'close') {
        return;
      }
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}

async function approveUser(id) {
  try {
    await request.post('/user/approve', { id });
    ElMessage.success('审批通过');
    await fetchUsers();
  } catch (err) {
    ElMessage.error(err?.message || '审批失败');
  }
}

async function rejectUser(id) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因（可选）', '拒绝注册', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
    await request.post('/user/reject', { id, reason: value || '' });
    ElMessage.success('已拒绝该账号');
    await fetchUsers();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '拒绝失败');
    }
  }
}

async function deleteUser(id) {
  try {
    await ElMessageBox.confirm('确认删除该用户吗？', '提示', { type: 'warning' });
    const pwdPrompt = await ElMessageBox.prompt('请输入当前账号密码确认发起用户删除', '治理变更发起确认', {
      inputType: 'password',
      inputPlaceholder: '请输入当前账号密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认发起',
      cancelButtonText: '取消',
    });
    await request.post('/governance-change/submit', {
      module: 'USER',
      action: 'DELETE',
      targetId: id,
      payloadJson: JSON.stringify({ id, deleteReason: 'governance_console_delete' }),
      confirmPassword: pwdPrompt.value,
    });
    ElMessage.success('用户删除申请已提交待复核');
    pagination.value.current = 1;
    await fetchUsers();
  } catch (err) {
    const message = String(err?.message || '');
    if (message.includes('不存在或不在当前公司')) {
      users.value = users.value.filter(item => item?.id !== id);
      ElMessage.warning('该用户不在当前公司，已从当前列表移除并刷新');
      await fetchUsers();
      return;
    }
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.message || '删除失败');
    }
  }
}

async function viewRoleRecommend(row) {
  const targetId = row?.id ? String(row.id) : '';
  const targetUsername = row?.username ? String(row.username).trim() : '';
  const targetKey = targetId || targetUsername;
  if (!targetKey || roleRecommendLoadingUserId.value === targetKey) {
    return;
  }
  roleRecommendLoadingUserId.value = targetKey;
  try {
    const data = await request.get(`/user/role-recommend/${encodeURIComponent(targetKey)}`, {
      params: {
        username: targetUsername || undefined,
      },
    });
    recommendPayload.value = data || null;
    showRoleRecommend.value = true;
  } catch (err) {
    ElMessage.error(err?.message || '获取角色推荐失败，请刷新用户列表后重试');
  } finally {
    roleRecommendLoadingUserId.value = null;
  }
}

onMounted(async () => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
  try {
    await fetchRoles();
  } catch (err) {
    ElMessage.error(err?.message || '角色加载失败');
  }
  await fetchUsers();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>
