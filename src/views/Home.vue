<template>
  <div class="workbench-home" ref="stageRef">
    <section class="hero-scene card-glass scene-block" ref="heroRef">
      <div class="hero-copy">
        <div class="eyebrow">{{ personaExperience.kicker }}</div>
        <h1 class="hero-headline">
          <span class="hero-title-primary workbench-title-core" data-workbench-title-anchor="home">{{ heroHeadline.primary }}</span>
          <span v-if="heroHeadline.suffix" class="hero-title-suffix">{{ heroHeadline.suffix }}</span>
        </h1>
        <p>{{ heroSubheadline }}</p>
        <div class="scene-tags">
          <span v-for="tag in overview.sceneTags" :key="tag" class="scene-tag">{{ tag }}</span>
        </div>
        <div class="operator-ribbon">
          <div class="operator-label">当前指挥席位</div>
          <div class="operator-name">{{ overview.operator.displayName }}</div>
          <div class="operator-meta">{{ overview.operator.roleName }} · {{ overview.operator.department }}</div>
        </div>
      </div>
      <div class="hero-quick-row">
        <span>公司ID {{ traceContext.companyId ?? '-' }}</span>
        <span>账号 {{ traceContext.companyUserCount ?? 0 }} 人</span>
        <span>生成 {{ traceContext.generatedAt || '-' }}</span>
      </div>
    </section>

    <div class="stat-grid scene-block">
      <stat-card
        v-for="card in statCards"
        :key="card.key"
        :title="card.label"
        :value="card.value"
        :suffix="card.suffix"
        :icon="card.icon"
        :color="card.color"
        :trend="card.delta"
      />
    </div>

    <section class="trace-grid scene-block">
      <el-card class="trace-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">首页追溯上下文</div>
            <p class="panel-subtitle">数据范围与溯源状态</p>
          </div>
        </div>
        <div class="trace-context-row">
          <span>公司ID：{{ traceContext.companyId ?? '-' }}</span>
          <span>账号范围：{{ traceContext.companyUserCount ?? 0 }} 人</span>
          <span>当前账号：{{ traceContext.currentUsername || '-' }} (#{{ traceContext.currentUserId ?? '-' }})</span>
          <span>生成时间：{{ traceContext.generatedAt || '-' }}</span>
        </div>
        <p class="trace-note">{{ traceContext.traceabilityStatement || '数据范围：按当前公司与账号统计；支持按原始记录溯源。' }}</p>
      </el-card>

      <el-card class="trace-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">模型谱系与漂移</div>
            <p class="panel-subtitle">模型状态、样本覆盖与发布状态</p>
          </div>
          <div class="panel-actions">
            <span class="verify-badge" :class="modelDriftBadgeClass">
              {{ modelDriftBadgeText }}
            </span>
            <el-button size="small" type="primary" :loading="traceBootstrapLoading" @click="bootstrapTraceableData">补充样本</el-button>
            <el-button size="small" :loading="modelGovernanceLoading" @click="fetchModelGovernance">刷新</el-button>
          </div>
        </div>
        <div class="trace-context-row">
          <span>累计训练运行：{{ modelLineage.totalRuns ?? 0 }}</span>
          <span>跟踪模型数：{{ modelLineage.trackedModelCount ?? 0 }}</span>
          <span>最新运行ID：{{ modelLineageRunId }}</span>
          <span>漂移分数：{{ modelDriftScoreText }}</span>
          <span>发布状态：{{ modelReleaseText }}</span>
        </div>
        <p class="trace-note">{{ modelGovernanceNote }}</p>
      </el-card>

      <el-card class="trace-card card-glass" v-if="isAdmin">
        <div class="panel-head">
          <div>
            <div class="card-header">治理就绪度闭环</div>
            <p class="panel-subtitle">闭环状态、风险预算与处置进展</p>
          </div>
          <div class="panel-actions">
            <el-button size="small" :loading="awardReadinessLoading" @click="fetchAwardReadiness">刷新</el-button>
            <el-button size="small" type="warning" :loading="autoRemediationLoading" @click="runAutoRemediationDryRun">自动处置演练</el-button>
            <el-button size="small" type="success" :loading="exportEvidenceLoading" @click="exportAwardEvidencePackage">导出证据包</el-button>
          </div>
        </div>
        <div class="trace-context-row">
          <span>已实现项：{{ awardReadinessImplemented }}/8</span>
          <span>待补项：{{ 8 - awardReadinessImplemented }}</span>
          <span>错误预算：{{ awardErrorBudgetText }}</span>
          <span>自动处置状态：{{ autoRemediationStatusText }}</span>
        </div>
        <p class="trace-note">{{ awardReadinessNote }}</p>
      </el-card>
    </section>

    <el-card class="trace-modules-card card-glass scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">模块追溯下钻</div>
          <p class="panel-subtitle">按模块查看原始记录</p>
        </div>
      </div>
      <div class="trace-module-list">
        <button
          v-for="entry in traceModuleEntries"
          :key="entry.key"
          type="button"
          class="trace-module-item"
          @click="openTraceDrilldown(entry.key)"
        >
          <div class="trace-module-title">{{ entry.label }}</div>
          <div class="trace-module-meta">{{ entry.traceRule }}</div>
          <div class="trace-module-count">{{ entry.count }}</div>
        </button>
      </div>
    </el-card>

    <section class="pulse-grid scene-block">
      <el-card class="pulse-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">治理总览</div>
            <p class="panel-subtitle">关键状态、风险信号与处理建议</p>
          </div>
          <div class="pulse-chip">{{ trustPulse.innovationLabel }}</div>
        </div>
        <div class="pulse-layout">
          <div class="pulse-score-ring">
            <span class="pulse-score">{{ trustPulse.score }}</span>
            <em>{{ trustPulse.pulseLevel }}</em>
          </div>
          <div class="pulse-copy">
            <strong>{{ trustPulse.mission }}</strong>
            <div class="pulse-dimensions">
              <article v-for="dimension in trustPulse.dimensions" :key="dimension.code" class="pulse-dimension">
                <div class="pulse-dimension-head">
                  <span>{{ dimension.label }}</span>
                  <strong>{{ dimension.score }}</strong>
                </div>
                <div class="pulse-bar"><i :style="{ width: `${dimension.score}%` }"></i></div>
                <p>{{ dimension.description }}</p>
              </article>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="pulse-signal-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">治理信号</div>
            <p class="panel-subtitle">当前优先事项与结果摘要</p>
          </div>
        </div>
        <div class="pulse-signal-list">
          <article v-for="signal in governanceOverviewSignals" :key="signal.title" class="pulse-signal-item">
            <div class="pulse-signal-top">
              <strong>{{ signal.title }}</strong>
              <span :class="['pulse-tone', signal.tone]">{{ signal.value }}</span>
            </div>
            <p>{{ signal.action }}</p>
          </article>
        </div>
      </el-card>
    </section>

    <el-card class="chart-card card-glass trend-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">治理脉冲趋势</div>
          <p class="panel-subtitle">趋势窗口：{{ trendEvidenceText }} · {{ forecastInlineNote }}</p>
        </div>
        <div class="panel-actions">
          <button class="mini-refresh-btn" :disabled="forecastRefreshing" @click="refreshForecastNow">
            {{ forecastRefreshing ? '刷新中...' : '刷新预测' }}
          </button>
          <div class="panel-badge">T+1 预测 {{ overview.trend.forecastNextDay }}</div>
        </div>
      </div>
      <div ref="trendChartRef" class="chart-canvas trend-canvas"></div>
    </el-card>


    <el-card class="chart-card card-glass risk-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">风险结构剖面</div>
          <p class="panel-subtitle">当前风险分布</p>
        </div>
      </div>
      <div class="risk-layout">
        <div ref="riskChartRef" class="chart-canvas risk-canvas"></div>
        <div class="risk-list">
          <div v-for="item in overview.riskDistribution" :key="item.level" class="risk-item">
            <span class="risk-dot" :class="riskTone(item.level)"></span>
            <div class="risk-copy">
              <strong>{{ item.level }}</strong>
              <span>{{ item.value }} 起</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="todo-card card-glass scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">治理待办编排</div>
          <p class="panel-subtitle">按优先级执行</p>
        </div>
      </div>
      <div class="todo-list">
        <button
          v-for="item in overview.todos"
          :key="item.title"
          class="todo-item clickable"
          @click="$router.push(item.route)"
        >
          <div class="todo-priority">{{ item.priority }}</div>
          <div class="todo-copy">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
          <div class="todo-metric">{{ item.metric }}</div>
        </button>
      </div>
    </el-card>

    <el-card class="ai-workbench-card card-glass scene-block" style="grid-column: 1 / -1">
      <div class="panel-head">
        <div>
          <div class="card-header">AI 调用审计日志</div>
          <p class="panel-subtitle">审计记录与链路状态</p>
        </div>
        <div class="panel-actions">
          <span class="verify-badge" :class="aiAuditVerify.passed ? 'ok' : 'warn'">
            {{ aiAuditVerify.passed ? '链路验真通过' : '链路待校验' }}
          </span>
          <el-button type="primary" :loading="traceBootstrapLoading" @click="bootstrapTraceableData">补充追溯样本</el-button>
          <el-button :loading="aiAuditVerify.loading" @click="verifyAiAuditChain">校验链路</el-button>
          <el-button :loading="aiAuditLoading" @click="loadAiAuditLogs">刷新</el-button>
        </div>
      </div>
      <div v-if="aiAuditLoading" class="empty-state">加载中...</div>
      <div v-else-if="!aiAuditLogs.length" class="empty-state">暂无记录</div>
      <div v-else class="event-list">
        <div v-for="item in aiAuditLogs" :key="item.id" class="event-item">
          <strong>{{ item.modelCode || '-' }}</strong>
          <span>{{ item.provider || '-' }}</span>
          <span>{{ item.status || '-' }}</span>
          <span>{{ item.durationMs || 0 }}ms</span>
          <span>{{ item.username || '-' }} (#{{ item.userId ?? '-' }})</span>
          <span>公司 {{ item.companyId ?? '-' }}</span>
          <span>资产 {{ item.dataAssetId ?? '-' }}</span>
          <span>{{ item.createTime || '-' }}</span>
        </div>
      </div>
    </el-card>

    <div v-if="isAdmin" class="adversarial-floating-wrap">
      <button
        class="floating-btn adversarial-orb"
        type="button"
        :disabled="adversarialRunning"
        @click="launchAdversarialDrill"
      >
        <span class="orb-label">演练</span>
        <strong>{{ adversarialRunning ? '执行中' : '攻防演练' }}</strong>
      </button>

      <Transition name="fade-slide">
        <section v-if="adversarialPanelOpen" class="adversarial-panel card-glass">
          <header class="adversarial-head">
            <div>
              <div class="card-header">攻防演练面板</div>
              <p class="panel-subtitle">演练进度与回放记录</p>
            </div>
            <button class="adversarial-close" type="button" @click="adversarialPanelOpen = false">关闭</button>
          </header>

          <div class="adversarial-config">
            <select v-model="adversarialConfig.scenario" :disabled="adversarialRunning">
              <option v-for="scene in adversarialMeta.scenarios" :key="scene.code" :value="scene.code">
                {{ scene.code }}
              </option>
            </select>
            <input v-model.number="adversarialConfig.rounds" type="number" min="1" max="100" :disabled="adversarialRunning" />
            <input v-model="adversarialConfig.seed" type="number" placeholder="seed(可选)" :disabled="adversarialRunning" />
            <button class="adversarial-run" type="button" :disabled="adversarialRunning" @click="runAdversarialBattle">
              {{ adversarialRunning ? '运行中...' : '开始演练' }}
            </button>
          </div>

          <p v-if="adversarialError" class="adversarial-error">{{ adversarialError }}</p>

          <div v-if="adversarialBattle" class="adversarial-summary-grid">
            <article>
              <span>胜方</span>
              <strong>{{ adversarialWinnerText }}</strong>
            </article>
            <article>
              <span>攻击成功率</span>
              <strong>{{ Math.round((adversarialBattle.attack_success_rate || 0) * 100) }}%</strong>
            </article>
            <article>
              <span>最终比分</span>
              <strong>{{ adversarialBattle.attacker_final_score }} : {{ adversarialBattle.defender_final_score }}</strong>
            </article>
          </div>

          <div v-if="adversarialVisibleRounds.length" class="adversarial-stream">
            <article v-for="round in adversarialVisibleRounds" :key="`adver-${round.round_num}`" class="adversarial-round">
              <div class="adversarial-round-top">
                <strong>第 {{ round.round_num }} 轮</strong>
                <span :class="round.attack_success ? 'hit' : 'block'">{{ round.attack_success ? '突破' : '阻断' }}</span>
              </div>
              <p>{{ round.attack_strategy }} vs {{ round.defense_strategy }} · 突破率 {{ Math.round((round.final_effectiveness || 0) * 100) }}%</p>
              <em>{{ round.narrative }}</em>
            </article>
          </div>

          <div v-if="adversarialBattle?.recommendations?.length" class="adversarial-recommendations">
            <h4>防御优化建议</h4>
            <p v-for="tip in adversarialBattle.recommendations" :key="tip">{{ tip }}</p>
          </div>
        </section>
      </Transition>
    </div>

    <AIPrivacyShield ref="privacyShieldRef" />

    <el-dialog
      v-model="traceDialogVisible"
      width="72%"
      :close-on-click-modal="false"
      title="模块追溯明细"
    >
      <div class="trace-dialog-head">
        <span>模块：{{ traceDialog.module || '-' }}</span>
        <span>规则：{{ traceDialog.traceRule || '-' }}</span>
        <span>记录数：{{ traceDialog.records.length }}</span>
      </div>
      <div v-if="traceDialogLoading" class="empty-state">加载中...</div>
      <div v-else-if="!traceDialog.records.length" class="empty-state">暂无可追溯记录</div>
      <div v-else class="trace-record-list">
        <article v-for="record in traceDialog.records" :key="record.id || JSON.stringify(record)" class="trace-record-item">
          <span v-for="(value, key) in record" :key="key"><strong>{{ key }}:</strong> {{ value ?? '-' }}</span>
        </article>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { ElMessage } from 'element-plus';
import { dashboardApi } from '../api/dashboard';
import request from '../api/request';
import StatCard from '../components/StatCard.vue';
import AIPrivacyShield from '../components/AIPrivacyShield.vue';
import { useUserStore } from '../store/user';
import { getPersonaExperience, personalizeWorkbench } from '../utils/persona';

function createEmptyOverview() {
  return {
    operator: {
      displayName: '访客',
      roleName: '待识别身份',
      department: '可信AI治理中心',
      avatar: ''
    },
    headline: 'Aegis Workbench 可信AI数据治理与隐私合规工作台',
    subheadline: '正在加载真实治理态势...',
    sceneTags: [],
    metrics: [],
    trend: {
      labels: [],
      riskSeries: [],
      auditSeries: [],
      aiCallSeries: [],
      costSeries: [],
      forecastNextDay: 0,
      riskEventSampleCount: 0,
      auditLogSampleCount: 0,
      modelStatSampleCount: 0,
      trendWindowDays: 7,
    },
    riskDistribution: [],
    todos: [],
    feeds: [],
    _dataSource: 'real_db',
  };
}

const stageRef = ref(null);
const heroRef = ref(null);
const trendChartRef = ref(null);
const riskChartRef = ref(null);
const userStore = useUserStore();
const overview = ref(createEmptyOverview());
const insights = ref({ postureScore: 0, summary: {}, highlights: [], recommendations: [] });
const trustPulse = ref({ score: 0, pulseLevel: '', mission: '', innovationLabel: '', dimensions: [], signals: [] });
const loading = ref(true);
const forecastDataSource = ref('real_db');
const traceContext = ref({
  companyId: null,
  companyUserCount: 0,
  currentUserId: null,
  currentUsername: '',
  generatedAt: '',
  traceabilityStatement: '',
});
const forecastExplain = ref({
  method: '',
  historyPoints: 0,
  note: '',
  fallback: false,
  dataSource: 'real_db',
});
const traceModules = ref({});
const modelGovernanceLoading = ref(false);
const modelLineage = ref({
  totalRuns: 0,
  trackedModelCount: 0,
  latestByModel: {},
  updatedAt: '',
});
const modelDrift = ref({
  available: false,
  driftScore: null,
  driftLevel: 'unknown',
  alert: false,
  reason: '',
  recentCount: 0,
});
const modelRelease = ref({
  stable: null,
  canary: null,
  updatedAt: '',
});
const awardReadinessLoading = ref(false);
const autoRemediationLoading = ref(false);
const exportEvidenceLoading = ref(false);
const awardReadiness = ref({
  gapChecklist: {},
  resilience: {},
  autoRemediation: {},
  generatedAt: '',
});
const traceDialogVisible = ref(false);
const traceDialogLoading = ref(false);
const traceDialog = ref({
  module: '',
  traceRule: '',
  records: [],
});

const privacyShieldRef = ref(null);
const aiDraftMessage = ref('');
const privacyBlockReason = ref('');
const privacyShieldActive = ref(false);
const aiModelOptions = ref([]);
const selectedAiModelCode = ref('');
const aiModelLoadState = ref('idle');
const aiModelLoadMessage = ref('');
const aiAccessReason = ref('工作台交互请求');
const aiResponsePreview = ref('');
const aiSending = ref(false);
const forecastRefreshing = ref(false);
const aiAuditLoading = ref(false);
const aiAuditLogs = ref([]);
const aiAuditVerify = ref({ loading: false, passed: false, checkedRows: 0, violationCount: 0 });
const traceBootstrapLoading = ref(false);
const isAdmin = computed(() => {
  const role = String(userStore.userInfo?.roleCode || userStore.userInfo?.role || '')
    .trim()
    .toUpperCase();
  return role === 'ADMIN' || role === 'ADMIN_OPS' || role === 'ADMIN_REVIEWER';
});
const adversarialMeta = ref({ scenarios: [] });
const adversarialConfig = ref({ scenario: 'random', rounds: 10, seed: '' });
const adversarialPanelOpen = ref(false);
const adversarialError = ref('');
const adversarialRunning = ref(false);
const adversarialBattle = ref(null);
const adversarialVisibleRounds = ref([]);
let adversarialPlaybackTimer = null;

function selectedModel() {
  return aiModelOptions.value.find(item => item.modelCode === selectedAiModelCode.value)
    || aiModelOptions.value[0]
    || null;
}

function normalizeAiReply(payload) {
  if (typeof payload === 'string') return payload;
  if (typeof payload?.reply === 'string') return payload.reply;
  if (typeof payload?.content === 'string') return payload.content;
  if (typeof payload?.data?.reply === 'string') return payload.data.reply;
  return '已收到响应';
}

async function loadAiAuditLogs() {
  aiAuditLoading.value = true;
  try {
    const data = await request.get('/ai/monitor/logs', { params: { page: 1, pageSize: 20 } });
    aiAuditLogs.value = Array.isArray(data?.list) ? data.list : [];
  } catch {
    aiAuditLogs.value = [];
  } finally {
    aiAuditLoading.value = false;
  }
}

async function verifyAiAuditChain() {
  aiAuditVerify.value.loading = true;
  try {
    const data = await request.get('/ai/monitor/logs/verify-chain');
    const checkedRows = Number(data?.checkedRows || 0);
    const passed = Boolean(data?.passed) && checkedRows > 0;
    aiAuditVerify.value = {
      loading: false,
      passed,
      checkedRows,
      violationCount: Number(data?.violationCount || 0),
    };
    if (aiAuditVerify.value.passed && aiAuditVerify.value.checkedRows > 0) {
      ElMessage.success(`AI 调用审计链校验通过（${aiAuditVerify.value.checkedRows} 条）`);
    } else if (aiAuditVerify.value.checkedRows === 0) {
      ElMessage.info('暂无可校验记录，请先点击“补充追溯样本”');
    } else {
      ElMessage.error(`AI 调用审计链存在异常（${aiAuditVerify.value.violationCount} 处）`);
    }
  } catch (error) {
    aiAuditVerify.value.loading = false;
    ElMessage.error(error?.message || 'AI 审计链校验失败');
  }
}

async function bootstrapTraceableData() {
  traceBootstrapLoading.value = true;
  try {
    const data = await request.post('/ai/monitor/bootstrap-trace', { sampleSize: 40 });
    const inserted = Number(data?.insertedAuditLogs || 0);
    const predicted = Number(data?.predictedSamples || 0);
    ElMessage.success(`已补充追溯样本：日志 ${inserted} 条，预测 ${predicted} 条`);
    await Promise.all([fetchModelGovernance(), loadAiAuditLogs()]);
  } catch (error) {
    ElMessage.error(error?.message || '补充追溯样本失败');
  } finally {
    traceBootstrapLoading.value = false;
  }
}

async function openTraceDrilldown(moduleKey) {
  traceDialogVisible.value = true;
  traceDialogLoading.value = true;
  traceDialog.value = { module: moduleKey, traceRule: '', records: [] };
  try {
    const data = await request.get('/dashboard/trace/drilldown', {
      params: { module: moduleKey, limit: 20 }
    });
    traceDialog.value = {
      module: data?.module || moduleKey,
      traceRule: data?.traceRule || '',
      records: Array.isArray(data?.records) ? data.records : [],
    };
  } catch (error) {
    traceDialog.value = {
      module: moduleKey,
      traceRule: '',
      records: [],
    };
    ElMessage.error(error?.message || '追溯明细加载失败');
  } finally {
    traceDialogLoading.value = false;
  }
}

async function sendAiDraft() {
  if (privacyBlockReason.value) {
    ElMessage.error(privacyBlockReason.value);
    return;
  }
  if (!aiDraftMessage.value.trim()) {
    ElMessage.warning('请输入要发送给 AI 的内容');
    return;
  }
  const model = selectedModel();
  if (!model) {
    ElMessage.warning('请先选择一个可用模型');
    return;
  }
  aiSending.value = true;
  try {
    const res = await request.post('/ai/chat', {
      provider: model.provider || 'qwen',
      model: model.modelCode,
      accessReason: aiAccessReason.value,
      messages: [{ role: 'user', content: aiDraftMessage.value.trim() }],
    });
    aiResponsePreview.value = normalizeAiReply(res);
    ElMessage.success('消息已发送，已收到网关响应');
    aiDraftMessage.value = '';
    privacyShieldActive.value = false;
  } catch (error) {
    ElMessage.error(error?.message || 'AI 请求失败');
  } finally {
    aiSending.value = false;
  }
}

async function fetchAiModels() {
  aiModelLoadState.value = 'loading';
  aiModelLoadMessage.value = '';

  try {
    let payload;
    try {
      payload = await request.get('/ai/catalog');
    } catch (error) {
      const message = String(error?.message || '').toLowerCase();
      if (message.includes('no static resource') || message.includes('404')) {
        payload = await request.get('/ai/catalog/list');
      } else {
        throw error;
      }
    }
    const list = normalizeModelListPayload(payload)
      .filter(isEnabledModel)
      .map(item => ({
        ...item,
        modelName: cleanModelName(item.modelName || item.name || item.modelCode) || '未命名模型',
        modelCode: cleanModelName(item.modelCode) || ''
      }));
    aiModelOptions.value = list;
    if (!selectedAiModelCode.value && aiModelOptions.value.length > 0) {
      selectedAiModelCode.value = aiModelOptions.value[0].modelCode;
    }
    if (aiModelOptions.value.length === 0) {
      aiModelLoadState.value = 'empty';
      aiModelLoadMessage.value = '当前没有可发送的启用模型，请联系管理员检查模型目录。';
    } else {
      aiModelLoadState.value = 'ready';
    }
  } catch (error) {
    aiModelOptions.value = [];
    selectedAiModelCode.value = '';
    aiModelLoadState.value = 'error';
    aiModelLoadMessage.value = error?.message || '模型目录加载失败';
  }
}

function normalizeModelListPayload(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.records)) return payload.records;
  if (Array.isArray(payload?.list)) return payload.list;
  if (Array.isArray(payload?.items)) return payload.items;
  if (Array.isArray(payload?.rows)) return payload.rows;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.data?.records)) return payload.data.records;
  return [];
}

function cleanModelName(name) {
  if (!name || typeof name !== 'string') return '';
  return String(name).trim();
}

function isEnabledModel(item) {
  const status = String(item?.status ?? '').trim().toLowerCase();
  return !status || status === 'enabled' || status === 'active' || status === '1' || status === 'true';
}

async function fetchCrossSiteGuardStatus(silent = true) {
  crossSiteGuard.value.loading = true;
  try {
    const status = await request.get('/security/cross-site/status');
    crossSiteGuard.value = {
      loading: false,
      enabled: Boolean(status?.enabled),
      mode: status?.mode || 'disabled',
      allowedOrigins: Array.isArray(status?.allowedOrigins) ? status.allowedOrigins : [],
      blockedCount: Number(status?.blockedCount || 0),
      lastBlockedAt: status?.lastBlockedAt || null,
      message: ''
    };
  } catch (error) {
    crossSiteGuard.value = {
      ...crossSiteGuard.value,
      loading: false,
      message: error?.message || '跨站拦截状态获取失败'
    };
    if (!silent) {
      ElMessage.warning(crossSiteGuard.value.message);
    }
  }
}

async function fetchAiModelStack() {
  aiModelStack.value.loading = true;
  try {
    const data = await request.get('/ai/model-metrics');
    const metrics = data?.metrics && typeof data.metrics === 'object' ? data.metrics : {};
    aiModelStack.value = {
      loading: false,
      available: Boolean(data?.available),
      classifierStack: Array.isArray(metrics?.classifier_stack) ? metrics.classifier_stack : [],
      benchmark: metrics?.benchmark || null,
      message: data?.available ? '' : (data?.message || '训练模型服务暂不可达')
    };
  } catch (error) {
    aiModelStack.value = {
      loading: false,
      available: false,
      classifierStack: [],
      benchmark: null,
      message: error?.message || '训练模型指标拉取失败'
    };
  }
}

function stopAdversarialPlayback() {
  if (adversarialPlaybackTimer) {
    clearInterval(adversarialPlaybackTimer);
    adversarialPlaybackTimer = null;
  }
}

function startAdversarialPlayback(rounds) {
  stopAdversarialPlayback();
  adversarialVisibleRounds.value = [];
  if (!Array.isArray(rounds) || rounds.length === 0) {
    return;
  }
  let cursor = 0;
  adversarialPlaybackTimer = window.setInterval(() => {
    adversarialVisibleRounds.value = rounds.slice(0, cursor + 1);
    cursor += 1;
    if (cursor >= rounds.length) {
      stopAdversarialPlayback();
    }
  }, 520);
}

async function fetchAdversarialMeta() {
  if (!isAdmin.value || adversarialMeta.value.scenarios.length > 0) {
    return;
  }
  const data = await request.get('/ai/adversarial/meta');
  adversarialMeta.value = data || { scenarios: [] };
  if (Array.isArray(adversarialMeta.value.scenarios) && adversarialMeta.value.scenarios.length > 0) {
    adversarialConfig.value.scenario = adversarialMeta.value.scenarios[0].code;
  }
}

async function toggleAdversarialPanel() {
  adversarialPanelOpen.value = !adversarialPanelOpen.value;
  if (adversarialPanelOpen.value) {
    adversarialError.value = '';
    try {
      await fetchAdversarialMeta();
    } catch (error) {
      adversarialError.value = error?.message || '攻防元数据加载失败';
    }
  }
}

async function launchAdversarialDrill() {
  if (!adversarialPanelOpen.value) {
    await toggleAdversarialPanel();
  }
  await runAdversarialBattle();
}

async function runAdversarialBattle() {
  adversarialError.value = '';
  adversarialRunning.value = true;
  stopAdversarialPlayback();
  try {
    const payload = {
      scenario: adversarialConfig.value.scenario || 'random',
      rounds: Math.max(1, Math.min(100, Number(adversarialConfig.value.rounds || 10))),
    };
    if (String(adversarialConfig.value.seed || '').trim()) {
      payload.seed = Number(adversarialConfig.value.seed);
    }
    const data = await request.post('/ai/adversarial/run', payload);
    if (data?.mode === 'real-threat-assessment') {
      adversarialBattle.value = {
        rounds: [],
        winner: '评估模式',
        attack_success_rate: Number(data?.riskScore || 0) / 100,
        attacker_final_score: Number(data?.riskScore || 0),
        defender_final_score: Math.max(0, 100 - Number(data?.riskScore || 0)),
        recommendations: Array.isArray(data?.optimizationSuggestions) ? data.optimizationSuggestions : [],
      };
      adversarialVisibleRounds.value = [];
      ElMessage.success('已完成实时态势检测');
      return;
    }
    if (!data?.ok || !data?.battle) {
      throw new Error(data?.error || '对弈执行失败');
    }
    adversarialBattle.value = data.battle;
    if (data.meta) {
      adversarialMeta.value = data.meta;
    }
    startAdversarialPlayback(data.battle.rounds || []);
  } catch (error) {
    adversarialError.value = error?.message || '攻防对弈失败';
  } finally {
    adversarialRunning.value = false;
  }
}

async function refreshForecastNow() {
  forecastRefreshing.value = true;
  try {
    const forecastData = await request.post('/risk/forecast/refresh');
    if (forecastData?.forecast?.length) {
      const series = forecastData.forecast.map(v => Math.round(v * 10) / 10);
      overview.value = {
        ...overview.value,
        trend: {
          ...overview.value.trend,
          forecastSeries: series,
          forecastNextDay: Math.round(series[0] ?? overview.value.trend.forecastNextDay),
        }
      };
      forecastDataSource.value = forecastData._dataSource || 'real_db';
      forecastExplain.value = {
        method: forecastData.method || '',
        historyPoints: Number(forecastData.historyPoints || (forecastData.inputHistory?.length || 0)),
        note: forecastData.note || '',
        fallback: Boolean(forecastData.fallback),
        dataSource: forecastData._dataSource || 'real_db',
      };
      ElMessage.success('预测已刷新');
    }
  } catch (error) {
    ElMessage.error(error?.message || '预测刷新失败');
  } finally {
    forecastRefreshing.value = false;
  }
}

let trendChart;
let riskChart;
let resizeHandler;
let echartsLib;
let primaryChartRenderTimer;

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts');
  }
  return echartsLib;
}

const metricVisualMap = {
  assets: { icon: 'DataAnalysis', color: 'var(--color-primary)' },
  alerts: { icon: 'Warning', color: 'var(--color-danger)' },
  aiCalls: { icon: 'StarFilled', color: 'var(--color-success)' },
  audits: { icon: 'Timer', color: 'var(--color-info)' },
};

const statCards = computed(() => overview.value.metrics.map(item => ({
  ...item,
  icon: metricVisualMap[item.key]?.icon || 'DataAnalysis',
  color: metricVisualMap[item.key]?.color || 'var(--color-primary)'
})));
const trendEvidenceText = computed(() => {
  const trend = overview.value?.trend || {};
  const days = Number(trend.trendWindowDays || 7);
  const riskSamples = Number(trend.riskEventSampleCount || 0);
  const auditSamples = Number(trend.auditLogSampleCount || 0);
  const modelSamples = Number(trend.modelStatSampleCount || 0);
  const source = String(overview.value?._dataSource || 'real_db').toLowerCase();
  const sourceLabel = source === 'real_db' ? '真实DB' : '降级源';
  return `数据窗${days}天 · 风险样本${riskSamples} · 审计样本${auditSamples} · 调用样本${modelSamples} · ${sourceLabel}`;
});
const forecastInlineNote = computed(() => {
  const method = forecastExplain.value?.method || 'LSTM';
  const points = Number(forecastExplain.value?.historyPoints || 0);
  const status = forecastExplain.value?.fallback ? '降级' : '正常';
  return `预测：${method} · 历史点 ${points} · 状态 ${status} · T+1 ${overview.value?.trend?.forecastNextDay ?? 0}`;
});
const heroHeadline = computed(() => {
  const prefix = 'Aegis Workbench';
  const headline = String(overview.value.headline || prefix).trim();
  if (headline.startsWith(prefix)) {
    return {
      primary: prefix,
      suffix: headline.slice(prefix.length).trim()
    };
  }
  return {
    primary: prefix,
    suffix: headline === prefix ? '' : headline
  };
});
const heroSubheadline = computed(() => {
  const scopedCompanyId = traceContext.value?.companyId ?? '-';
  const scopedUsers = traceContext.value?.companyUserCount ?? 0;
  const sourceText = String(overview.value?.subheadline || '').trim();
  if (sourceText) {
    let normalized = sourceText;
    if (/传统平台/.test(normalized)) {
      normalized = '当前治理状态已同步';
    }
    if (/作战|总控|全域/.test(normalized)) {
      normalized = '已汇总资产、模型与风险状态';
    }
    return normalized;
  }
  return `数据范围：公司 ID ${scopedCompanyId} / 账号 ${scopedUsers} 人；支持按原始记录溯源。`;
});
const personaExperience = computed(() => getPersonaExperience(userStore.userInfo));
const traceModuleEntries = computed(() => {
  const modules = traceModules.value || {};
  return Object.entries(modules)
    .filter(([key, val]) => key !== 'userScope' && val && typeof val === 'object')
    .map(([key, val]) => ({
      key,
      label: val.label || key,
      traceRule: val.traceRule || '-',
      count: Number(val.count || 0),
    }));
});
const latestSensitiveRun = computed(() => {
  const latestByModel = modelLineage.value?.latestByModel || {};
  if (latestByModel.sensitive_clf) {
    return latestByModel.sensitive_clf;
  }
  const recentRuns = Array.isArray(modelLineage.value?.recentRuns) ? modelLineage.value.recentRuns : [];
  return recentRuns.find(item => item?.modelKey === 'sensitive_clf') || null;
});
const modelLineageRunId = computed(() => {
  const runId = String(latestSensitiveRun.value?.runId || '').trim();
  if (!runId) return '-';
  return runId.length > 22 ? `${runId.slice(0, 22)}...` : runId;
});
const modelDriftScoreText = computed(() => {
  const score = modelDrift.value?.driftScore;
  if (typeof score !== 'number' || Number.isNaN(score)) return '-';
  return score.toFixed(4);
});
const modelDriftBadgeClass = computed(() => {
  if (!modelDrift.value?.available) return 'warn';
  return modelDrift.value?.alert ? 'warn' : 'ok';
});
const modelDriftBadgeText = computed(() => {
  if (!modelDrift.value?.available) return '漂移待评估';
  return modelDrift.value?.alert ? '漂移告警' : '漂移正常';
});
const modelReleaseText = computed(() => {
  const stable = modelRelease.value?.stable;
  const canary = modelRelease.value?.canary;
  if (canary?.candidateId) {
    return `Canary ${canary.candidateId}`;
  }
  if (stable?.candidateId) {
    return `Stable ${stable.candidateId}`;
  }
  return '未发布';
});
const modelGovernanceNote = computed(() => {
  if (!modelDrift.value?.available) {
    const reason = String(modelDrift.value?.reason || '').toUpperCase();
    if (reason.includes('INSUFFICIENT')) {
      return '暂无足够历史数据，暂无法完成模型漂移评估。';
    }
    return '模型漂移评估暂不可用，请稍后重试。';
  }
  const source = latestSensitiveRun.value?.source || 'unknown_source';
  const runTime = latestSensitiveRun.value?.timestamp || modelLineage.value?.updatedAt || '-';
  return `模型来源：${source}；更新时间：${runTime}；发布状态：${modelReleaseText.value}；最近在线样本：${modelDrift.value?.recentCount || 0}。`;
});
const awardReadinessImplemented = computed(() => {
  const checklist = awardReadiness.value?.gapChecklist || {};
  return Object.values(checklist).filter(item => item?.status === 'implemented').length;
});
const awardErrorBudgetText = computed(() => {
  const budget = awardReadiness.value?.resilience?.errorBudget;
  if (typeof budget !== 'number' || Number.isNaN(budget)) return '-';
  return `${budget.toFixed(2)}%`;
});
const autoRemediationStatusText = computed(() => {
  const executed = awardReadiness.value?.autoRemediation?.executed;
  const dryRun = awardReadiness.value?.autoRemediation?.dryRun;
  if (executed === true) return '已执行';
  if (dryRun === true) return '演练模式';
  return '未触发';
});
const awardReadinessNote = computed(() => {
  const checklist = awardReadiness.value?.gapChecklist || {};
  const missing = Object.entries(checklist)
    .filter(([, item]) => item?.status !== 'implemented')
    .map(([, item]) => item?.goal)
    .filter(Boolean);
  if (!missing.length) return '系统状态健康，建议优先处理高风险项。';
  return `建议优先处理：${missing.slice(0, 2).join('；')}。`;
});
const governanceOverviewSignals = computed(() => {
  const merged = [];
  const seenTitles = new Set();

  (trustPulse.value?.signals || []).forEach((item) => {
    const title = String(item?.title || '').trim();
    if (!title || seenTitles.has(title)) return;
    seenTitles.add(title);
    merged.push({
      title,
      value: item?.value || '-',
      tone: item?.tone || 'neutral',
      action: item?.action || '持续跟进',
    });
  });

  (insights.value?.highlights || []).forEach((item) => {
    const title = String(item?.title || '').trim();
    if (!title || seenTitles.has(title)) return;
    seenTitles.add(title);
    merged.push({
      title,
      value: item?.value || '-',
      tone: 'neutral',
      action: item?.description || '状态已同步',
    });
  });

  (insights.value?.recommendations || []).forEach((item) => {
    const title = String(item?.title || '').trim();
    if (!title || seenTitles.has(title)) return;
    seenTitles.add(title);
    merged.push({
      title,
      value: item?.metric || item?.priority || '-',
      tone: item?.priority === 'P0' ? 'danger' : (item?.priority === 'P1' ? 'warning' : 'safe'),
      action: item?.description || '建议跟进',
    });
  });

  return merged.slice(0, 6);
});
const adversarialWinnerText = computed(() => {
  const winner = String(adversarialBattle.value?.winner || '-');
  if (winner.includes('攻击方')) return '攻方模拟器';
  if (winner.includes('防御方')) return '防御策略';
  return winner.replace(/\([^)]*\)/g, '').trim() || '-';
});

function riskTone(level) {
  const value = String(level || '').toLowerCase();
  if (value.includes('高') || value.includes('high') || value.includes('critical') || value.includes('p0')) return 'danger';
  if (value.includes('中') || value.includes('medium') || value.includes('processing') || value.includes('p1')) return 'warning';
  if (value.includes('低') || value.includes('low')) return 'safe';
  return 'neutral';
}

function clampSeriesOutliers(series, lowQ = 0.05, highQ = 0.95) {
  const numeric = (series || []).filter(v => typeof v === 'number' && Number.isFinite(v));
  if (numeric.length < 6) {
    return [...(series || [])];
  }
  const sorted = [...numeric].sort((a, b) => a - b);
  const low = sorted[Math.max(0, Math.floor((sorted.length - 1) * lowQ))];
  const high = sorted[Math.max(0, Math.floor((sorted.length - 1) * highQ))];
  return (series || []).map(v => {
    if (typeof v !== 'number' || !Number.isFinite(v)) return v;
    return Math.min(high, Math.max(low, v));
  });
}

function resolveYAxisMax(seriesList) {
  const values = seriesList.flat().filter(v => typeof v === 'number' && Number.isFinite(v));
  if (values.length === 0) {
    return 10;
  }
  const max = Math.max(...values);
  const padded = max * 1.2;
  return Math.max(10, Math.ceil(padded));
}

async function renderTrendChart() {
  const echarts = await ensureEcharts();
  if (!trendChartRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
  }
  const trend = overview.value.trend;
  const hasForecast = Array.isArray(trend.forecastSeries) && trend.forecastSeries.length > 0;
  const riskSeries = clampSeriesOutliers(trend.riskSeries || []);
  const auditSeries = clampSeriesOutliers(trend.auditSeries || []);
  const aiCallSeries = clampSeriesOutliers(trend.aiCallSeries || []);
  const costSeries = clampSeriesOutliers(trend.costSeries || []);
  const forecastSeries = clampSeriesOutliers(trend.forecastSeries || []);

  const historicLabels = trend.labels || [];
  const forecastLabels = hasForecast
    ? forecastSeries.map((_, i) => `预测+${i + 1}`)
    : [];
  const allLabels = [...historicLabels, ...forecastLabels];

  const forecastPad = hasForecast ? forecastSeries.map(() => null) : [];

  const riskWithForecast = hasForecast
    ? [...riskSeries, ...forecastPad]
    : riskSeries;
  const forecastWithPad = hasForecast
    ? [...riskSeries.map(() => null), ...forecastSeries]
    : [];
  const leftAxisMax = resolveYAxisMax([riskWithForecast, forecastWithPad, [...auditSeries, ...forecastPad], [...aiCallSeries, ...forecastPad]]);
  const rightAxisMax = resolveYAxisMax([[...costSeries, ...forecastPad]]);

  trendChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      textStyle: { color: '#b8c2d4' },
      data: hasForecast
        ? ['风险事件', '审计留痕', 'AI调用', '成本(分)', 'LSTM预测']
        : ['风险事件', '审计留痕', 'AI调用', '成本(分)']
    },
    grid: { left: 24, right: 28, top: 48, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: allLabels,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
      axisLabel: { color: '#93a0b8' }
    },
    yAxis: [
      {
        type: 'value',
        min: 0,
        max: leftAxisMax,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
        axisLabel: { color: '#93a0b8' }
      },
      {
        type: 'value',
        min: 0,
        max: rightAxisMax,
        axisLine: { show: false },
        splitLine: { show: false },
        axisLabel: { color: '#93a0b8' }
      }
    ],
    series: [
      {
        name: '风险事件',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        symbolSize: 8,
        data: riskWithForecast,
        lineStyle: { width: 3, color: '#ff7d66' },
        itemStyle: { color: '#ff7d66' },
        areaStyle: { color: 'rgba(255,125,102,0.12)' }
      },
      ...(hasForecast ? [{
        name: 'LSTM预测',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        symbolSize: 7,
        data: forecastWithPad,
        lineStyle: { width: 2, type: 'dashed', color: '#c77dff' },
        itemStyle: { color: '#c77dff' },
        areaStyle: { color: 'rgba(199,125,255,0.08)' }
      }] : []),
      {
        name: '审计留痕',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        symbolSize: 7,
        data: [...auditSeries, ...forecastPad],
        lineStyle: { width: 3, color: '#6aa6ff' },
        itemStyle: { color: '#6aa6ff' },
        areaStyle: { color: 'rgba(106,166,255,0.12)' }
      },
      {
        name: 'AI调用',
        type: 'bar',
        barMaxWidth: 18,
        data: [...aiCallSeries, ...forecastPad],
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#4fe3c1' },
            { offset: 1, color: '#1a8c74' }
          ])
        }
      },
      {
        name: '成本(分)',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        yAxisIndex: 1,
        symbolSize: 6,
        data: [...costSeries, ...forecastPad],
        lineStyle: { width: 2, type: 'dashed', color: '#f5d06f' },
        itemStyle: { color: '#f5d06f' }
      }
    ]
  });
}

async function renderRiskChart() {
  const echarts = await ensureEcharts();
  if (!riskChartRef.value) return;
  if (!riskChart) {
    riskChart = echarts.init(riskChartRef.value);
  }
  riskChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['56%', '76%'],
        center: ['50%', '52%'],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: {
          borderColor: '#111826',
          borderWidth: 6
        },
        data: overview.value.riskDistribution.map(item => ({
          value: item.value,
          name: item.level,
          itemStyle: {
            color: item.level === '高危'
              ? '#ff6b6b'
              : item.level === '中危'
                ? '#ffb454'
                : item.level === '低危'
                  ? '#2ecc71'
                  : '#7f8aa3'
          }
        }))
      }
    ]
  });
}


function playEntryScene() {
  if (!stageRef.value) return;
  const blocks = Array.from(stageRef.value.querySelectorAll('.scene-block'));
  const cinematicEntry = sessionStorage.getItem('aegis.transition.origin') === 'login';
  sessionStorage.removeItem('aegis.transition.origin');

  if (cinematicEntry) {
    const revealBlocks = blocks.filter(block => block !== heroRef.value);
    gsap.set(heroRef.value, { opacity: 1, y: 0 });
    gsap.set(revealBlocks, { opacity: 0, y: 18 });
    gsap.timeline({ defaults: { ease: 'power2.out' } })
      .to(revealBlocks, {
        opacity: 1,
        y: 0,
        duration: 0.8,
        stagger: 0.1
      }, 0.02);
    return;
  }

  gsap.set(blocks, { opacity: 0, y: 16 });
  gsap.timeline({ defaults: { ease: 'power2.out' } })
    .to(heroRef.value, { opacity: 1, y: 0, duration: 0.6 })
    .to(blocks, {
      opacity: 1,
      y: 0,
      duration: 0.5,
      stagger: 0.1
    }, '-=0.3');
}

async function fetchData() {
  loading.value = true;
  try {
    const bundle = await dashboardApi.getHomeBundle();
    const workbench = bundle?.workbench || {};
    const insightData = bundle?.insights || {};
    const pulseData = bundle?.trustPulse || {};
    const forecastData = bundle?.forecast || {};
    traceContext.value = bundle?.traceContext || traceContext.value;
    traceModules.value = bundle?.traceModules || {};
    const personalized = personalizeWorkbench(workbench, userStore.userInfo);

    if (forecastData?.forecast?.length) {
      const series = forecastData.forecast.map(v => Math.round(v * 10) / 10);
      personalized.trend = {
        ...personalized.trend,
        forecastSeries: series,
        forecastNextDay: Math.round(series[0] ?? personalized.trend.forecastNextDay),
      };
      const method = String(forecastData.method || '').toLowerCase();
      forecastDataSource.value = forecastData._dataSource || (method.includes('lstm') ? 'real_db' : 'degraded');
      forecastExplain.value = {
        method: forecastData.method || '',
        historyPoints: Number(forecastData.historyPoints || (forecastData.inputHistory?.length || 0)),
        note: forecastData.note || '',
        fallback: Boolean(forecastData.fallback),
        dataSource: forecastData._dataSource || (method.includes('lstm') ? 'real_db' : 'degraded'),
      };
    }

    overview.value = personalized;
    insights.value = insightData;
    trustPulse.value = pulseData;
    playEntryScene();
    loading.value = false;

    schedulePrimaryChartRender();
  } catch (error) {
    ElMessage.error(error?.message || '首页工作台加载失败');
  } finally {
    if (loading.value) {
      loading.value = false;
    }
  }
}

async function fetchModelGovernance() {
  modelGovernanceLoading.value = true;
  try {
    const [lineageResp, driftResp, releaseResp] = await Promise.all([
      dashboardApi.getModelLineage(),
      dashboardApi.getModelDriftStatus(),
      dashboardApi.getModelReleaseStatus(),
    ]);
    modelLineage.value = lineageResp?.lineage && typeof lineageResp.lineage === 'object'
      ? lineageResp.lineage
      : (lineageResp || modelLineage.value);
    modelDrift.value = driftResp?.drift && typeof driftResp.drift === 'object'
      ? driftResp.drift
      : (driftResp || modelDrift.value);
    modelRelease.value = releaseResp?.release && typeof releaseResp.release === 'object'
      ? releaseResp.release
      : (releaseResp || modelRelease.value);
  } catch (error) {
    modelDrift.value = {
      ...modelDrift.value,
      available: false,
      reason: error?.message || 'FETCH_FAILED',
    };
  } finally {
    modelGovernanceLoading.value = false;
  }
}

async function fetchAwardReadiness() {
  awardReadinessLoading.value = true;
  try {
    const report = await dashboardApi.getAwardReadinessReport();
    awardReadiness.value = report || awardReadiness.value;
  } catch (error) {
    ElMessage.warning(error?.message || '治理就绪度报告加载失败');
  } finally {
    awardReadinessLoading.value = false;
  }
}

async function runAutoRemediationDryRun() {
  autoRemediationLoading.value = true;
  try {
    await dashboardApi.runAutoRemediationPlaybook({ dryRun: true });
    await fetchAwardReadiness();
    ElMessage.success('自动处置演练已执行');
  } catch (error) {
    ElMessage.error(error?.message || '自动处置演练失败');
  } finally {
    autoRemediationLoading.value = false;
  }
}

async function exportAwardEvidencePackage() {
  exportEvidenceLoading.value = true;
  try {
    const payload = {
      includePdf: false,
      includeJson: true,
    };
    const data = await dashboardApi.exportEvidencePackage(payload);
    const signature = String(data?.signature || '').trim();
    const sigSuffix = signature ? signature.slice(0, 10) : 'n/a';
    const warning = String(data?.warning || '').trim();
    if (warning) {
      ElMessage.warning(`证据包已导出（JSON），签名: ${sigSuffix}`);
    } else {
      ElMessage.success(`证据包已导出，签名: ${sigSuffix}`);
    }
  } catch (error) {
    ElMessage.error(error?.message || '证据包导出失败');
  } finally {
    exportEvidenceLoading.value = false;
  }
}

function schedulePrimaryChartRender() {
  if (primaryChartRenderTimer) {
    clearTimeout(primaryChartRenderTimer);
  }
  primaryChartRenderTimer = window.setTimeout(async () => {
    await nextTick();
    await Promise.all([renderTrendChart(), renderRiskChart()]);
  }, 180);
}


watch(() => overview.value.trend, async () => {
  schedulePrimaryChartRender();
}, { deep: true });

onMounted(() => {
  fetchData();
  fetchModelGovernance();
  fetchAwardReadiness();
  loadAiAuditLogs();
  resizeHandler = () => {
    trendChart?.resize();
    riskChart?.resize();
  };
  window.addEventListener('resize', resizeHandler);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler);
  if (primaryChartRenderTimer) clearTimeout(primaryChartRenderTimer);
  stopAdversarialPlayback();
  trendChart?.dispose();
  riskChart?.dispose();
});
</script>

<style scoped>
.ai-model-select :deep(.el-select__input) {
  color: var(--color-text);
}

.ai-model-select :deep(.el-select__placeholder) {
  color: var(--color-text-subtle);
}

.ai-model-select :deep(.el-select-dropdown) {
  background: linear-gradient(160deg, rgba(12, 20, 34, 0.98), rgba(8, 14, 26, 0.96));
  border-color: rgba(169, 196, 255, 0.16);
  color: var(--color-text);
}

.ai-model-select :deep(.el-select-dropdown__item) {
  color: var(--color-text);
}

.ai-model-select :deep(.el-select-dropdown__item:hover) {
  background: rgba(95, 135, 255, 0.12);
}

.award-card {
  min-height: 260px;
}

.award-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.award-metric-card {
  border: 1px solid rgba(138, 182, 255, 0.2);
  border-radius: 14px;
  padding: 12px;
  background: linear-gradient(165deg, rgba(20, 35, 60, 0.5), rgba(15, 25, 46, 0.35));
}

.award-metric-card span {
  display: block;
  color: #a9bde8;
  font-size: 13px;
}

.award-metric-card strong {
  display: block;
  margin-top: 4px;
  color: #f5f7ff;
  font-size: 26px;
  line-height: 1.1;
}

.award-metric-card em {
  display: block;
  margin-top: 6px;
  color: #9ab1da;
  font-size: 12px;
  font-style: normal;
}

.award-actions-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.award-evidence-line {
  color: #b3c3e7;
  font-size: 13px;
  margin-bottom: 6px;
}

.award-evidence-line strong {
  color: #f0f3ff;
}

.observability-card {
  min-height: 220px;
}

.web-vitals-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.web-vital-item {
  border: 1px solid rgba(133, 166, 243, 0.18);
  border-radius: 12px;
  padding: 10px;
  background: rgba(12, 22, 40, 0.42);
}

.web-vital-item strong {
  display: block;
  color: #f0f3ff;
  font-size: 16px;
}

.web-vital-item span,
.web-vital-item em {
  display: block;
  color: #a9bbdf;
  font-size: 12px;
  font-style: normal;
  margin-top: 4px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.compare-item {
  border: 1px dashed rgba(150, 185, 255, 0.26);
  border-radius: 12px;
  padding: 10px;
  background: rgba(11, 19, 35, 0.42);
}

.compare-item span {
  color: #9fb1d6;
  font-size: 12px;
}

.compare-item strong {
  display: block;
  color: #f8faff;
  font-size: 20px;
  margin-top: 4px;
}

@media (max-width: 1100px) {
  .trace-grid {
    grid-template-columns: 1fr;
  }

  .trace-module-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .award-grid {
    grid-template-columns: 1fr;
  }

  .award-actions-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .web-vitals-grid,
  .compare-grid {
    grid-template-columns: 1fr;
  }
}

.workbench-home {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.trace-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.trace-card {
  min-height: 168px;
}

.trace-context-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}

.trace-context-row span {
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  background: rgba(11, 19, 35, 0.42);
  color: #dbe7ff;
  font-size: 12px;
}

.trace-note {
  margin-top: 10px;
  color: #9fb1d6;
  font-size: 13px;
  line-height: 1.6;
}

.trace-modules-card {
  grid-column: 1 / -1;
}

.trace-module-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.trace-module-item {
  text-align: left;
  border: 1px solid rgba(140, 172, 239, 0.22);
  background: rgba(11, 19, 35, 0.45);
  border-radius: 12px;
  padding: 10px;
  color: #dbe7ff;
  cursor: pointer;
}

.trace-module-item:hover {
  border-color: rgba(140, 172, 239, 0.5);
}

.trace-module-title {
  font-size: 14px;
  font-weight: 700;
}

.trace-module-meta {
  margin-top: 4px;
  color: #a7b9de;
  font-size: 12px;
}

.trace-module-count {
  margin-top: 8px;
  color: #f5f8ff;
  font-size: 18px;
  font-weight: 700;
}

.trace-dialog-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.trace-dialog-head span {
  padding: 6px 10px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 10px;
  background: rgba(11, 19, 35, 0.45);
  color: #dbe7ff;
  font-size: 12px;
}

.trace-record-list {
  display: grid;
  gap: 8px;
  max-height: 50vh;
  overflow: auto;
}

.trace-record-item {
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 10px;
  background: rgba(11, 19, 35, 0.45);
  padding: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #d5e2fd;
  font-size: 12px;
}

.trace-record-item strong {
  color: #f5f8ff;
}

.verify-badge {
  font-size: 12px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 999px;
  padding: 6px 10px;
}

.verify-badge.ok {
  color: #9ef2c4;
  border-color: rgba(46, 204, 113, 0.45);
  background: rgba(46, 204, 113, 0.1);
}

.verify-badge.warn {
  color: #ffd7a4;
  border-color: rgba(255, 180, 84, 0.45);
  background: rgba(255, 180, 84, 0.1);
}

.hero-scene {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 28px;
  padding: 34px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 8% 18%, rgba(70, 118, 255, 0.28), transparent 30%),
    radial-gradient(circle at 82% 20%, rgba(34, 208, 181, 0.18), transparent 28%),
    linear-gradient(135deg, rgba(9, 14, 24, 0.98), rgba(12, 18, 31, 0.92));
}

.hero-scene::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 72px 72px;
  opacity: 0.12;
  mask-image: linear-gradient(180deg, rgba(0,0,0,0.88), transparent 100%);
}

.hero-copy,
.hero-quick-row {
  position: relative;
  z-index: 1;
}

.hero-quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-quick-row span {
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  background: rgba(11, 19, 35, 0.42);
  color: #dbe7ff;
  font-size: 12px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(118, 164, 255, 0.24);
  background: rgba(19, 29, 49, 0.72);
  color: #d8e6ff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero-headline {
  margin: 18px 0 14px;
  font-size: clamp(36px, 4vw, 52px);
  line-height: 1.04;
  letter-spacing: -0.04em;
  color: #f6f8fe;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.18em 0.34em;
}

.hero-title-primary {
  display: inline-block;
  color: #f6f8fe;
}

.hero-title-suffix {
  display: inline-block;
}

.hero-copy p {
  max-width: 720px;
  margin: 0;
  color: #98a5bb;
  font-size: 16px;
  line-height: 1.8;
}

.scene-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.scene-tag {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.08);
  color: #d7deee;
  font-size: 12px;
}

.operator-ribbon {
  margin-top: 26px;
  padding: 18px 20px;
  width: min(100%, 420px);
  border-radius: 22px;
  border: 1px solid rgba(255,255,255,0.08);
  background: linear-gradient(135deg, rgba(255,255,255,0.07), rgba(255,255,255,0.03));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.06);
}

.operator-label {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #7c8aa3;
  text-transform: uppercase;
}

.operator-name {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
}

.operator-meta {
  margin-top: 4px;
  color: #95a0b5;
}

.stat-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.pulse-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 20px;
}

.pulse-card,
.pulse-signal-card {
  padding: 22px;
}

.pulse-chip {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(118, 164, 255, 0.2);
  background: rgba(87, 127, 255, 0.08);
  color: #dce7ff;
  font-size: 12px;
}

.pulse-layout {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 22px;
  align-items: center;
}

.pulse-score-ring {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  align-content: center;
  border: 1px solid rgba(118, 164, 255, 0.16);
  background:
    radial-gradient(circle at center, rgba(7, 12, 21, 0.86) 0 54%, transparent 55%),
    conic-gradient(from 180deg, #edf4ff, #85abff, #466de0, #edf4ff);
  box-shadow: inset 0 0 40px rgba(255,255,255,0.04), 0 18px 42px rgba(52, 93, 210, 0.16);
}

.pulse-score {
  font-size: 58px;
  font-weight: 800;
  line-height: 1;
  color: #f7fbff;
}

.pulse-score-ring em {
  margin-top: 6px;
  color: #98a9c8;
  font-style: normal;
}

.pulse-copy strong {
  color: #f7fbff;
  font-size: 20px;
  line-height: 1.55;
}

.pulse-dimensions {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.pulse-dimension {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.03);
}

.pulse-dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pulse-dimension-head span {
  color: #dfe8f7;
  font-weight: 700;
}

.pulse-dimension-head strong {
  font-size: 18px;
}

.pulse-bar {
  height: 8px;
  margin-top: 10px;
  border-radius: 999px;
  background: rgba(255,255,255,0.05);
  overflow: hidden;
}

.pulse-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #f1f6ff, #85adff 36%, #4469db 100%);
}

.pulse-dimension p,
.pulse-signal-item p {
  margin: 10px 0 0;
  color: #90a0b8;
  line-height: 1.7;
}

.pulse-signal-list {
  display: grid;
  gap: 12px;
}

.pulse-signal-item {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.03);
}

.pulse-signal-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pulse-signal-top strong {
  color: #f6faff;
}

.pulse-tone {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.pulse-tone.danger {
  background: rgba(255, 107, 107, 0.12);
  color: #ffd8d8;
}

.pulse-tone.warning {
  background: rgba(118, 164, 255, 0.12);
  color: #dce8ff;
}

.pulse-tone.safe {
  background: rgba(105, 169, 255, 0.12);
  color: #dcefff;
}

.chart-card,
.todo-card {
  padding: 22px;
}

.trend-card {
  grid-column: span 2;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-subtitle {
  margin: 8px 0 0;
  color: #8f9bb1;
  line-height: 1.7;
}

.panel-badge {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 115, 115, 0.24);
  background: rgba(255, 99, 99, 0.08);
  color: #ffd3d3;
  font-size: 12px;
  font-weight: 700;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mini-refresh-btn {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(122, 188, 255, 0.32);
  background: rgba(82, 157, 255, 0.12);
  color: #d6e8ff;
  font-size: 12px;
  cursor: pointer;
}

.mini-refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.chart-canvas {
  width: 100%;
}

.trend-canvas {
  height: 360px;
}

.risk-layout {
  display: grid;
  grid-template-columns: minmax(220px, 0.9fr) minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.risk-canvas {
  height: 290px;
}

.risk-list {
  display: grid;
  gap: 12px;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
}

.risk-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.risk-dot.danger {
  background: #ff6b6b;
}

.risk-dot.warning {
  background: #ffb454;
}

.risk-dot.safe {
  background: #2ecc71;
}

.risk-dot.neutral {
  background: #7f8aa3;
}

.risk-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.risk-copy strong,
.todo-copy strong {
  color: #f6f8fe;
}

.risk-copy span,
.todo-copy p {
  color: #90a0b8;
}

.todo-list {
  display: grid;
  gap: 12px;
}

.todo-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: center;
  width: 100%;
  padding: 16px 18px;
  text-align: left;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.todo-item:hover {
  transform: translateY(-2px);
  border-color: rgba(115, 164, 255, 0.28);
  background: rgba(255,255,255,0.05);
}

.todo-priority {
  min-width: 48px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255,107,107,0.14);
  color: #ffd6d6;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.todo-copy p {
  margin: 6px 0 0;
  line-height: 1.6;
}

.todo-metric {
  color: #e4ecfa;
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 1280px) {
  .pulse-grid,
  .hero-scene,
  .risk-layout,

  .pulse-layout {
    grid-template-columns: 1fr;
  }
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .trend-card {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .workbench-home {
    grid-template-columns: 1fr;
  }

  .hero-scene,
  .chart-card,
  .todo-card {
    padding: 18px;
  }

  .todo-item {
    grid-template-columns: 1fr;
  }

  .todo-metric {
    text-align: left;
  }
}

.ai-workbench-card {
  grid-column: 1 / -1;
}

.ps-status-badge {
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  white-space: nowrap;
  flex-shrink: 0;
}
.ps-status-badge.badge-on {
  background: rgba(16, 185, 129, 0.18);
  color: #34d399;
  border: 1px solid rgba(52, 211, 153, 0.3);
}
.ps-status-badge.badge-off {
  background: rgba(100, 116, 139, 0.14);
  color: #94a3b8;
  border: 1px solid rgba(100, 116, 139, 0.2);
}

.ai-input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  margin-top: 14px;
}

.ai-config-row {
  display: grid;
  grid-template-columns: minmax(220px, 0.85fr) minmax(0, 1.15fr);
  gap: 12px;
  margin-top: 12px;
}

.ai-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.ai-meta-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.ai-meta-pill.state-idle,
.ai-meta-pill.state-loading {
  color: #c0d4f8;
  background: rgba(73, 126, 233, 0.18);
  border: 1px solid rgba(106, 166, 255, 0.25);
}

.ai-meta-pill.state-ready,
.ai-meta-pill.state-selected {
  color: #c5f8e8;
  background: rgba(16, 185, 129, 0.14);
  border: 1px solid rgba(52, 211, 153, 0.25);
}

.ai-meta-pill.state-empty,
.ai-meta-pill.state-error {
  color: #ffd0d0;
  background: rgba(220, 38, 38, 0.14);
  border: 1px solid rgba(248, 113, 113, 0.24);
}

.ai-model-notice {
  margin: 8px 0 0;
  font-size: 12px;
  color: #fca5a5;
  line-height: 1.5;
}

.security-surface {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.security-card {
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  background: rgba(8, 17, 34, 0.56);
  padding: 12px;
}

.security-card-title {
  color: #dce8ff;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.security-item-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.security-item {
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.security-item.security-item-wide {
  grid-column: 1 / -1;
}

.security-item span {
  color: #8ea1c4;
  font-size: 11px;
}

.security-item strong {
  color: #f6f8ff;
  font-size: 12px;
}

.security-origin-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.origin-chip {
  border-radius: 999px;
  border: 1px solid rgba(99, 179, 237, 0.25);
  background: rgba(59, 130, 246, 0.13);
  color: #b6d4ff;
  font-size: 11px;
  padding: 3px 8px;
}

.origin-chip.muted {
  border-color: rgba(148, 163, 184, 0.25);
  background: rgba(100, 116, 139, 0.18);
  color: #c0cad7;
}

.stack-placeholder {
  font-size: 12px;
  color: #8ea1c4;
  line-height: 1.6;
}

.stack-list {
  display: grid;
  gap: 8px;
}

.stack-item {
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 8px 10px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.stack-item strong {
  color: #f5f8ff;
  font-size: 12px;
}

.stack-item span {
  color: #6ee7b7;
  font-size: 11px;
}

.stack-item em {
  color: #fcd34d;
  font-size: 11px;
  font-style: normal;
}

.ai-model-select,
.ai-reason-input {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #e2e8f0;
  font-size: 13px;
  padding: 10px 12px;
  font-family: inherit;
  unicode-bidi: plaintext;
  text-rendering: optimizeLegibility;
}

.ai-model-select:focus,
.ai-reason-input:focus {
  outline: none;
  border-color: rgba(99, 179, 237, 0.5);
}

.ai-model-select option {
  color: #111827;
  background-color: #ffffff;
  font-family: inherit;
  font-size: 14px;
  padding: 8px 12px;
}

.ai-draft-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #e2e8f0;
  font-size: 13px;
  padding: 10px 14px;
  resize: none;
  font-family: inherit;
  line-height: 1.55;
  transition: border-color 0.2s;
}
.ai-draft-input:focus {
  outline: none;
  border-color: rgba(99, 179, 237, 0.5);
}
.ai-draft-input::placeholder { color: rgba(255,255,255,0.28); }

.ai-send-btn {
  padding: 10px 22px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s, background 0.2s;
  white-space: nowrap;
}
.ai-send-btn:hover:not(:disabled) { opacity: 0.88; }
.ai-send-btn:disabled {
  background: linear-gradient(135deg, #7f1d1d, #991b1b);
  cursor: not-allowed;
}

.ai-block-notice {
  margin: 10px 0 0;
  font-size: 12px;
  color: #f87171;
  font-weight: 600;
  line-height: 1.5;
}
.ai-safe-notice {
  margin: 10px 0 0;
  font-size: 12px;
  color: #34d399;
  font-weight: 600;
}

.ai-response-panel {
  margin-top: 12px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 12px;
  background: rgba(10, 20, 38, 0.65);
  padding: 12px;
}

.ai-response-title {
  font-size: 12px;
  font-weight: 700;
  color: #bfd7ff;
  margin-bottom: 8px;
}

.ai-response-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #d9e8ff;
  font-size: 12px;
  line-height: 1.55;
}

.adversarial-floating-wrap {
  position: fixed;
  right: 24px;
  bottom: 36px;
  z-index: 1100;
}

.adversarial-orb {
  min-width: 132px;
  border: 1px solid rgba(125, 190, 255, 0.28);
  background: linear-gradient(135deg, rgba(27, 58, 116, 0.92), rgba(12, 30, 74, 0.94));
  color: #e8f1ff;
  box-shadow: 0 18px 38px rgba(13, 41, 94, 0.42);
  display: grid;
  gap: 4px;
  text-align: left;
}

.adversarial-orb .orb-label {
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9cc3ff;
}

.adversarial-orb strong {
  font-size: 14px;
}

.adversarial-panel {
  width: min(480px, calc(100vw - 28px));
  margin-top: 10px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(110, 162, 255, 0.24);
  max-height: min(72vh, 760px);
  overflow: auto;
}

.adversarial-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.adversarial-close {
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.03);
  color: #d5e4ff;
  border-radius: 10px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}

.adversarial-config {
  margin-top: 8px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 86px 96px auto;
  gap: 8px;
}

.adversarial-config select,
.adversarial-config input {
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: #d6e7ff;
  padding: 8px 10px;
}

.adversarial-run {
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #2d8eff, #1859d6);
  color: #f8fbff;
  font-weight: 700;
  padding: 8px 12px;
  cursor: pointer;
}

.adversarial-run:disabled {
  opacity: 0.68;
  cursor: not-allowed;
}

.adversarial-error {
  margin: 10px 0 0;
  color: #ffb9b9;
  font-size: 12px;
}

.adversarial-summary-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.adversarial-summary-grid article {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  padding: 9px;
  display: grid;
  gap: 4px;
}

.adversarial-summary-grid span {
  color: #90a5c8;
  font-size: 11px;
}

.adversarial-summary-grid strong {
  color: #f4f8ff;
  font-size: 13px;
}

.adversarial-stream {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.adversarial-round {
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 10px;
}

.adversarial-round-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.adversarial-round-top strong {
  color: #ecf3ff;
  font-size: 13px;
}

.adversarial-round-top span {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
}

.adversarial-round-top span.hit {
  background: rgba(255, 92, 92, 0.16);
  color: #ffd7d7;
}

.adversarial-round-top span.block {
  background: rgba(49, 196, 136, 0.16);
  color: #c8ffe6;
}

.adversarial-round p {
  margin: 8px 0 0;
  color: #a4b7d8;
  font-size: 12px;
}

.adversarial-round em {
  margin-top: 6px;
  color: #d9e7ff;
  font-style: normal;
  display: block;
  line-height: 1.6;
  font-size: 12px;
}

.adversarial-recommendations {
  margin-top: 10px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 10px;
}

.adversarial-recommendations h4 {
  margin: 0 0 8px;
  color: #ecf3ff;
  font-size: 13px;
}

.adversarial-recommendations p {
  margin: 6px 0 0;
  color: #9cb1d4;
  line-height: 1.6;
  font-size: 12px;
}

@media (max-width: 768px) {
  .ai-config-row,
  .security-surface {
    grid-template-columns: 1fr;
  }

  .ai-input-row {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .ai-send-btn {
    width: 100%;
  }

  .adversarial-floating-wrap {
    right: 10px;
    bottom: 14px;
  }

  .adversarial-panel {
    width: calc(100vw - 20px);
    max-height: 74vh;
  }

  .adversarial-config,
  .adversarial-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
