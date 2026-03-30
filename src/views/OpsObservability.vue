<template>
  <el-card>
    <h2>运维观测</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="天数"><el-input-number v-model="days" :min="1" :max="30" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button></el-form-item>
    </el-form>

    <el-row :gutter="16">
      <el-col :span="8"><el-card><h4>HTTP 指标</h4><pre>{{ httpMetrics }}</pre></el-card></el-col>
      <el-col :span="8"><el-card><h4>关键任务</h4><pre>{{ keyTasks }}</pre></el-card></el-col>
      <el-col :span="8"><el-card><h4>慢查询摘要</h4><pre>{{ slowQueries }}</pre></el-card></el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <h4>Web Vitals 汇总</h4>
      <pre>{{ webVitals }}</pre>
    </el-card>

    <el-card style="margin-top:16px">
      <h4>接口历史</h4>
      <pre>{{ httpHistory }}</pre>
    </el-card>
  </el-card>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';

const loading = ref(false);
const days = ref(7);
const httpMetrics = ref({});
const keyTasks = ref({});
const slowQueries = ref({});
const webVitals = ref({});
const httpHistory = ref({});

async function loadAll() {
  loading.value = true;
  try {
    const [http, key, slow, vitals, history] = await Promise.all([
      request.get('/ops-metrics/http'),
      request.get('/ops-metrics/key-tasks'),
      request.get('/ops-metrics/slow-queries', { params: { days: days.value } }),
      request.get('/ops-metrics/web-vitals/summary', { params: { days: days.value } }),
      request.get('/ops-metrics/http-history', { params: { days: days.value } }),
    ]);
    httpMetrics.value = http || {};
    keyTasks.value = key || {};
    slowQueries.value = slow || {};
    webVitals.value = vitals || {};
    httpHistory.value = history || {};
  } catch (err) {
    ElMessage.error(err?.message || '加载运维观测失败');
  } finally {
    loading.value = false;
  }
}

loadAll();
</script>
