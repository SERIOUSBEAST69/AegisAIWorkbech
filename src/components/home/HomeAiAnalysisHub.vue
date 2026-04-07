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

      </div>

      <div class="hub-bottom-grid single-panel">
        <el-card class="hub-panel deepseek-panel" shadow="never">
          <template #header>
            <div class="panel-headline panel-headline-between">
              <div class="deepseek-title-block">
                <span>DeepSeek智能解读</span>
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
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
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

const deepseekLoading = ref(false);
const deepseekText = ref('');
const allowedLevels = computed(() => Array.isArray(scopeOptions.value?.allowedLevels) && scopeOptions.value.allowedLevels.length
  ? scopeOptions.value.allowedLevels
  : ['company', 'department', 'user']);
const userOptionsForScope = computed(() => {
  const all = Array.isArray(scopeOptions.value?.users) ? scopeOptions.value.users : [];
  if (scope.value.level !== 'user') return all;
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
  grid-template-columns: 1fr;
}

.matrix-panel,
.radar-panel {
  width: 100%;
}

.hub-bottom-grid {
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
  color: #fbbf24;
  font-size: 12px;
}

.radar-dim-item:hover {
  border-color: rgba(251, 191, 36, 0.48);
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
