<template>
  <section class="ai-hub-wrap scene-block">
    <div class="ai-hub-shell card-glass" :class="[`motion-tier-${motionTierSafe}`, { 'reduce-motion': reducedMotion }]">
      <div class="hub-ambient-grid" aria-hidden="true"></div>
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
          <el-button :loading="loading" type="primary" @click="handleRefreshHub">刷新中枢</el-button>
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
        <BorderGlow
          class="hub-glow"
          :edge-sensitivity="36"
          glow-color="218 68 72"
          background-color="#080b12"
          :border-radius="24"
          :glow-radius="34"
          :glow-intensity="1"
          :cone-spread="24"
          :colors="['#6da8ff', '#466bcb', '#1f3e88']"
        >
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
        </BorderGlow>

        <BorderGlow
          class="hub-glow"
          :edge-sensitivity="34"
          glow-color="216 62 70"
          background-color="#080b12"
          :border-radius="24"
          :glow-radius="34"
          :glow-intensity="0.95"
          :cone-spread="22"
          :colors="['#74b3ff', '#4f7be0', '#244999']"
        >
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
        </BorderGlow>
      </div>

      <div class="hub-main-grid hub-main-grid-revamped">
        <BorderGlow
          class="hub-glow"
          :edge-sensitivity="38"
          glow-color="220 66 70"
          background-color="#060910"
          :border-radius="26"
          :glow-radius="42"
          :glow-intensity="1"
          :cone-spread="26"
          :colors="['#79b5ff', '#5388eb', '#2a4ea1']"
        >
          <el-card class="hub-panel matrix-panel" shadow="never">
          <template #header>
            <div class="panel-headline">全库战情矩阵舱</div>
          </template>
          <WarMatrixBay
            :rows="matrixRows"
            :motion-tier="motionTierSafe"
            :reduced-motion="reducedMotion"
            @detail="$emit('detail', $event)"
            @select="handleMatrixSelect"
          />
          </el-card>
        </BorderGlow>

        <BorderGlow
          class="hub-glow"
          :edge-sensitivity="32"
          glow-color="224 72 70"
          background-color="#070a11"
          :border-radius="26"
          :glow-radius="40"
          :glow-intensity="0.95"
          :cone-spread="22"
          :colors="['#7ab6ff', '#5a8ff0', '#2a5aa8']"
        >
          <el-card class="hub-panel radar-panel" shadow="never">
          <template #header>
            <div class="panel-headline">风险热力走廊</div>
          </template>
          <RiskHeatCorridor
            :dimensions="radarSortedDimensions"
            :focus-code="corridorFocusCode"
            :boost-by-code="corridorBoostByCode"
            :motion-tier="motionTierSafe"
            :reduced-motion="reducedMotion"
            @detail="$emit('detail', $event)"
          />
          </el-card>
        </BorderGlow>

      </div>

      <div class="hub-bottom-grid single-panel">
        <BorderGlow
          class="hub-glow"
          :edge-sensitivity="36"
          glow-color="223 70 70"
          background-color="#060812"
          :border-radius="26"
          :glow-radius="45"
          :glow-intensity="1"
          :cone-spread="28"
          :colors="['#72afff', '#4a7de0', '#2552a7']"
        >
          <el-card class="hub-panel deepseek-panel" shadow="never">
          <template #header>
            <div class="panel-headline panel-headline-between">
              <div class="deepseek-title-block">
                <span class="deepseek-title-text">DeepSeek智能解读 <span class="deepseek-emoji" aria-hidden="true">🐋</span></span>
                <small>聚焦 DeepSeek 全库治理解读</small>
              </div>
              <div class="deepseek-header-actions">
                <el-button size="small" :loading="deepseekLoading" @click="refreshDeepseekAnalysis">刷新解读</el-button>
              </div>
            </div>
          </template>
          <div v-if="deepseekText" class="deepseek-content">{{ deepseekText }}</div>
          <el-empty v-else description="暂无智能解读结果" :image-size="54" />
          </el-card>
        </BorderGlow>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { dashboardApi } from '../../api/dashboard';
import BorderGlow from '../../components/BorderGlow.vue';
import WarMatrixBay from './WarMatrixBay.vue';
import RiskHeatCorridor from './RiskHeatCorridor.vue';

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
  motionTier: {
    type: String,
    default: 'high',
  },
  reducedMotion: {
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

const deepseekLoading = ref(false);
const deepseekText = ref('');
const corridorFocusCode = ref('');
const corridorBoostByCode = ref({});
let corridorFocusTimer = null;
const motionTierSafe = computed(() => {
  const raw = String(props.motionTier || 'high').toLowerCase();
  if (raw === 'low' || raw === 'medium' || raw === 'high') return raw;
  return 'high';
});
const allowedLevels = computed(() => Array.isArray(scopeOptions.value?.allowedLevels) && scopeOptions.value.allowedLevels.length
  ? scopeOptions.value.allowedLevels
  : ['company', 'department', 'user']);
const userOptionsForScope = computed(() => {
  const all = Array.isArray(scopeOptions.value?.users) ? scopeOptions.value.users : [];
  if (scope.value.level !== 'user') return all;
  return all;
});
function normalizeText(value) {
  return String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
}

function hasMojibake(value) {
  const text = normalizeText(value);
  if (!text) return false;
  if (/\uFFFD/.test(text)) return true;
  if (/[\u0000-\u001F\u007F]/.test(text)) return true;
  // Common UTF-8/GBK mojibake fragments.
  if (/(Ã.|Â|Ð|Ñ|Ê|Ë|Ï|ï»¿)/.test(text)) return true;
  // Typical UTF-8-decoded-as-Latin1 garbage, e.g. "å®¡è®¡".
  const hasLatin1Noise = /[\u00C0-\u00FF]/.test(text);
  const hasReadableHan = /[\u4E00-\u9FFF]/.test(text);
  if (hasLatin1Noise && !hasReadableHan) return true;
  // High ratio of punctuation/latin fragments usually indicates garbling in this board title.
  const alnum = text.replace(/[^a-zA-Z0-9\u4E00-\u9FFF]/g, '');
  if (alnum.length > 0 && alnum.length <= 3 && text.length >= 8) return true;
  return false;
}

const alertItems = computed(() => {
  const rows = Array.isArray(props.hub?.alertBoard?.items) ? props.hub.alertBoard.items : [];
  return rows
    .map(item => ({
      ...item,
      title: normalizeText(item?.title),
      eventType: normalizeText(item?.eventType),
      status: normalizeText(item?.status),
      username: normalizeText(item?.username),
      eventTime: normalizeText(item?.eventTime),
    }))
    .filter(item => item.title && !hasMojibake(item.title))
    .slice(0, 6);
});
const personaStats = computed(() => Array.isArray(props.hub?.scopePersona?.stats) ? props.hub.scopePersona.stats : []);
const matrixRows = computed(() => {
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

function clearCorridorFocusTimer() {
  if (corridorFocusTimer) {
    window.clearTimeout(corridorFocusTimer);
    corridorFocusTimer = null;
  }
}

function normalizeCode(value) {
  return String(value || '').trim().toLowerCase();
}

function inferCorridorCodeFromText(text) {
  const raw = normalizeCode(text);
  if (!raw) return '';
  if (raw.includes('隐私') || raw.includes('privacy')) return 'privacy';
  if (raw.includes('漂移') || raw.includes('drift')) return 'drift';
  if (raw.includes('审批') || raw.includes('approval')) return 'approval';
  if (raw.includes('资产') || raw.includes('asset')) return 'asset';
  if (raw.includes('影子') || raw.includes('shadow')) return 'shadow';
  if (raw.includes('外部') || raw.includes('external')) return 'external';
  if (raw.includes('风险') || raw.includes('risk')) return 'risk';
  return '';
}

function pickCorridorCode(payload) {
  const hint = normalizeCode(payload?.codeHint);
  const dims = Array.isArray(props.hub?.radar?.dimensions) ? props.hub.radar.dimensions : [];
  const codeSet = new Set(dims.map(item => normalizeCode(item?.code)));
  if (hint && codeSet.has(hint)) return hint;

  const fromLabel = inferCorridorCodeFromText(payload?.label);
  if (fromLabel && codeSet.has(fromLabel)) return fromLabel;

  const fromId = inferCorridorCodeFromText(payload?.id);
  if (fromId && codeSet.has(fromId)) return fromId;

  return normalizeCode(dims?.[0]?.code || '');
}

function handleMatrixSelect(payload) {
  const focus = pickCorridorCode(payload);
  if (!focus) return;
  corridorFocusCode.value = focus;
  corridorBoostByCode.value = {
    [focus]: 20,
  };
  clearCorridorFocusTimer();
  corridorFocusTimer = window.setTimeout(() => {
    corridorFocusCode.value = '';
    corridorBoostByCode.value = {};
    corridorFocusTimer = null;
  }, 8000);
}

function getScopeParams() {
  const params = { scopeLevel: scope.value.level || 'company' };
  if (scope.value.level === 'department' && scope.value.department) {
    params.department = scope.value.department;
  }
  if (scope.value.level === 'user' && scope.value.username) {
    params.username = scope.value.username;
  }
  return params;
}

async function refreshDeepseekAnalysis() {
  deepseekLoading.value = true;
  try {
    const data = await dashboardApi.getHomeAiHubDeepseekAnalysis(getScopeParams());
    deepseekText.value = String(data?.analysis || '').trim();
  } catch (error) {
    deepseekText.value = '';
    ElMessage.error(error?.message || 'DeepSeek解读加载失败');
  } finally {
    deepseekLoading.value = false;
  }
}

function handleRefreshHub() {
  emit('refresh');
  refreshDeepseekAnalysis();
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
  refreshDeepseekAnalysis();
}

onMounted(() => {
  loadScopeOptions();
  refreshDeepseekAnalysis();
});

onBeforeUnmount(() => {
  clearCorridorFocusTimer();
});
</script>

<style scoped>
.ai-hub-wrap {
  grid-column: 1 / -1;
}

.ai-hub-shell {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(102, 147, 198, 0.28);
  background:
    radial-gradient(circle at 8% 8%, rgba(22, 84, 171, 0.28), transparent 36%),
    radial-gradient(circle at 92% 92%, rgba(180, 72, 9, 0.25), transparent 40%),
    linear-gradient(132deg, #071427 0%, #0b1c33 45%, #0e223d 100%);
  padding: 16px;
  border-radius: 18px;
}

.hub-ambient-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background:
    radial-gradient(circle at 22% 20%, rgba(135, 194, 255, 0.16), transparent 44%),
    radial-gradient(circle at 82% 74%, rgba(255, 171, 104, 0.14), transparent 42%),
    repeating-linear-gradient(112deg, rgba(117, 170, 236, 0.08) 0, rgba(117, 170, 236, 0.08) 1px, transparent 1px, transparent 36px);
  mix-blend-mode: screen;
  opacity: 0.46;
  animation: ambientShift 12s ease-in-out infinite;
}

.hub-head {
  position: relative;
  z-index: 1;
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
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.hub-insight-grid {
  position: relative;
  z-index: 1;
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
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.25fr 0.95fr;
  gap: 10px;
  margin-bottom: 10px;
}

.hub-main-grid-revamped {
  grid-template-columns: 1fr;
}

.matrix-panel,
.radar-panel {
  width: 100%;
}

.hub-bottom-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.hub-bottom-grid.single-panel {
  grid-template-columns: 1fr;
}

.hub-panel {
  background: rgba(8, 20, 38, 0.72);
  border: 1px solid rgba(89, 132, 182, 0.24);
}

.panel-headline {
  color: #dbeafe;
  font-weight: 600;
}

.panel-headline-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.deepseek-title-block {
  display: grid;
  gap: 2px;
}

.deepseek-title-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.deepseek-emoji {
  font-size: 16px;
  line-height: 1;
}

.deepseek-title-block small {
  color: #9cb8dd;
  font-size: 11px;
}

.deepseek-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
  color: #9fcbff;
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
  color: #93c3ff;
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
  background: linear-gradient(90deg, #6eaefc 0%, #5f9df8 100%);
}

.deepseek-content {
  color: #dce9ff;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
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
  color: #93c3ff;
  font-size: 12px;
}

.radar-dim-item:hover {
  border-color: rgba(251, 191, 36, 0.48);
}

.hub-glow {
  min-height: 100%;
}

.hub-glow :deep(.border-glow-card) {
  height: 100%;
}

.hub-glow :deep(.border-glow-inner) {
  height: 100%;
}

.hub-panel {
  border: 1px solid rgba(136, 184, 255, 0.2);
  background:
    linear-gradient(145deg, rgba(7, 12, 24, 0.88), rgba(8, 17, 34, 0.78)),
    radial-gradient(circle at 18% 14%, rgba(108, 168, 255, 0.18), transparent 45%);
  backdrop-filter: blur(14px) saturate(132%);
  box-shadow: inset 0 1px 0 rgba(204, 227, 255, 0.09), 0 20px 44px rgba(4, 8, 22, 0.4);
}

.panel-headline {
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #cbe2ff;
}

.alert-item,
.persona-stat-item,
.persona-tag-row span,
.matrix-item,
.radar-heat-item {
  border-color: rgba(124, 176, 255, 0.24);
  background: linear-gradient(132deg, rgba(10, 18, 35, 0.78), rgba(7, 13, 26, 0.64));
}

.alert-item:hover,
.matrix-item:hover,
.radar-heat-item:hover {
  transform: translateY(-1px);
  border-color: rgba(154, 202, 255, 0.58);
  box-shadow: 0 0 0 1px rgba(112, 168, 255, 0.2), 0 14px 30px rgba(0, 0, 0, 0.38);
}

.cinema-matrix-grid {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  padding: 4px;
}

.cinema-matrix-grid::before {
  content: '';
  position: absolute;
  inset: -10% -5%;
  background: repeating-linear-gradient(
    115deg,
    rgba(121, 179, 255, 0.1) 0,
    rgba(121, 179, 255, 0.1) 1px,
    transparent 1px,
    transparent 36px
  );
  opacity: 0.46;
  pointer-events: none;
}

.matrix-item {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
}

.matrix-item::after {
  content: '';
  position: absolute;
  inset: auto -14% -70% -14%;
  height: 70%;
  background: radial-gradient(ellipse at center, rgba(112, 170, 255, 0.3), transparent 70%);
  pointer-events: none;
}

.matrix-bar {
  height: 9px;
  background: rgba(183, 216, 255, 0.08);
}

.matrix-bar i {
  box-shadow: 0 0 12px rgba(119, 176, 255, 0.42);
}

.risk-corridor-list {
  gap: 12px;
}

.corridor-item {
  position: relative;
  border-radius: 16px;
  padding: 11px 12px 12px 18px;
}

.corridor-item::before {
  content: '';
  position: absolute;
  left: 10px;
  top: 50%;
  width: 7px;
  height: 7px;
  margin-top: -3px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(174, 213, 255, 0.96) 0, rgba(92, 154, 255, 0.5) 68%, transparent 100%);
  box-shadow: 0 0 11px rgba(98, 164, 255, 0.58);
}

.corridor-item .radar-heat-track {
  height: 10px;
  border: 1px solid rgba(146, 194, 255, 0.24);
  background: linear-gradient(90deg, rgba(105, 167, 255, 0.12), rgba(84, 138, 255, 0.12));
}

.corridor-item .radar-heat-track i {
  background: linear-gradient(90deg, #6faeff 0%, #4f82e8 42%, #6ec8ff 100%);
  animation: corridorFlow 3.5s linear infinite;
}

.deepseek-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(126, 178, 244, 0.36);
  background:
    radial-gradient(140% 110% at 15% 8%, rgba(112, 176, 255, 0.26), transparent 56%),
    radial-gradient(130% 110% at 88% 100%, rgba(40, 108, 199, 0.34), transparent 60%),
    linear-gradient(168deg, rgba(6, 21, 46, 0.9), rgba(5, 14, 30, 0.86));
}

.deepseek-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 24%, rgba(186, 220, 255, 0.12), transparent 30%),
    repeating-linear-gradient(112deg, rgba(130, 188, 255, 0.08) 0, rgba(130, 188, 255, 0.08) 1px, transparent 1px, transparent 28px);
  mix-blend-mode: screen;
  opacity: 0.58;
}

.deepseek-content {
  position: relative;
  z-index: 2;
  padding: 8px 4px;
  color: #eaf3ff;
  font-size: 15px;
  line-height: 1.9;
  text-shadow: 0 1px 6px rgba(9, 34, 73, 0.5);
  font-weight: 500;
}

.ai-hub-shell.motion-tier-low .hub-ambient-grid,
.ai-hub-shell.reduce-motion .hub-ambient-grid {
  animation: none;
  opacity: 0.24;
}

.ai-hub-shell.motion-tier-low .cinema-matrix-grid::before {
  display: none;
}

.ai-hub-shell.motion-tier-low .corridor-item .radar-heat-track i {
  animation: none;
}

.ai-hub-shell.reduce-motion * {
  transition: none !important;
}

.ai-hub-shell.reduce-motion .deepseek-emoji,
.ai-hub-shell.reduce-motion .corridor-item .radar-heat-track i {
  animation: none !important;
}

@keyframes corridorFlow {
  0% { filter: saturate(1.05) brightness(0.95); }
  50% { filter: saturate(1.28) brightness(1.12); }
  100% { filter: saturate(1.05) brightness(0.95); }
}

@keyframes ambientShift {
  0% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(0, -1.5%, 0) scale(1.02); }
  100% { transform: translate3d(0, 0, 0) scale(1); }
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

  .deepseek-header-actions {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }
}
</style>
