<template>
  <el-card class="ops-observability-page">
    <div class="page-header">
      <div>
        <h2>治理观测总览</h2>
        <p class="page-subtitle">风险趋势、告警统计与 AI 调用分析均来自真实入库数据。{{ evidenceText }}</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <section class="cockpit-banner">
      <div class="cockpit-title-block">
        <span class="cockpit-eyebrow">AUDIT COCKPIT</span>
        <h3>审计驾驶舱</h3>
        <p>把风险压力、审计覆盖、AI 调用和租户健康聚合在一屏，值守席位可直接判断优先级与处置窗口。</p>
      </div>
      <div class="cockpit-signals">
        <article v-for="signal in cockpitSignals" :key="signal.title" class="signal-chip">
          <strong>{{ signal.title }}</strong>
          <span>{{ signal.value }}</span>
        </article>
      </div>
    </section>

    <section class="kpi-grid">
      <article v-for="kpi in cockpitKpis" :key="kpi.label" class="kpi-card">
        <span>{{ kpi.label }}</span>
        <strong>{{ kpi.value }}</strong>
        <em>{{ kpi.note }}</em>
      </article>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">风险趋势（近7天）</div>
          </template>
          <div v-if="hasRiskTrendData" ref="riskTrendChartRef" class="chart-canvas"></div>
          <el-empty v-else :description="riskTrendStateText" :image-size="72" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="6">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">告警统计</div>
          </template>
          <div v-if="hasAlertStatsData" ref="alertStatsChartRef" class="chart-canvas"></div>
          <el-empty v-else :description="alertStatsStateText" :image-size="72" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="6">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">AI使用分析（近30天）</div>
          </template>
          <div v-if="hasAiUsageData" ref="aiUsageChartRef" class="chart-canvas"></div>
          <el-empty v-else :description="aiUsageStateText" :image-size="72" />
        </el-card>
      </el-col>
    </el-row>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-top:16px"
      title="隐私盾告警明细已归并至“员工AI行为监控-隐私盾告警”模块，运维观测仅保留趋势与统计。"
    />

    <el-card class="health-card" shadow="never">
      <template #header>
        <div class="health-header">
          <div>
            <div class="card-header">租户健康自检</div>
            <p class="health-subtitle">融合权限缺口、审计覆盖率、隐私债务和风险压力，输出企业级治理健康状态。</p>
          </div>
          <div style="display:flex;gap:8px;">
            <el-button :loading="healthLoading" @click="loadHealthLatest">刷新报告</el-button>
            <el-button type="primary" :loading="healthRunning" @click="runHealthCheck">立即体检</el-button>
          </div>
        </div>
      </template>

      <el-skeleton :loading="healthLoading" :rows="4" animated>
        <div v-if="healthReport?.exists" class="health-grid">
          <article class="health-item">
            <span>状态</span>
            <el-tag :type="healthStatusType(healthReport.status)">{{ String(healthReport.status || '-').toUpperCase() }}</el-tag>
          </article>
          <article class="health-item">
            <span>审计覆盖率</span>
            <strong>{{ healthReport.auditCoverage ?? 0 }}%</strong>
          </article>
          <article class="health-item">
            <span>隐私债务</span>
            <strong>{{ healthReport.privacyDebtScore ?? 0 }}</strong>
          </article>
          <article class="health-item">
            <span>检查时间</span>
            <strong>{{ healthReport.checkedAt || '-' }}</strong>
          </article>
          <article class="health-item health-item-wide">
            <span>权限缺口</span>
            <div class="health-line">无角色用户：{{ parsedPermissionGaps.rolelessUserCount ?? 0 }}，已绑定角色用户：{{ parsedPermissionGaps.roleBoundUserCount ?? 0 }}</div>
            <div class="health-line" v-if="Array.isArray(parsedPermissionGaps.rolelessSample) && parsedPermissionGaps.rolelessSample.length > 0">
              样例：{{ parsedPermissionGaps.rolelessSample.join(', ') }}
            </div>
          </article>
          <article class="health-item health-item-wide">
            <span>风险概览</span>
            <div class="health-line">未闭环风险：{{ parsedRiskMetrics.openRiskEvents ?? 0 }}，待处理安全事件：{{ parsedRiskMetrics.pendingSecurityEvents ?? 0 }}</div>
            <div class="health-line">近30天高危安全事件：{{ parsedRiskMetrics.highSecurityEvents30d ?? 0 }}，隐私事件：{{ parsedRiskMetrics.privacyEvents30d ?? 0 }}</div>
            <div class="health-line">近30天AI调用：{{ parsedRiskMetrics.aiCalls30d ?? 0 }}，综合风险分：{{ parsedRiskMetrics.riskScore ?? 0 }}</div>
          </article>
        </div>
        <el-empty v-else description="暂无健康报告，点击“立即体检”生成" :image-size="72" />
      </el-skeleton>
    </el-card>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import request from '../api/request';

const loading = ref(false);
const healthLoading = ref(false);
const healthRunning = ref(false);
const healthReport = ref(null);
const riskTrend = ref({ labels: [], riskSeries: [], auditSeries: [], aiCallSeries: [], forecastNextDay: 0 });
const alertStats = ref({});
const aiUsage = ref({ models: [] });
const moduleFailure = ref({ riskTrend: false, alertStats: false, aiUsage: false });

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

const riskTrendStateText = computed(() => {
  if (moduleFailure.value.riskTrend) return '风险趋势接口暂不可用';
  return '风险样本为空，暂无可绘制趋势';
});

const alertStatsStateText = computed(() => {
  if (moduleFailure.value.alertStats) return '告警统计接口暂不可用';
  return '告警统计为0，暂无可绘制数据';
});

const aiUsageStateText = computed(() => {
  if (moduleFailure.value.aiUsage) return 'AI使用分析接口暂不可用';
  return 'AI调用样本为空，暂无可绘制数据';
});

const parsedPermissionGaps = computed(() => parseJsonMaybe(healthReport.value?.permissionGaps));
const parsedRiskMetrics = computed(() => parseJsonMaybe(healthReport.value?.riskMetrics));
const aiModels = computed(() => (Array.isArray(aiUsage.value?.models) ? aiUsage.value.models : []));

const aiCallTotal = computed(() => aiModels.value.reduce((sum, item) => sum + Number(item?.total || 0), 0));

const aiTopModel = computed(() => {
  if (!aiModels.value.length) {
    return '-';
  }
  const sorted = [...aiModels.value].sort((a, b) => Number(b?.total || 0) - Number(a?.total || 0));
  return sorted[0]?.modelCode || '-';
});

const riskPressureScore = computed(() => {
  const pending = Number(alertStats.value?.pending || 0);
  const critical = Number(alertStats.value?.critical || 0);
  const high = Number(alertStats.value?.high || 0);
  const blocked = Number(alertStats.value?.blocked || 0);
  const score = pending + critical * 2 + Math.round(high * 1.2) - blocked;
  return Math.max(0, score);
});

const cockpitKpis = computed(() => {
  const status = String(healthReport.value?.status || '-').toUpperCase();
  const coverage = healthReport.value?.auditCoverage != null ? `${healthReport.value.auditCoverage}%` : '-';
  return [
    {
      label: '风险压力分',
      value: String(riskPressureScore.value),
      note: '待处理 + 高危权重 - 已阻断'
    },
    {
      label: '租户健康状态',
      value: status,
      note: '健康体检的最新结论'
    },
    {
      label: '审计覆盖率',
      value: coverage,
      note: '近 30 天被审计用户占比'
    },
    {
      label: 'AI 调用总量',
      value: String(aiCallTotal.value),
      note: `热点模型：${aiTopModel.value}`
    }
  ];
});

const cockpitSignals = computed(() => {
  const checkedAt = healthReport.value?.checkedAt ? String(healthReport.value.checkedAt) : '未体检';
  const openRisk = Number(parsedRiskMetrics.value?.openRiskEvents || 0);
  const pendingSecurity = Number(parsedRiskMetrics.value?.pendingSecurityEvents || 0);
  return [
    {
      title: '待闭环风险',
      value: `${openRisk}`
    },
    {
      title: '待处理安全事件',
      value: `${pendingSecurity}`
    },
    {
      title: '最近体检时间',
      value: checkedAt
    }
  ];
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
  renderRiskTrendChart();
  renderAlertStatsChart();
  renderAiUsageChart();
}

function handleResize() {
  riskTrendChart?.resize();
  alertStatsChart?.resize();
  aiUsageChart?.resize();
}

async function loadAll() {
  loading.value = true;
  try {
    const [workbenchResult, alertsResult, aiSummaryResult] = await Promise.allSettled([
      request.get('/dashboard/workbench'),
      request.get('/alert-center/stats'),
      request.get('/ai/monitor/summary')
    ]);

    const workbench = workbenchResult.status === 'fulfilled' ? workbenchResult.value : {};
    const alerts = alertsResult.status === 'fulfilled' ? alertsResult.value : {};
    const aiSummary = aiSummaryResult.status === 'fulfilled' ? aiSummaryResult.value : [];
    moduleFailure.value = {
      riskTrend: workbenchResult.status === 'rejected',
      alertStats: alertsResult.status === 'rejected',
      aiUsage: aiSummaryResult.status === 'rejected'
    };

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

    const failedModules = [];
    if (workbenchResult.status === 'rejected') failedModules.push('风险趋势');
    if (alertsResult.status === 'rejected') failedModules.push('告警统计');
    if (aiSummaryResult.status === 'rejected') failedModules.push('AI使用分析');
    if (failedModules.length > 0) {
      ElMessage.warning(`${failedModules.join('、')} 暂不可用，已展示可用模块`);
    }

    await loadHealthLatest();
  } catch (err) {
    disposeCharts();
    ElMessage.error(err?.message || '加载治理观测失败');
  } finally {
    loading.value = false;
  }
}

async function loadHealthLatest() {
  healthLoading.value = true;
  try {
    const data = await request.get('/company/health/latest');
    healthReport.value = data || null;
  } catch (err) {
    healthReport.value = null;
    ElMessage.error(err?.message || '加载租户健康报告失败');
  } finally {
    healthLoading.value = false;
  }
}

async function runHealthCheck() {
  if (healthRunning.value) {
    return;
  }
  healthRunning.value = true;
  try {
    const data = await request.post('/company/health-check', {});
    healthReport.value = {
      ...data,
      exists: true,
    };
    ElMessage.success(`体检完成：${String(data?.status || 'healthy').toUpperCase()}`);
  } catch (err) {
    ElMessage.error(err?.message || '租户健康体检失败');
  } finally {
    healthRunning.value = false;
  }
}

function parseJsonMaybe(value) {
  if (!value) return {};
  if (typeof value === 'object') return value;
  try {
    return JSON.parse(String(value));
  } catch {
    return {};
  }
}

function healthStatusType(status) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'critical') return 'danger';
  if (normalized === 'warning') return 'warning';
  if (normalized === 'healthy') return 'success';
  return 'info';
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
  --cockpit-accent: var(--color-primary, #5f87ff);
  --cockpit-accent-2: var(--color-primary-light, #a9c4ff);
  --cockpit-ink: var(--color-text, #f7fbff);
  --cockpit-muted: var(--color-text-muted, #b8c7dd);
  font-family: 'Space Grotesk', 'IBM Plex Sans', 'Segoe UI', sans-serif;
  background:
    radial-gradient(circle at 10% 14%, rgba(95, 135, 255, 0.18), transparent 36%),
    radial-gradient(circle at 90% 16%, rgba(169, 196, 255, 0.16), transparent 34%),
    linear-gradient(180deg, rgba(10, 19, 33, 0.86), rgba(16, 27, 44, 0.9));
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

.cockpit-banner {
  border: 1px solid var(--color-border, rgba(169, 196, 255, 0.16));
  border-radius: 18px;
  padding: 18px;
  margin-bottom: 16px;
  background:
    linear-gradient(132deg, rgba(95, 135, 255, 0.2), rgba(31, 63, 138, 0.45) 52%, rgba(10, 19, 33, 0.92));
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}

.cockpit-title-block h3 {
  margin: 6px 0;
  color: var(--cockpit-ink);
  font-size: 30px;
  line-height: 1.1;
}

.cockpit-title-block p {
  margin: 0;
  color: var(--cockpit-muted);
  font-size: 13px;
}

.cockpit-eyebrow {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--cockpit-accent-2);
  font-weight: 700;
}

.cockpit-signals {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.signal-chip {
  border: 1px dashed rgba(169, 196, 255, 0.28);
  border-radius: 12px;
  padding: 10px 12px;
  display: grid;
  gap: 4px;
  background: rgba(10, 19, 33, 0.62);
}

.signal-chip strong {
  font-size: 12px;
  color: var(--cockpit-muted);
  font-weight: 600;
}

.signal-chip span {
  font-size: 13px;
  color: var(--cockpit-ink);
  font-weight: 700;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.kpi-card {
  border: 1px solid var(--color-border, rgba(169, 196, 255, 0.16));
  border-radius: 14px;
  padding: 12px;
  display: grid;
  gap: 6px;
  background: rgba(10, 19, 33, 0.72);
}

.kpi-card span {
  font-size: 12px;
  color: var(--cockpit-muted);
}

.kpi-card strong {
  font-size: 24px;
  line-height: 1.1;
  color: var(--cockpit-ink);
}

.kpi-card em {
  font-style: normal;
  color: var(--color-text-tertiary, #94a8c6);
  font-size: 12px;
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

.health-card {
  margin-top: 16px;
}

.health-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.health-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.health-item {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 12px;
  display: grid;
  gap: 8px;
}

.health-item span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.health-item strong {
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.health-item-wide {
  grid-column: span 2;
}

.health-line {
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .cockpit-banner {
    grid-template-columns: 1fr;
  }

  .cockpit-title-block h3 {
    font-size: 24px;
  }

  .kpi-grid {
    grid-template-columns: 1fr 1fr;
  }

  .chart-card {
    min-height: 320px;
  }

  .chart-canvas {
    height: 250px;
  }

  .health-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .health-grid {
    grid-template-columns: 1fr;
  }

  .health-item-wide {
    grid-column: span 1;
  }
}
</style>
