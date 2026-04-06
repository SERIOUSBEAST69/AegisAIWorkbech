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
    <div class="approval-table-wrap">
    <el-table :data="approvals" class="approval-table" style="width: 100%" v-loading="loading" table-layout="fixed">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="applicantId" label="申请人ID" />
      <el-table-column label="申请账号" min-width="130">
        <template #default="scope">{{ userNameById(scope.row.applicantId) }}</template>
      </el-table-column>
      <el-table-column label="申请角色" min-width="130">
        <template #default="scope">{{ roleById(scope.row.applicantId) }}</template>
      </el-table-column>
      <el-table-column label="申请部门" min-width="130">
        <template #default="scope">{{ departmentById(scope.row.applicantId) }}</template>
      </el-table-column>
      <el-table-column label="申请岗位" min-width="130">
        <template #default="scope">{{ traceValue(scope.row.reason, 'position') || positionById(scope.row.applicantId) }}</template>
      </el-table-column>
      <el-table-column label="申请公司" min-width="120">
        <template #default="scope">{{ traceValue(scope.row.reason, 'companyId') || companyById(scope.row.applicantId) }}</template>
      </el-table-column>
      <el-table-column label="申请设备" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ traceValue(scope.row.reason, 'device') || deviceById(scope.row.applicantId) }}</template>
      </el-table-column>
      <el-table-column prop="assetId" label="资产ID" />
      <el-table-column label="理由" min-width="260" show-overflow-tooltip>
        <template #default="scope">{{ cleanReason(scope.row.reason) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row)">{{ statusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approverId" label="审批人ID" />
      <el-table-column label="审批账号" min-width="130">
        <template #default="scope">{{ userNameById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="审批角色" min-width="130">
        <template #default="scope">{{ roleById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="审批部门" min-width="130">
        <template #default="scope">{{ departmentById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="审批岗位" min-width="130">
        <template #default="scope">{{ positionById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="审批公司" min-width="120">
        <template #default="scope">{{ companyById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="审批设备" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ deviceById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="申请时间" width="190">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="190">
        <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="scope">
          <div class="action-wrap">
            <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
            <template v-if="canApproveRow(scope.row)">
              <el-button size="small" type="success" @click="approve(scope.row, '通过')">通过</el-button>
              <el-button size="small" type="danger" plain @click="approve(scope.row, '拒绝')">驳回</el-button>
            </template>
          </div>
        </template>
      </el-table-column>
    </el-table>
    </div>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';
import {
  hasRole,
} from '../utils/roleBoundary';
import { hasPermissionByUser } from '../utils/permission';

const userStore = useUserStore();
const currentUser = computed(() => userStore.userInfo || {});
const approvals = ref([]);
const userDirectory = ref(new Map());
const loading = ref(false);
const showAdd = ref(false);
const saving = ref(false);
const addForm = ref({ assetId: '', reason: '' });
const query = ref({ applicantId: '', assetId: '', status: '', keyword: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const addFormRef = ref();
const isNarrowScreen = ref(false);
const rules = {
  assetId: [{ required: true, message: '资产ID不能为空', trigger: 'blur' }],
  reason: [{ required: true, message: '理由不能为空', trigger: 'blur' }]
};

function sortByLatestTime(rows) {
  return [...rows].sort((a, b) => {
    const ta = new Date(String(a?.updateTime || a?.createTime || '').replace(' ', 'T')).getTime();
    const tb = new Date(String(b?.updateTime || b?.createTime || '').replace(' ', 'T')).getTime();
    return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
  });
}

function approvalSignature(row) {
  return [
    row?.applicantId ?? '-',
    row?.assetId ?? '-',
    String(row?.status || '').toLowerCase(),
    cleanReason(row?.reason || ''),
    row?.approverId ?? '-',
  ].join('|');
}

function dedupeTraceableApprovals(rows, limit = 15) {
  const seen = new Set();
  const unique = [];
  for (const row of sortByLatestTime(Array.isArray(rows) ? rows : [])) {
    const signature = approvalSignature(row);
    if (seen.has(signature)) {
      continue;
    }
    seen.add(signature);
    unique.push(row);
    if (limit > 0 && unique.length >= limit) {
      break;
    }
  }
  return unique;
}

function syncViewport() {
  isNarrowScreen.value = typeof window !== 'undefined' ? window.innerWidth < 992 : false;
}

async function fetchApprovals() {
  loading.value = true;
  try {
    await ensureUserDirectory();
    const params = {
      ...query.value,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    };
    const res = await request.get('/approval/page', { params });
    approvals.value = dedupeTraceableApprovals(res?.list || [], 0);
    pagination.value.total = approvals.value.length;
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

function onPageChange(page) {
  pagination.value.current = page;
  fetchApprovals();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchApprovals();
}

async function fetchTodo() {
  loading.value = true;
  try {
    await ensureUserDirectory();
    approvals.value = dedupeTraceableApprovals(await request.get('/approval/todo'), 0);
    pagination.value.total = approvals.value.length;
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
      pagination.value.current = 1;
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

async function ensureUserDirectory() {
  if (userDirectory.value.size > 0) {
    return;
  }
  try {
    const users = await request.get('/user/list');
    const map = new Map();
    (Array.isArray(users) ? users : []).forEach(item => {
      if (item?.id != null) {
        map.set(String(item.id), item);
      }
    });
    userDirectory.value = map;
  } catch {
    userDirectory.value = new Map();
  }
}

function userById(id) {
  if (id == null) return null;
  return userDirectory.value.get(String(id)) || null;
}

function userNameById(id) {
  const user = userById(id);
  return user?.username || '-';
}

function roleById(id) {
  const user = userById(id);
  return user?.roleCode || '-';
}

function departmentById(id) {
  const user = userById(id);
  return user?.department || '-';
}

function positionById(id) {
  const user = userById(id);
  return user?.jobTitle || '-';
}

function companyById(id) {
  const user = userById(id);
  return user?.companyId != null ? String(user.companyId) : '-';
}

function deviceById(id) {
  const user = userById(id);
  return user?.deviceId || '-';
}

function parseTrace(reason) {
  const text = String(reason || '');
  const match = text.match(/\[TRACE\s+([^\]]+)\]/i);
  if (!match?.[1]) {
    return {};
  }
  return match[1]
    .trim()
    .split(/\s+/)
    .reduce((acc, token) => {
      const idx = token.indexOf('=');
      if (idx <= 0) {
        return acc;
      }
      const key = token.slice(0, idx);
      const value = token.slice(idx + 1);
      if (key) {
        acc[key] = value;
      }
      return acc;
    }, {});
}

function cleanReason(reason) {
  return String(reason || '').replace(/\s*\[TRACE\s+[^\]]+\]\s*$/i, '').trim();
}

function resolveRequestType(row) {
  const reason = String(cleanReason(row?.reason || '')).trim().toUpperCase();
  if (reason.startsWith('[GOVERNANCE]')) {
    return 'GOVERNANCE';
  }
  if (reason.startsWith('[BUSINESS]')) {
    return 'BUSINESS';
  }
  if (reason.startsWith('[PERSONAL]')) {
    return 'PERSONAL';
  }
  if (reason.startsWith('[DATA]')) {
    return 'DATA';
  }
  return row?.assetId == null ? 'BUSINESS' : 'DATA';
}

function canOperateType(row) {
  const user = currentUser.value;
  const type = resolveRequestType(row);
  if (hasRole(user, 'ADMIN') || hasPermissionByUser(user, 'approval:operate')) {
    return true;
  }
  if (type === 'DATA') {
    return hasPermissionByUser(user, 'approval:operate:data');
  }
  if (type === 'GOVERNANCE') {
    return hasPermissionByUser(user, 'approval:operate:governance');
  }
  if (type === 'BUSINESS') {
    return hasPermissionByUser(user, 'approval:operate:business');
  }
  return false;
}

function canApproveRow(row) {
  if (!canOperateType(row)) {
    return false;
  }
  return normalizeStatus(row?.status) === 'pending';
}

function canRejectRow(row) {
  if (!canOperateType(row)) {
    return false;
  }
  return normalizeStatus(row?.status) === 'pending';
}

function traceValue(reason, key) {
  return parseTrace(reason)[key] || '';
}

function openDetail(row) {
  ElMessageBox.alert(
    [
      `申请ID：${row?.id ?? '-'}`,
      `申请状态：${statusText(row)}`,
      `申请人：${userNameById(row?.applicantId)}`,
      `审批人：${userNameById(row?.approverId)}`,
      `申请理由：${cleanReason(row?.reason || '-')}`,
      `申请时间：${formatTime(row?.createTime)}`,
      `更新时间：${formatTime(row?.updateTime)}`,
    ].join('\n'),
    '审批详情',
    { confirmButtonText: '关闭' }
  );
}

function normalizeStatus(status) {
  const raw = String(status || '').toLowerCase();
  if (['pending', '待审批', '待处理'].includes(raw)) return 'pending';
  if (['approved', '通过', '已通过'].includes(raw)) return 'approved';
  if (['rejected', '拒绝', '已驳回'].includes(raw)) return 'rejected';
  if (['revoked', '撤回', '已撤回'].includes(raw)) return 'revoked';
  return raw || 'pending';
}

function statusText(row) {
  const value = normalizeStatus(row?.status);
  if (value === 'pending') return '待审批';
  if (value === 'approved') return '已通过';
  if (value === 'rejected') return '已驳回';
  if (value === 'revoked') return '已撤回';
  return String(row?.status || '-');
}

function statusTagType(row) {
  const value = normalizeStatus(row?.status);
  if (value === 'pending') return 'warning';
  if (value === 'approved') return 'success';
  if (value === 'rejected') return 'danger';
  if (value === 'revoked') return 'info';
  return '';
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}
fetchApprovals();

onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>

<style scoped>
.approval-table-wrap {
  overflow-x: auto;
}

:deep(.approval-table) {
  min-width: 2360px;
}

:deep(.approval-table .el-table__body-wrapper) {
  padding-bottom: 14px;
}

:deep(.approval-table .el-table__fixed-right) {
  bottom: 14px;
  z-index: 4;
  background: rgba(7, 12, 22, 0.98);
  box-shadow: -8px 0 18px rgba(0, 0, 0, 0.22);
}

:deep(.approval-table .el-table__fixed-right-patch) {
  height: 14px;
}

:deep(.approval-table .el-table__fixed-right .el-table__fixed-body-wrapper) {
  bottom: 14px;
}

.action-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}
</style>
