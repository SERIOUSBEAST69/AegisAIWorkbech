<template>
  <el-card class="security-cockpit-page">
    <div class="page-header">
      <div>
        <h2>安全指挥驾驶舱</h2>
        <p>热力图、趋势、拓扑与实时流均来自真实入库告警数据，点击任意图形可下钻到明细。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="reloadAll">刷新全局</el-button>
      </div>
    </div>

    <section class="kpi-grid">
      <article class="kpi-card kpi-threat">
        <span>今日威胁总量</span>
        <strong>{{ overview.todayThreatTotal }}</strong>
      </article>
      <article class="kpi-card kpi-blocked">
        <span>已阻断告警</span>
        <strong>{{ overview.blockedTotal }}</strong>
      </article>
      <article class="kpi-card kpi-risk">
        <span>高风险人员</span>
        <strong>{{ overview.highRiskUsers }}</strong>
      </article>
      <article class="kpi-card kpi-pending">
        <span>未处置事件</span>
        <strong>{{ overview.unresolvedTotal }}</strong>
      </article>
    </section>

    <el-row :gutter="16" class="charts-row">
      <el-col :xs="24" :sm="24" :md="24" :lg="8">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">部门风险热力图（7天）</div>
          </template>
          <div ref="heatmapRef" class="chart-canvas"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="24" :lg="8">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">24小时告警状态趋势</div>
          </template>
          <div ref="trendRef" class="chart-canvas"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="24" :lg="8">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">风险传播拓扑（7天）</div>
          </template>
          <div ref="topologyRef" class="chart-canvas"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="stream-card" shadow="never">
      <template #header>
        <div class="card-header stream-title">
          <span>实时告警流（SSE）</span>
          <el-tag size="small" :type="streamConnected ? 'success' : 'warning'">
            {{ streamConnected ? '已连接' : '重连中' }}
          </el-tag>
        </div>
      </template>
      <el-table :data="realtimeAlerts" class="security-table" table-layout="fixed" max-height="320">
        <el-table-column prop="eventTime" label="发生时间" width="180" />
        <el-table-column prop="triggerUser" label="触发用户" width="130" />
        <el-table-column prop="eventType" label="事件类型" min-width="160" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="风险等级" width="110">
          <template #default="scope">
            <el-tag :type="severityTagType(scope.row.riskLevel)">{{ severityText(scope.row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceModule" label="来源模块" width="150" />
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="66%">
      <el-table :data="detailRows" class="security-table" table-layout="fixed" max-height="560">
        <el-table-column prop="eventTime" label="发生时间" width="180" />
        <el-table-column prop="triggerUser" label="用户" width="130" />
        <el-table-column prop="eventType" label="事件类型" min-width="150" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="等级" width="100">
          <template #default="scope">
            <el-tag :type="severityTagType(scope.row.riskLevel)">{{ severityText(scope.row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="事件标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <div class="row-actions">
              <el-button v-if="canShowPendingActions(scope.row) && canBlock" size="small" type="danger" @click="block(scope.row.eventId)">阻断</el-button>
              <el-button v-if="canShowPendingActions(scope.row) && canIgnore" size="small" @click="ignore(scope.row.eventId)">忽略</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts/core';
import { BarChart, GraphChart, LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import request from '../api/request';
import {
  fetchCockpitOverview,
  fetchDepartmentDetail,
  fetchDepartmentHeatmap,
  fetchHourlyTrend,
  fetchRecentAlerts,
  fetchRiskTopology,
  fetchTopologyDetail,
  openCockpitAlertStream,
} from '../api/securityCockpit';
import { useUserStore } from '../store/user';
import { canBlockThreatEvent, canIgnoreThreatEvent } from '../utils/roleBoundary';

echarts.use([LineChart, BarChart, GraphChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const userStore = useUserStore();
const canBlock = computed(() => canBlockThreatEvent(userStore.userInfo));
const canIgnore = computed(() => canIgnoreThreatEvent(userStore.userInfo));

const loading = ref(false);
const streamConnected = ref(false);

const overview = ref({ todayThreatTotal: 0, blockedTotal: 0, highRiskUsers: 0, unresolvedTotal: 0 });
const heatmap = ref([]);
const trend = ref({ hours: [], blockedSeries: [], pendingSeries: [], ignoredSeries: [], details: [] });
const topology = ref({ nodes: [], edges: [] });
const realtimeAlerts = ref([]);

const detailVisible = ref(false);
const detailTitle = ref('事件明细');
const detailRows = ref([]);

const heatmapRef = ref(null);
const trendRef = ref(null);
const topologyRef = ref(null);
let heatmapChart = null;
let trendChart = null;
let topologyChart = null;
let streamHandle = null;
let reconnectTimer = null;
let pollTimer = null;
let streamCursor = 0;

async function reloadAll() {
  loading.value = true;
  try {
    const [o, h, t, top, alerts] = await Promise.all([
      fetchCockpitOverview(),
      fetchDepartmentHeatmap(7),
      fetchHourlyTrend(24),
      fetchRiskTopology(7),
      fetchRecentAlerts(40),
    ]);
    overview.value = o || overview.value;
    heatmap.value = Array.isArray(h?.items) ? h.items : [];
    trend.value = t || trend.value;
    topology.value = top || topology.value;
    realtimeAlerts.value = Array.isArray(alerts) ? alerts : [];
    if (realtimeAlerts.value.length > 0) {
      streamCursor = Math.max(streamCursor, Number(realtimeAlerts.value[0]?.eventId || 0));
    }
    await nextTick();
    renderHeatmap();
    renderTrend();
    renderTopology();
  } catch (err) {
    ElMessage.error(err?.message || '加载安全驾驶舱数据失败');
  } finally {
    loading.value = false;
  }
}

function renderHeatmap() {
  if (!heatmapRef.value) return;
  if (!heatmapChart) {
    heatmapChart = echarts.init(heatmapRef.value);
    heatmapChart.on('click', params => {
      const department = String(params?.name || '').trim();
      if (department) {
        openDepartmentDetail(department);
      }
    });
  }
  const labels = heatmap.value.map(item => item.department);
  const values = heatmap.value.map(item => Number(item.riskScore || 0));
  const events = heatmap.value.map(item => Number(item.eventCount || 0));
  heatmapChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      formatter: params => {
        const row = Array.isArray(params) ? params[0] : null;
        if (!row) return '';
        const index = row.dataIndex;
        return `${row.axisValue}<br/>风险得分: ${values[index]}<br/>事件数: ${events[index]}`;
      },
    },
    grid: { left: 50, right: 12, top: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { rotate: 32, color: '#9fb7d8' },
      axisLine: { lineStyle: { color: '#476b92' } },
    },
    yAxis: {
      type: 'value',
      name: '风险得分',
      axisLabel: { color: '#9fb7d8' },
      splitLine: { lineStyle: { color: 'rgba(149, 179, 214, 0.2)' } },
    },
    series: [{
      type: 'bar',
      data: values,
      barWidth: '52%',
      itemStyle: {
        color: params => {
          const score = Number(params.value || 0);
          if (score >= 75) return '#f97316';
          if (score >= 45) return '#facc15';
          return '#22c55e';
        }
      }
    }],
  });
}

function renderTrend() {
  if (!trendRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendRef.value);
    trendChart.on('click', params => {
      const hour = params?.name;
      if (hour) {
        const bucket = (trend.value.details || []).find(item => item.hour === hour);
        detailTitle.value = `小时明细 - ${hour}`;
        detailRows.value = Array.isArray(bucket?.events) ? bucket.events : [];
        detailVisible.value = true;
      }
    });
  }
  trendChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: ['已阻断', '待处置', '已忽略'], top: 0, textStyle: { color: '#b6cbea' } },
    grid: { left: 40, right: 8, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: trend.value.hours || [],
      axisLabel: { color: '#9fb7d8', interval: 3 },
      axisLine: { lineStyle: { color: '#476b92' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9fb7d8' },
      splitLine: { lineStyle: { color: 'rgba(149, 179, 214, 0.2)' } },
    },
    series: [
      { name: '已阻断', type: 'line', smooth: true, data: trend.value.blockedSeries || [], lineStyle: { color: '#22c55e' } },
      { name: '待处置', type: 'line', smooth: true, data: trend.value.pendingSeries || [], lineStyle: { color: '#f97316' } },
      { name: '已忽略', type: 'line', smooth: true, data: trend.value.ignoredSeries || [], lineStyle: { color: '#94a3b8' } },
    ],
  });
}

function renderTopology() {
  if (!topologyRef.value) return;
  if (!topologyChart) {
    topologyChart = echarts.init(topologyRef.value);
    topologyChart.on('click', params => {
      if (params?.dataType !== 'edge') return;
      const sourceNode = topology.value.nodes.find(item => item.id === params.data.source);
      const targetNode = topology.value.nodes.find(item => item.id === params.data.target);
      if (!sourceNode || !targetNode) return;
      openTopologyDetail(sourceNode.label, targetNode.label);
    });
  }

  const nodes = (topology.value.nodes || []).map(item => ({
    id: item.id,
    name: item.label,
    value: Number(item.value || 1),
    symbolSize: Math.min(48, 12 + Number(item.value || 1) * 2),
    itemStyle: {
      color: item.type === 'source' ? '#38bdf8' : '#f97316',
      borderColor: '#cbd5e1',
      borderWidth: 1,
    },
  }));
  const links = (topology.value.edges || []).map(edge => ({
    source: edge.source,
    target: edge.target,
    value: Number(edge.count || 1),
    lineStyle: {
      width: Math.min(6, 1 + Number(edge.count || 1) * 0.4),
      color: edge.risk === 'critical' || edge.risk === 'high' ? '#ef4444' : '#facc15',
      opacity: 0.76,
    },
  }));

  topologyChart.setOption({
    tooltip: { trigger: 'item' },
    animationDurationUpdate: 700,
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: { repulsion: 220, edgeLength: [48, 110] },
      label: { show: true, color: '#dbeafe', fontSize: 11 },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [4, 8],
      data: nodes,
      links,
    }],
  });
}

async function openDepartmentDetail(department) {
  try {
    const rows = await fetchDepartmentDetail(department, 7, 120);
    detailTitle.value = `部门明细 - ${department}`;
    detailRows.value = Array.isArray(rows) ? rows : [];
    detailVisible.value = true;
  } catch (err) {
    ElMessage.error(err?.message || '部门明细加载失败');
  }
}

async function openTopologyDetail(sourceIp, target) {
  try {
    const rows = await fetchTopologyDetail(sourceIp, target, 7, 120);
    detailTitle.value = `拓扑链路明细 - ${sourceIp} -> ${target}`;
    detailRows.value = Array.isArray(rows) ? rows : [];
    detailVisible.value = true;
  } catch (err) {
    ElMessage.error(err?.message || '拓扑明细加载失败');
  }
}

function startStream() {
  stopStream();
  try {
    streamHandle = openCockpitAlertStream({
      lastEventId: streamCursor,
      limit: 30,
      onAlerts: payload => {
        streamConnected.value = true;
        const items = Array.isArray(payload?.items) ? payload.items : [];
        if (items.length === 0) return;
        streamCursor = Math.max(streamCursor, Number(payload?.cursor || 0));
        const merged = [...items, ...realtimeAlerts.value]
          .sort((a, b) => Number(b.eventId || 0) - Number(a.eventId || 0));
        const seen = new Set();
        realtimeAlerts.value = merged.filter(item => {
          const id = Number(item.eventId || 0);
          if (!id || seen.has(id)) return false;
          seen.add(id);
          return true;
        }).slice(0, 80);
      },
      onError: () => {
        streamConnected.value = false;
        scheduleReconnect();
      },
    });
  } catch (err) {
    streamConnected.value = false;
    scheduleReconnect();
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    startStream();
  }, 4000);
}

function stopStream() {
  if (streamHandle) {
    streamHandle.close();
    streamHandle = null;
  }
  streamConnected.value = false;
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function normalizeStatus(value) {
  const raw = String(value || '').toLowerCase();
  if (raw === 'blocked') return 'blocked';
  if (raw === 'ignored') return 'ignored';
  return 'pending';
}

function statusText(value) {
  const status = normalizeStatus(value);
  if (status === 'blocked') return '已阻断';
  if (status === 'ignored') return '已忽略';
  return '待处置';
}

function statusTagType(value) {
  const status = normalizeStatus(value);
  if (status === 'blocked') return 'success';
  if (status === 'ignored') return 'info';
  return 'warning';
}

function normalizeSeverity(value) {
  const raw = String(value || '').toLowerCase();
  if (raw === 'critical') return 'critical';
  if (raw === 'high') return 'high';
  if (raw === 'medium') return 'medium';
  return 'low';
}

function severityText(value) {
  const level = normalizeSeverity(value);
  if (level === 'critical') return '严重';
  if (level === 'high') return '高';
  if (level === 'medium') return '中';
  return '低';
}

function severityTagType(value) {
  const level = normalizeSeverity(value);
  if (level === 'critical' || level === 'high') return 'danger';
  if (level === 'medium') return 'warning';
  return 'info';
}

function canShowPendingActions(row) {
  return normalizeStatus(row?.status) === 'pending';
}

async function block(id) {
  try {
    await request.post('/security/block', { id });
    ElMessage.success('阻断成功');
    await reloadAll();
  } catch (err) {
    ElMessage.error(err?.message || '阻断失败');
  }
}

async function ignore(id) {
  try {
    await request.post('/security/ignore', { id });
    ElMessage.success('忽略成功');
    await reloadAll();
  } catch (err) {
    ElMessage.error(err?.message || '忽略失败');
  }
}

function onResize() {
  heatmapChart?.resize();
  trendChart?.resize();
  topologyChart?.resize();
}

function disposeCharts() {
  heatmapChart?.dispose();
  trendChart?.dispose();
  topologyChart?.dispose();
  heatmapChart = null;
  trendChart = null;
  topologyChart = null;
}

onMounted(async () => {
  await reloadAll();
  startStream();
  pollTimer = window.setInterval(() => {
    reloadAll();
  }, 5 * 60 * 1000);
  window.addEventListener('resize', onResize, { passive: true });
});

onBeforeUnmount(() => {
  stopStream();
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
  window.removeEventListener('resize', onResize);
  disposeCharts();
});
</script>

<style scoped>
.security-cockpit-page {
  background: radial-gradient(circle at 12% 8%, rgba(15, 86, 167, 0.24), transparent 38%),
    radial-gradient(circle at 90% 90%, rgba(174, 74, 13, 0.22), transparent 42%),
    linear-gradient(120deg, #08162a 0%, #0f1e33 48%, #11243b 100%);
  border: 1px solid rgba(84, 126, 168, 0.28);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  color: #f8fbff;
}

.page-header p {
  margin: 8px 0 0;
  color: #9eb6d4;
}

.kpi-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
}

.kpi-card {
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #d9e8fa;
  border: 1px solid rgba(123, 168, 213, 0.22);
}

.kpi-card strong {
  font-size: 28px;
  line-height: 1;
}

.kpi-threat { background: linear-gradient(150deg, rgba(37, 99, 235, 0.24), rgba(30, 58, 138, 0.32)); }
.kpi-blocked { background: linear-gradient(150deg, rgba(22, 163, 74, 0.22), rgba(21, 128, 61, 0.3)); }
.kpi-risk { background: linear-gradient(150deg, rgba(234, 88, 12, 0.22), rgba(194, 65, 12, 0.3)); }
.kpi-pending { background: linear-gradient(150deg, rgba(202, 138, 4, 0.22), rgba(161, 98, 7, 0.3)); }

.charts-row { margin-bottom: 16px; }

.chart-card,
.stream-card {
  background: rgba(7, 19, 36, 0.72);
  border: 1px solid rgba(90, 131, 176, 0.24);
}

.card-header {
  color: #dce9f9;
  font-weight: 600;
}

.chart-canvas {
  height: 280px;
}

.stream-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.security-table :deep(.el-table__cell .cell) {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
