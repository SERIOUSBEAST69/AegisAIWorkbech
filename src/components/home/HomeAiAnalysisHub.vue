<template>
  <section class="ai-hub-wrap scene-block">
    <div class="ai-hub-shell card-glass">
      <header class="hub-head">
        <div>
          <div class="hub-kicker">AI FULL-DB ANALYSIS CORE</div>
          <h2>AI全库分析中枢</h2>
          <p>统一汇聚资产、风险、审计、模型调用与闭环状态，构建首页一级指挥视图。</p>
        </div>
        <div class="hub-head-actions">
          <div class="scope-switch">
            <el-radio-group v-model="scope.level" size="small" @change="onScopeLevelChange">
              <el-radio-button label="company" :disabled="!allowedLevels.includes('company')">公司</el-radio-button>
              <el-radio-button label="department" :disabled="!allowedLevels.includes('department')">部门</el-radio-button>
              <el-radio-button label="user" :disabled="!allowedLevels.includes('user')">个人</el-radio-button>
            </el-radio-group>
          </div>
          <el-select
            v-if="scope.level === 'department'"
            v-model="scope.department"
            filterable
            clearable
            size="small"
            placeholder="选择部门"
            style="width: 240px"
            @change="onDepartmentChange"
          >
            <el-option
              v-for="item in scopeOptions.departments"
              :key="item.value"
              :label="`${item.label} · ${item.memberCount}人 · 风险${item.riskEventCount}`"
              :value="item.value"
            />
          </el-select>
          <el-select
            v-if="scope.level === 'user'"
            v-model="scope.username"
            filterable
            clearable
            size="small"
            placeholder="选择个人"
            style="width: 260px"
            @change="onUsernameChange"
          >
            <el-option
              v-for="item in userOptionsForScope"
              :key="item.value"
              :label="`${item.label} · ${item.department} · 风险${item.riskEventCount}`"
              :value="item.value"
            />
          </el-select>
          <el-button :loading="loading" type="primary" @click="$emit('refresh')">刷新中枢</el-button>
        </div>
      </header>

      <div class="hub-kpi-grid">
        <article v-for="kpi in hub.kpis" :key="kpi.key" class="hub-kpi-card">
          <span>{{ kpi.label }}</span>
          <strong>{{ kpi.value }}</strong>
          <em>{{ kpi.note }}</em>
        </article>
      </div>

      <div class="hub-insight-grid">
        <el-card class="hub-panel alert-panel" shadow="never">
          <template #header>
            <div class="panel-headline">关键告警看板</div>
          </template>
          <div class="alert-summary">
            <span>待处置 {{ hub.alertBoard?.pendingCount || 0 }}</span>
            <span>高风险 {{ hub.alertBoard?.highSeverityCount || 0 }}</span>
          </div>
          <div class="alert-list">
            <button
              v-for="item in alertItems"
              :key="item.id"
              type="button"
              class="alert-item"
              @click="$emit('detail', { kind: 'graph-node', key: 'risk', label: item.title })"
            >
              <div class="alert-top">
                <strong>{{ item.title }}</strong>
                <span>{{ item.severity || '-' }}</span>
              </div>
              <p>{{ item.eventType }} · {{ item.status }} · {{ item.username || '未知用户' }}</p>
              <em>{{ item.eventTime }}</em>
            </button>
            <el-empty v-if="alertItems.length === 0" description="暂无关键告警" :image-size="50" />
          </div>
        </el-card>

        <el-card class="hub-panel persona-panel" shadow="never">
          <template #header>
            <div class="panel-headline">当前视角画像</div>
          </template>
          <div class="persona-head">
            <strong>{{ hub.scopePersona?.title || '视角画像' }}</strong>
            <p>{{ hub.scopePersona?.summary || '展示当前层级的治理画像信息。' }}</p>
          </div>
          <div class="persona-tag-row">
            <span v-if="hub.scopePersona?.department">部门：{{ hub.scopePersona.department }}</span>
            <span v-if="hub.scopePersona?.username">成员：{{ hub.scopePersona.username }}</span>
            <span v-if="hub.scopePersona?.roleCode">角色：{{ hub.scopePersona.roleCode }}</span>
          </div>
          <div class="persona-stats">
            <article v-for="item in personaStats" :key="item.label" class="persona-stat-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>
        </el-card>
      </div>

      <div class="hub-main-grid hub-main-grid-revamped">
        <el-card class="hub-panel matrix-panel" shadow="never">
          <template #header>
            <div class="panel-headline">全库战情矩阵</div>
          </template>
          <div class="matrix-grid">
            <article
              v-for="row in matrixRows"
              :key="row.id"
              class="matrix-item"
              @click="$emit('detail', { kind: 'graph-node', key: row.id, label: row.label })"
            >
              <div class="matrix-top">
                <strong>{{ row.label }}</strong>
                <span>{{ row.score }}</span>
              </div>
              <div class="matrix-bar"><i :style="{ width: `${row.score}%`, background: row.color }"></i></div>
              <p>{{ row.note }}</p>
            </article>
          </div>
        </el-card>

        <el-card class="hub-panel radar-panel" shadow="never">
          <template #header>
            <div class="panel-headline">风险热力带</div>
          </template>
          <div class="radar-heat-list">
            <button
              v-for="dim in radarSortedDimensions"
              :key="dim.code"
              type="button"
              class="radar-heat-item"
              @click="$emit('detail', { kind: 'radar-dimension', key: dim.code, label: dim.label })"
            >
              <div class="radar-heat-top">
                <strong>{{ dim.label }}</strong>
                <span>{{ dim.value }}</span>
              </div>
              <div class="radar-heat-track">
                <i :style="{ width: `${dim.value}%` }"></i>
              </div>
            </button>
          </div>
          <div class="radar-tip">点击任一热力项可直接下钻到证据细节。</div>
        </el-card>

        <el-card class="hub-panel pulse-wall-panel" shadow="never">
          <template #header>
            <div class="panel-headline">中枢脉冲墙</div>
          </template>
          <div class="pulse-wall">
            <div v-for="idx in 24" :key="`pulse-${idx}`" class="pulse-node" :style="{ animationDelay: `${idx * 0.08}s` }"></div>
          </div>
        </el-card>
      </div>

      <div class="hub-bottom-grid">
        <el-card class="hub-panel timeline-panel" shadow="never">
          <template #header>
            <div class="panel-headline">实时分析时间线</div>
          </template>
          <div class="timeline-list">
            <article
              v-for="(item, idx) in hub.timeline"
              :key="item.id"
              class="timeline-item"
              :class="{ 'is-new': idx < 2 }"
            >
              <div class="timeline-top">
                <strong>{{ item.title }}</strong>
                <span>{{ item.time }}</span>
              </div>
              <p>{{ item.summary }}</p>
            </article>
            <el-empty v-if="hub.timeline.length === 0" description="暂无时序事件" :image-size="54" />
          </div>
        </el-card>

        <el-card class="hub-panel rec-panel" shadow="never">
          <template #header>
            <div class="panel-headline">策略建议跳转</div>
          </template>
          <div class="rec-list">
            <button
              v-for="rec in hub.recommendations"
              :key="rec.title"
              class="rec-item"
              type="button"
              @click="$emit('jump', rec)"
            >
              <div class="rec-top">
                <strong>{{ rec.title }}</strong>
                <span>{{ rec.priority }}</span>
              </div>
              <p>{{ rec.action }}</p>
              <em>前往 {{ rec.targetLabel }}</em>
            </button>
            <el-empty v-if="hub.recommendations.length === 0" description="暂无建议" :image-size="54" />
          </div>
        </el-card>
      </div>
    </div>

    <button
      type="button"
      class="ai-fab"
      :class="{ active: assistantOpen }"
      @click="assistantOpen = !assistantOpen"
      title="AI分析助手"
    >
      <span>AI</span>
    </button>

    <transition name="ai-dock">
      <section v-if="assistantOpen" class="ai-dock card-glass">
        <header class="ai-dock-head">
          <div>
            <strong>AI分析助手</strong>
            <p>全库数据实时分析与问答</p>
          </div>
          <el-button size="small" @click="assistantOpen = false">关闭</el-button>
        </header>

        <div class="ai-quick-cards">
          <button type="button" class="quick-card" :disabled="quickLoading.weekly" @click="runQuickAction('weekly')">
            <strong>智能周报</strong>
            <span>{{ quickLoading.weekly ? '生成中...' : '一键汇总近7天态势' }}</span>
          </button>
          <button type="button" class="quick-card" :disabled="quickLoading.predict" @click="runQuickAction('predict')">
            <strong>风险预测</strong>
            <span>{{ quickLoading.predict ? '分析中...' : '预测未来风险趋势' }}</span>
          </button>
          <button type="button" class="quick-card" :disabled="quickLoading.compliance" @click="runQuickAction('compliance')">
            <strong>合规体检</strong>
            <span>{{ quickLoading.compliance ? '体检中...' : '生成合规评分与建议' }}</span>
          </button>
        </div>

        <div class="ai-presets">
          <button type="button" @click="sendPreset('总结今日风险')">总结今日风险</button>
          <button type="button" @click="sendPreset('预测下周高风险部门')">预测下周高风险部门</button>
          <button type="button" @click="sendPreset('给出合规优化建议')">给出合规优化建议</button>
        </div>

        <div class="ai-chat-stream">
          <article v-for="item in chatMessages" :key="item.id" class="chat-item" :class="item.role">
            <strong>{{ item.role === 'user' ? '你' : 'AI' }}</strong>
            <template v-if="item.blocks?.length">
              <template v-for="(block, bidx) in item.blocks" :key="`${item.id}-b-${bidx}`">
                <p v-if="block.type === 'text'">{{ block.text }}</p>
                <div v-else-if="block.type === 'bars'" class="mini-bars">
                  <i
                    v-for="bar in block.bars"
                    :key="bar.label"
                    :style="{ height: `${bar.value}%` }"
                    :title="`${bar.label} ${bar.value}`"
                  ></i>
                </div>
              </template>
            </template>
            <p v-else>{{ item.content }}</p>
            <div v-if="!item.blocks?.length && item.chart?.length" class="mini-bars">
              <i v-for="bar in item.chart" :key="bar.label" :style="{ height: `${bar.value}%` }" :title="`${bar.label} ${bar.value}`"></i>
            </div>
          </article>
          <el-empty v-if="chatMessages.length === 0" description="输入问题，AI将结合当前数据库态势回答" :image-size="50" />
        </div>

        <div class="ai-input-row">
          <el-input
            v-model="chatQuestion"
            placeholder="例如：最近一周哪个部门的影子AI最多？"
            @keyup.enter="sendQuestion"
          />
          <el-button type="primary" :loading="chatSending" @click="sendQuestion">发送</el-button>
        </div>
      </section>
    </transition>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../../api/request';
import { dashboardApi } from '../../api/dashboard';

const props = defineProps({
  hub: {
    type: Object,
    default: () => ({
      kpis: [],
      graph: { nodes: [], edges: [] },
      radar: { dimensions: [] },
      timeline: [],
      recommendations: [],
      alertBoard: { items: [] },
      scopePersona: { stats: [] },
    }),
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['refresh', 'jump', 'scope-change', 'detail']);

const scope = ref({
  level: 'company',
  department: '',
  username: '',
});
const scopeOptions = ref({
  allowedLevels: ['company', 'department', 'user'],
  departments: [],
  users: [],
  viewer: { username: '', department: '', roleCode: '' },
});

const assistantOpen = ref(false);
const chatQuestion = ref('');
const chatSending = ref(false);
const chatMessages = ref([]);
const quickLoading = ref({
  weekly: false,
  predict: false,
  compliance: false,
});
const allowedLevels = computed(() => Array.isArray(scopeOptions.value?.allowedLevels) && scopeOptions.value.allowedLevels.length
  ? scopeOptions.value.allowedLevels
  : ['company', 'department', 'user']);
const userOptionsForScope = computed(() => {
  const all = Array.isArray(scopeOptions.value?.users) ? scopeOptions.value.users : [];
  if (scope.value.level !== 'user') {
    return all;
  }
  if (scope.value.level === 'user' && scope.value.department) {
    return all.filter(item => String(item.department || '') === String(scope.value.department || ''));
  }
  return all;
});
const alertItems = computed(() => Array.isArray(props.hub?.alertBoard?.items) ? props.hub.alertBoard.items.slice(0, 6) : []);
const personaStats = computed(() => Array.isArray(props.hub?.scopePersona?.stats) ? props.hub.scopePersona.stats : []);
const matrixRows = computed(() => {
  const nodeMap = new Map((props.hub?.graph?.nodes || []).map(item => [String(item.id), item]));
  const edgeMap = new Map();
  (props.hub?.graph?.edges || []).forEach(edge => {
    const source = String(edge?.source || '');
    const target = String(edge?.target || '');
    if (!source && !target) return;
    edgeMap.set(source, (edgeMap.get(source) || 0) + Number(edge?.value || 0));
    edgeMap.set(target, (edgeMap.get(target) || 0) + Number(edge?.value || 0));
  });
  return (props.hub?.graph?.nodes || [])
    .map(item => {
      const base = Number(item?.value || 0);
      const flow = Number(edgeMap.get(String(item?.id || '')) || 0);
      const score = Math.max(6, Math.min(100, Math.round((base * 9) + (flow * 6))));
      return {
        id: String(item?.id || ''),
        label: String(item?.label || '-'),
        score,
        color: item?.color || '#60a5fa',
        note: `关联强度 ${flow.toFixed(0)} · 基线值 ${base.toFixed(0)}`,
      };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 8);
});
const radarSortedDimensions = computed(() => {
  return (props.hub?.radar?.dimensions || [])
    .map(item => ({
      code: String(item?.code || item?.label || ''),
      label: String(item?.label || '-'),
      value: Math.max(0, Math.min(100, Number(item?.value || 0))),
    }))
    .sort((a, b) => b.value - a.value);
});

function pushAssistantMessage(content, chart = []) {
  chatMessages.value.push({
    id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    role: 'assistant',
    content,
    chart,
    blocks: [
      { type: 'text', text: content },
      ...(Array.isArray(chart) && chart.length ? [{ type: 'bars', bars: chart }] : []),
    ],
  });
}

function radarMiniChart() {
  return (props.hub?.radar?.dimensions || [])
    .slice(0, 6)
    .map(item => ({
      label: String(item?.label || '-'),
      value: Math.max(6, Math.min(100, Number(item?.value || 0))),
    }));
}

function normalizeReply(payload) {
  if (typeof payload === 'string') return payload;
  if (typeof payload?.reply === 'string') return payload.reply;
  if (typeof payload?.content === 'string') return payload.content;
  if (typeof payload?.data?.reply === 'string') return payload.data.reply;
  return '已完成分析。';
}

function normalizeAssistantPayload(payload) {
  const source = payload?.data || payload;
  const answerBlocks = Array.isArray(source?.answerBlocks) ? source.answerBlocks : [];
  const blocks = [];
  for (const block of answerBlocks) {
    const kind = String(block?.type || '').toLowerCase();
    if (kind === 'text' && String(block?.text || '').trim()) {
      blocks.push({ type: 'text', text: String(block.text) });
    }
    if (kind === 'bars' && Array.isArray(block?.bars) && block.bars.length) {
      blocks.push({
        type: 'bars',
        bars: block.bars.map(item => ({
          label: String(item?.label || '-'),
          value: Math.max(6, Math.min(100, Number(item?.value || 0))),
        })),
      });
    }
  }

  const chartFromPayload = Array.isArray(source?.chartData)
    ? source.chartData.map(item => ({
        label: String(item?.label || '-'),
        value: Math.max(6, Math.min(100, Number(item?.value || 0))),
      }))
    : [];

  const fallbackText = normalizeReply(payload);
  if (!blocks.length) {
    const fallbackChart = chartFromPayload.length ? chartFromPayload : radarMiniChart();
    return {
      content: fallbackText,
      chart: fallbackChart,
      blocks: [
        { type: 'text', text: fallbackText },
        ...(fallbackChart.length ? [{ type: 'bars', bars: fallbackChart }] : []),
      ],
    };
  }

  const firstText = blocks.find(item => item.type === 'text')?.text || fallbackText;
  const firstBars = blocks.find(item => item.type === 'bars')?.bars || chartFromPayload;
  return {
    content: firstText,
    chart: firstBars,
    blocks,
  };
}

async function sendQuestion() {
  const q = String(chatQuestion.value || '').trim();
  if (!q) {
    ElMessage.warning('请输入问题');
    return;
  }
  chatMessages.value.push({
    id: `user-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    role: 'user',
    content: q,
    chart: [],
  });
  chatQuestion.value = '';
  chatSending.value = true;
  try {
    const res = await request.post('/ai/chat', {
      provider: 'qwen',
      model: 'qwen-plus',
      accessReason: 'home-ai-assistant',
      messages: [{ role: 'user', content: q }],
    });
    const normalized = normalizeAssistantPayload(res);
    chatMessages.value.push({
      id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      role: 'assistant',
      content: normalized.content,
      chart: normalized.chart,
      blocks: normalized.blocks,
    });
  } catch (error) {
    pushAssistantMessage('当前分析服务暂时繁忙，请稍后重试。', radarMiniChart());
    ElMessage.error(error?.message || 'AI问答失败');
  } finally {
    chatSending.value = false;
  }
}

function sendPreset(question) {
  chatQuestion.value = question;
  sendQuestion();
}

async function runQuickAction(type) {
  quickLoading.value[type] = true;
  try {
    if (type === 'weekly') {
      const data = await dashboardApi.getHomeAiHub({ scopeLevel: scope.value.level || 'company' });
      const risk = (data?.kpis || []).find(item => item?.key === 'riskEvents')?.value ?? '-';
      const calls = (data?.kpis || []).find(item => item?.key === 'aiCalls')?.value ?? '-';
      pushAssistantMessage(`本周治理摘要：累计风险事件 ${risk} 起，模型调用 ${calls} 次，建议优先收敛待处置告警并复核高频异常账号。`, radarMiniChart());
      return;
    }
    if (type === 'predict') {
      const forecast = await dashboardApi.getForecast();
      const next = Number(forecast?.nextDayRiskCount ?? forecast?.predictedRisk ?? 0);
      pushAssistantMessage(`风险预测：按近30天趋势估计，下一个周期高风险事件约 ${next} 起。建议提前加固夜间访问与影子AI检测。`, radarMiniChart());
      return;
    }
    const readiness = await dashboardApi.getAwardReadinessReport();
    const score = Number(readiness?.score ?? readiness?.readinessScore ?? 0);
    pushAssistantMessage(`合规体检结果：当前综合得分 ${score}。建议补齐待处理项、保持审计链连续可验，并提升关键策略覆盖率。`, radarMiniChart());
  } catch (error) {
    ElMessage.error(error?.message || '快捷分析执行失败');
  } finally {
    quickLoading.value[type] = false;
  }
}

async function loadScopeOptions() {
  try {
    const data = await dashboardApi.getHomeAiHubScopeOptions();
    scopeOptions.value = {
      allowedLevels: Array.isArray(data?.allowedLevels) ? data.allowedLevels : ['company', 'department', 'user'],
      departments: Array.isArray(data?.departments) ? data.departments : [],
      users: Array.isArray(data?.users) ? data.users : [],
      viewer: data?.viewer || { username: '', department: '', roleCode: '' },
    };

    if (!allowedLevels.value.includes(scope.value.level)) {
      scope.value.level = allowedLevels.value[0] || 'user';
    }
    if (!scope.value.department && scopeOptions.value.viewer?.department) {
      scope.value.department = scopeOptions.value.viewer.department;
    }
    if (!scope.value.username && scopeOptions.value.viewer?.username) {
      scope.value.username = scopeOptions.value.viewer.username;
    }
    onScopeLevelChange();
  } catch (error) {
    ElMessage.error(error?.message || '加载视角选项失败');
  }
}

function onScopeLevelChange() {
  if (scope.value.level === 'department') {
    if (!scope.value.department) {
      scope.value.department = scopeOptions.value.departments?.[0]?.value || scopeOptions.value.viewer?.department || '';
    }
    scope.value.username = '';
  }
  if (scope.value.level === 'user') {
    if (!scope.value.username) {
      scope.value.username = scopeOptions.value.viewer?.username || userOptionsForScope.value?.[0]?.value || '';
    }
  }
  if (scope.value.level === 'company') {
    scope.value.department = '';
    scope.value.username = '';
  }
  emitScopeChange();
}

function onDepartmentChange() {
  emitScopeChange();
}

function onUsernameChange() {
  emitScopeChange();
}

function emitScopeChange() {
  emit('scope-change', { ...scope.value });
}

onMounted(() => {
  loadScopeOptions();
});
</script>

<style scoped>
.ai-hub-wrap {
  grid-column: 1 / -1;
}

.ai-hub-shell {
  border: 1px solid rgba(102, 147, 198, 0.28);
  background:
    radial-gradient(circle at 8% 8%, rgba(22, 84, 171, 0.28), transparent 36%),
    radial-gradient(circle at 92% 92%, rgba(180, 72, 9, 0.25), transparent 40%),
    linear-gradient(132deg, #071427 0%, #0b1c33 45%, #0e223d 100%);
  padding: 16px;
  border-radius: 18px;
}

.hub-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.hub-kicker {
  color: #8cc0ff;
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hub-head h2 {
  margin: 4px 0;
  color: #f5faff;
  font-family: "Montserrat", "DIN Alternate", "Segoe UI", sans-serif;
}

.hub-head p {
  margin: 0;
  color: #a7c1e2;
  font-size: 12px;
}

.hub-head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.hub-kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.hub-insight-grid {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 10px;
  margin-bottom: 10px;
}

.alert-summary {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #9dc2ec;
  font-size: 12px;
  margin-bottom: 8px;
}

.alert-list {
  display: grid;
  gap: 8px;
}

.alert-item {
  border: 1px solid rgba(127, 170, 219, 0.24);
  border-radius: 10px;
  background: rgba(9, 23, 42, 0.62);
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
}

.alert-item:hover {
  border-color: rgba(251, 191, 36, 0.5);
}

.alert-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.alert-top strong {
  color: #ecf4ff;
  font-size: 12px;
}

.alert-top span {
  color: #fb923c;
  font-size: 12px;
}

.alert-item p,
.alert-item em {
  margin: 6px 0 0;
  color: #9ab5da;
  font-size: 12px;
  font-style: normal;
}

.persona-head strong {
  color: #ecf4ff;
}

.persona-head p {
  margin: 6px 0 0;
  color: #9ab5da;
  font-size: 12px;
}

.persona-tag-row {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.persona-tag-row span {
  border: 1px solid rgba(127, 170, 219, 0.24);
  border-radius: 999px;
  padding: 4px 8px;
  color: #cfe2fb;
  font-size: 12px;
  background: rgba(10, 24, 43, 0.62);
}

.persona-stats {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.persona-stat-item {
  border: 1px solid rgba(127, 170, 219, 0.24);
  border-radius: 10px;
  background: rgba(9, 23, 42, 0.62);
  padding: 8px;
}

.persona-stat-item span {
  color: #9ab5da;
  font-size: 12px;
}

.persona-stat-item strong {
  display: block;
  margin-top: 4px;
  color: #f8fbff;
}

.hub-kpi-card {
  border: 1px solid rgba(117, 163, 219, 0.24);
  border-radius: 12px;
  background: rgba(9, 23, 42, 0.58);
  padding: 10px;
}

.hub-kpi-card span,
.hub-kpi-card em {
  display: block;
  color: #98b6db;
  font-size: 12px;
  font-style: normal;
}

.hub-kpi-card strong {
  display: block;
  margin: 6px 0;
  font-size: 28px;
  color: #f8fbff;
  line-height: 1;
}

.hub-main-grid {
  display: grid;
  grid-template-columns: 1.25fr 0.95fr;
  gap: 10px;
  margin-bottom: 10px;
}

.hub-main-grid-revamped {
  grid-template-columns: 1.25fr 0.95fr 0.8fr;
}

.hub-bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.hub-panel {
  background: rgba(8, 20, 38, 0.72);
  border: 1px solid rgba(89, 132, 182, 0.24);
}

.panel-headline {
  color: #dbeafe;
  font-weight: 600;
}

.hub-chart {
  height: 280px;
}

.matrix-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.matrix-item {
  border: 1px solid rgba(127, 170, 219, 0.24);
  border-radius: 10px;
  background: rgba(9, 23, 42, 0.62);
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.matrix-item:hover {
  border-color: rgba(251, 191, 36, 0.5);
}

.matrix-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.matrix-top strong {
  color: #ecf4ff;
  font-size: 12px;
}

.matrix-top span {
  color: #ffcd8e;
  font-size: 12px;
}

.matrix-bar {
  margin-top: 8px;
  height: 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.matrix-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.matrix-item p {
  margin: 8px 0 0;
  color: #9ab5da;
  font-size: 12px;
}

.radar-heat-list {
  display: grid;
  gap: 8px;
}

.radar-heat-item {
  border: 1px solid rgba(129, 168, 215, 0.25);
  border-radius: 10px;
  background: rgba(11, 25, 45, 0.58);
  color: #cfe2fb;
  text-align: left;
  padding: 8px 10px;
  cursor: pointer;
}

.radar-heat-item:hover {
  border-color: rgba(251, 191, 36, 0.48);
}

.radar-heat-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.radar-heat-top strong {
  font-size: 12px;
}

.radar-heat-top span {
  color: #fbbf24;
  font-size: 12px;
}

.radar-heat-track {
  margin-top: 7px;
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.radar-heat-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #fb923c 0%, #60a5fa 100%);
}

.pulse-wall {
  min-height: 280px;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  align-content: center;
}

.pulse-node {
  height: 18px;
  border-radius: 7px;
  background: linear-gradient(180deg, rgba(124, 194, 255, 0.92), rgba(58, 110, 224, 0.78));
  box-shadow: 0 0 8px rgba(108, 179, 255, 0.35);
  animation: pulseNodeBlink 1.6s ease-in-out infinite;
}

.radar-tip {
  margin-top: 6px;
  color: #8eabd1;
  font-size: 12px;
}

.radar-dim-list {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.radar-dim-item {
  border: 1px solid rgba(129, 168, 215, 0.25);
  border-radius: 8px;
  background: rgba(11, 25, 45, 0.58);
  color: #cfe2fb;
  text-align: left;
  padding: 8px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.radar-dim-item strong {
  font-size: 12px;
}

.radar-dim-item span {
  color: #fbbf24;
  font-size: 12px;
}

.radar-dim-item:hover {
  border-color: rgba(251, 191, 36, 0.48);
}

.timeline-list,
.rec-list {
  display: grid;
  gap: 8px;
}

.timeline-item,
.rec-item {
  border: 1px solid rgba(121, 164, 216, 0.24);
  border-radius: 10px;
  background: rgba(11, 25, 45, 0.58);
  padding: 10px;
}

.timeline-item.is-new {
  animation: timelineFlash 1s ease-in-out;
}

.timeline-top,
.rec-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.timeline-top strong,
.rec-top strong {
  color: #ecf4ff;
}

.timeline-top span,
.rec-top span,
.timeline-item p,
.rec-item p,
.rec-item em {
  color: #99b6dc;
  font-size: 12px;
  font-style: normal;
}

.rec-item {
  text-align: left;
  cursor: pointer;
}

.rec-item:hover {
  border-color: rgba(245, 158, 11, 0.5);
}

.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 68px;
  height: 68px;
  border: 0;
  border-radius: 50%;
  color: #f3f8ff;
  font-size: 22px;
  font-weight: 800;
  cursor: pointer;
  z-index: 80;
  background:
    radial-gradient(circle at 30% 30%, rgba(132, 212, 255, 0.95), rgba(42, 96, 225, 0.95));
  box-shadow: 0 0 0 0 rgba(86, 160, 255, 0.52), 0 10px 28px rgba(9, 38, 92, 0.45);
  animation: aiFabPulse 2.1s infinite;
}

.ai-fab::before {
  content: '';
  position: absolute;
  inset: -9px;
  border-radius: 50%;
  border: 1px solid rgba(121, 180, 255, 0.46);
  animation: aiFabOrbit 3s linear infinite;
}

.ai-fab.active {
  box-shadow: 0 0 0 14px rgba(86, 160, 255, 0.15), 0 10px 28px rgba(9, 38, 92, 0.45);
}

.ai-dock {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 18px;
  width: min(1080px, 60vw);
  max-height: 74vh;
  z-index: 75;
  border-radius: 18px;
  border: 1px solid rgba(123, 167, 224, 0.34);
  background:
    radial-gradient(circle at 10% 0%, rgba(42, 96, 225, 0.16), transparent 38%),
    linear-gradient(140deg, rgba(10, 15, 31, 0.94), rgba(15, 23, 43, 0.92));
  padding: 14px;
  display: grid;
  gap: 10px;
}

.ai-dock-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.ai-dock-head strong {
  color: #f4f8ff;
  font-size: 16px;
}

.ai-dock-head p {
  margin: 4px 0 0;
  color: #9db4d8;
  font-size: 12px;
}

.ai-quick-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.quick-card {
  border: 1px solid rgba(119, 161, 217, 0.26);
  border-radius: 12px;
  background: rgba(12, 22, 40, 0.72);
  color: #e7f0ff;
  text-align: left;
  padding: 10px;
  cursor: pointer;
}

.quick-card strong {
  display: block;
  font-size: 13px;
}

.quick-card span {
  display: block;
  margin-top: 4px;
  color: #9db4d8;
  font-size: 12px;
}

.quick-card:hover {
  border-color: rgba(156, 196, 253, 0.52);
}

.ai-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-presets button {
  border: 1px solid rgba(118, 164, 255, 0.26);
  background: rgba(20, 34, 62, 0.6);
  color: #d8e6ff;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.ai-chat-stream {
  max-height: 34vh;
  overflow: auto;
  display: grid;
  gap: 8px;
  padding-right: 2px;
}

.chat-item {
  border: 1px solid rgba(116, 160, 223, 0.24);
  border-radius: 10px;
  background: rgba(9, 18, 35, 0.58);
  padding: 8px 10px;
}

.chat-item.user {
  border-color: rgba(80, 181, 255, 0.42);
}

.chat-item strong {
  color: #f1f6ff;
  font-size: 12px;
}

.chat-item p {
  margin: 6px 0 0;
  color: #cbdcf8;
  font-size: 13px;
  line-height: 1.6;
}

.mini-bars {
  margin-top: 8px;
  height: 34px;
  display: flex;
  align-items: flex-end;
  gap: 4px;
}

.mini-bars i {
  width: 10px;
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, #9fd2ff 0%, #4c82f5 100%);
}

.ai-input-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.ai-dock-enter-active,
.ai-dock-leave-active {
  transition: all 0.35s cubic-bezier(0.2, 1.35, 0.3, 1);
}

.ai-dock-enter-from,
.ai-dock-leave-to {
  opacity: 0;
  transform: translate(-50%, 18px) scale(0.95);
}

@keyframes aiFabPulse {
  0% { box-shadow: 0 0 0 0 rgba(86, 160, 255, 0.5), 0 10px 28px rgba(9, 38, 92, 0.45); }
  70% { box-shadow: 0 0 0 14px rgba(86, 160, 255, 0), 0 10px 28px rgba(9, 38, 92, 0.45); }
  100% { box-shadow: 0 0 0 0 rgba(86, 160, 255, 0), 0 10px 28px rgba(9, 38, 92, 0.45); }
}

@keyframes aiFabOrbit {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes timelineFlash {
  0% { box-shadow: 0 0 0 rgba(103, 175, 255, 0); border-color: rgba(121, 164, 216, 0.24); }
  45% { box-shadow: 0 0 0 1px rgba(103, 175, 255, 0.58), 0 0 18px rgba(103, 175, 255, 0.32); border-color: rgba(103, 175, 255, 0.72); }
  100% { box-shadow: 0 0 0 rgba(103, 175, 255, 0); border-color: rgba(121, 164, 216, 0.24); }
}

@keyframes pulseNodeBlink {
  0%,
  100% {
    transform: scaleY(0.75);
    opacity: 0.45;
  }
  50% {
    transform: scaleY(1.08);
    opacity: 1;
  }
}

@media (max-width: 1200px) {
  .hub-kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .hub-main-grid {
    grid-template-columns: 1fr;
  }

  .hub-main-grid-revamped {
    grid-template-columns: 1fr;
  }

  .matrix-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hub-kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hub-bottom-grid {
    grid-template-columns: 1fr;
  }

  .hub-head {
    flex-direction: column;
  }

  .radar-dim-list {
    grid-template-columns: 1fr;
  }

  .ai-fab {
    right: 14px;
    bottom: 14px;
    width: 58px;
    height: 58px;
    font-size: 18px;
  }

  .ai-dock {
    left: 0;
    transform: none;
    width: 100vw;
    bottom: 0;
    border-radius: 16px 16px 0 0;
    max-height: 80vh;
  }

  .ai-dock-enter-from,
  .ai-dock-leave-to {
    transform: translateY(18px);
  }

  .ai-quick-cards {
    grid-template-columns: 1fr;
  }
}
</style>
