<template>
  <el-card class="approval-center-card">
    <template #header>
      <div class="card-header">
        <div class="headline-block">
          <div class="headline-kicker">Governance Workflow</div>
          <h2>审批中心</h2>
          <p>统一处理治理变更待办与我发起申请</p>
        </div>
        <el-button v-if="canSubmit" type="primary" @click="openSubmit">发起治理变更</el-button>
      </div>
    </template>

    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="approval-tabs">
      <el-tab-pane v-if="showTodoTab" name="todo" label="待我审批" />
      <el-tab-pane v-if="showMineTab" name="mine" label="我发起" />
    </el-tabs>

    <div class="metrics-row">
      <div class="metric-card">
        <span>当前列表总数</span>
        <strong>{{ pagination.total }}</strong>
      </div>
      <div class="metric-card">
        <span>待审批</span>
        <strong>{{ pendingCount }}</strong>
      </div>
      <div class="metric-card">
        <span>已完成</span>
        <strong>{{ doneCount }}</strong>
      </div>
    </div>

    <el-form :inline="true" @submit.prevent class="filter-form">
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
          <el-option label="待审批" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="已撤回" value="revoked" />
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
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="申请标题/备注" style="width: 220px" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchList">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" style="width: 100%" empty-text="暂无记录" class="approval-table">
      <el-table-column prop="id" label="申请ID" width="110" />
      <el-table-column prop="title" label="申请标题" min-width="230" show-overflow-tooltip />
      <el-table-column prop="requestType" label="类型" min-width="170" />
      <el-table-column prop="riskLevel" label="风险" width="100" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="requesterId" label="发起人" width="100" />
      <el-table-column prop="approverId" label="审批人" width="100" />
      <el-table-column prop="createTime" label="发起时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="300" :fixed="isNarrowScreen ? false : 'right'">
        <template #default="scope">
          <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
          <el-button
            size="small"
            type="success"
            :disabled="activeTab !== 'todo' || scope.row.status !== 'pending'"
            @click="doApprove(scope.row, true)"
          >通过</el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="activeTab !== 'todo' || scope.row.status !== 'pending'"
            @click="doApprove(scope.row, false)"
          >驳回</el-button>
          <el-button
            size="small"
            type="warning"
            :disabled="activeTab !== 'mine' || scope.row.status !== 'pending'"
            @click="doRevoke(scope.row)"
          >撤回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-wrap">
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

    <el-drawer v-model="showDetail" title="申请详情" size="52%">
      <div v-if="detailLoading" class="detail-loading">加载中...</div>
      <div v-else-if="detail" class="detail-wrap">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="申请标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.requestType }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detail.requesterId }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ detail.approverId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ formatTime(detail.approvedAt) }}</el-descriptions-item>
          <el-descriptions-item label="变更理由" :span="2">{{ detail.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="影响说明" :span="2">{{ detail.impact || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">{{ detail.approveNote || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="diff-title">变更对比</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <div class="diff-panel">
              <div class="panel-title">变更前</div>
              <pre>{{ prettyJson(diffData.before) }}</pre>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="diff-panel">
              <div class="panel-title">变更后</div>
              <pre>{{ prettyJson(diffData.after) }}</pre>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-drawer>

    <el-dialog v-model="showSubmit" title="发起治理变更申请" width="640px">
      <el-form :model="submitForm" :rules="submitRules" ref="submitFormRef" label-width="100px">
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
            placeholder='例如: {"title":"新增审计角色","reason":"收敛权限边界","impact":"影响运维审批","name":"审计角色","code":"AUDIT_REVIEW"}'
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
import {
  approveRequest,
  fetchApprovalDetail,
  fetchApprovalDiff,
  fetchMyPage,
  fetchTodoPage,
  revokeRequest,
  submitGovernanceChange,
} from '../api/approvalCenter';
import { getSession } from '../utils/auth';
import { canReviewGovernanceChange, canSubmitGovernanceChange, normalizeRoleCode } from '../utils/roleBoundary';

const activeTab = ref('todo');
const query = ref({ status: 'pending', module: '', keyword: '' });
const rows = ref([]);
const loading = ref(false);
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const isNarrowScreen = ref(false);

const showDetail = ref(false);
const detailLoading = ref(false);
const detail = ref(null);
const diffData = ref({ before: {}, after: {} });

const showSubmit = ref(false);
const saving = ref(false);
const submitFormRef = ref();
const submitForm = ref({ module: 'ROLE', action: 'ADD', targetId: null, payloadJson: '' });

const canSubmit = computed(() => canSubmitGovernanceChange(getSession()?.user));
const canReview = computed(() => canReviewGovernanceChange(getSession()?.user));
const roleCode = computed(() => normalizeRoleCode(getSession()?.user));
const showTodoTab = computed(() => {
  if (roleCode.value === 'ADMIN') return false;
  if (roleCode.value === 'ADMIN_REVIEWER') return true;
  return canReview.value;
});
const showMineTab = computed(() => {
  if (roleCode.value === 'ADMIN') return true;
  if (roleCode.value === 'ADMIN_REVIEWER') return false;
  return canSubmit.value;
});
const pendingCount = computed(() => rows.value.filter(item => String(item?.status || '').toLowerCase() === 'pending').length);
const doneCount = computed(() => rows.value.filter(item => {
  const status = String(item?.status || '').toLowerCase();
  return status === 'approved' || status === 'rejected' || status === 'revoked';
}).length);

const submitRules = {
  module: [{ required: true, message: '模块不能为空', trigger: 'change' }],
  action: [{ required: true, message: '动作不能为空', trigger: 'change' }],
  payloadJson: [{ required: true, message: '变更载荷不能为空', trigger: 'blur' }],
};

function statusText(status) {
  const map = {
    pending: '待审批',
    approved: '已通过',
    rejected: '已驳回',
    revoked: '已撤回',
  };
  return map[String(status || '').toLowerCase()] || String(status || '-');
}

function statusTagType(status) {
  const value = String(status || '').toLowerCase();
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

function prettyJson(value) {
  try {
    return JSON.stringify(value || {}, null, 2);
  } catch {
    return '{}';
  }
}

function syncViewport() {
  isNarrowScreen.value = typeof window !== 'undefined' ? window.innerWidth < 992 : false;
}

function onTabChange() {
  if (activeTab.value === 'todo' && !showTodoTab.value && showMineTab.value) {
    activeTab.value = 'mine';
  }
  if (activeTab.value === 'mine' && !showMineTab.value && showTodoTab.value) {
    activeTab.value = 'todo';
  }
  pagination.value.current = 1;
  query.value.status = activeTab.value === 'todo' ? 'pending' : '';
  fetchList();
}

function resetFilter() {
  query.value = {
    status: activeTab.value === 'todo' ? 'pending' : '',
    module: '',
    keyword: '',
  };
  pagination.value.current = 1;
  fetchList();
}

function onPageChange(page) {
  pagination.value.current = page;
  fetchList();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchList();
}

function getListParams() {
  return {
    ...query.value,
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
  };
}

async function fetchList() {
  loading.value = true;
  try {
    let data;
    if (activeTab.value === 'todo') {
      if (!showTodoTab.value) {
        rows.value = [];
        pagination.value.total = 0;
        return;
      }
      data = await fetchTodoPage(getListParams());
    } else {
      if (!showMineTab.value) {
        rows.value = [];
        pagination.value.total = 0;
        return;
      }
      data = await fetchMyPage(getListParams());
    }
    rows.value = data?.list || [];
    pagination.value.total = Number(data?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载审批列表失败');
  } finally {
    loading.value = false;
  }
}

async function openDetail(row) {
  showDetail.value = true;
  detailLoading.value = true;
  detail.value = null;
  diffData.value = { before: {}, after: {} };
  try {
    const [detailRes, diffRes] = await Promise.all([
      fetchApprovalDetail(row.id),
      fetchApprovalDiff(row.id),
    ]);
    detail.value = detailRes;
    diffData.value = {
      before: diffRes?.before || {},
      after: diffRes?.after || {},
    };
  } catch (err) {
    ElMessage.error(err?.message || '加载详情失败');
  } finally {
    detailLoading.value = false;
  }
}

async function doApprove(row, approve) {
  if (!canReview.value) {
    ElMessage.error('当前身份无审批权限');
    return;
  }

  let note = '';
  try {
    const prompt = await ElMessageBox.prompt(`请输入${approve ? '通过' : '驳回'}意见`, '审批意见', {
      inputPlaceholder: '审批意见必填',
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputValidator: value => (!!value && value.trim().length > 0) || '审批意见不能为空',
    });
    note = String(prompt.value || '').trim();
  } catch {
    return;
  }

  let confirmPassword = '';
  try {
    const pwdPrompt = await ElMessageBox.prompt('请输入当前账号密码确认审批', '敏感操作二次校验', {
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
    await approveRequest({
      requestId: row.id,
      approve,
      note,
      confirmPassword,
    });
    ElMessage.success(`已${approve ? '通过' : '驳回'}该申请`);
    fetchList();
  } catch (err) {
    ElMessage.error(err?.message || '审批失败');
  }
}

async function doRevoke(row) {
  if (!canSubmit.value) {
    ElMessage.error('当前身份无撤回权限');
    return;
  }

  let note = '';
  try {
    const prompt = await ElMessageBox.prompt('请输入撤回原因（可选）', '撤回申请', {
      inputPlaceholder: '例如：变更计划调整',
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputValue: '',
    });
    note = String(prompt.value || '').trim();
  } catch {
    return;
  }

  let confirmPassword = '';
  try {
    const pwdPrompt = await ElMessageBox.prompt('请输入当前账号密码确认撤回', '敏感操作二次校验', {
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
    await revokeRequest({
      requestId: row.id,
      note,
      confirmPassword,
    });
    ElMessage.success('申请已撤回');
    fetchList();
  } catch (err) {
    ElMessage.error(err?.message || '撤回失败');
  }
}

function openSubmit() {
  if (!canSubmit.value) {
    ElMessage.error('当前身份无发起治理变更权限');
    return;
  }
  submitForm.value = { module: 'ROLE', action: 'ADD', targetId: null, payloadJson: '' };
  showSubmit.value = true;
}

function submitRequest() {
  if (!submitFormRef.value) return;
  submitFormRef.value.validate(async valid => {
    if (!valid) return;

    let confirmPassword = '';
    try {
      const pwdPrompt = await ElMessageBox.prompt('请输入当前账号密码确认发起', '敏感操作二次校验', {
        inputType: 'password',
        inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
        inputPlaceholder: '请输入密码',
        inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
      });
      confirmPassword = pwdPrompt.value;
    } catch {
      return;
    }

    saving.value = true;
    try {
      await submitGovernanceChange({
        ...submitForm.value,
        targetId: submitForm.value.targetId || null,
        payloadJson: submitForm.value.payloadJson,
        confirmPassword,
      });
      ElMessage.success('治理变更申请已提交');
      showSubmit.value = false;
      activeTab.value = 'mine';
      query.value.status = '';
      pagination.value.current = 1;
      fetchList();
    } catch (err) {
      ElMessage.error(err?.message || '提交治理变更申请失败');
    } finally {
      saving.value = false;
    }
  });
}

onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
  if (showTodoTab.value) {
    activeTab.value = 'todo';
  } else if (showMineTab.value) {
    activeTab.value = 'mine';
  }
  query.value.status = activeTab.value === 'todo' ? 'pending' : '';
  fetchList();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>

<style scoped>
.approval-center-card {
  border-radius: 18px;
  border: 1px solid var(--color-border-light);
  background:
    radial-gradient(circle at 12% -8%, rgba(93, 188, 255, 0.16), transparent 45%),
    radial-gradient(circle at 88% 0%, rgba(57, 112, 255, 0.12), transparent 38%),
    linear-gradient(155deg, rgba(8, 15, 28, 0.94), rgba(10, 20, 34, 0.9));
  box-shadow: 0 22px 44px rgba(3, 7, 18, 0.4);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.headline-block {
  display: grid;
  gap: 6px;
}

.headline-kicker {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(168, 198, 255, 0.88);
}

.card-header h2 {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  color: var(--color-text);
  text-align: left;
}

.card-header p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.page-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.approval-tabs {
  margin-bottom: 12px;
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.metric-card {
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(17, 30, 50, 0.88) 0%, rgba(13, 26, 44, 0.88) 100%);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-card span {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.metric-card strong {
  font-size: 24px;
  line-height: 1;
  color: var(--color-text);
}

.filter-form {
  background: linear-gradient(150deg, rgba(16, 28, 46, 0.84), rgba(13, 23, 38, 0.86));
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  padding: 12px 12px 2px;
  margin-bottom: 12px;
}

.approval-table {
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  overflow: hidden;
}

.detail-loading {
  padding: 14px;
  color: var(--color-text-secondary);
}

.detail-wrap {
  display: grid;
  gap: 14px;
}

.diff-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.diff-panel {
  border: 1px solid var(--color-border-light);
  border-radius: 10px;
  padding: 10px;
  background: linear-gradient(145deg, rgba(15, 27, 45, 0.86), rgba(10, 20, 34, 0.9));
}

.panel-title {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.diff-panel pre {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-soft);
}

@media (max-width: 900px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .metrics-row {
    grid-template-columns: 1fr;
  }
}
</style>
