<template>
  <el-card class="dashboard-card card-glass">
    <div class="dashboard-header">
      <div>
        <div class="card-header">数据可视化仪表板</div>
        <p class="panel-subtitle">实时监控平台各项关键指标和风险态势</p>
      </div>
      <div class="dashboard-controls">
        <el-select v-model="timeRange" class="time-range-select" @change="refreshData">
          <el-option label="最近24小时" value="24h" />
          <el-option label="最近7天" value="7d" />
          <el-option label="最近30天" value="30d" />
        </el-select>
        <button class="refresh-btn" :disabled="refreshing" @click="refreshData">
          {{ refreshing ? '刷新中...' : '刷新' }}
        </button>
      </div>
    </div>

    <div class="dashboard-content">
      <div class="dashboard-metrics">
        <div v-for="metric in metrics" :key="metric.key" class="metric-card">
          <div class="metric-icon" :class="metric.color">
            <component :is="metric.icon" />
          </div>
          <div class="metric-info">
            <span class="metric-label">{{ metric.label }}</span>
            <strong class="metric-value">{{ metric.value }}</strong>
            <span class="metric-trend" :class="metric.trend >= 0 ? 'up' : 'down'">
              {{ metric.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(metric.trend) }}%
            </span>
          </div>
        </div>
      </div>

      <div class="dashboard-charts">
        <div class="chart-row">
          <div class="chart-container">
            <div class="chart-title">威胁态势趋势</div>
            <div ref="threatChartRef" class="chart-canvas"></div>
          </div>
          <div class="chart-container">
            <div class="chart-title">AI服务调用统计</div>
            <div ref="aiUsageChartRef" class="chart-canvas"></div>
          </div>
        </div>

        <div class="chart-row">
          <div class="chart-container">
            <div class="chart-title">风险事件分布</div>
            <div ref="riskDistChartRef" class="chart-canvas"></div>
          </div>
          <div class="chart-container">
            <div class="chart-title">系统健康状态</div>
            <div ref="healthChartRef" class="chart-canvas"></div>
          </div>
        </div>
      </div>

      <div class="dashboard-alerts">
        <div class="alerts-header">
          <strong>实时告警</strong>
          <span class="alerts-count">{{ alerts.length }}</span>
        </div>
        <div class="alerts-list">
          <div v-for="alert in alerts.slice(0, 5)" :key="alert.id" class="alert-item" :class="alert.level">
            <div class="alert-icon">{{ getAlertIcon(alert.level) }}</div>
            <div class="alert-content">
              <strong>{{ alert.title }}</strong>
              <p>{{ alert.description }}</p>
            </div>
            <span class="alert-time">{{ alert.time }}</span>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue';
import * as echarts from 'echarts';
import { Warning, TrendCharts, DataAnalysis, Monitor } from '@element-plus/icons-vue';

const timeRange = ref('24h');
const refreshing = ref(false);
const threatChartRef = ref(null);
const aiUsageChartRef = ref(null);
const riskDistChartRef = ref(null);
const healthChartRef = ref(null);

let threatChart = null;
let aiUsageChart = null;
let riskDistChart = null;
let healthChart = null;

const metrics = ref([
  {
    key: 'threats',
    label: '威胁检测',
    value: '1,234',
    trend: 12.5,
    color: 'danger',
    icon: Warning
  },
  {
    key: 'ai_calls',
    label: 'AI调用次数',
    value: '45,678',
    trend: 8.3,
    color: 'primary',
    icon: TrendCharts
  },
  {
    key: 'risk_events',
    label: '风险事件',
    value: '567',
    trend: -5.2,
    color: 'warning',
    icon: DataAnalysis
  },
  {
    key: 'system_health',
    label: '系统健康度',
    value: '98.5%',
    trend: 0.5,
    color: 'success',
    icon: Monitor
  }
]);

const alerts = ref([
  {
    id: 1,
    level: 'critical',
    title: '检测到异常AI调用',
    description: '用户admin在短时间内频繁调用敏感模型',
    time: '2分钟前'
  },
  {
    id: 2,
    level: 'warning',
    title: '数据泄露风险',
    description: '检测到可能包含敏感信息的响应',
    time: '15分钟前'
  },
  {
    id: 3,
    level: 'info',
    title: '系统自动备份完成',
    description: '数据库备份已成功完成',
    time: '1小时前'
  },
  {
    id: 4,
    level: 'warning',
    title: 'API响应延迟',
    description: '部分API响应时间超过阈值',
    time: '2小时前'
  },
  {
    id: 5,
    level: 'info',
    title: '用户登录异常',
    description: '检测到异地登录尝试',
    time: '3小时前'
  }
]);

function getAlertIcon(level) {
  const icons = {
    critical: '🚨',
    warning: '⚠️',
    info: 'ℹ️'
  };
  return icons[level] || 'ℹ️';
}

function initCharts() {
  initThreatChart();
  initAiUsageChart();
  initRiskDistChart();
  initHealthChart();
}

function initThreatChart() {
  if (!threatChartRef.value) return;
  
  threatChart = echarts.init(threatChartRef.value);
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 16, 27, 0.9)',
      borderColor: '#5f87ff',
      textStyle: { color: '#fff' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00'],
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } },
      axisLabel: { color: 'rgba(255, 255, 255, 0.6)' }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } },
      axisLabel: { color: 'rgba(255, 255, 255, 0.6)' },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } }
    },
    series: [
      {
        name: '威胁检测',
        type: 'line',
        smooth: true,
        data: [120, 132, 101, 134, 90, 230, 210],
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255, 99, 132, 0.3)' },
            { offset: 1, color: 'rgba(255, 99, 132, 0.05)' }
          ])
        },
        lineStyle: { color: '#ff6384' },
        itemStyle: { color: '#ff6384' }
      },
      {
        name: '风险事件',
        type: 'line',
        smooth: true,
        data: [220, 182, 191, 234, 290, 330, 310],
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(54, 162, 235, 0.3)' },
            { offset: 1, color: 'rgba(54, 162, 235, 0.05)' }
          ])
        },
        lineStyle: { color: '#36a2eb' },
        itemStyle: { color: '#36a2eb' }
      }
    ]
  };
  threatChart.setOption(option);
}

function initAiUsageChart() {
  if (!aiUsageChartRef.value) return;
  
  aiUsageChart = echarts.init(aiUsageChartRef.value);
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 16, 27, 0.9)',
      borderColor: '#5f87ff',
      textStyle: { color: '#fff' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } },
      axisLabel: { color: 'rgba(255, 255, 255, 0.6)' }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } },
      axisLabel: { color: 'rgba(255, 255, 255, 0.6)' },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } }
    },
    series: [
      {
        name: '调用次数',
        type: 'bar',
        data: [5200, 6320, 7010, 7340, 8900, 6300, 7200],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#5f87ff' },
            { offset: 1, color: '#a9c4ff' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  };
  aiUsageChart.setOption(option);
}

function initRiskDistChart() {
  if (!riskDistChartRef.value) return;
  
  riskDistChart = echarts.init(riskDistChartRef.value);
  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 16, 27, 0.9)',
      borderColor: '#5f87ff',
      textStyle: { color: '#fff' }
    },
    legend: {
      orient: 'vertical',
      right: '10%',
      top: 'center',
      textStyle: { color: 'rgba(255, 255, 255, 0.8)' }
    },
    series: [
      {
        name: '风险事件',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#08101b',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
            color: '#fff'
          }
        },
        labelLine: {
          show: false
        },
        data: [
          { value: 1048, name: '高危', itemStyle: { color: '#ff6384' } },
          { value: 735, name: '中危', itemStyle: { color: '#ff9f40' } },
          { value: 580, name: '低危', itemStyle: { color: '#ffcd56' } },
          { value: 484, name: '信息', itemStyle: { color: '#4bc0c0' } }
        ]
      }
    ]
  };
  riskDistChart.setOption(option);
}

function initHealthChart() {
  if (!healthChartRef.value) return;
  
  healthChart = echarts.init(healthChartRef.value);
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 16, 27, 0.9)',
      borderColor: '#5f87ff',
      textStyle: { color: '#fff' }
    },
    radar: {
      indicator: [
        { name: '系统性能', max: 100 },
        { name: '安全性', max: 100 },
        { name: '可用性', max: 100 },
        { name: '响应速度', max: 100 },
        { name: '数据完整性', max: 100 },
        { name: '监控覆盖', max: 100 }
      ],
      splitArea: {
        areaStyle: {
          color: ['rgba(95, 135, 255, 0.05)', 'rgba(95, 135, 255, 0.1)']
        }
      },
      axisLine: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.2)' }
      },
      splitLine: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.1)' }
      },
      axisName: {
        color: 'rgba(255, 255, 255, 0.8)',
        fontSize: 12
      }
    },
    series: [
      {
        name: '系统健康度',
        type: 'radar',
        data: [
          {
            value: [95, 98, 99, 92, 97, 94],
            name: '当前状态',
            areaStyle: {
              color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [
                { offset: 0, color: 'rgba(95, 135, 255, 0.6)' },
                { offset: 1, color: 'rgba(95, 135, 255, 0.1)' }
              ])
            },
            lineStyle: { color: '#5f87ff', width: 2 },
            itemStyle: { color: '#5f87ff' }
          }
        ]
      }
    ]
  };
  healthChart.setOption(option);
}

function refreshData() {
  refreshing.value = true;
  setTimeout(() => {
    refreshing.value = false;
  }, 1000);
}

function handleResize() {
  threatChart?.resize();
  aiUsageChart?.resize();
  riskDistChart?.resize();
  healthChart?.resize();
}

onMounted(() => {
  initCharts();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  threatChart?.dispose();
  aiUsageChart?.dispose();
  riskDistChart?.dispose();
  healthChart?.dispose();
});
</script>

<style scoped>
.dashboard-card {
  grid-column: 1 / -1;
  margin-bottom: 24px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.dashboard-controls {
  display: flex;
  gap: 12px;
  align-items: center;
}

.time-range-select {
  width: 120px;
}

.refresh-btn {
  padding: 8px 16px;
  background: rgba(95, 135, 255, 0.1);
  border: 1px solid rgba(95, 135, 255, 0.3);
  border-radius: 6px;
  color: #5f87ff;
  cursor: pointer;
  transition: all 0.3s ease;
}

.refresh-btn:hover:not(:disabled) {
  background: rgba(95, 135, 255, 0.2);
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.dashboard-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.metric-card:hover {
  background: rgba(255, 255, 255, 0.05);
  transform: translateY(-2px);
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.metric-icon.danger {
  background: rgba(255, 99, 132, 0.2);
  color: #ff6384;
}

.metric-icon.primary {
  background: rgba(95, 135, 255, 0.2);
  color: #5f87ff;
}

.metric-icon.warning {
  background: rgba(255, 159, 64, 0.2);
  color: #ff9f40;
}

.metric-icon.success {
  background: rgba(75, 192, 192, 0.2);
  color: #4bc0c0;
}

.metric-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: #fff;
}

.metric-trend {
  font-size: 12px;
  font-weight: 500;
}

.metric-trend.up {
  color: #4bc0c0;
}

.metric-trend.down {
  color: #ff6384;
}

.dashboard-charts {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.chart-container {
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 16px;
}

.chart-canvas {
  width: 100%;
  height: 300px;
}

.dashboard-alerts {
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.alerts-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.alerts-header strong {
  font-size: 16px;
  color: #fff;
}

.alerts-count {
  padding: 4px 12px;
  background: rgba(255, 99, 132, 0.2);
  border-radius: 12px;
  font-size: 12px;
  color: #ff6384;
  font-weight: 600;
}

.alerts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
  border-left: 3px solid transparent;
  transition: all 0.3s ease;
}

.alert-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.alert-item.critical {
  border-left-color: #ff6384;
}

.alert-item.warning {
  border-left-color: #ff9f40;
}

.alert-item.info {
  border-left-color: #36a2eb;
}

.alert-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.alert-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.alert-content strong {
  font-size: 14px;
  color: #fff;
}

.alert-content p {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.alert-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .dashboard-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .chart-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dashboard-metrics {
    grid-template-columns: 1fr;
  }
  
  .dashboard-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .time-range-select {
    width: 100%;
  }
}
</style>