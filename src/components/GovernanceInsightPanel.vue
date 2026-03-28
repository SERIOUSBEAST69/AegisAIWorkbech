<template>
  <el-card class="governance-panel card-glass">
    <div class="card-header">治理创新洞察</div>

    <el-skeleton v-if="loading" animated :rows="6" class="skeleton-card" />

    <div v-else>
      <div class="panel-top">
        <div class="score-block">
          <div class="score-ring" :style="ringStyle">
            <div class="score-inner">
              <div class="score-value">{{ safeInsights.postureScore }}</div>
              <div class="score-label">治理指数</div>
            </div>
          </div>
          <p class="score-desc">基于高敏资产、风险闭环、主体工单、审计留痕等现有代码能力实时计算。</p>
        </div>

        <div class="summary-grid">
          <div class="summary-item">
            <span>高敏资产</span>
            <strong>{{ safeInsights.summary.highSensitivityAssets }}</strong>
          </div>
          <div class="summary-item">
            <span>未闭环风险</span>
            <strong>{{ safeInsights.summary.openRiskEvents }}</strong>
          </div>
          <div class="summary-item">
            <span>高风险模型</span>
            <strong>{{ safeInsights.summary.highRiskModels }}</strong>
          </div>
          <div class="summary-item">
            <span>主体工单</span>
            <strong>{{ safeInsights.summary.pendingSubjectRequests }}</strong>
          </div>
          <div class="summary-item">
            <span>今日审计</span>
            <strong>{{ safeInsights.summary.todayAuditCount }}</strong>
          </div>
          <div class="summary-item">
            <span>AI 成本</span>
            <strong>¥{{ costText }}</strong>
          </div>
        </div>
      </div>

      <div v-if="errorText" class="panel-error">{{ errorText }}</div>

      <div class="highlight-list">
        <div v-for="item in safeInsights.highlights" :key="item.title" class="highlight-item">
          <div class="highlight-title-row">
            <span class="highlight-title">{{ item.title }}</span>
            <span class="highlight-value">{{ item.value }}</span>
          </div>
          <p>{{ item.description }}</p>
        </div>
      </div>

      <div class="recommendation-list">
        <div v-for="item in safeInsights.recommendations" :key="item.code" class="recommendation-item">
          <div class="recommendation-head">
            <el-tag :type="tagType(item.priority)" effect="dark">{{ item.priority }}</el-tag>
            <button class="route-btn clickable" @click="$router.push(item.route)">{{ item.title }}</button>
          </div>
          <p class="recommendation-desc">{{ item.description }}</p>
          <div class="recommendation-metric">{{ item.metric }}</div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  insights: {
    type: Object,
    default: () => ({
      postureScore: 0,
      summary: {},
      highlights: [],
      recommendations: [],
    }),
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const safeInsights = computed(() => ({
  postureScore: props.insights?.postureScore ?? 0,
  summary: {
    highSensitivityAssets: props.insights?.summary?.highSensitivityAssets ?? 0,
    openRiskEvents: props.insights?.summary?.openRiskEvents ?? 0,
    highRiskModels: props.insights?.summary?.highRiskModels ?? 0,
    pendingSubjectRequests: props.insights?.summary?.pendingSubjectRequests ?? 0,
    todayAuditCount: props.insights?.summary?.todayAuditCount ?? 0,
    totalCostCents: props.insights?.summary?.totalCostCents ?? 0,
  },
  highlights: props.insights?.highlights ?? [],
  recommendations: props.insights?.recommendations ?? [],
}));

const costText = computed(() => ((safeInsights.value.summary.totalCostCents || 0) / 100).toFixed(2));
const errorText = computed(() => props.insights?._error || '');
const ringStyle = computed(() => ({
  background: `conic-gradient(var(--color-primary) 0deg, var(--color-primary-light) ${Math.round((safeInsights.value.postureScore / 100) * 360)}deg, rgba(255,255,255,0.08) ${Math.round((safeInsights.value.postureScore / 100) * 360)}deg 360deg)`,
}));

function tagType(priority) {
  if (priority === 'P0') return 'danger';
  if (priority === 'P1') return 'warning';
  return 'info';
}
</script>

<style scoped>
.governance-panel {
  grid-column: span 2;
}

.panel-top {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.score-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.score-ring {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  box-shadow: inset 0 0 30px rgba(22, 93, 255, 0.16);
}

.score-inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(10, 14, 23, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.score-value {
  font-size: 40px;
  font-weight: 800;
  color: var(--color-text);
}

.score-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.score-desc {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
  line-height: 1.5;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.summary-item,
.highlight-item,
.recommendation-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.summary-item {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-item span {
  color: var(--color-text-muted);
  font-size: 13px;
}

.summary-item strong {
  color: var(--color-text);
  font-size: 24px;
}

.panel-error {
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--color-warning-light);
  background: rgba(255, 125, 0, 0.1);
  border: 1px solid rgba(255, 125, 0, 0.2);
}

.highlight-list,
.recommendation-list {
  display: grid;
  gap: 12px;
}

.highlight-list {
  grid-template-columns: repeat(2, 1fr);
  margin-bottom: 16px;
}

.highlight-item,
.recommendation-item {
  padding: 14px 16px;
}

.highlight-title-row,
.recommendation-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.highlight-title,
.route-btn {
  color: var(--color-text);
  font-weight: 700;
}

.highlight-value,
.recommendation-metric {
  color: var(--color-primary-light);
  font-weight: 700;
}

.highlight-item p,
.recommendation-desc {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.route-btn {
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.route-btn:hover {
  color: var(--color-primary-light);
}

@media (max-width: 1200px) {
  .panel-top {
    grid-template-columns: 1fr;
  }

  .summary-grid,
  .highlight-list {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .governance-panel {
    grid-column: span 1;
  }

  .summary-grid,
  .highlight-list {
    grid-template-columns: 1fr;
  }
}
</style>
