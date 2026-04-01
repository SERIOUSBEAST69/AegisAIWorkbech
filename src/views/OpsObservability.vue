<template>
  <el-card class="ops-observability-page">
    <div class="page-header">
      <div>
        <h2>治理观测总览</h2>
        <p class="page-subtitle">风险趋势、告警统计与 AI 调用分析均来自真实入库数据。{{ evidenceText }}</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">风险趋势（近7天）</div>
          </template>
          <div v-if="hasRiskTrendData" ref="riskTrendChartRef" class="chart-canvas"></div>
          <el-empty v-else description="暂无记录" :image-size="72" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="6">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">告警统计</div>
          </template>
          <div v-if="hasAlertStatsData" ref="alertStatsChartRef" class="chart-canvas"></div>
          <el-empty v-else description="暂无记录" :image-size="72" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="6">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">AI使用分析（近30天）</div>
          </template>
          <div v-if="hasAiUsageData" ref="aiUsageChartRef" class="chart-canvas"></div>
          <el-empty v-else description="暂无记录" :image-size="72" />
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import request from '../api/request';

const loading = ref(false);
const riskTrend = ref({ labels: [], riskSeries: [], auditSeries: [], aiCallSeries: [], forecastNextDay: 0 });
const alertStats = ref({});
const aiUsage = ref({ models: [] });

const riskTrendChartRef = ref(null);
const alertStatsChartRef = ref(null);
const aiUsageChartRef = ref(null);

let riskTrendChart = null;
let alertStatsChart = null;
let aiUsageChart = null;

const hasRiskTrendData = computed(() => {
  const labels = riskTrend.value?.labels || [];
  return labels.length > 0;
});

const hasAlertStatsData = computed(() => {
  const stats = alertStats.value || {};
  return [stats.pending, stats.blocked, stats.critical, stats.high, stats.privacy, stats.anomaly, stats.shadowAi]
    .some(item => Number(item || 0) > 0);
});

const hasAiUsageData = computed(() => {
  const models = Array.isArray(aiUsage.value?.models) ? aiUsage.value.models : [];
  return models.some(item => Number(item?.total || 0) > 0);
});

const evidenceText = computed(() => {
  const trend = riskTrend.value || {};
  const days = Number(trend.trendWindowDays || 7);
  const riskSamples = Number(trend.riskEventSampleCount || 0);
  const auditSamples = Number(trend.auditLogSampleCount || 0);
  const modelSamples = Number(trend.modelStatSampleCount || 0);
  return `数据窗${days}天 · 风险样本${riskSamples} · 审计样本${auditSamples} · 调用样本${modelSamples}`;
});

function disposeCharts() {
  riskTrendChart?.dispose();
  alertStatsChart?.dispose();
  aiUsageChart?.dispose();
  riskTrendChart = null;
  alertStatsChart = null;
  aiUsageChart = null;
}

function renderRiskTrendChart() {
  if (!riskTrendChartRef.value || !hasRiskTrendData.value) return;
  riskTrendChart = riskTrendChart || echarts.init(riskTrendChartRef.value);

  const labels = riskTrend.value.labels || [];
  const riskSeries = riskTrend.value.riskSeries || [];
  const auditSeries = riskTrend.value.auditSeries || [];
  const aiCallSeries = riskTrend.value.aiCallSeries || [];

  riskTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['风险事件', '审计留痕', 'AI调用'] },
    grid: { left: 20, right: 20, top: 40, bottom: 20, containLabel: true },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [
      {
        name: '风险事件',
        type: 'line',
        smooth: true,
        data: riskSeries,
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '审计留痕',
        type: 'line',
        smooth: true,
        data: auditSeries
      },
      {
        name: 'AI调用',
        type: 'bar',
        data: aiCallSeries,
        barMaxWidth: 20
      }
    ]
  });
}

function renderAlertStatsChart() {
  if (!alertStatsChartRef.value || !hasAlertStatsData.value) return;
  alertStatsChart = alertStatsChart || echarts.init(alertStatsChartRef.value);

  const stats = alertStats.value || {};
  const data = [
    { name: '待处理', value: Number(stats.pending || 0) },
    { name: '已阻断', value: Number(stats.blocked || 0) },
    { name: '严重', value: Number(stats.critical || 0) },
    { name: '高危', value: Number(stats.high || 0) },
    { name: '隐私告警', value: Number(stats.privacy || 0) },
    { name: '异常告警', value: Number(stats.anomaly || 0) },
    { name: '影子AI', value: Number(stats.shadowAi || 0) }
  ].filter(item => item.value > 0);

  alertStatsChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        name: '告警',
        type: 'pie',
        radius: ['35%', '70%'],
        center: ['50%', '45%'],
        data,
        label: { formatter: '{b}: {c}' }
      }
    ]
  });
}

function renderAiUsageChart() {
  if (!aiUsageChartRef.value || !hasAiUsageData.value) return;
  aiUsageChart = aiUsageChart || echarts.init(aiUsageChartRef.value);

  const models = Array.isArray(aiUsage.value.models) ? aiUsage.value.models : [];
  aiUsageChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 20, right: 20, top: 30, bottom: 20, containLabel: true },
    xAxis: {
      type: 'category',
      data: models.map(item => item.modelCode || 'unknown'),
      axisLabel: { interval: 0, rotate: 30 }
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '调用次数',
        type: 'bar',
        data: models.map(item => Number(item.total || 0)),
        barMaxWidth: 24
      }
    ]
  });
}

function renderCharts() {
  if (!loading.value) {
    renderRiskTrendChart();
    renderAlertStatsChart();
    renderAiUsageChart();
  }
}

function handleResize() {
  riskTrendChart?.resize();
  alertStatsChart?.resize();
  aiUsageChart?.resize();
}

async function loadAll() {
  loading.value = true;
  try {
    const [workbench, alerts, aiSummary] = await Promise.all([
      request.get('/dashboard/workbench'),
      request.get('/alert-center/stats'),
      request.get('/ai/monitor/summary')
    ]);

    riskTrend.value = {
      labels: workbench?.trend?.labels || [],
      riskSeries: workbench?.trend?.riskSeries || [],
      auditSeries: workbench?.trend?.auditSeries || [],
      aiCallSeries: workbench?.trend?.aiCallSeries || [],
      forecastNextDay: workbench?.trend?.forecastNextDay ?? 0,
      riskEventSampleCount: workbench?.trend?.riskEventSampleCount ?? 0,
      auditLogSampleCount: workbench?.trend?.auditLogSampleCount ?? 0,
      modelStatSampleCount: workbench?.trend?.modelStatSampleCount ?? 0,
      trendWindowDays: workbench?.trend?.trendWindowDays ?? 7
    };
    alertStats.value = alerts || {};
    aiUsage.value = {
      models: Array.isArray(aiSummary) ? aiSummary : []
    };

    await nextTick();
    disposeCharts();
    renderCharts();
  } catch (err) {
    disposeCharts();
    ElMessage.error(err?.message || '加载治理观测失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadAll();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  disposeCharts();
});
</script>

<style scoped>
.ops-observability-page {
  border-radius: 14px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.chart-card {
  height: 100%;
  min-height: 360px;
}

.card-header {
  font-weight: 600;
}

.chart-canvas {
  width: 100%;
  height: 290px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .chart-card {
    min-height: 320px;
  }

  .chart-canvas {
    height: 250px;
  }
}
</style>
