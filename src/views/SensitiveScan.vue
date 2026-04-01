<template>
  <div class="page-grid">
    <el-card class="card-glass">
      <div class="card-header">敏感数据自动扫描</div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="用途说明：用于发现数据样本中的敏感字段（身份证、手机号等）。权限策略：治理管理员可全量管理，数据管理员仅管理本人提交任务。"
      />
      <el-form :inline="true" @submit.prevent ref="formRef" :model="form" :rules="rules">
        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="form.sourceType" style="width:140px">
            <el-option label="文件" value="file" />
            <el-option label="数据库" value="db" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径/表" prop="sourcePath">
          <el-input v-model="form.sourcePath" placeholder="/data/users.xlsx 或 db.table" />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="create">创建任务</el-button>
      </el-form>
      <el-table :data="list" style="margin-top:16px" v-loading="loading">
        <el-table-column prop="id" label="ID" width="250">
          <template #default="scope">
            <div class="cell nowrap">{{ scope.row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="类型" width="80" />
        <el-table-column prop="sourcePath" label="来源" />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column prop="companyId" label="公司" width="90" />
        <el-table-column prop="userId" label="提交人ID" width="110" />
        <el-table-column label="提交人账号" min-width="130">
          <template #default="scope">{{ traceValue(scope.row.traceJson, 'username') || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交角色" width="120">
          <template #default="scope">{{ traceValue(scope.row.traceJson, 'role') || '-' }}</template>
        </el-table-column>
        <el-table-column label="设备/IP" min-width="150" show-overflow-tooltip>
          <template #default="scope">{{ traceValue(scope.row.traceJson, 'device') || '-' }}</template>
        </el-table-column>
        <el-table-column label="敏感占比(%)" width="110">
          <template #default="scope">
            <span :class="sensitiveClass(scope.row.sensitiveRatio)">
              {{ scope.row.sensitiveRatio != null ? scope.row.sensitiveRatio + '%' : '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button size="small" type="primary" @click="run(scope.row.id)">执行</el-button>
            <el-button
              size="small"
              type="info"
              :disabled="!scope.row.reportData"
              @click="viewReport(scope.row)"
            >查看报告</el-button>
            <el-button size="small" type="danger" @click="remove(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- BERT 扫描报告详情对话框 -->
    <el-dialog
      v-model="reportVisible"
      title="BERT 敏感识别报告"
      width="680px"
      destroy-on-close
    >
      <div v-if="activeReport" class="report-dialog">
        <div class="report-summary">
          <div class="report-stat">
            <span class="stat-label">扫描样本总数</span>
            <strong>{{ activeReport.summary?.total ?? 0 }}</strong>
          </div>
          <div class="report-stat">
            <span class="stat-label">敏感字段占比</span>
            <strong :class="sensitiveClass(activeReport.summary?.ratio)">
              {{ activeReport.summary?.ratio ?? 0 }}%
            </strong>
          </div>
          <div class="report-stat">
            <span class="stat-label">识别到的敏感类别</span>
            <div class="tag-row">
              <el-tag
                v-for="cat in activeReport.summary?.sensitiveFields"
                :key="cat"
                size="small"
                type="danger"
                style="margin:2px"
              >{{ FIELD_LABELS[cat] || cat }}</el-tag>
              <span v-if="!activeReport.summary?.sensitiveFields?.length" class="no-tag">无</span>
            </div>
          </div>
        </div>
        <el-divider />
        <p class="report-section-title">逐条识别结果</p>
        <el-table :data="activeReport.results" size="small" max-height="300">
          <el-table-column label="文本摘要" min-width="200" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.text }}</template>
          </el-table-column>
          <el-table-column label="识别标签" width="120">
            <template #default="scope">
              <el-tag
                size="small"
                :type="scope.row.label === 'unknown' ? 'info' : 'danger'"
              >{{ FIELD_LABELS[scope.row.label] || scope.row.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="90">
            <template #default="scope">
              {{ scope.row.score != null ? (scope.row.score * 100).toFixed(1) + '%' : '—' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';

const FIELD_LABELS = {
  id_card: '身份证号',
  phone: '手机号',
  bank_card: '银行卡号',
  email: '电子邮箱',
  address: '地址',
  name: '姓名',
  unknown: '未识别',
};

const form = ref({ sourceType: 'file', sourcePath: '' });
const list = ref([]);
const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const rules = {
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  sourcePath: [{ required: true, message: '请输入路径/表', trigger: 'blur' }]
};

// 报告对话框
const reportVisible = ref(false);
const activeReport = ref(null);

function sensitiveClass(ratio) {
  if (ratio == null) return '';
  if (ratio >= 40) return 'ratio-high';
  if (ratio >= 15) return 'ratio-medium';
  return 'ratio-low';
}

function traceValue(raw, key) {
  const text = String(raw || '');
  if (text.startsWith('{')) {
    try {
      const obj = JSON.parse(text);
      return obj?.[key] != null ? String(obj[key]) : '';
    } catch {
      return '';
    }
  }
  return '';
}

async function fetchList() {
  loading.value = true;
  try {
    list.value = await request.get('/sensitive-scan/list');
  } catch (err) {
    ElMessage.error(err?.message || '加载扫描任务失败');
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!formRef.value) return;
  formRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/sensitive-scan/create', form.value);
      ElMessage.success('创建成功');
      fetchList();
    } catch (err) {
      ElMessage.error(err?.message || '创建失败');
    } finally {
      saving.value = false;
    }
  });
}

async function run(id) {
  try {
    const result = await request.post('/sensitive-scan/run', { id });
    ElMessage.success('任务已执行');
    // 更新列表中当前项以便立即显示报告按钮
    list.value = list.value.map(item => item.id === id ? { ...item, ...result } : item);
  } catch (err) {
    ElMessage.error(err?.message || '执行失败');
    fetchList();
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该任务吗？', '提示', { type: 'warning' });
    await request.post('/sensitive-scan/delete', { id });
    ElMessage.success('删除成功');
    fetchList();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}

async function viewReport(row) {
  try {
    let reportRaw = row.reportData || null;
    if (!reportRaw && row.id) {
      reportRaw = await request.get(`/sensitive-scan/${row.id}/report`);
    }
    const data = typeof reportRaw === 'string'
      ? JSON.parse(reportRaw)
      : reportRaw;
    if (!data) {
      ElMessage.info('暂无报告数据，请先执行扫描任务');
      return;
    }
    activeReport.value = data;
    reportVisible.value = true;
  } catch {
    ElMessage.error('报告数据解析失败');
  }
}

fetchList();
</script>

<style scoped>
.page-grid { display: grid; gap: 16px; }
.card-header { font-weight: 600; margin-bottom: 12px; }

.ratio-high { color: var(--color-danger); font-weight: 700; }
.ratio-medium { color: var(--color-warning); font-weight: 600; }
.ratio-low { color: var(--color-success); }

/* 报告对话框样式 */
.report-dialog { padding: 4px 0; }

.report-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 8px;
}

.report-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.report-stat strong {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}

.no-tag { font-size: 13px; color: var(--color-text-muted); }

.report-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin: 0 0 10px;
}
</style>

