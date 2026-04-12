<template>
  <el-card>
    <h2>治理变更复核</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
          <el-option label="待复核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已拒绝" value="rejected" />
        </el-select>
      </el-form-item>
      <el-form-item label="模块">
        <el-select v-model="query.module" placeholder="全部" clearable style="width: 150px">
          <el-option label="角色" value="ROLE" />
          <el-option label="权限" value="PERMISSION" />
          <el-option label="策略" value="POLICY" />
          <el-option label="用户" value="USER" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchRequests">查询</el-button>
        <el-button v-if="canSubmit" @click="openSubmit">发起治理变更</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="reviewerGuideText"
      :title="reviewerGuideText"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-table :data="requests" v-loading="loading" style="width: 100%" empty-text="暂无记录">
      <el-table-column prop="id" label="申请ID" width="120" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="action" label="动作" width="120" />
      <el-table-column prop="riskLevel" label="风险等级" width="120" />
      <el-table-column label="变更内容" min-width="260" show-overflow-tooltip>
        <template #default="scope">{{ summarizePayload(scope.row.payloadJson) }}</template>
      </el-table-column>
      <el-table-column prop="requesterId" label="申请人ID" width="120" />
      <el-table-column label="申请账号" min-width="130">
        <template #default="scope">{{ userNameById(scope.row.requesterId) }}</template>
      </el-table-column>
      <el-table-column prop="requesterRoleCode" label="申请角色" width="120" />
      <el-table-column label="申请部门" min-width="120">
        <template #default="scope">{{ traceValue(scope.row.payloadJson, 'department') || departmentById(scope.row.requesterId) }}</template>
      </el-table-column>
      <el-table-column label="申请岗位" min-width="120">
        <template #default="scope">{{ traceValue(scope.row.payloadJson, 'position') || positionById(scope.row.requesterId) }}</template>
      </el-table-column>
      <el-table-column label="申请公司" min-width="120">
        <template #default="scope">{{ traceValue(scope.row.payloadJson, 'companyId') || companyById(scope.row.requesterId) }}</template>
      </el-table-column>
      <el-table-column label="申请设备/IP" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ traceValue(scope.row.payloadJson, 'device') || traceValue(scope.row.payloadJson, 'deviceId') || '-' }}</template>
      </el-table-column>
      <el-table-column prop="approverId" label="复核人ID" width="120" />
      <el-table-column label="复核账号" min-width="130">
        <template #default="scope">{{ userNameById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column prop="approverRoleCode" label="复核角色" width="120" />
      <el-table-column label="复核部门" min-width="120">
        <template #default="scope">{{ departmentById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="复核岗位" min-width="120">
        <template #default="scope">{{ positionById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="复核公司" min-width="120">
        <template #default="scope">{{ companyById(scope.row.approverId) }}</template>
      </el-table-column>
      <el-table-column label="复核设备/IP" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ traceValue(scope.row.approveNote, 'device') || traceValue(scope.row.approveNote, 'deviceId') || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="approveNote" label="复核备注" min-width="200" />
      <el-table-column label="申请时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="复核时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.approvedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" :fixed="isNarrowScreen ? false : 'right'">
        <template #default="scope">
          <el-button
            size="small"
            type="success"
            :disabled="!canReview || scope.row.status !== 'pending' || isSelfRequest(scope.row)"
            @click="review(scope.row, true)"
          >通过</el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="!canReview || scope.row.status !== 'pending' || isSelfRequest(scope.row)"
            @click="review(scope.row, false)"
          >拒绝</el-button>
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

    <el-dialog v-model="showSubmit" title="发起治理变更申请" width="640px">
      <el-form :model="submitForm" :rules="rules" ref="submitFormRef" label-width="100px">
        <el-form-item label="模块" prop="module">
          <el-select v-model="submitForm.module" placeholder="请选择模块" style="width: 100%">
            <el-option label="角色" value="ROLE" />
            <el-option label="权限" value="PERMISSION" />
            <el-option label="策略" value="POLICY" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作" prop="action">
          <el-select v-model="submitForm.action" placeholder="请选择动作" style="width: 100%">
            <el-option label="新增" value="ADD" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input-number v-model="submitForm.targetId" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="变更载荷" prop="payloadJson">
          <el-input
            v-model="submitForm.payloadJson"
            type="textarea"
            :rows="7"
            placeholder='例如: {"name":"审计角色","code":"AUDIT_REVIEW","description":"治理审批用"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRequest">提交申请</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { getSession } from '../utils/auth';
import { canReviewGovernanceChange, canSubmitGovernanceChange } from '../utils/roleBoundary';
import { getUserDirectory } from '../utils/userDirectoryCache';

const requests = ref([]);
const userDirectory = ref(new Map());
const loading = ref(false);
const saving = ref(false);
const showSubmit = ref(false);
const query = ref({ status: '', module: '' });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const submitFormRef = ref();
const submitForm = ref({ module: 'ROLE', action: 'ADD', targetId: null, payloadJson: '' });
const isNarrowScreen = ref(false);

const canSubmit = computed(() => canSubmitGovernanceChange(getSession()?.user));
const canReview = computed(() => canReviewGovernanceChange(getSession()?.user));
const currentUserId = computed(() => getSession()?.user?.id);
const reviewerGuideText = computed(() => {
  if (!canReview.value) return '';
  return '当前账号为复核员，请在本页直接查看“待复核”治理变更并执行通过/拒绝。';
});

const rules = {
  module: [{ required: true, message: '模块不能为空', trigger: 'change' }],
  action: [{ required: true, message: '动作不能为空', trigger: 'change' }],
  payloadJson: [{ required: true, message: '变更载荷不能为空', trigger: 'blur' }],
};

function syncViewport() {
  isNarrowScreen.value = typeof window !== 'undefined' ? window.innerWidth < 992 : false;
}

function onPageChange(page) {
  pagination.value.current = page;
  fetchRequests();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchRequests();
}

function openSubmit() {
  if (!canSubmit.value) {
    ElMessage.error('当前身份无发起治理变更权限');
    return;
  }
  submitForm.value = { module: 'ROLE', action: 'ADD', targetId: null, payloadJson: '' };
  showSubmit.value = true;
}

function isSelfRequest(row) {
  if (!row) return false;
  return String(row.requesterId ?? '') === String(currentUserId.value ?? '');
}

async function fetchRequests() {
  loading.value = true;
  try {
    await ensureUserDirectory();
    const endpoint = canReview.value ? '/governance-change/todo-page' : '/governance-change/page';
    const res = await request.get(endpoint, {
      params: {
        ...query.value,
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
      },
    });
    requests.value = res?.list || [];
    pagination.value.total = Number(res?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载治理变更列表失败');
  } finally {
    loading.value = false;
  }
}

async function submitRequest() {
  if (!canSubmit.value) {
    ElMessage.error('当前身份无发起治理变更权限');
    return;
  }
  if (!submitFormRef.value) return;
  submitFormRef.value.validate(async valid => {
    if (!valid) return;
    let confirmPassword = '';
    try {
      const prompt = await ElMessageBox.prompt('请输入当前治理管理员密码确认发起', '敏感操作二次校验', {
        inputType: 'password',
        inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
        inputPlaceholder: '请输入密码',
        inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
      });
      confirmPassword = prompt.value;
    } catch {
      return;
    }

    saving.value = true;
    try {
      await request.post('/governance-change/submit', {
        ...submitForm.value,
        targetId: submitForm.value.targetId || null,
        payloadJson: submitForm.value.payloadJson,
        confirmPassword,
      });
      ElMessage.success('治理变更申请已提交');
      showSubmit.value = false;
      pagination.value.current = 1;
      fetchRequests();
    } catch (err) {
      ElMessage.error(err?.message || '提交治理变更申请失败');
    } finally {
      saving.value = false;
    }
  });
}

async function review(row, approve) {
  if (!canReview.value) {
    ElMessage.error('当前身份无治理变更复核权限');
    return;
  }
  if (isSelfRequest(row)) {
    ElMessage.warning('当前账号不能复核自己提交的治理变更');
    return;
  }
  let note = '';
  try {
    const notePrompt = await ElMessageBox.prompt(`请输入${approve ? '通过' : '拒绝'}备注`, '复核备注', {
      inputPlaceholder: '备注可为空',
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputValue: '',
    });
    note = notePrompt.value || '';
  } catch {
    return;
  }

  let confirmPassword = '';
  try {
    const pwdPrompt = await ElMessageBox.prompt('请输入当前账号密码确认复核操作', '敏感操作二次校验', {
      inputType: 'password',
      inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
    confirmPassword = pwdPrompt.value;
  } catch {
    return;
  }

  try {
    await request.post('/governance-change/approve', {
      requestId: row.id,
      approve,
      note,
      confirmPassword,
    });
    ElMessage.success(`已${approve ? '通过' : '拒绝'}该变更`);
    fetchRequests();
  } catch (err) {
    ElMessage.error(err?.message || '治理变更复核失败');
  }
}

async function ensureUserDirectory() {
  if (userDirectory.value.size > 0) {
    return;
  }
  try {
    userDirectory.value = await getUserDirectory();
  } catch {
    userDirectory.value = new Map();
  }
}

function escapeRegExp(value) {
  return String(value || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function userById(id) {
  if (id == null) return null;
  return userDirectory.value.get(String(id)) || null;
}

function userNameById(id) {
  const user = userById(id);
  return user?.username || '-';
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

function traceValue(raw, key) {
  const text = String(raw || '');
  if (text.startsWith('{')) {
    try {
      const obj = JSON.parse(text);
      const trace = obj?.trace;
      if (trace && trace[key] != null) {
        return String(trace[key]);
      }
    } catch {
      // Fall back to legacy TRACE text parsing.
    }
  }
  const safeKey = escapeRegExp(key);
  const match = text.match(new RegExp(`${safeKey}=([^\s\]]+)`));
  return match?.[1] || '';
}

function summarizePayload(payloadJson) {
  const text = String(payloadJson || '').trim();
  if (!text) return '-';
  try {
    const data = JSON.parse(text);
    const clone = { ...data };
    delete clone.trace;
    const compact = JSON.stringify(clone);
    return compact.length > 120 ? `${compact.slice(0, 120)}...` : compact;
  } catch {
    return text.length > 120 ? `${text.slice(0, 120)}...` : text;
  }
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}

if (canReview.value && !canSubmit.value) {
  query.value.status = 'pending';
  pagination.value.current = 1;
}

onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
  fetchRequests();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>