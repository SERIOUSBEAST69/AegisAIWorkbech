<template>
  <el-card>
    <h2>风险事件管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="事件类型">
        <el-input v-model="query.type" placeholder="输入事件类型" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchEvents">查询</el-button>
        <el-button :disabled="!canHandleRiskEvent" @click="openAdd">新增事件</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="events" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="level" label="风险等级" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="处置记录" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ cleanProcessLog(scope.row.processLog) }}</template>
      </el-table-column>
      <el-table-column prop="handlerId" label="处置人ID" />
      <el-table-column label="处置账号" min-width="130">
        <template #default="scope">{{ userNameById(scope.row.handlerId) }}</template>
      </el-table-column>
      <el-table-column label="处置角色" min-width="120">
        <template #default="scope">{{ roleById(scope.row.handlerId) }}</template>
      </el-table-column>
      <el-table-column label="处置部门" min-width="120">
        <template #default="scope">{{ departmentById(scope.row.handlerId) }}</template>
      </el-table-column>
      <el-table-column label="处置岗位" min-width="120">
        <template #default="scope">{{ positionById(scope.row.handlerId) }}</template>
      </el-table-column>
      <el-table-column label="处置公司" min-width="120">
        <template #default="scope">{{ companyById(scope.row.handlerId) }}</template>
      </el-table-column>
      <el-table-column label="处置设备/IP" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ traceValue(scope.row.processLog, 'device') || traceValue(scope.row.processLog, 'ip') || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="scope">
          <template v-if="canHandleRiskEvent">
            <el-button size="small" @click="editEvent(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteEvent(scope.row.id)">删除</el-button>
          </template>
          <span v-else class="cell">仅查看</span>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="新增风险事件">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-alert title="处置人将自动记录为当前操作账号" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form-item label="类型" prop="type"><el-input v-model="addForm.type" /></el-form-item>
        <el-form-item label="风险等级" prop="level"><el-input v-model="addForm.level" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-input v-model="addForm.status" /></el-form-item>
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
        <el-form-item label="风险等级" prop="level"><el-input v-model="editForm.level" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-input v-model="editForm.status" /></el-form-item>
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
const saving = ref(false);
const addForm = ref({ type: '', level: '', status: '', processLog: '' });
const editForm = ref({});
const query = ref({ type: '' });
const addFormRef = ref();
const editFormRef = ref();
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
    events.value = res || [];
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
function openAdd() {
  if (!canHandleRiskEvent.value) {
    ElMessage.warning('当前身份仅可查看风险事件，不能新增');
    return;
  }
  addForm.value = { type: '', level: '', status: '', processLog: '' };
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
  editForm.value = { ...row };
  showEdit.value = true;
}

function cleanProcessLog(value) {
  const text = String(value || '');
  return text.replace(/\s*\[TRACE\s+[^\]]+\]/gi, ' ').replace(/\s+/g, ' ').trim();
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
      await request.post('/risk-event/update', editForm.value);
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
