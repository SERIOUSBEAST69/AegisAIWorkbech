<template>
  <el-card>
    <h2>风险事件管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="事件类型">
        <el-input v-model="query.type" placeholder="输入事件类型" />
      </el-form-item>
      <el-form-item label="风险等级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 130px">
          <el-option label="低" value="LOW" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="高" value="HIGH" />
          <el-option label="严重" value="CRITICAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
          <el-option label="待处理" value="OPEN" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已处置" value="RESOLVED" />
          <el-option label="已忽略" value="IGNORED" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="处置记录/规则ID" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchEvents">查询</el-button>
        <el-button :disabled="!canHandleRiskEvent" @click="openAdd">新增事件</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="pagedEvents" style="width: 100%" v-loading="loading" empty-text="暂无风险事件">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="事件类型" min-width="170" show-overflow-tooltip />
      <el-table-column label="风险等级" width="100">
        <template #default="scope">
          <el-tag :type="levelTagType(scope.row.level)">{{ levelText(scope.row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row)">{{ statusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="事件时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="规则/关联日志" min-width="190" show-overflow-tooltip>
        <template #default="scope">{{ eventRuleText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="事件详情" min-width="260" show-overflow-tooltip>
        <template #default="scope">{{ eventDetail(scope.row) }}</template>
      </el-table-column>
      <el-table-column prop="handlerId" label="处置人ID" />
      <el-table-column label="处置账号" min-width="130">
        <template #default="scope">{{ handlerName(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="处置角色" min-width="120">
        <template #default="scope">{{ handlerRole(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="处置部门" min-width="120">
        <template #default="scope">{{ handlerDepartment(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="处置岗位" min-width="120">
        <template #default="scope">{{ handlerPosition(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="处置公司" min-width="120">
        <template #default="scope">{{ handlerCompany(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="处置设备/IP" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ traceValue(scope.row.processLog, 'device') || traceValue(scope.row.processLog, 'ip') || '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="420" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
          <template v-if="canHandleRiskEvent">
            <el-button size="small" type="warning" @click="markStatus(scope.row, 'PROCESSING')">标记处理中</el-button>
            <el-button size="small" type="success" @click="markStatus(scope.row, 'RESOLVED')">标记已处置</el-button>
            <el-button size="small" type="info" @click="markStatus(scope.row, 'IGNORED')">标记已忽略</el-button>
          </template>
          <template v-if="canHandleRiskEvent">
            <el-button size="small" @click="editEvent(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteEvent(scope.row.id)">删除</el-button>
          </template>
          <span v-else class="cell">仅查看</span>
        </template>
      </el-table-column>
    </el-table>
    <div style="display:flex;justify-content:flex-end;margin-top:16px;">
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

    <el-drawer v-model="showDetail" title="风险事件详情" size="48%">
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="事件ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">{{ detailRow.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">{{ levelText(detailRow.level) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="事件时间">{{ formatTime(detailRow.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detailRow.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="规则/关联日志" :span="2">{{ eventRuleText(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="处置账号">{{ handlerName(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="处置角色">{{ handlerRole(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="处置部门">{{ handlerDepartment(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="处置岗位">{{ handlerPosition(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="处置公司" :span="2">{{ handlerCompany(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="处置记录" :span="2">
          <pre style="white-space: pre-wrap; word-break: break-word; margin: 0;">{{ detailRow.processLog || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <el-dialog v-model="showAdd" title="新增风险事件">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-alert title="处置人将自动记录为当前操作账号" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form-item label="类型" prop="type"><el-input v-model="addForm.type" /></el-form-item>
        <el-form-item label="风险等级" prop="level">
          <el-select v-model="addForm.level" style="width: 100%">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="addForm.status" style="width: 100%">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已处置" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置记录"><el-input v-model="addForm.processLog" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addEvent">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showEdit" title="编辑风险事件">
      <el-form :model="editForm" :rules="rules" ref="editFormRef">
        <el-alert title="保存后将自动刷新处置人为当前操作账号" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form-item label="类型" prop="type"><el-input v-model="editForm.type" /></el-form-item>
        <el-form-item label="风险等级" prop="level">
          <el-select v-model="editForm.level" style="width: 100%">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已处置" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置记录"><el-input v-model="editForm.processLog" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updateEvent">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';
import { hasPermissionByUser } from '../utils/permission';
import { hasAnyRole } from '../utils/roleBoundary';

const userStore = useUserStore();
const events = ref([]);
const userDirectory = ref(new Map());
const loading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const showDetail = ref(false);
const saving = ref(false);
const addForm = ref({ type: '', level: 'MEDIUM', status: 'OPEN', processLog: '' });
const editForm = ref({});
const detailRow = ref(null);
const query = ref({ type: '', level: '', status: '', keyword: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const addFormRef = ref();
const editFormRef = ref();
const pagedEvents = computed(() => {
  const start = (pagination.value.current - 1) * pagination.value.pageSize;
  return filteredEvents.value.slice(start, start + pagination.value.pageSize);
});
const filteredEvents = computed(() => {
  return events.value.filter(item => {
    const levelMatched = !query.value.level || normalizeLevel(item?.level) === query.value.level;
    const statusMatched = !query.value.status || normalizeStatus(item) === query.value.status;
    const keyword = String(query.value.keyword || '').trim().toLowerCase();
    const keywordMatched = !keyword
      || eventDetail(item).toLowerCase().includes(keyword)
      || eventRuleText(item).toLowerCase().includes(keyword);
    return levelMatched && statusMatched && keywordMatched;
  });
});
const currentUser = computed(() => userStore.userInfo || {});
const canViewRiskEvent = computed(() => {
  const user = currentUser.value;
  return hasAnyRole(user, ['ADMIN', 'SECOPS'])
    || hasPermissionByUser(user, 'risk:event:view')
    || hasPermissionByUser(user, 'risk:event:handle');
});
const canHandleRiskEvent = computed(() => {
  const user = currentUser.value;
  return hasAnyRole(user, ['SECOPS']) || hasPermissionByUser(user, 'risk:event:handle');
});
const rules = {
  type: [{ required: true, message: '类型不能为空', trigger: 'blur' }],
  level: [{ required: true, message: '风险等级不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'blur' }]
};
async function fetchEvents() {
  if (!canViewRiskEvent.value) {
    events.value = [];
    ElMessage.warning('当前身份无权查看风险事件');
    return;
  }
  loading.value = true;
  try {
    await ensureUserDirectory();
    const res = await request.get('/risk-event/list', { params: query.value });
    events.value = Array.isArray(res) ? res : [];
    pagination.value.total = filteredEvents.value.length;
    pagination.value.current = 1;
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
    events.value = [];
    pagination.value.total = 0;
  } finally {
    loading.value = false;
  }
}

function onPageChange(page) {
  pagination.value.current = page;
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
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

function resetQuery() {
  query.value = { type: '', level: '', status: '', keyword: '' };
  pagination.value.current = 1;
  fetchEvents();
}

function openAdd() {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能新增');
    return;
  }
  addForm.value = { type: '', level: 'MEDIUM', status: 'OPEN', processLog: '' };
  showAdd.value = true;
}
async function addEvent() {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能新增');
    return;
  }
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/risk-event/add', addForm.value);
      ElMessage.success('保存成功');
      showAdd.value = false;
      fetchEvents();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}
function editEvent(row) {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能编辑');
    return;
  }
  editForm.value = {
    ...row,
    level: normalizeLevel(row?.level),
    status: normalizeStatus(row),
  };
  showEdit.value = true;
}

function openDetail(row) {
  detailRow.value = { ...row };
  showDetail.value = true;
}

function cleanProcessLog(value) {
  const text = String(value || '');
  return text.replace(/\s*\[TRACE\s+[^\]]+\]/gi, ' ').replace(/\s+/g, ' ').trim();
}

function normalizeLevel(value) {
  const text = String(value || '').trim().toUpperCase();
  if (!text) return 'MEDIUM';
  if (text === 'LOW' || text === 'MEDIUM' || text === 'HIGH' || text === 'CRITICAL') return text;
  if (text === 'L1') return 'LOW';
  if (text === 'L2') return 'MEDIUM';
  if (text === 'L3') return 'HIGH';
  if (text === 'L4') return 'CRITICAL';
  return 'MEDIUM';
}

function levelText(value) {
  const normalized = normalizeLevel(value);
  if (normalized === 'LOW') return '低';
  if (normalized === 'MEDIUM') return '中';
  if (normalized === 'HIGH') return '高';
  return '严重';
}

function levelTagType(value) {
  const normalized = normalizeLevel(value);
  if (normalized === 'LOW') return 'info';
  if (normalized === 'MEDIUM') return 'warning';
  if (normalized === 'HIGH') return 'danger';
  return 'danger';
}

function normalizeStatus(row) {
  const raw = String(row?.status || '').trim().toUpperCase();
  if (raw === 'OPEN' || raw === 'PENDING') return 'OPEN';
  if (raw === 'PROCESSING' || raw === 'IN_PROGRESS') return 'PROCESSING';
  if (raw === 'RESOLVED' || raw === 'CLOSED' || raw === 'DONE') return 'RESOLVED';
  if (raw === 'IGNORED' || raw === 'SKIPPED') return 'IGNORED';
  const log = cleanProcessLog(row?.processLog).toLowerCase();
  if (!log) return 'OPEN';
  if (/(忽略|ignore)/.test(log)) return 'IGNORED';
  if (/(处置|完成|resolved|closed|处理完成)/.test(log)) return 'RESOLVED';
  return 'PROCESSING';
}

function statusText(row) {
  const normalized = normalizeStatus(row);
  if (normalized === 'OPEN') return '待处理';
  if (normalized === 'PROCESSING') return '处理中';
  if (normalized === 'RESOLVED') return '已处置';
  return '已忽略';
}

function statusTagType(row) {
  const normalized = normalizeStatus(row);
  if (normalized === 'OPEN') return 'warning';
  if (normalized === 'PROCESSING') return '';
  if (normalized === 'RESOLVED') return 'success';
  return 'info';
}

function parseTraceEntries(value) {
  const text = String(value || '');
  const entries = [];
  const regex = /\[TRACE\s+([^\]]+)\]/gi;
  let match;
  while ((match = regex.exec(text)) !== null) {
    const pairs = String(match[1] || '').split(/\s+/).filter(Boolean);
    const map = {};
    pairs.forEach(part => {
      const [key, ...rest] = part.split('=');
      if (!key || rest.length === 0) return;
      map[key] = rest.join('=').trim();
    });
    entries.push(map);
  }
  return entries;
}

function latestTrace(row) {
  const entries = parseTraceEntries(row?.processLog);
  if (entries.length === 0) return null;
  return entries[entries.length - 1];
}

function handlerName(row) {
  const trace = latestTrace(row);
  if (trace?.operator && trace.operator !== '-') return trace.operator;
  return userNameById(row?.handlerId);
}

function handlerRole(row) {
  const trace = latestTrace(row);
  if (trace?.role && trace.role !== '-') return trace.role;
  return roleById(row?.handlerId);
}

function handlerDepartment(row) {
  const trace = latestTrace(row);
  if (trace?.department && trace.department !== '-') return trace.department;
  return departmentById(row?.handlerId);
}

function handlerPosition(row) {
  const trace = latestTrace(row);
  if (trace?.position && trace.position !== '-') return trace.position;
  return positionById(row?.handlerId);
}

function handlerCompany(row) {
  const trace = latestTrace(row);
  if (trace?.companyId && trace.companyId !== '-') return trace.companyId;
  return companyById(row?.handlerId);
}

function eventRuleText(row) {
  const related = row?.relatedLogId != null ? `relatedLogId=${row.relatedLogId}` : '';
  const audit = row?.auditLogIds ? `auditLogIds=${row.auditLogIds}` : '';
  const values = [related, audit].filter(Boolean);
  return values.length > 0 ? values.join(' | ') : '-';
}

function eventDetail(row) {
  const detail = cleanProcessLog(row?.processLog);
  return detail || '无处置备注';
}

function traceValue(processLog, key) {
  const text = String(processLog || '');
  const match = text.match(new RegExp(`${key}=([^\] ]+)`, 'i'));
  return match?.[1] || '';
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}
async function updateEvent() {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能编辑');
    return;
  }
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/risk-event/update', {
        ...editForm.value,
        level: normalizeLevel(editForm.value.level),
        status: normalizeStatus(editForm.value),
      });
      ElMessage.success('更新成功');
      showEdit.value = false;
      fetchEvents();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}

async function markStatus(row, status) {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能处置');
    return;
  }
  try {
    const label = status === 'PROCESSING' ? '处理中' : status === 'RESOLVED' ? '已处置' : '已忽略';
    await ElMessageBox.confirm(`确认将该事件标记为${label}吗？`, '提示', { type: 'warning' });
    const nextLog = cleanProcessLog(row?.processLog);
    const appended = nextLog ? `${nextLog}；状态更新为${label}` : `状态更新为${label}`;
    await request.post('/risk-event/update', {
      ...row,
      level: normalizeLevel(row?.level),
      status,
      processLog: appended,
    });
    ElMessage.success('状态更新成功');
    fetchEvents();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '状态更新失败');
    }
  }
}
async function deleteEvent(id) {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该事件吗？', '提示', { type: 'warning' });
    await request.post('/risk-event/delete', { id });
    ElMessage.success('删除成功');
    fetchEvents();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchEvents();
</script>
