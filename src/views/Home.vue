<template>
  <div class="workbench-home" ref="stageRef">
    <section class="hero-scene card-glass scene-block" ref="heroRef">
      <div class="hero-copy">
        <div class="eyebrow">{{ personaExperience.kicker }}</div>
        <h1 class="hero-headline">
          <span class="hero-title-primary workbench-title-core" data-workbench-title-anchor="home">{{ heroHeadline.primary }}</span>
          <span v-if="heroHeadline.suffix" class="hero-title-suffix">{{ heroHeadline.suffix }}</span>
        </h1>
        <p>{{ overview.subheadline }}</p>
        <div class="scene-tags">
          <span v-for="tag in overview.sceneTags" :key="tag" class="scene-tag">{{ tag }}</span>
        </div>
        <div class="operator-ribbon">
          <div class="operator-label">当前指挥席位</div>
          <div class="operator-name">{{ overview.operator.displayName }}</div>
          <div class="operator-meta">{{ overview.operator.roleName }} · {{ overview.operator.department }}</div>
        </div>
      </div>

      <div class="hero-stage">
        <div class="forecast-tower">
          <span class="tower-label">明日风险预估</span>
          <strong>{{ overview.trend.forecastNextDay }}</strong>
          <span class="tower-unit">起重点事件</span>
          <div class="tower-divider"></div>
          <div class="tower-footnote">
            {{ forecastDataSource === 'real_db' ? '🟢 真实历史 DB · LSTM 预测' : '⚪ 降级预测' }}
            · 来自近 7 日风险事件数据库聚合
          </div>
        </div>

        <div class="tower-grid">
          <article v-for="metric in overview.metrics.slice(0, 2)" :key="metric.key" class="tower-card">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}{{ metric.suffix }}</strong>
            <em :class="metric.delta >= 0 ? 'rise' : 'fall'">{{ metric.delta >= 0 ? '+' : '' }}{{ metric.delta }}%</em>
          </article>
        </div>
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

    <section class="pulse-grid scene-block">
      <el-card class="pulse-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">治理脉冲引擎</div>
            <p class="panel-subtitle">把数据边界、模型可信、流程闭环和审计准备度压缩成一个可执行脉冲。</p>
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
            <div class="card-header">行动窗口</div>
            <p class="panel-subtitle">引擎自动标出当前最值得优先处理的三类治理压力。</p>
          </div>
        </div>
        <div class="pulse-signal-list">
          <article v-for="signal in trustPulse.signals" :key="signal.title" class="pulse-signal-item">
            <div class="pulse-signal-top">
              <strong>{{ signal.title }}</strong>
              <span :class="['pulse-tone', signal.tone]">{{ signal.value }}</span>
            </div>
            <p>{{ signal.action }}</p>
          </article>
        </div>
      </el-card>
    </section>

    <governance-insight-panel
      :insights="insights"
      :loading="loading"
      class="scene-block"
    />

    <el-card class="chart-card card-glass trend-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">治理脉冲趋势</div>
          <p class="panel-subtitle">风险事件、审计留痕、AI 调用量与成本全部来自数据库聚合结果。</p>
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

    <el-card class="chart-card card-glass award-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">国家级评审材料中枢</div>
          <p class="panel-subtitle">所有指标均由真实治理事件、审计留痕与在线探测产生，自动沉淀为可追溯证据。</p>
        </div>
        <div class="panel-actions">
          <button class="mini-refresh-btn" :disabled="awardLoading" @click="fetchAwardSummary">
            {{ awardLoading ? '刷新中...' : '刷新材料' }}
          </button>
        </div>
      </div>

      <div class="award-grid">
        <article class="award-metric-card">
          <span>误报率下降</span>
          <strong>{{ awardImprovement.falsePositiveReductionPct }}%</strong>
          <em>基线 vs 当前窗口</em>
        </article>
        <article class="award-metric-card">
          <span>响应时长压降</span>
          <strong>{{ awardImprovement.responseTimeReductionPct }}%</strong>
          <em>闭环耗时（秒）</em>
        </article>
        <article class="award-metric-card">
          <span>拦截能力提升</span>
          <strong>{{ awardImprovement.interceptionUpliftPct }}%</strong>
          <em>Blocked 占比提升</em>
        </article>
      </div>

      <div class="award-actions-row">
        <button class="mini-refresh-btn" :disabled="evidenceGenerating" @click="generateComplianceEvidenceNow">
          {{ evidenceGenerating ? '生成中...' : '生成合规证据' }}
        </button>
        <button class="mini-refresh-btn" :disabled="drillRunning" @click="runReliabilityDrillNow">
          {{ drillRunning ? '演练中...' : '执行可靠性演练' }}
        </button>
      </div>

      <div class="award-evidence-line">
        <span>最新证据哈希：</span>
        <strong>{{ awardLatestEvidenceHash }}</strong>
      </div>
      <div class="award-evidence-line">
        <span>最新演练状态：</span>
        <strong>{{ awardLatestDrillStatus }}</strong>
      </div>
    </el-card>

    <el-card class="chart-card card-glass observability-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">Web Vitals 实时观测</div>
          <p class="panel-subtitle">来自真实前端采样（LCP/FCP/CLS/TTFB）的近期质量态势。</p>
        </div>
      </div>
      <div class="web-vitals-grid">
        <article v-for="item in webVitalSummary.summary || []" :key="item.name" class="web-vital-item">
          <strong>{{ item.name }}</strong>
          <span>平均 {{ item.avg }}</span>
          <em>Good {{ item.goodRate }}%</em>
        </article>
      </div>
    </el-card>

    <el-card class="chart-card card-glass observability-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">接口 P95/P99 历史趋势</div>
          <p class="panel-subtitle">按天聚合接口延迟分位点，支持回溯性能回归。</p>
        </div>
      </div>
      <div ref="apiLatencyChartRef" class="chart-canvas trend-canvas"></div>
    </el-card>

    <el-card class="chart-card card-glass observability-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">时间序列对照图卡片</div>
          <p class="panel-subtitle">基线窗口与当前窗口关键指标对照（国奖答辩用）。</p>
        </div>
      </div>
      <div class="compare-grid" v-if="awardSummary.experiment">
        <article class="compare-item">
          <span>基线误报率</span>
          <strong>{{ awardSummary.experiment?.baseline?.falsePositiveRate ?? 0 }}%</strong>
        </article>
        <article class="compare-item">
          <span>当前误报率</span>
          <strong>{{ awardSummary.experiment?.current?.falsePositiveRate ?? 0 }}%</strong>
        </article>
        <article class="compare-item">
          <span>基线平均响应</span>
          <strong>{{ awardSummary.experiment?.baseline?.avgResponseSeconds ?? 0 }}s</strong>
        </article>
        <article class="compare-item">
          <span>当前平均响应</span>
          <strong>{{ awardSummary.experiment?.current?.avgResponseSeconds ?? 0 }}s</strong>
        </article>
      </div>
    </el-card>

    <el-card class="chart-card card-glass risk-card scene-block">
      <div class="panel-head">
        <div>
          <div class="card-header">风险结构剖面</div>
          <p class="panel-subtitle">按严重级别拆解平台内的实际风险事件，不再展示静态占位图。</p>
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
          <p class="panel-subtitle">从风险、告警、主体权利和模型治理中提炼出的下一步动作。</p>
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

    <!-- ── AI 工作台开放面板 ──────────────────────────────────────────── -->
    <el-card class="ai-workbench-card card-glass scene-block" style="grid-column: 1 / -1">
      <div class="panel-head">
        <div>
          <div class="card-header">AI 工作台 · 隐私盾守护</div>
          <p class="panel-subtitle">在此处向 AI 发送消息前，AegisAI 将实时扫描您的输入，阻断含个人隐私信息的请求并检测 AI 响应中的数据外泄风险。</p>
        </div>
        <div class="ps-status-badge" :class="privacyShieldActive ? 'badge-on' : 'badge-off'">
          {{ privacyShieldActive ? '🛡️ 隐私盾已激活' : '⚪ 隐私盾待机' }}
        </div>
      </div>
      <div class="ai-config-row">
        <el-select v-model="selectedAiModelCode" class="ai-model-select" placeholder="请选择已注册模型" style="width: 100%; max-width: 400px;">
          <el-option
            v-for="model in aiModelOptions"
            :key="model.id"
            :label="`${cleanModelName(model.modelName)} (${cleanModelName(model.modelCode)})`"
            :value="model.modelCode"
          />
        </el-select>
        <input
          v-model="aiAccessReason"
          class="ai-reason-input"
          placeholder="访问目的（高风险模型建议填写更具体）"
        />
      </div>
      <div class="ai-meta-row">
        <span class="ai-meta-pill" :class="`state-${aiModelLoadState}`">{{ aiModelLoadLabel }}</span>
        <span v-if="selectedModelLabel" class="ai-meta-pill state-selected">当前模型：{{ selectedModelLabel }}</span>
      </div>
      <p v-if="aiModelLoadMessage" class="ai-model-notice">{{ aiModelLoadMessage }}</p>

      <div class="security-surface">
        <article class="security-card">
          <div class="security-card-title">跨站拦截状态</div>
          <div class="security-item-grid">
            <div class="security-item">
              <span>防护模式</span>
              <strong>{{ crossSiteGuard.enabled ? '强制拦截' : '已关闭' }}</strong>
            </div>
            <div class="security-item">
              <span>已拦截请求</span>
              <strong>{{ crossSiteGuard.blockedCount }}</strong>
            </div>
            <div class="security-item security-item-wide">
              <span>最近一次拦截</span>
              <strong>{{ crossSiteLastBlockedText }}</strong>
            </div>
          </div>
          <div class="security-origin-row">
            <span v-for="origin in crossSiteGuard.allowedOrigins.slice(0, 3)" :key="origin" class="origin-chip">{{ origin }}</span>
            <span v-if="crossSiteGuard.allowedOrigins.length === 0" class="origin-chip muted">未配置来源</span>
          </div>
        </article>

        <article class="security-card">
          <div class="security-card-title">训练模型栈</div>
          <div v-if="aiModelStack.loading" class="stack-placeholder">正在同步 Python 模型指标...</div>
          <div v-else-if="!aiModelStack.available" class="stack-placeholder">
            {{ aiModelStack.message || '训练模型服务暂不可达，请检查 python-service' }}
          </div>
          <div v-else class="stack-list">
            <article v-for="item in aiModelStack.classifierStack.slice(0, 3)" :key="item.name" class="stack-item">
              <strong>{{ item.name }}</strong>
              <span>{{ item.trained ? '已训练' : '规则/零样本' }}</span>
              <em v-if="typeof item.benchmark_accuracy === 'number'">准确率 {{ Math.round(item.benchmark_accuracy * 1000) / 10 }}%</em>
            </article>
          </div>
        </article>
      </div>

      <div class="ai-input-row">
        <textarea
          v-model="aiDraftMessage"
          class="ai-draft-input"
          rows="3"
          placeholder="在此输入您想发给 AI 的消息…（自动检测个人隐私信息）"
          @input="onAiDraftInput"
        ></textarea>
        <button class="ai-send-btn" :disabled="!!privacyBlockReason || aiSending || !selectedAiModelCode" @click="sendAiDraft">
          {{ privacyBlockReason ? '⛔ 已拦截' : (aiSending ? '发送中...' : '发送') }}
        </button>
      </div>
      <p v-if="privacyBlockReason" class="ai-block-notice">{{ privacyBlockReason }}</p>
      <p v-else-if="aiDraftMessage.length > 3" class="ai-safe-notice">✅ 未检测到隐私信息，可安全发送。</p>
      <div v-if="aiResponsePreview" class="ai-response-panel">
        <div class="ai-response-title">模型响应</div>
        <pre class="ai-response-content">{{ aiResponsePreview }}</pre>
      </div>
    </el-card>

    <div v-if="isAdmin" class="adversarial-floating-wrap">
      <button
        class="floating-btn adversarial-orb"
        type="button"
        :disabled="adversarialRunning"
        @click="toggleAdversarialPanel"
      >
        <span class="orb-label">OpenClaw</span>
        <strong>{{ adversarialRunning ? '对弈中' : '攻防演练' }}</strong>
      </button>

      <Transition name="fade-slide">
        <section v-if="adversarialPanelOpen" class="adversarial-panel card-glass">
          <header class="adversarial-head">
            <div>
              <div class="card-header">OpenClaw 攻防实战面板</div>
              <p class="panel-subtitle">真实调用 python-service 的 BattleArena，引擎每轮结果按时间轴实时回放。</p>
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
              {{ adversarialRunning ? '运行中...' : '启动对弈' }}
            </button>
          </div>

          <p v-if="adversarialError" class="adversarial-error">{{ adversarialError }}</p>

          <div v-if="adversarialBattle" class="adversarial-summary-grid">
            <article>
              <span>胜方</span>
              <strong>{{ adversarialBattle.winner }}</strong>
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

    <!-- 浮动隐私盾组件（全局） -->
    <AIPrivacyShield ref="privacyShieldRef" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { ElMessage } from 'element-plus';
import { dashboardApi } from '../api/dashboard';
import request from '../api/request';
import GovernanceInsightPanel from '../components/GovernanceInsightPanel.vue';
import StatCard from '../components/StatCard.vue';
import AIPrivacyShield from '../components/AIPrivacyShield.vue';
import { useUserStore } from '../store/user';
import { getPersonaExperience, personalizeWorkbench } from '../utils/persona';
import { quickPrivacyCheck } from '../utils/privacyPatterns';

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
    },
    riskDistribution: [],
    todos: [],
    feeds: [],
  };
}

const stageRef = ref(null);
const heroRef = ref(null);
const trendChartRef = ref(null);
const riskChartRef = ref(null);
const apiLatencyChartRef = ref(null);
const userStore = useUserStore();
const overview = ref(createEmptyOverview());
const insights = ref({ postureScore: 0, summary: {}, highlights: [], recommendations: [] });
const trustPulse = ref({ score: 0, pulseLevel: '', mission: '', innovationLabel: '', dimensions: [], signals: [] });
const loading = ref(true);
const forecastDataSource = ref('real_db');
const awardLoading = ref(false);
const evidenceGenerating = ref(false);
const drillRunning = ref(false);
const awardSummary = ref({ experiment: {}, latestEvidence: {}, latestDrill: {} });
const webVitalSummary = ref({ summary: [], trend: [] });
const httpHistory = ref({ rows: [] });
const innovationReport = ref({});

// ── AI Privacy Shield ────────────────────────────────────────────────────────
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
const crossSiteGuard = ref({
  loading: false,
  enabled: false,
  mode: 'disabled',
  allowedOrigins: [],
  blockedCount: 0,
  lastBlockedAt: null,
  message: ''
});
const aiModelStack = ref({
  loading: false,
  available: false,
  classifierStack: [],
  benchmark: null,
  message: ''
});
const adversarialPanelOpen = ref(false);
const adversarialRunning = ref(false);
const adversarialMeta = ref({ scenarios: [] });
const adversarialBattle = ref(null);
const adversarialVisibleRounds = ref([]);
const adversarialError = ref('');
const adversarialConfig = ref({
  scenario: 'random',
  rounds: 10,
  seed: ''
});
const isAdmin = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase() === 'ADMIN');
let adversarialPlaybackTimer = null;

let privacyCheckDebounceTimer = null;
function onAiDraftInput() {
  if (privacyCheckDebounceTimer) clearTimeout(privacyCheckDebounceTimer);
  privacyBlockReason.value = '';
  privacyShieldActive.value = true;
  privacyShieldRef.value?.check(aiDraftMessage.value);
  privacyCheckDebounceTimer = setTimeout(async () => {
    const detected = quickPrivacyCheck(aiDraftMessage.value);
    if (detected.length > 0) {
      privacyBlockReason.value = '⚠️ 检测到隐私信息（' + detected.join('、') + '），禁止发送给 AI。';
    } else {
      privacyBlockReason.value = '';
    }
  }, 300);
}

function selectedModel() {
  return aiModelOptions.value.find(item => item.modelCode === selectedAiModelCode.value) || null;
}

const selectedModelLabel = computed(() => {
  const model = selectedModel();
  if (!model) return '';
  const modelName = cleanModelName(model.modelName || model.name || model.modelCode) || '未命名模型';
  const modelCode = cleanModelName(model.modelCode) || '';
  return modelCode ? `${modelName} (${modelCode})` : modelName;
});

const aiModelLoadLabel = computed(() => {
  if (aiModelLoadState.value === 'loading') return '模型列表加载中';
  if (aiModelLoadState.value === 'ready') return `已加载 ${aiModelOptions.value.length} 个可用模型`;
  if (aiModelLoadState.value === 'empty') return '未发现可用模型';
  if (aiModelLoadState.value === 'error') return '模型加载失败';
  return '等待加载模型';
});

const crossSiteLastBlockedText = computed(() => {
  if (!crossSiteGuard.value.lastBlockedAt) return '暂无';
  return new Date(crossSiteGuard.value.lastBlockedAt).toLocaleString('zh-CN', { hour12: false });
});

function normalizeAiReply(data) {
  if (!data) return '';
  if (typeof data.reply === 'string' && data.reply.trim()) return data.reply;
  if (typeof data.raw === 'string' && data.raw.trim()) return data.raw;
  return JSON.stringify(data, null, 2);
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
        summary: {
          winner: 'assessment',
          total_rounds: 0,
          attack_success_rate: Number(data?.riskScore || 0) / 100,
        },
      };
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
let apiLatencyChart;
let resizeHandler;
let securityStatusTimer;
let echartsLib;

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
const personaExperience = computed(() => getPersonaExperience(userStore.userInfo));
const awardImprovement = computed(() => awardSummary.value?.experiment?.improvement || {
  falsePositiveReductionPct: 0,
  responseTimeReductionPct: 0,
  interceptionUpliftPct: 0,
});
const awardLatestEvidenceHash = computed(() => {
  const hash = awardSummary.value?.latestEvidence?.evidenceHash;
  return hash || '暂无';
});
const awardLatestDrillStatus = computed(() => {
  const latest = awardSummary.value?.latestDrill || {};
  if (!latest?.sloStatus) return '暂无';
  return `${latest.sloStatus} · 恢复 ${latest.recoverySeconds ?? 0}s · 可用性 ${latest.sliAvailability ?? 0}%`;
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

  // 若有 LSTM 预测序列，将其拼接在历史 x 轴之后
  const historicLabels = trend.labels || [];
  const forecastLabels = hasForecast
    ? forecastSeries.map((_, i) => `预测+${i + 1}`)
    : [];
  const allLabels = [...historicLabels, ...forecastLabels];

  // 历史部分对应预测列以 null 填充，保证 x 轴对齐
  const forecastPad = hasForecast ? forecastSeries.map(() => null) : [];

  // 历史风险序列留 null 给预测占位，预测序列前面留 null 给历史占位
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

async function renderApiLatencyChart() {
  const echarts = await ensureEcharts();
  if (!apiLatencyChartRef.value) return;
  if (!apiLatencyChart) {
    apiLatencyChart = echarts.init(apiLatencyChartRef.value);
  }
  const rows = Array.isArray(httpHistory.value?.rows) ? httpHistory.value.rows : [];
  const labels = [...new Set(rows.map(item => item.day))];
  const p95Series = labels.map(day => {
    const subset = rows.filter(item => item.day === day);
    if (subset.length === 0) return 0;
    return Math.round((subset.reduce((sum, item) => sum + Number(item.p95 || 0), 0) / subset.length) * 100) / 100;
  });
  const p99Series = labels.map(day => {
    const subset = rows.filter(item => item.day === day);
    if (subset.length === 0) return 0;
    return Math.round((subset.reduce((sum, item) => sum + Number(item.p99 || 0), 0) / subset.length) * 100) / 100;
  });

  apiLatencyChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#b8c2d4' }, data: ['P95', 'P99'] },
    grid: { left: 24, right: 28, top: 42, bottom: 22, containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
      axisLabel: { color: '#93a0b8' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      axisLabel: { color: '#93a0b8' }
    },
    series: [
      {
        name: 'P95',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: p95Series,
        lineStyle: { color: '#6aa6ff', width: 2 },
        itemStyle: { color: '#6aa6ff' }
      },
      {
        name: 'P99',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: p99Series,
        lineStyle: { color: '#ff9d66', width: 2 },
        itemStyle: { color: '#ff9d66' }
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
    const [workbench, insightData, pulseData, forecastData] = await Promise.all([
      dashboardApi.getWorkbench(),
      dashboardApi.getInsights(),
      dashboardApi.getTrustPulse(),
      dashboardApi.getForecast(),
    ]);
    const personalized = personalizeWorkbench(workbench, userStore.userInfo);

    // 将 LSTM 7 日预测序列合并到 trend 中，使预测气泡数字来自实际模型
    if (forecastData?.forecast?.length) {
      const series = forecastData.forecast.map(v => Math.round(v * 10) / 10);
      personalized.trend = {
        ...personalized.trend,
        // 追加 7 日预测时序（供图表展示），并以第一天预测值作为"明日事件数"
        forecastSeries: series,
        forecastNextDay: Math.round(series[0] ?? personalized.trend.forecastNextDay),
      };
      // 标记数据来源（real_db = 实际 LSTM 输出；degraded = 降级均值）
      forecastDataSource.value = forecastData._dataSource || (forecastData.method === 'lstm' ? 'real_db' : 'degraded');
    }

    overview.value = personalized;
    insights.value = insightData;
    trustPulse.value = pulseData;
    await nextTick();
    await Promise.all([renderTrendChart(), renderRiskChart()]);
    playEntryScene();
    await Promise.all([fetchAwardSummary(), fetchObservabilityData(), fetchInnovationReport()]);
  } catch (error) {
    ElMessage.error(error?.message || '首页工作台加载失败');
  } finally {
    loading.value = false;
  }
}

async function fetchAwardSummary() {
  awardLoading.value = true;
  try {
    const summary = await dashboardApi.getAwardSummary();
    awardSummary.value = summary || { experiment: {}, latestEvidence: {}, latestDrill: {} };
  } catch (error) {
    ElMessage.warning(error?.message || '评审材料加载失败');
  } finally {
    awardLoading.value = false;
  }
}

async function fetchObservabilityData() {
  try {
    const [vitals, history] = await Promise.all([
      dashboardApi.getWebVitalSummary(7),
      dashboardApi.getHttpHistory(7),
    ]);
    webVitalSummary.value = vitals || { summary: [], trend: [] };
    httpHistory.value = history || { rows: [] };
    await nextTick();
    await renderApiLatencyChart();
  } catch (error) {
    ElMessage.warning(error?.message || '观测数据加载失败');
  }
}

async function fetchInnovationReport() {
  try {
    const today = new Date();
    const currentTo = today.toISOString().slice(0, 10);
    const currentFromDate = new Date(today);
    currentFromDate.setDate(today.getDate() - 6);
    const baselineToDate = new Date(currentFromDate);
    baselineToDate.setDate(currentFromDate.getDate() - 1);
    const baselineFromDate = new Date(baselineToDate);
    baselineFromDate.setDate(baselineToDate.getDate() - 6);
    innovationReport.value = await dashboardApi.getInnovationReport({
      baselineFrom: baselineFromDate.toISOString().slice(0, 10),
      baselineTo: baselineToDate.toISOString().slice(0, 10),
      currentFrom: currentFromDate.toISOString().slice(0, 10),
      currentTo,
    });
  } catch (error) {
    innovationReport.value = { available: false, message: error?.message || '创新报告加载失败' };
  }
}

async function generateComplianceEvidenceNow() {
  evidenceGenerating.value = true;
  try {
    await dashboardApi.generateComplianceEvidence({});
    ElMessage.success('已生成最新合规证据');
    await fetchAwardSummary();
  } catch (error) {
    ElMessage.error(error?.message || '合规证据生成失败');
  } finally {
    evidenceGenerating.value = false;
  }
}

async function runReliabilityDrillNow() {
  drillRunning.value = true;
  try {
    await dashboardApi.runReliabilityDrill({
      scenario: 'latency-and-failure-observe',
      targetPath: '/api/auth/registration-options',
      injectPath: '/api/non-existent-reliability-probe',
      probeCount: 4,
    });
    ElMessage.success('可靠性演练已完成并入库');
    await fetchAwardSummary();
  } catch (error) {
    ElMessage.error(error?.message || '可靠性演练失败');
  } finally {
    drillRunning.value = false;
  }
}

watch(() => overview.value.trend, async () => {
  await nextTick();
  await Promise.all([renderTrendChart(), renderRiskChart()]);
}, { deep: true });

watch(() => httpHistory.value.rows, async () => {
  await nextTick();
  await renderApiLatencyChart();
}, { deep: true });

onMounted(() => {
  fetchData();
  fetchAiModels();
  fetchCrossSiteGuardStatus(false);
  fetchAiModelStack();
  resizeHandler = () => {
    trendChart?.resize();
    riskChart?.resize();
    apiLatencyChart?.resize();
  };
  window.addEventListener('resize', resizeHandler);
  securityStatusTimer = window.setInterval(() => {
    fetchCrossSiteGuardStatus(true);
  }, 15000);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler);
  if (securityStatusTimer) {
    clearInterval(securityStatusTimer);
  }
  stopAdversarialPlayback();
  trendChart?.dispose();
  riskChart?.dispose();
  apiLatencyChart?.dispose();
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

.hero-scene {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.85fr);
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
.hero-stage {
  position: relative;
  z-index: 1;
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

.hero-stage {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 16px;
}

.forecast-tower,
.tower-card {
  border-radius: 24px;
  border: 1px solid rgba(255,255,255,0.09);
  background: rgba(255,255,255,0.04);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.06);
}

.forecast-tower {
  padding: 24px;
}

.tower-label,
.tower-footnote,
.tower-card span,
.tower-card em {
  color: #93a0b8;
}

.forecast-tower strong {
  display: block;
  margin-top: 10px;
  font-size: 56px;
  line-height: 1;
  color: #f7f9fd;
}

.tower-unit {
  display: inline-block;
  margin-top: 8px;
  color: #cdd8ec;
}

.tower-divider {
  height: 1px;
  margin: 16px 0 14px;
  background: linear-gradient(90deg, rgba(255,255,255,0.24), rgba(255,255,255,0));
}

.tower-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.tower-card {
  padding: 20px;
}

.tower-card strong {
  display: block;
  margin: 14px 0 8px;
  font-size: 26px;
  color: #ffffff;
}

.tower-card em {
  font-style: normal;
  font-weight: 700;
}

.tower-card em.rise {
  color: #6ae6c2;
}

.tower-card em.fall {
  color: #ff8e88;
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

  .tower-grid,
  .todo-item {
    grid-template-columns: 1fr;
  }

  .todo-metric {
    text-align: left;
  }
}

/* ── AI 工作台隐私盾面板 ─────────────────────────────────────────────────── */
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
