<template>
  <div class="page-grid">
    <el-card class="card-glass mgmt-table-exempt">
      <div class="policy-head">
        <div class="card-header">数据主体权利工单</div>
        <div class="policy-count">共 {{ displayTotal }} 条，当前显示 {{ pagedList.length }} 条</div>
      </div>
      <div class="engine-tip">主体权利工单表与策略管理采用同款表格布局，便于统一审计与跟踪。</div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="本模块为数据主体权利工单管理，用于处理用户根据《个人信息保护法》提出的数据查询、导出、删除申请，所有工单全程留痕，不可篡改或删除。"
      />

      <el-form :inline="true" @submit.prevent ref="formRef" :model="form" :rules="rules">
        <el-form-item v-if="showUserIdField" label="用户ID" prop="userId"><el-input v-model="form.userId" /></el-form-item>
        <el-form-item label="申请类型" prop="type">
          <el-select v-model="form.type" style="width:220px">
            <el-option label="个人数据查询申请" value="access" />
            <el-option label="个人数据导出申请" value="export" />
            <el-option v-if="canSelectDeleteType" label="个人数据删除申请" value="delete" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务备注" prop="comment"><el-input v-model="form.comment" style="width:360px" placeholder="请填写真实业务场景" /></el-form-item>
        <el-button v-if="canCreateRequest" type="primary" :loading="saving" @click="create">提交申请</el-button>
        <span v-else class="hint-text">当前账号仅可查看工单</span>
      </el-form>

      <el-table :data="pagedList" table-layout="auto" style="width: 100%; margin-top:12px" v-loading="loading" empty-text="暂无记录">
        <el-table-column prop="id" label="ID" width="220" show-overflow-tooltip />
        <el-table-column label="申请账号" min-width="120">
          <template #default="scope">{{ userNameById(scope.row.userId) }}</template>
        </el-table-column>
        <el-table-column label="申请角色" min-width="120">
          <template #default="scope">{{ requesterRoleLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="申请设备" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ traceValue(scope.row.traceRawComment, 'device') || deviceById(scope.row.userId) }}</template>
        </el-table-column>
        <el-table-column label="类型" min-width="150">
          <template #default="scope">{{ typeLabel(scope.row.type) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="备注" min-width="200">
          <template #default="scope">
            <div class="comment-cell">{{ getSafeComment(scope.row.comment || '') }}</div>
          </template>
        </el-table-column>
        <el-table-column label="处理账号" min-width="100">
          <template #default="scope">{{ handlerAccountLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="处理角色" min-width="100">
          <template #default="scope">{{ handlerRoleLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="190">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="190">
          <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="处理记录" width="110">
          <template #default="scope">
            <el-button size="small" @click="openRecord(scope.row)">详情</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <div class="action-wrap">
              <template v-if="canShowPendingActions(scope.row)">
                <el-button size="small" type="primary" @click="startProcessing(scope.row)">处理</el-button>
                <el-button size="small" type="danger" plain @click="rejectPending(scope.row)">驳回</el-button>
              </template>
              <template v-else-if="canShowProcessingActions(scope.row)">
                <el-button size="small" type="primary" @click="finishProcessing(scope.row)">处理</el-button>
                <el-button size="small" type="danger" plain @click="rejectProcessing(scope.row)">驳回</el-button>
              </template>
              <span v-else class="hint-text">仅可查看</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="policy-pagination">
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
    </el-card>

    <el-drawer v-model="recordVisible" title="处理记录" :size="isNarrowScreen ? '96%' : '48%'">
      <div class="record-head">
        <div>工单ID：{{ recordTarget?.id || '-' }}</div>
        <div>当前状态：{{ statusLabel(recordTarget?.status) }}</div>
      </div>
      <el-timeline>
        <el-timeline-item :timestamp="formatTime(recordTarget?.createTime)" type="primary">
          申请提交：{{ userNameById(recordTarget?.userId) }}（{{ typeLabel(recordTarget?.type) }}）
        </el-timeline-item>
        <el-timeline-item :timestamp="formatTime(recordTarget?.updateTime)" :type="timelineType(recordTarget?.status)">
          管理员处理：{{ handlerAccountLabel(recordTarget) }} / {{ handlerRoleLabel(recordTarget) }}
        </el-timeline-item>
        <el-timeline-item :timestamp="formatTime(recordTarget?.updateTime)" :type="timelineType(recordTarget?.status)">
          处理意见：{{ recordResultText(recordTarget) }}
        </el-timeline-item>
        <el-timeline-item :timestamp="formatTime(recordTarget?.updateTime)" :type="timelineType(recordTarget?.status)">
          处理结果：{{ statusLabel(recordTarget?.status) }}
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';
import {
  canCreateSubjectRequest,
  canCreateSubjectRequestType,
  canProcessSubjectRequest,
  hasRole,
} from '../utils/roleBoundary';
function getSafeComment(comment) {
  return removeTraceSuffix(String(comment || ''));
}

function parseTrace(comment) {
  const text = String(comment || '');
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

function removeTraceSuffix(comment) {
  return String(comment || '').replace(/\s*\[TRACE\s+[^\]]+\]\s*$/i, '').trim();
}

const form = ref({ userId: '', type: 'access', comment: '' });
const list = ref([]);
const userDirectory = ref(new Map());
const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const userStore = useUserStore();
const recordVisible = ref(false);
const recordTarget = ref(null);
const isNarrowScreen = ref(false);
const pagination = ref({ current: 1, pageSize: 10, total: 0 });

const rules = {
  type: [{ required: true, message: '类型不能为空', trigger: 'change' }],
  comment: [{ required: true, message: '备注不能为空', trigger: 'blur' }]
};
const currentUser = computed(() => userStore.userInfo || {});
const showUserIdField = computed(() => hasRole(currentUser.value, 'ADMIN'));
const canCreateRequest = computed(() => canCreateSubjectRequest(currentUser.value));
const canSelectDeleteType = computed(() => canCreateSubjectRequestType(currentUser.value, 'delete'));
const canProcessRequest = computed(() => canProcessSubjectRequest(currentUser.value));
const isGovernanceAdmin = computed(() => hasRole(currentUser.value, 'ADMIN'));

const pagedList = computed(() => {
  const start = (pagination.value.current - 1) * pagination.value.pageSize;
  const end = start + pagination.value.pageSize;
  return list.value.slice(start, end);
});
const displayTotal = computed(() => list.value.length);

function sortByLatestTime(rows) {
  return [...rows].sort((a, b) => {
    const ta = new Date(String(a?.updateTime || a?.createTime || '').replace(' ', 'T')).getTime();
    const tb = new Date(String(b?.updateTime || b?.createTime || '').replace(' ', 'T')).getTime();
    return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
  });
}

function subjectSignature(row) {
  const plainComment = removeTraceSuffix(row?.comment || row?.traceRawComment || '');
  const result = String(row?.result || '').trim();
  return [
    row?.userId ?? '-',
    String(row?.type || '').toLowerCase(),
    String(row?.status || '').toLowerCase(),
    plainComment,
    result,
  ].join('|');
}

function dedupeTraceableRequests(rows, limit = 15) {
  const seen = new Set();
  const unique = [];
  const sorted = sortByLatestTime(Array.isArray(rows) ? rows : []);
  for (const row of sorted) {
    const signature = subjectSignature(row);
    if (seen.has(signature)) {
      continue;
    }
    seen.add(signature);
    unique.push(row);
    if (unique.length >= limit) {
      break;
    }
  }
  return unique;
}

function syncViewport() {
  isNarrowScreen.value = typeof window !== 'undefined' ? window.innerWidth < 992 : false;
}

async function load() {
  loading.value = true;
  try {
    await ensureUserDirectory();
    const data = await request.get('/subject-request/list');
    const normalized = (Array.isArray(data) ? data : []).map(item => ({
      ...item,
      traceRawComment: String(item?.comment || ''),
      comment: getSafeComment(item?.comment),
    }));
    list.value = dedupeTraceableRequests(normalized, 15);
    pagination.value.total = list.value.length;
    const pageCount = Math.max(1, Math.ceil(pagination.value.total / pagination.value.pageSize));
    if (pagination.value.current > pageCount) {
      pagination.value.current = pageCount;
    }
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
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

function requesterRoleLabel(row) {
  const fromTrace = traceValue(row?.traceRawComment, 'role');
  return fromTrace || roleById(row?.userId);
}

function handlerRoleLabel(row) {
  const status = String(row?.status || '').toLowerCase();
  if (!row?.handlerId && !['done', 'rejected', 'processing'].includes(status)) {
    return '-';
  }
  const account = String(handlerAccountLabel(row) || '').toLowerCase();
  if (account === 'admin' || account === '治理管理员/admin') {
    return '合规管理员';
  }
  const rawRole = roleById(row?.handlerId);
  return rawRole && rawRole !== '-' ? rawRole : '合规管理员';
}

function handlerAccountLabel(row) {
  const status = String(row?.status || '').toLowerCase();
  if (!row?.handlerId && !['done', 'rejected', 'processing'].includes(status)) {
    return '-';
  }
  const username = userNameById(row?.handlerId);
  if (username && username !== '-') {
    return username;
  }
  return '治理管理员/admin';
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

function traceValue(comment, key) {
  return parseTrace(comment)[key] || '';
}

function typeLabel(type) {
  const value = String(type || '').toLowerCase();
  if (value === 'access') return '个人数据查询申请';
  if (value === 'delete') return '个人数据删除申请';
  if (value === 'export') return '个人数据导出申请';
  return '-';
}

function statusLabel(status) {
  const value = String(status || '').toLowerCase();
  if (value === 'pending') return '待处理';
  if (value === 'processing') return '处理中';
  if (value === 'done') return '已完成';
  if (value === 'rejected') return '已驳回';
  return value || '-';
}

function statusTagType(status) {
  const value = String(status || '').toLowerCase();
  if (value === 'pending') return 'warning';
  if (value === 'processing') return 'primary';
  if (value === 'done') return 'success';
  if (value === 'rejected') return 'danger';
  return 'info';
}

function timelineType(status) {
  const value = String(status || '').toLowerCase();
  if (value === 'done') return 'success';
  if (value === 'rejected') return 'danger';
  if (value === 'processing') return 'primary';
  return 'warning';
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}

function canShowPendingActions(row) {
  if (!canProcessRequest.value || !isGovernanceAdmin.value) {
    return false;
  }
  return String(row?.status || '').toLowerCase() === 'pending';
}

function canShowProcessingActions(row) {
  if (!canProcessRequest.value) {
    return false;
  }
  const status = String(row?.status || '').toLowerCase();
  if (status !== 'processing') {
    return false;
  }
  return Number(row?.handlerId || 0) === Number(currentUser.value?.id || 0);
}

async function create() {
  if (!canCreateRequest.value) {
    ElMessage.warning('当前账号仅可查看工单');
    return;
  }
  if (!canCreateSubjectRequestType(currentUser.value, form.value.type)) {
    ElMessage.warning('当前账号不可提交该类型工单');
    return;
  }
  if (!formRef.value) return;
  formRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload = {
        ...form.value,
        userId: form.value.userId || userStore.userInfo?.id || null,
      };
      await request.post('/subject-request/create', payload);
      ElMessage.success('提交成功');
      await load();
    } catch (err) {
      ElMessage.error(err?.message || '提交失败');
    } finally {
      saving.value = false;
    }
  });
}

async function update(row, status, resultText = '') {
  try {
    const payload = {
      ...row,
      status,
      handlerId: row.handlerId || userStore.userInfo?.id || null,
      result: resultText || row.result || '',
    };
    await request.post('/subject-request/process', payload);
    ElMessage.success('更新成功');
    await load();
  } catch (err) {
    const message = String(err?.message || '');
    if (message.includes('工单不存在')) {
      ElMessage.warning('该工单已不存在，列表已刷新');
      await load();
      return;
    }
    ElMessage.error(err?.message || '更新失败');
  }
}

async function startProcessing(row) {
  await update(row, 'processing', row.result || '治理管理员已接单处理中');
}

async function rejectPending(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回工单', {
      inputPlaceholder: '例如：因申请删除的数据涉及合规审计要求，无法删除，请联系合规管理员说明情况',
      inputValidator: v => (!!v && String(v).trim().length > 0) || '驳回原因不能为空',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
    });
    await update(row, 'rejected', value);
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err?.message || '驳回失败');
  }
}

async function finishProcessing(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入处理意见', '完成工单', {
      inputPlaceholder: '例如：已完成查询并通过安全渠道反馈给申请人',
      inputValidator: v => (!!v && String(v).trim().length > 0) || '处理意见不能为空',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
    });
    await update(row, 'done', value);
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err?.message || '处理失败');
  }
}

async function rejectProcessing(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回工单', {
      inputPlaceholder: '例如：因申请删除的数据涉及合规审计要求，无法删除，请联系合规管理员说明情况',
      inputValidator: v => (!!v && String(v).trim().length > 0) || '驳回原因不能为空',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
    });
    await update(row, 'rejected', value);
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err?.message || '驳回失败');
  }
}

function openRecord(row) {
  recordTarget.value = row;
  recordVisible.value = true;
}

function recordResultText(row) {
  const text = String(row?.result || '').trim();
  if (text) {
    return text;
  }
  if (String(row?.status || '').toLowerCase() === 'rejected') {
    return '因申请删除的数据涉及合规审计要求，无法删除，请联系合规管理员说明情况';
  }
  if (String(row?.status || '').toLowerCase() === 'done') {
    return '已按合规流程处理完成';
  }
  return '待处理';
}

function onPageChange(page) {
  pagination.value.current = page;
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
}

load();

onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});

if (!form.value.userId && userStore.userInfo?.id) {
  form.value.userId = userStore.userInfo.id;
}
</script>

<style scoped>
.page-grid { display: grid; gap: 16px; }
.card-header { font-weight: 600; margin-bottom: 12px; color: var(--color-text); }
.policy-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.policy-count {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.engine-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--color-border-light);
  background: var(--color-fill-light);
  color: var(--color-text-primary);
  font-size: 13px;
}
.hint-text { color: var(--color-text-secondary); font-size: 12px; }
.policy-pagination { margin-top: 14px; display: flex; justify-content: flex-end; }
.record-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--color-text-secondary);
}

.action-wrap {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  align-items: center;
}

.comment-cell {
  white-space: normal;
  word-break: break-word;
}

:deep(.el-table) {
  min-width: 1560px;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-border-color: var(--color-border-light);
  --el-table-text-color: var(--color-text);
  --el-table-header-text-color: var(--color-text-secondary);
}

:deep(.el-table .el-table__row) {
  height: 56px;
}

:deep(.el-table .el-table__cell) {
  font-size: 13px;
  word-break: break-word;
}

:deep(.el-table .el-table__body-wrapper) {
  padding-bottom: 14px;
}

:deep(.el-table .el-table__fixed-right) {
  bottom: 14px;
  z-index: 4;
  background: rgba(7, 12, 22, 0.98);
  box-shadow: -8px 0 18px rgba(0, 0, 0, 0.22);
}

:deep(.el-table .el-table__fixed-right-patch) {
  height: 14px;
}

:deep(.el-table .el-table__fixed-right .el-table__fixed-body-wrapper) {
  bottom: 14px;
}

@media (max-width: 900px) {
  .policy-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
