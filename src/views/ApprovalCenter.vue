<template>
  <el-card class="approval-center-card">
    <template v-if="hasVisibleTab">
    <div class="card-header">
      <div class="headline-block">
        <h2>审批中心</h2>
        <p>处理待办审批并跟进我发起的治理变更</p>
      </div>
      <el-button v-if="canSubmit" type="primary" @click="showSubmitHint">发起治理变更</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="approval-tabs">
      <el-tab-pane v-if="showTodoTab" name="todo" label="待审批" />
      <el-tab-pane v-if="showProcessedTab" name="processed" label="已处理" />
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
          <el-option label="草稿" value="draft" />
        </el-select>
      </el-form-item>
      <el-form-item label="模块">
        <el-select v-model="query.module" placeholder="全部" clearable style="width: 150px">
          <el-option label="角色" value="ROLE" />
          <el-option label="权限" value="PERMISSION" />
          <el-option label="策略" value="POLICY" />
          <el-option label="公司AI白名单" value="AI_WHITELIST" />
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

    <div class="table-wrap">
    <el-table :data="rows" v-loading="loading" style="width: 100%" empty-text="暂无记录" class="approval-table" table-layout="fixed">
      <el-table-column prop="id" label="申请ID" width="110" />
      <el-table-column label="申请标题" min-width="280" show-overflow-tooltip>
        <template #default="scope">{{ requestNameText(scope.row) }}</template>
      </el-table-column>
      <el-table-column prop="requestTypeLabel" label="类型" min-width="180" show-overflow-tooltip>
        <template #default="scope">{{ requestTypeText(scope.row) }}</template>
      </el-table-column>
      <el-table-column v-if="activeTab !== 'mine'" prop="requesterName" label="申请人" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.requesterName || scope.row.requesterId || '-' }}</template>
      </el-table-column>
      <el-table-column v-else prop="currentApproverName" label="当前审批人" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ currentApproverDisplay(scope.row) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" min-width="170">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="scope">
          <div class="row-actions">
            <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
            <template v-if="canShowTodoActions(scope.row)">
              <el-button
                size="small"
                type="success"
                @click="doApprove(scope.row, true)"
              >通过</el-button>
              <el-button
                size="small"
                type="danger"
                plain
                @click="doApprove(scope.row, false)"
              >驳回</el-button>
            </template>
            <template v-else-if="canShowRevokeAction(scope.row)">
              <el-button
                size="small"
                type="warning"
                @click="doRevoke(scope.row)"
              >撤回</el-button>
            </template>
            <template v-else-if="canShowDeleteAction(scope.row)">
              <el-button
                size="small"
                type="danger"
                plain
                @click="doDeleteDraft(scope.row)"
              >删除</el-button>
            </template>
          </div>
        </template>
      </el-table-column>
    </el-table>
    </div>

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
          <el-descriptions-item label="申请标题">{{ requestNameText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ requestTypeText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.requesterName || detail.requesterId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前审批人">{{ currentApproverDisplay(detail) }}</el-descriptions-item>
          <el-descriptions-item label="变更对象">{{ targetDisplayText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="目标ID">{{ detail.targetId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
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
    </template>

    <el-empty v-else description="当前身份暂无可见的审批功能" style="padding: 56px 0" />

  </el-card>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  approveRequest,
  deleteDraftRequest,
  fetchApprovalDetail,
  fetchApprovalDiff,
  fetchMyPage,
  fetchTodoPage,
  revokeRequest,
} from '../api/approvalCenter';
import { getSession } from '../utils/auth';
import { canReviewGovernanceChange, canSubmitGovernanceChange } from '../utils/roleBoundary';

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

const canSubmit = computed(() => canSubmitGovernanceChange(getSession()?.user));
const canReview = computed(() => canReviewGovernanceChange(getSession()?.user));
const showTodoTab = computed(() => canReview.value);
const showProcessedTab = computed(() => canReview.value);
const showMineTab = computed(() => canSubmit.value);
const hasVisibleTab = computed(() => showTodoTab.value || showProcessedTab.value || showMineTab.value);
const pendingCount = computed(() => rows.value.filter(item => String(item?.status || '').toLowerCase() === 'pending').length);
const doneCount = computed(() => rows.value.filter(item => {
  const status = String(item?.status || '').toLowerCase();
  return status === 'approved' || status === 'rejected' || status === 'revoked';
}).length);

function statusText(status) {
  const map = {
    pending: '待审批',
    approved: '已通过',
    rejected: '已驳回',
    revoked: '已撤回',
    draft: '草稿',
  };
  return map[String(status || '').toLowerCase()] || String(status || '-');
}

function normalizedStatus(status) {
  const value = String(status || '').trim().toLowerCase();
  if (value === '待审批' || value === '审批中' || value === 'pending') return 'pending';
  if (value === '已通过' || value === 'approved' || value === '通过') return 'approved';
  if (value === '已驳回' || value === '已拒绝' || value === 'rejected' || value === '拒绝') return 'rejected';
  if (value === '已撤回' || value === 'revoked' || value === '撤回') return 'revoked';
  if (value === '草稿' || value === 'draft') return 'draft';
  return value;
}

function canShowTodoActions(row) {
  return activeTab.value === 'todo' && normalizedStatus(row?.status) === 'pending';
}

function defaultStatusForTab(tabName) {
  if (tabName === 'todo') return 'pending';
  if (tabName === 'processed') return 'processed';
  return '';
}

function canShowRevokeAction(row) {
  return activeTab.value === 'mine' && normalizedStatus(row?.status) === 'pending';
}

function canShowDeleteAction(row) {
  return activeTab.value === 'mine' && normalizedStatus(row?.status) === 'draft';
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

function currentApproverDisplay(row) {
  if (!row) return '-';
  const status = normalizedStatus(row.status);
  if (status === 'pending' || status === 'draft') {
    return row.currentApproverName || row.approverName || '-';
  }
  return row.approverName || row.currentApproverName || '-';
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
  if (!hasVisibleTab.value) {
    rows.value = [];
    pagination.value.total = 0;
    return;
  }
  if (activeTab.value === 'todo' && !showTodoTab.value && showMineTab.value) {
    activeTab.value = 'mine';
  }
  if (activeTab.value === 'processed' && !showProcessedTab.value && showTodoTab.value) {
    activeTab.value = 'todo';
  }
  if (activeTab.value === 'mine' && !showMineTab.value && showTodoTab.value) {
    activeTab.value = 'todo';
  }
  pagination.value.current = 1;
  query.value.status = defaultStatusForTab(activeTab.value);
  fetchList();
}

function resetFilter() {
  query.value = {
    status: defaultStatusForTab(activeTab.value),
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

function showSubmitHint() {
  ElMessage.info('请前往角色/权限/策略管理页发起治理变更，审批结果会同步到本中心。');
}

function getListParams() {
  return {
    ...query.value,
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
  };
}

function safeText(value) {
  return String(value == null ? '' : value).trim();
}

function parsePayload(source) {
  if (source && typeof source === 'object' && !Array.isArray(source)) {
    return source;
  }
  if (!source) return {};
  try {
    const parsed = JSON.parse(source);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

const MODULE_TEXT_MAP = {
  ROLE: '角色',
  PERMISSION: '权限',
  POLICY: '策略',
  AI_WHITELIST: '公司AI白名单',
  USER: '用户',
};

const ACTION_TEXT_MAP = {
  ADD: '新增',
  CREATE: '新增',
  UPDATE: '变更',
  EDIT: '变更',
  DELETE: '删除',
  REMOVE: '删除',
  GRANT: '授权',
  REVOKE: '撤销',
};

function toUpperText(value) {
  return String(value || '').trim().toUpperCase();
}

function moduleText(value) {
  const upper = toUpperText(value);
  return MODULE_TEXT_MAP[upper] || String(value || '').trim();
}

function actionText(value) {
  const upper = toUpperText(value);
  return ACTION_TEXT_MAP[upper] || String(value || '').trim();
}

function requestTypeText(row) {
  if (!row) return '-';
  const label = String(row.requestTypeLabel || '').trim();
  if (label && /[\u4e00-\u9fa5]/.test(label)) {
    return label;
  }
  const requestType = String(row.requestType || '').trim();
  if (requestType && /[\u4e00-\u9fa5]/.test(requestType)) {
    return requestType;
  }
  const modText = moduleText(row.module);
  const actText = actionText(row.action);
  if (modText && actText) {
    return `${modText}${actText}请求`;
  }
  if (label) return label;
  if (requestType) return requestType;
  return '治理变更请求';
}

function requestNameText(row) {
  if (!row) return '-';
  const raw = String(row.title || '').trim();
  if (raw && /[\u4e00-\u9fa5]/.test(raw)) {
    return raw;
  }

  const payload = parsePayload(row.payload || row.payloadJson);
  const targetName = String(payload?.name || row.targetName || '').trim();
  const typeText = requestTypeText(row);

  if (/^governance\s*change\s*request/i.test(raw) || /^request\s*#/i.test(raw) || !raw) {
    return targetName ? `${typeText}（${targetName}）` : typeText;
  }

  return targetName ? `${typeText}（${targetName}）` : raw;
}

function targetDisplayText(row) {
  if (!row) return '-';
  const payload = parsePayload(row.payload || row.payloadJson);
  return (
    safeText(row.targetName)
    || safeText(payload?.name)
    || safeText(payload?.roleName)
    || safeText(payload?.code)
    || safeText(payload?.username)
    || (row.targetId ? `ID:${row.targetId}` : '-')
  );
}

function simpleHash(input) {
  const text = String(input || '');
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  }
  return hash.toString(16).toUpperCase().padStart(8, '0');
}

function toTimeNumber(value) {
  if (!value) return 0;
  const d = new Date(String(value).replace(' ', 'T'));
  return Number.isNaN(d.getTime()) ? 0 : d.getTime();
}

function normalizeTodoRow(raw) {
  const payload = parsePayload(raw?.payload || raw?.payloadJson);
  const trace = payload?.trace && typeof payload.trace === 'object' ? payload.trace : {};
  const traceUsername = safeText(trace.username) || `U${safeText(raw?.requesterId) || '-'}`;
  const traceRole = safeText(trace.role) || safeText(raw?.requesterRoleCode) || '-';
  const traceDevice = safeText(trace.device) || '-';
  const traceActor = `${traceUsername} / ${traceRole} / ${traceDevice}`;
  const tracePath = `C${safeText(raw?.companyId) || '-'} > ${safeText(raw?.module) || '-'} > ${safeText(raw?.action) || '-'} > T${safeText(raw?.targetId) || '-'} > R${safeText(raw?.id) || '-'}`;
  const dedupeKey = [
    safeText(raw?.companyId),
    safeText(raw?.module).toUpperCase(),
    safeText(raw?.action).toUpperCase(),
    safeText(raw?.targetId),
    safeText(raw?.requesterId),
    safeText(raw?.status).toLowerCase(),
    safeText(payload?.reason || raw?.reason),
  ].join('|');

  return {
    ...raw,
    payload,
    requestTypeLabel: requestTypeText({ ...raw, payload }),
    title: requestNameText({ ...raw, payload }),
    traceActor,
    tracePath,
    dedupeKey,
    traceFingerprint: `GC-${simpleHash(dedupeKey)}`,
  };
}

function dedupeTodoRows(list) {
  const uniqueByKey = new Map();
  (Array.isArray(list) ? list : []).forEach(item => {
    const row = normalizeTodoRow(item);
    const key = row.dedupeKey || String(row.id || '');
    const prev = uniqueByKey.get(key);
    if (!prev || toTimeNumber(row.updateTime) >= toTimeNumber(prev.updateTime)) {
      uniqueByKey.set(key, row);
    }
  });

  const deduped = Array.from(uniqueByKey.values()).sort((a, b) => toTimeNumber(b.updateTime) - toTimeNumber(a.updateTime));
  duplicateCollapsed.value = Math.max(0, (Array.isArray(list) ? list.length : 0) - deduped.length);
  return deduped;
}

async function fetchList() {
  loading.value = true;
  try {
    if (!hasVisibleTab.value) {
      rows.value = [];
      pagination.value.total = 0;
      return;
    }
    let data;
    if (activeTab.value === 'todo' || activeTab.value === 'processed') {
      if (!showTodoTab.value) {
        rows.value = [];
        pagination.value.total = 0;
        return;
      }
      data = await fetchTodoPage(getListParams());
      if ((!Array.isArray(data?.list) || data.list.length === 0) && query.value.status === 'pending') {
        data = await fetchTodoPage({ ...getListParams(), status: '待审批' });
      }
      const sourceList = Array.isArray(data?.list) ? data.list : [];
      rows.value = sourceList.map(item => normalizeTodoRow(item));
      pagination.value.total = Number(data?.total || sourceList.length);
    } else {
      if (!showMineTab.value) {
        rows.value = [];
        pagination.value.total = 0;
        return;
      }
      data = await fetchMyPage(getListParams());
      rows.value = (data?.list || []).map(item => normalizeTodoRow(item));
      pagination.value.total = Number(data?.total || 0);
    }
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
    detail.value = {
      ...detailRes,
      payload: parsePayload(detailRes?.payload || detailRes?.payloadJson),
    };
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

async function doDeleteDraft(row) {
  if (!canSubmit.value) {
    ElMessage.error('当前身份无删除权限');
    return;
  }

  try {
    await ElMessageBox.confirm('确认删除这条草稿申请吗？删除后无法恢复。', '删除草稿', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }

  try {
    await deleteDraftRequest(row.id);
    ElMessage.success('草稿已删除');
    fetchList();
  } catch (err) {
    ElMessage.error(err?.message || '删除失败');
  }
}

onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
  if (showTodoTab.value) {
    activeTab.value = 'todo';
  } else if (showProcessedTab.value) {
    activeTab.value = 'processed';
  } else if (showMineTab.value) {
    activeTab.value = 'mine';
  } else {
    activeTab.value = 'mine';
  }
  query.value.status = defaultStatusForTab(activeTab.value);
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
  justify-content: flex-start;
  gap: 16px;
  text-align: left;
}

.headline-block {
  display: grid;
  gap: 6px;
  text-align: left;
}

.card-header .el-button {
  margin-left: auto;
}

.approval-center-card :deep(.el-card__body) {
  text-align: left;
}

.headline-block h2 {
  margin: 0;
  font-size: 24px;
  color: #f3f6ff;
}

.headline-block p {
  margin: 0;
  color: rgba(223, 231, 255, 0.82);
  font-size: 13px;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.table-wrap {
  overflow-x: visible;
}

:deep(.approval-table) {
  min-width: 1660px;
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

.row-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: nowrap;
}

.trace-cell {
  display: grid;
  gap: 2px;
}

.trace-line {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trace-label {
  display: inline-block;
  width: 34px;
  color: var(--color-text-tertiary);
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
  overflow: visible;
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
