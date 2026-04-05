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
      <el-form-item label="权限ID">
        <el-input v-model="query.permissionId" placeholder="权限ID" style="width:140px" clearable />
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
    <el-table :data="logs" style="width: 100%; margin-top:12px" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column prop="permissionId" label="权限ID" width="110" />
      <el-table-column prop="permissionName" label="权限名称" width="180" show-overflow-tooltip />
      <el-table-column prop="assetId" label="资产ID" width="80" />
      <el-table-column prop="operation" label="操作类型" width="110" />
      <el-table-column prop="operationTime" label="操作时间" width="160" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="result" label="结果" width="80" />
      <el-table-column prop="riskLevel" label="风险级" width="80" />
      <el-table-column label="输入摘要" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.inputOverview || '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { hasPermission } from '../utils/permission';

const route = useRoute();
const logs = ref([]);
const loading = ref(false);
const query = ref(buildQueryFromRoute(route.query));
const canExport = hasPermission('audit:export');

function buildQueryFromRoute(routeQuery = {}) {
  return {
    userId: String(routeQuery.userId || '').trim(),
    operation: String(routeQuery.operation || '').trim(),
    permissionId: String(routeQuery.permissionId || '').trim(),
    from: '',
    to: '',
  };
}

function buildParams() {
  const p = {};
  if (query.value.userId) p.userId = query.value.userId;
  if (query.value.operation) p.operation = query.value.operation;
  if (query.value.permissionId) p.permissionId = query.value.permissionId;
  if (query.value.from) p.from = query.value.from;
  if (query.value.to) p.to = query.value.to;
  return p;
}

async function fetchLogs() {
  loading.value = true;
  try {
    // 后端使用 GET /api/audit-log/search，ES CriteriaQuery 按 userId/operation/时间段检索
    const res = await request.get('/audit-log/search', { params: buildParams() });
    logs.value = Array.isArray(res) ? res : [];
  } catch (err) {
    ElMessage.error(err?.message || '查询失败');
    logs.value = [];
  } finally {
    loading.value = false;
  }
}

function exportCsv() {
  if (!Array.isArray(logs.value) || logs.value.length === 0) {
    ElMessage.warning('当前无可导出的日志');
    return;
  }
  const headers = ['id', 'userId', 'permissionId', 'permissionName', 'assetId', 'operation', 'operationTime', 'ip', 'result', 'riskLevel', 'inputOverview'];
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
  query.value = { userId: '', operation: '', permissionId: '', from: '', to: '' };
  fetchLogs();
}

watch(
  () => route.query,
  (nextQuery) => {
    query.value = {
      ...query.value,
      userId: String(nextQuery.userId || '').trim(),
      operation: String(nextQuery.operation || '').trim(),
      permissionId: String(nextQuery.permissionId || '').trim(),
    };
    fetchLogs();
  }
);

fetchLogs();
</script>

<style scoped>
.card-header { font-weight: 600; margin-bottom: 16px; }
</style>

