<template>
  <el-card>
    <h2>治理观测总览</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card>
          <h4>风险趋势（近7天）</h4>
          <pre>{{ riskTrend }}</pre>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <h4>告警统计</h4>
          <pre>{{ alertStats }}</pre>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <h4>AI使用分析（近30天）</h4>
          <pre>{{ aiUsage }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';

const loading = ref(false);
const riskTrend = ref({});
const alertStats = ref({});
const aiUsage = ref({});

async function loadAll() {
  loading.value = true;
  try {
    const [workbench, alerts, aiSummary] = await Promise.all([
      request.get('/dashboard/workbench'),
      request.get('/alert-center/stats'),
      request.get('/ai/monitor/summary'),
    ]);

    riskTrend.value = {
      labels: workbench?.trend?.labels || [],
      riskSeries: workbench?.trend?.riskSeries || [],
      auditSeries: workbench?.trend?.auditSeries || [],
      aiCallSeries: workbench?.trend?.aiCallSeries || [],
      forecastNextDay: workbench?.trend?.forecastNextDay ?? 0,
    };

    alertStats.value = alerts || {};
    aiUsage.value = {
      window: '近30天',
      models: Array.isArray(aiSummary) ? aiSummary : [],
    };
  } catch (err) {
    ElMessage.error(err?.message || '加载治理观测失败');
  } finally {
    loading.value = false;
  }
}

loadAll();
</script>
