<template>
  <el-card class="card-glass">
    <h2 class="card-header">审计日志查询</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="用户ID">
        <el-input v-model="query.userId" placeholder="用户ID" style="width:120px" clearable />
      </el-form-item>
      <el-form-item label="操作类型">
        <el-input v-model="query.operation" placeholder="如 login / export" style="width:160px" clearable />
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="query.from"
          type="datetime"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="开始时间"
          style="width:190px"
        />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-date-picker
          v-model="query.to"
          type="datetime"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="结束时间"
          style="width:190px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchLogs">查询</el-button>
        <el-button v-if="canExport" @click="exportCsv">导出CSV</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="pagedLogs" style="width: 100%; margin-top:12px" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户ID" width="90" />
      <el-table-column label="账号" width="120">
        <template #default="scope">{{ userNameById(scope.row.userId) }}</template>
      </el-table-column>
      <el-table-column label="账号 / 角色" width="220" show-overflow-tooltip>
        <template #default="scope">{{ accountRoleById(scope.row.userId) }}</template>
      </el-table-column>
      <el-table-column label="公司" width="100">
        <template #default="scope">{{ userCompanyById(scope.row.userId) }}</template>
      </el-table-column>
      <el-table-column prop="operation" label="操作类型" width="130" />
      <el-table-column prop="operationTime" label="操作时间" width="190" />
      <el-table-column prop="result" label="结果" width="90">
        <template #default="scope">
          <el-tag :type="resultTagType(scope.row.result)">{{ scope.row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="风险级" width="90">
        <template #default="scope">
          <el-tag :type="riskTagType(scope.row.riskLevel)">{{ scope.row.riskLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="输入摘要" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.inputOverview || '—' }}</template>
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
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { hasPermission } from '../utils/permission';

const route = useRoute();
const logs = ref([]);
const userDirectory = ref(new Map());
const loading = ref(false);
const query = ref(buildQueryFromRoute(route.query));
const canExport = hasPermission('audit:export');
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const pagedLogs = computed(() => {
  const start = (pagination.value.current - 1) * pagination.value.pageSize;
  return logs.value.slice(start, start + pagination.value.pageSize);
});

function buildQueryFromRoute(routeQuery = {}) {
  return {
    userId: String(routeQuery.userId || '').trim(),
    operation: String(routeQuery.operation || '').trim(),
    from: '',
    to: '',
  };
}

function buildParams() {
  const p = {};
  if (query.value.userId) p.userId = query.value.userId;
  if (query.value.operation) p.operation = query.value.operation;
  if (query.value.from) p.from = query.value.from;
  if (query.value.to) p.to = query.value.to;
  return p;
}

async function fetchLogs() {
  loading.value = true;
  try {
    await ensureUserDirectory();
    // 后端使用 GET /api/audit-log/search，ES CriteriaQuery 按 userId/operation/时间段检索
    const res = await request.get('/audit-log/search', { params: buildParams() });
    logs.value = normalizeAuditLogs(Array.isArray(res) ? res : []);
    pagination.value.total = logs.value.length;
    pagination.value.current = 1;
  } catch (err) {
    ElMessage.error(err?.message || '查询失败');
    logs.value = [];
    pagination.value.total = 0;
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

function normalizeOperation(value) {
  const raw = String(value || '').toLowerCase();
  if (!raw) return '访问';
  if (/(login|signin)/.test(raw)) return '登录';
  if (/(export|download)/.test(raw)) return '导出';
  if (/(update|edit|modify)/.test(raw)) return '修改';
  if (/(delete|remove)/.test(raw)) return '删除';
  if (/(approve|review)/.test(raw)) return '审批';
  return String(value || '访问');
}

function normalizeResult(value) {
  const raw = String(value || '').toLowerCase();
  if (['success', 'ok', 'pass', 'true', '1'].includes(raw)) return '成功';
  if (['fail', 'failed', 'error', 'deny', 'false', '0'].includes(raw)) return '失败';
  if (raw.includes('success') || raw.includes('ok')) return '成功';
  if (raw.includes('fail') || raw.includes('error') || raw.includes('deny')) return '失败';
  return '成功';
}

function normalizeRiskLevel(value, operation, result) {
  const raw = String(value || '').toLowerCase();
  if (['critical', 'high', 'medium', 'low'].includes(raw)) {
    if (raw === 'critical') return '高';
    if (raw === 'high') return '高';
    if (raw === 'medium') return '中';
    return '低';
  }
  if (result === '失败') return '中';
  if (operation === '删除' || operation === '导出') return '高';
  if (operation === '审批' || operation === '修改') return '中';
  return '低';
}

function normalizeAuditLogs(rows) {
  const seen = new Set();
  const normalized = [];
  for (const item of rows) {
    const operation = normalizeOperation(item?.operation);
    const result = normalizeResult(item?.result);
    const riskLevel = normalizeRiskLevel(item?.riskLevel, operation, result);
    const normalizedItem = {
      ...item,
      operation,
      result,
      riskLevel,
      operationTime: item?.operationTime || item?.createTime || item?.updateTime || '-',
      ip: item?.ip || item?.clientIp || '-',
      inputOverview: String(item?.inputOverview || item?.requestSummary || item?.input || item?.detail || '-'),
    };
    const signature = [
      normalizedItem.userId,
      normalizedItem.operation,
      normalizedItem.result,
      normalizedItem.operationTime,
      normalizedItem.assetId,
      normalizedItem.inputOverview,
    ].join('|');
    if (seen.has(signature)) continue;
    seen.add(signature);
    normalized.push(normalizedItem);
  }
  return normalized;
}

function userById(id) {
  if (id == null) return null;
  return userDirectory.value.get(String(id)) || null;
}

function userNameById(id) {
  return userById(id)?.username || '-';
}

function userRoleById(id) {
  return userById(id)?.roleCode || '-';
}

function accountRoleById(id) {
  const user = userById(id);
  if (!user) return '-';
  const username = user.username || '-';
  const role = user.roleCode || '-';
  return `${username} / ${role}`;
}

function userDepartmentById(id) {
  return userById(id)?.department || '-';
}

function userCompanyById(id) {
  const companyId = userById(id)?.companyId;
  return companyId != null ? String(companyId) : '-';
}

function resultTagType(value) {
  return String(value || '') === '失败' ? 'danger' : 'success';
}

function riskTagType(value) {
  if (String(value || '') === '高') return 'danger';
  if (String(value || '') === '中') return 'warning';
  return 'info';
}

function onPageChange(page) {
  pagination.value.current = page;
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
}

function exportCsv() {
  if (!Array.isArray(logs.value) || logs.value.length === 0) {
    ElMessage.warning('当前无可导出的日志');
    return;
  }
  const headers = ['id', 'userId', 'assetId', 'operation', 'operationTime', 'result', 'riskLevel', 'inputOverview'];
  const lines = [headers.join(',')];
  for (const item of logs.value) {
    const row = headers.map(key => {
      const raw = item?.[key] ?? '';
      const text = String(raw).replace(/"/g, '""');
      return `"${text}"`;
    });
    lines.push(row.join(','));
  }
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `audit-log-${Date.now()}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function resetQuery() {
  query.value = { userId: '', operation: '', from: '', to: '' };
  pagination.value.current = 1;
  fetchLogs();
}

watch(
  () => route.query,
  (nextQuery) => {
    query.value = {
      ...query.value,
      userId: String(nextQuery.userId || '').trim(),
      operation: String(nextQuery.operation || '').trim(),
    };
    pagination.value.current = 1;
    fetchLogs();
  }
);

fetchLogs();
</script>

<style scoped>
.card-header { font-weight: 600; margin-bottom: 16px; }
</style>

