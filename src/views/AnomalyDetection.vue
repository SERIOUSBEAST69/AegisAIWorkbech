<template>
  <div class="anomaly-page">

    <!-- 页头 -->
    <div class="page-header scene-block">
      <div class="page-header-copy">
        <div class="page-eyebrow">AI BEHAVIOR ANOMALY DETECTION</div>
        <h1 class="page-title">员工 AI 行为异常检测</h1>
        <p class="page-subtitle">
          基于孤立森林（Isolation Forest）模型，对员工 AI 使用行为建立基线，实时检测
          深夜大量发代码、突然访问新 AI 服务、大量数据转储等异常行为，并记录告警日志。
        </p>
      </div>
      <div class="page-header-actions">
        <el-tag v-if="isEmployeeView" type="warning" size="large" effect="dark">
          员工模式：检测强制开启
        </el-tag>
        <el-tag :type="statusTagType" size="large" effect="dark">
          {{ modelStatusLabel }}
        </el-tag>
        <el-button type="primary" :loading="statusLoading" @click="loadStatus">
          <el-icon><Refresh /></el-icon>
          刷新状态
        </el-button>
      </div>
    </div>

    <!-- 模型状态卡片 -->
    <div class="stats-row scene-block">
      <div class="stat-card card-glass">
        <div class="stat-icon">🤖</div>
        <div class="stat-body">
          <div class="stat-value">{{ modelMeta.training_samples || '—' }}</div>
          <div class="stat-label">训练样本数</div>
        </div>
      </div>
      <div class="stat-card card-glass">
        <div class="stat-icon">🚨</div>
        <div class="stat-body">
          <div class="stat-value anomaly-val">{{ modelMeta.anomaly_samples || '—' }}</div>
          <div class="stat-label">异常样本数</div>
        </div>
      </div>
      <div class="stat-card card-glass">
        <div class="stat-icon">📊</div>
        <div class="stat-body">
          <div class="stat-value">
            {{ modelMeta.evaluation_metrics?.f1 != null
               ? (modelMeta.evaluation_metrics.f1 * 100).toFixed(1) + '%'
               : '—' }}
          </div>
          <div class="stat-label">F1 得分</div>
        </div>
      </div>
      <div class="stat-card card-glass">
        <div class="stat-icon">⚡</div>
        <div class="stat-body">
          <div class="stat-value">{{ recentAnomalyCount }}</div>
          <div class="stat-label">近期异常事件</div>
        </div>
      </div>
    </div>

    <!-- 主体：左侧检测面板 + 右侧事件日志 -->
    <div class="main-grid scene-block">

      <!-- 实时检测面板 -->
      <div class="check-panel card-glass">
        <div class="panel-title">🔍 实时行为检测</div>
        <p class="panel-hint">填写一条员工 AI 使用行为记录，点击"检测"获取异常评分。</p>

        <el-form :model="checkForm" label-position="top" size="default" class="check-form">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="员工 ID">
                <el-input v-model="checkForm.employee_id" :disabled="isEmployeeView" placeholder="EMP_R0001" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="部门">
                <el-select v-model="checkForm.department" :disabled="isEmployeeView" style="width:100%">
                  <el-option v-for="d in DEPARTMENTS" :key="d" :label="d" :value="d" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="AI 服务">
                <el-select v-model="checkForm.ai_service" style="width:100%" allow-create filterable>
                  <el-option v-for="s in AI_SERVICES" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="操作时间（小时，0-23）">
                <el-input-number v-model="checkForm.hour_of_day" :min="0" :max="23" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="星期几（0=周一）">
                <el-input-number v-model="checkForm.day_of_week" :min="0" :max="6" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="消息长度（字符数）">
                <el-input-number v-model="checkForm.message_length" :min="1" :max="10000" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="话题类型">
                <el-select v-model="checkForm.topic_code" style="width:100%">
                  <el-option :value="0" label="0 – 代码" />
                  <el-option :value="1" label="1 – 文档" />
                  <el-option :value="2" label="2 – 数据分析" />
                  <el-option :value="3" label="3 – 沟通" />
                  <el-option :value="4" label="4 – 其他" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="会话时长（分钟）">
                <el-input-number v-model="checkForm.session_duration_min" :min="1" :max="300" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="是否首次使用该 AI 服务">
                <el-switch v-model="checkForm.is_new_service_bool" active-text="是（新服务）" inactive-text="否（熟悉服务）" />
              </el-form-item>
            </el-col>
          </el-row>

          <div class="check-actions">
            <el-button type="primary" :loading="checking" style="width:100%" @click="submitCheck">
              🔍 检测异常
            </el-button>
            <el-button v-if="!isEmployeeView" type="default" @click="fillScenario('late_night')">⚡ 演示：深夜代码</el-button>
            <el-button v-if="!isEmployeeView" type="default" @click="fillScenario('new_ai')">⚡ 演示：新AI服务</el-button>
          </div>
        </el-form>

        <!-- 检测结果 -->
        <transition name="fade">
          <div v-if="checkResult" class="check-result" :class="`result-${checkResult.risk_level}`">
            <div class="result-header">
              <span class="result-icon">{{ checkResult.is_anomaly ? '🚨' : '✅' }}</span>
              <span class="result-title">
                {{ checkResult.is_anomaly ? '检测到异常行为' : '行为正常' }}
              </span>
              <el-tag :type="riskTagType(checkResult.risk_level)" size="small" effect="dark">
                {{ riskLabel(checkResult.risk_level) }}
              </el-tag>
            </div>
            <div class="result-score">
              异常分数：<strong>{{ checkResult.anomaly_score?.toFixed(4) }}</strong>
              <span class="score-hint">（越负越异常，阈值约 -0.05）</span>
            </div>
            <div class="result-msg">{{ checkResult.message }}</div>
            <ul v-if="checkResult.reasons?.length" class="result-reasons">
              <li v-for="r in checkResult.reasons" :key="r">{{ r }}</li>
            </ul>
          </div>
        </transition>
      </div>

      <!-- 异常事件日志 -->
      <div class="events-panel card-glass">
        <div class="panel-title-row">
          <div class="panel-title">📋 异常事件日志</div>
          <div class="panel-actions">
            <el-switch v-model="anomalyOnly" active-text="仅显示异常" size="small" @change="loadEvents" />
            <el-button size="small" :loading="eventsLoading" @click="loadEvents">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
        </div>

        <div v-if="eventsLoading" class="events-loading">
          <el-icon class="spin"><Loading /></el-icon> 加载中...
        </div>
        <div v-else-if="events.length === 0" class="events-empty">
          暂无事件记录。使用上方面板提交检测后，记录将出现在这里。
        </div>
        <div v-else class="events-list">
          <div
            v-for="ev in events"
            :key="ev.id"
            class="event-row"
            :class="{ 'event-anomaly': ev.is_anomaly }"
          >
            <div class="event-main">
              <span class="event-icon">{{ ev.is_anomaly ? '🚨' : '✅' }}</span>
              <div class="event-info">
                <div class="event-title">
                  <strong>{{ ev.employee_id }}</strong>
                  <span class="event-dept">{{ ev.department }}</span>
                  → <span class="event-service">{{ ev.ai_service }}</span>
                </div>
                <div class="event-meta">
                  分数 {{ Number(ev.anomaly_score).toFixed(4) }}
                  · <span :class="`risk-inline risk-${ev.risk_level}`">{{ riskLabel(ev.risk_level) }}</span>
                  · {{ formatTime(ev.created_at) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 训练说明 -->
    <div class="train-tip scene-block card-glass">
      <div class="tip-title">⚙️ 模型训练说明</div>
      <ol class="tip-steps">
        <li>安装依赖：<code>pip install scikit-learn joblib numpy faker</code></li>
        <li>生成合成数据（1200 条）：<code>cd python-service && python gen_behavior_data.py</code></li>
        <li>训练孤立森林模型：<code>python train_anomaly.py</code></li>
        <li>启动推理服务：<code>python app.py</code>（或通过 Docker Compose 启动）</li>
        <li>模型文件保存在 <code>python-service/models/</code>，可定期重新训练更新。</li>
      </ol>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh, Loading } from '@element-plus/icons-vue';
import request from '../api/request';
import { useUserStore } from '../store/user';

// ── 常量 ──────────────────────────────────────────────────────────────────────
const DEPARTMENTS = ['研发', '销售', 'HR', '法务', '财务'];
const AI_SERVICES = [
  'ChatGPT', 'Claude', 'Gemini', 'GitHub Copilot',
  '文心一言', '通义千问', '豆包', 'Kimi',
  'Perplexity', 'Ollama',
];

const userStore = useUserStore();

// ── State ─────────────────────────────────────────────────────────────────────
const modelStatus = ref(null);
const modelMeta = ref({});
const statusLoading = ref(false);
const serviceOffline = ref(false);
const events = ref([]);
const eventsLoading = ref(false);
const anomalyOnly = ref(false);
const checking = ref(false);
const checkResult = ref(null);

const checkForm = ref({
  employee_id: 'EMP_R0001',
  department:  '研发',
  ai_service:  'ChatGPT',
  hour_of_day: 14,
  day_of_week: 1,
  message_length: 300,
  topic_code: 0,
  session_duration_min: 12,
  is_new_service_bool: false,
});

// ── Computed ──────────────────────────────────────────────────────────────────
const modelReady = computed(() => modelStatus.value?.model_ready === true);

const statusTagType = computed(() => {
  if (serviceOffline.value) return 'danger';
  return modelReady.value ? 'success' : 'warning';
});
const modelStatusLabel = computed(() => {
  if (serviceOffline.value) return '❌ 推理服务不可用';
  return modelReady.value ? '✅ 模型已就绪' : '⚠️ 模型未训练';
});

const recentAnomalyCount = computed(() =>
  events.value.filter(e => e.is_anomaly).length
);
const isEmployeeView = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase() === 'EMPLOYEE');

// ── API ───────────────────────────────────────────────────────────────────────
async function loadStatus() {
  statusLoading.value = true;
  try {
    const data = await request.get('/anomaly/status');
    modelStatus.value  = data;
    modelMeta.value    = data?.meta || {};
    serviceOffline.value = false;
  } catch (e) {
    serviceOffline.value = true;
    ElMessage.warning('推理服务不可用，请确认 Python 推理服务已启动（cd python-service && python app.py）');
  } finally {
    statusLoading.value = false;
  }
}

async function loadEvents() {
  eventsLoading.value = true;
  try {
    const params = anomalyOnly.value ? '?anomaly_only=true&limit=50' : '?limit=50';
    const data = await request.get(`/anomaly/events${params}`);
    events.value = Array.isArray(data?.events) ? data.events : [];
  } catch (e) {
    events.value = [];
  } finally {
    eventsLoading.value = false;
  }
}

async function submitCheck() {
  checking.value = true;
  checkResult.value = null;
  try {
    const employeeId = userStore.userInfo?.username || checkForm.value.employee_id;
    const employeeDept = userStore.userInfo?.department || checkForm.value.department;
    const payload = {
      ...checkForm.value,
      employee_id: isEmployeeView.value ? employeeId : checkForm.value.employee_id,
      department: isEmployeeView.value ? employeeDept : checkForm.value.department,
      is_new_service: checkForm.value.is_new_service_bool ? 1 : 0,
    };
    delete payload.is_new_service_bool;

    const data = await request.post('/anomaly/check', payload);
    checkResult.value = data;

    if (data?.is_anomaly) {
      ElMessage.warning(`检测到异常：${data.risk_level === 'high' ? '高风险' : '中风险'}`);
    } else {
      ElMessage.success('行为正常');
    }
    // 刷新事件列表
    await loadEvents();
  } catch (e) {
    ElMessage.error(e?.message || '检测失败，请确认 Python 推理服务已启动且模型已训练');
  } finally {
    checking.value = false;
  }
}

// ── 演示场景填充 ──────────────────────────────────────────────────────────────
function fillScenario(scene) {
  if (scene === 'late_night') {
    Object.assign(checkForm.value, {
      employee_id: 'EMP_R0042',
      department: '研发',
      ai_service: 'GitHub Copilot',
      hour_of_day: 2,
      day_of_week: 1,
      message_length: 3200,
      topic_code: 0,
      session_duration_min: 90,
      is_new_service_bool: false,
    });
  } else if (scene === 'new_ai') {
    Object.assign(checkForm.value, {
      employee_id: 'EMP_S0015',
      department: '销售',
      ai_service: 'Ollama',
      hour_of_day: 11,
      day_of_week: 2,
      message_length: 350,
      topic_code: 3,
      session_duration_min: 10,
      is_new_service_bool: true,
    });
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function riskTagType(level) {
  return { high: 'danger', medium: 'warning', low: 'success' }[level] || 'info';
}

function riskLabel(level) {
  return { high: '⚠️ 高风险', medium: '⚡ 中风险', low: '✅ 低风险' }[level] || '未知';
}

function formatTime(ts) {
  if (!ts) return '—';
  return new Date(ts.replace(' ', 'T') + 'Z').toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour12: false,
  });
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────
onMounted(async () => {
  if (isEmployeeView.value) {
    checkForm.value.employee_id = userStore.userInfo?.username || checkForm.value.employee_id;
    checkForm.value.department = userStore.userInfo?.department || checkForm.value.department;
  }
  await loadStatus();
  await loadEvents();
});
</script>

<style scoped>
/* ── Layout ─────────────────────────────────────────────────────────────────── */
.anomaly-page {
  padding: 24px 32px;
  min-height: 100vh;
  color: var(--color-text);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-header-copy { flex: 1; }

.page-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--color-primary);
  text-transform: uppercase;
  margin-bottom: 6px;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--color-text-secondary);
}

.page-subtitle {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  max-width: 640px;
  line-height: 1.6;
}

.page-header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* ── Stats Row ───────────────────────────────────────────────────────────────── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
}

.stat-icon { font-size: 28px; flex-shrink: 0; }

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-primary-light);
}

.stat-value.anomaly-val { color: #f53f3f; }

.stat-label {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* ── Main Grid ───────────────────────────────────────────────────────────────── */
.main-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.check-panel,
.events-panel {
  padding: 20px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.panel-hint {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0 0 16px;
  line-height: 1.5;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* ── Check Form ──────────────────────────────────────────────────────────────── */
.check-form :deep(.el-form-item__label) {
  font-size: 12px;
  color: var(--color-text-muted);
  padding-bottom: 4px;
}

.check-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

/* ── Check Result ────────────────────────────────────────────────────────────── */
.check-result {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid transparent;
}

.result-high   { background: rgba(245, 63, 63, 0.08); border-color: rgba(245, 63, 63, 0.3); }
.result-medium { background: rgba(250, 140, 22, 0.08); border-color: rgba(250, 140, 22, 0.3); }
.result-low    { background: rgba(82, 196, 26, 0.08);  border-color: rgba(82, 196, 26, 0.3); }

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.result-icon   { font-size: 18px; }
.result-title  { font-size: 14px; font-weight: 700; color: var(--color-text-secondary); flex: 1; }

.result-score {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.score-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-left: 4px;
}

.result-msg {
  font-size: 13px;
  color: var(--color-text-soft);
  margin-bottom: 4px;
}

.result-reasons {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.result-reasons li { margin-bottom: 2px; }

/* ── Events Panel ────────────────────────────────────────────────────────────── */
.events-loading,
.events-empty {
  font-size: 13px;
  color: var(--color-text-muted);
  text-align: center;
  padding: 24px 0;
}

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.events-list {
  max-height: 480px;
  overflow-y: auto;
}

.event-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
}

.event-row:last-child { border-bottom: none; }

.event-row.event-anomaly { background: rgba(245, 63, 63, 0.04); }

.event-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.event-icon { font-size: 16px; margin-top: 2px; flex-shrink: 0; }

.event-title {
  font-size: 13px;
  color: var(--color-text-soft);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.event-dept    { font-size: 11px; color: var(--color-text-muted); }
.event-service { color: var(--color-primary-light); font-weight: 600; }

.event-meta {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.risk-inline       { font-weight: 600; }
.risk-high         { color: #f53f3f; }
.risk-medium       { color: #fa8c16; }
.risk-low          { color: #52c41a; }

/* ── Training Tip ────────────────────────────────────────────────────────────── */
.train-tip {
  padding: 16px 20px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
}

.tip-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}

.tip-steps {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 2;
}

.tip-steps code {
  background: var(--color-bg-alt);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-primary-light);
}

/* ── Transition ──────────────────────────────────────────────────────────────── */
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to       { opacity: 0; }

/* ── Responsive ──────────────────────────────────────────────────────────────── */
@media (max-width: 900px) {
  .main-grid   { grid-template-columns: 1fr; }
  .stats-row   { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 480px) {
  .stats-row   { grid-template-columns: 1fr; }
}
</style>
