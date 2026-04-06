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
      <el-form @submit.prevent ref="formRef" :model="form" :rules="rules" class="scan-entry-form">
        <div class="scan-entry-grid">
          <el-form-item label="文件上传（默认）" prop="uploadToken" class="entry-item">
            <div class="upload-entry-box">
              <el-upload
                class="upload-box"
                drag
                :show-file-list="false"
                :before-upload="beforeUpload"
                :http-request="uploadFile"
                accept=".xlsx,.csv,.json,.db,.parquet"
              >
                <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
                <template #tip>
                  <div class="el-upload__tip">支持 .xlsx/.csv/.json/.db/.parquet，单文件不超过 200MB</div>
                </template>
              </el-upload>
              <div class="upload-meta" v-if="uploadedFile">
                <p>文件：{{ uploadedFile.fileName }}</p>
                <p>来源路径：{{ form.sourcePath }}</p>
                <p>设备IP：{{ uploadedFile.deviceIp || '-' }}</p>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="手动路径/表名（可选）" prop="sourcePath" class="entry-item">
            <div class="manual-entry-box">
              <el-select v-model="form.sourceType" style="width: 120px; margin-bottom: 10px">
                <el-option label="文件" value="file" />
                <el-option label="数据库" value="db" />
              </el-select>
              <el-input
                v-model="manualSourcePath"
                placeholder="例如: /data/existing/orders.parquet 或 db.schema.table"
                clearable
                @input="onManualPathInput"
              />
              <p class="entry-tip">上传文件与手动路径二选一必填。输入手动路径后将覆盖上传路径。</p>
            </div>
          </el-form-item>
        </div>

        <div class="action-row">
          <el-button type="primary" :loading="saving" :disabled="!canCreateTask" @click="create">创建任务</el-button>
        </div>
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
          <template #default="scope">
            {{ traceValue(scope.row.traceJson, 'deviceIp') || traceValue(scope.row.traceJson, 'device') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="敏感占比(%)" width="110">
          <template #default="scope">
            <span :class="sensitiveClass(scope.row.sensitiveRatio)">
              {{ scope.row.sensitiveRatio != null ? scope.row.sensitiveRatio + '%' : '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
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
      <div style="display:flex;justify-content:flex-end;margin-top:14px;">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="pagination.total"
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>
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
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
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

const form = ref({ sourceType: 'file', sourcePath: '', uploadToken: '' });
const manualSourcePath = ref('');
const uploadedFile = ref(null);
const list = ref([]);
const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const pagination = ref({ page: 1, pageSize: 10, total: 0 });
const rules = {
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  sourcePath: [{ required: true, message: '请上传文件或输入路径/表名', trigger: 'blur' }]
};

const SUPPORTED_EXTENSIONS = ['xlsx', 'csv', 'json', 'db', 'parquet'];
const MAX_SIZE = 200 * 1024 * 1024;

const canCreateTask = computed(() => String(form.value.sourcePath || '').trim().length > 0);

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
    const data = await request.get('/sensitive-scan/list', {
      params: {
        page: pagination.value.page,
        pageSize: pagination.value.pageSize,
      },
    });
    list.value = Array.isArray(data?.list) ? data.list : [];
    pagination.value.total = Number(data?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载扫描任务失败');
  } finally {
    loading.value = false;
  }
}

function onPageChange(page) {
  pagination.value.page = page;
  fetchList();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.page = 1;
  fetchList();
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}

async function create() {
  if (!canCreateTask.value) {
    ElMessage.warning('请先上传文件或输入路径/表名');
    return;
  }
  if (!formRef.value) return;
  formRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/sensitive-scan/create', {
        sourceType: form.value.sourceType,
        sourcePath: form.value.sourcePath,
      });
      ElMessage.success('创建成功');
      resetEntryForm();
      fetchList();
    } catch (err) {
      ElMessage.error(err?.message || '创建失败');
    } finally {
      saving.value = false;
    }
  });
}

function beforeUpload(file) {
  const name = String(file?.name || '');
  const dot = name.lastIndexOf('.');
  const ext = dot > -1 ? name.slice(dot + 1).toLowerCase() : '';
  if (!SUPPORTED_EXTENSIONS.includes(ext)) {
    ElMessage.error('仅支持 .xlsx/.csv/.json/.db/.parquet 文件');
    return false;
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('文件大小不能超过 200MB');
    return false;
  }
  if (/\.\./.test(name) || /[\\/:*?"<>|]/.test(name)) {
    ElMessage.error('文件名不合法，请重命名后上传');
    return false;
  }
  return true;
}

async function uploadFile(option) {
  const formData = new FormData();
  formData.append('file', option.file);
  try {
    const data = await request.post('/sensitive-scan/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const sourcePath = String(data?.sourcePath || '');
    if (!sourcePath) {
      throw new Error('上传成功但未返回有效路径');
    }
    form.value.sourceType = 'file';
    form.value.sourcePath = sourcePath;
    form.value.uploadToken = sourcePath;
    manualSourcePath.value = '';
    uploadedFile.value = {
      fileName: String(data?.fileName || option.file?.name || ''),
      sourcePath,
      deviceIp: String(data?.deviceIp || '-'),
      size: Number(data?.size || 0),
    };
    option.onSuccess && option.onSuccess(data);
    ElMessage.success('上传成功，可直接创建扫描任务');
  } catch (err) {
    option.onError && option.onError(err);
    ElMessage.error(err?.message || '文件上传失败');
  }
}

function onManualPathInput(value) {
  const safeValue = String(value || '').trim();
  if (!safeValue) {
    if (!uploadedFile.value) {
      form.value.sourcePath = '';
      form.value.uploadToken = '';
    }
    return;
  }
  form.value.sourcePath = safeValue;
  form.value.uploadToken = 'manual';
  uploadedFile.value = null;
}

function resetEntryForm() {
  form.value.sourceType = 'file';
  form.value.sourcePath = '';
  form.value.uploadToken = '';
  manualSourcePath.value = '';
  uploadedFile.value = null;
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

.scan-entry-form {
  display: grid;
  gap: 10px;
}

.scan-entry-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.entry-item {
  margin-bottom: 0;
}

.upload-entry-box,
.manual-entry-box {
  width: 100%;
  min-height: 206px;
  border: 1px solid rgba(131, 173, 245, 0.32);
  border-radius: 12px;
  background: rgba(10, 22, 42, 0.56);
  padding: 12px;
}

.upload-box {
  width: 100%;
}

.upload-meta {
  margin-top: 10px;
  border-top: 1px dashed rgba(131, 173, 245, 0.28);
  padding-top: 10px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.upload-meta p {
  margin: 4px 0;
}

.entry-tip {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.action-row {
  display: flex;
  justify-content: flex-end;
}

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

@media (max-width: 980px) {
  .scan-entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>

