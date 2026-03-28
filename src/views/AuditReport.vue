<template>
  <div class="page-grid">
    <el-card class="card-glass">
      <div class="card-header">审计报告对比</div>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="fromDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="toDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结束日期"
          />
        </el-form-item>
        <el-button type="primary" :loading="comparing" @click="compareReport">对比统计</el-button>
      </el-form>

      <div class="report-summary" v-if="compareData">
        <article class="summary-item">
          <span>总操作数</span>
          <strong>{{ compareData.total }}</strong>
        </article>
        <article class="summary-item">
          <span>成功数</span>
          <strong class="success">{{ compareData.success }}</strong>
        </article>
        <article class="summary-item">
          <span>失败数</span>
          <strong class="danger">{{ compareData.fail }}</strong>
        </article>
      </div>
    </el-card>

    <el-card class="card-glass">
      <div class="card-header">生成审计报告</div>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="报告范围标识">
          <el-input v-model="reportRange" placeholder="如 2026Q1 或 weekly" style="width: 220px" />
        </el-form-item>
        <el-button type="primary" :loading="generating" @click="generateReport">生成报告</el-button>
      </el-form>

      <el-alert
        v-if="generated"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      >
        <template #title>{{ generated.title }}</template>
        <div class="download-row">
          <span>下载链接：</span>
          <a :href="generated.downloadUrl" target="_blank" rel="noopener noreferrer">{{ generated.downloadUrl }}</a>
        </div>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';

const today = new Date();
const day = String(today.getDate()).padStart(2, '0');
const month = String(today.getMonth() + 1).padStart(2, '0');
const year = String(today.getFullYear());
const toDate = ref(`${year}-${month}-${day}`);
const fromDate = ref(`${year}-${month}-01`);

const comparing = ref(false);
const compareData = ref(null);

const reportRange = ref('');
const generating = ref(false);
const generated = ref(null);

async function compareReport() {
  if (!fromDate.value || !toDate.value) {
    ElMessage.warning('请选择完整的日期范围');
    return;
  }
  comparing.value = true;
  try {
    compareData.value = await request.get('/audit-report/compare', {
      params: { from: fromDate.value, to: toDate.value }
    });
  } catch (error) {
    ElMessage.error(error?.message || '审计报告对比失败');
  } finally {
    comparing.value = false;
  }
}

async function generateReport() {
  generating.value = true;
  try {
    generated.value = await request.get('/audit-report/generate', {
      params: reportRange.value ? { range: reportRange.value } : {}
    });
    ElMessage.success('审计报告已生成');
  } catch (error) {
    ElMessage.error(error?.message || '审计报告生成失败');
  } finally {
    generating.value = false;
  }
}
</script>

<style scoped>
.page-grid {
  display: grid;
  gap: 16px;
}

.card-header {
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--color-text);
}

.report-summary {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-item {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--color-border-light);
  background: rgba(255, 255, 255, 0.03);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-item span {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.summary-item strong {
  font-size: 24px;
  color: var(--color-text);
}

.summary-item .success {
  color: #34d399;
}

.summary-item .danger {
  color: #f87171;
}

.download-row {
  margin-top: 4px;
  word-break: break-all;
}

.download-row a {
  color: var(--color-primary-light);
}

@media (max-width: 900px) {
  .report-summary {
    grid-template-columns: 1fr;
  }
}
</style>
