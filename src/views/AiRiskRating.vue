<template>
  <div class="risk-rating-page">

    <!-- 页头 -->
    <div class="page-header scene-block">
      <div class="page-header-copy">
        <div class="page-eyebrow">AI SERVICE RISK RATING</div>
        <h1 class="page-title">AI 服务风险评级</h1>
        <p class="page-subtitle">
          对常见 AI 服务进行可验证的风险评估，评级依据包括隐私政策、数据存储地、安全认证及历史泄露事件。
          企业可据此制定 AI 使用白名单与分级管控策略。
        </p>
      </div>
      <div class="page-header-actions">
        <el-tag type="info" size="large">{{ services.length }} 个已收录服务</el-tag>
        <span v-if="updatedAt" class="updated-tag">📅 数据更新：{{ updatedAt }}</span>
        <el-button type="default" :loading="refreshing" @click="refreshData">
          <el-icon><RefreshRight /></el-icon>
          更新数据
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadList">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 评分说明 -->
    <div class="score-legend scene-block card-glass">
      <div class="legend-title">📊 评分维度（满分 100，分数越高风险越大）</div>
      <div class="legend-items">
        <div class="legend-item">
          <span class="legend-dot" style="background:#f53f3f"></span>
          <strong>隐私政策 ×30</strong>
          <span>是否默认使用数据训练模型</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background:#fa8c16"></span>
          <strong>数据存储地 ×25</strong>
          <span>境外存储风险更高（数据出境合规）</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background:#52c41a"></span>
          <strong>安全认证 ×20</strong>
          <span>ISO 27001 / SOC2 / 等保三级认证</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background:#722ed1"></span>
          <strong>历史泄露 ×25</strong>
          <span>历史数据安全事件记录</span>
        </div>
      </div>
    </div>

    <!-- 服务卡片列表 -->
    <div v-loading="loading" class="service-grid scene-block">
      <div
        v-for="svc in services"
        :key="svc.id"
        class="service-card card-glass"
        :class="`risk-border-${svc.risk_level}`"
        @click="openDetail(svc)"
      >
        <!-- 卡片头 -->
        <div class="card-top">
          <span class="svc-logo">{{ svc.logo }}</span>
          <div class="svc-title-group">
            <div class="svc-name">{{ svc.name }}</div>
            <div class="svc-provider">{{ svc.provider }}</div>
          </div>
          <div class="risk-score-circle" :class="`score-${svc.risk_level}`">
            {{ svc.total_risk_score }}
          </div>
        </div>

        <!-- 风险等级徽章 -->
        <div class="risk-badge-row">
          <el-tag :type="riskTagType(svc.risk_level)" size="small" effect="dark">
            {{ riskLabel(svc.risk_level) }}
          </el-tag>
          <!-- 星级评分（1–5 星，星数越少风险越高） -->
          <div class="star-rating" :title="`风险星级 ${scoreToStars(svc.total_risk_score)}/5（星数越少风险越高）`">
            <span
              v-for="n in 5"
              :key="n"
              class="star"
              :class="n <= scoreToStars(svc.total_risk_score) ? 'star-filled' : 'star-empty'"
            >★</span>
          </div>
          <span class="svc-category">{{ categoryLabel(svc.category) }}</span>
        </div>

        <!-- 标签 -->
        <div class="svc-tags">
          <el-tag
            v-for="tag in svc.tags"
            :key="tag"
            size="small"
            :type="tagType(tag)"
            style="margin: 2px"
          >{{ tag }}</el-tag>
        </div>

        <!-- 迷你进度条 -->
        <div class="mini-bar-group">
          <div class="mini-bar-row">
            <span>风险指数</span>
            <el-progress
              :percentage="svc.total_risk_score"
              :color="riskColor(svc.risk_level)"
              :stroke-width="6"
              :show-text="false"
              style="flex:1; margin-left:8px"
            />
          </div>
        </div>

        <div class="card-click-hint">点击查看详情 →</div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog
      v-if="selected"
      v-model="showDetail"
      :title="`${selected.logo} ${selected.name} — 风险详情`"
      width="680px"
      class="risk-detail-dialog"
    >
      <div class="detail-body">
        <!-- 总分 -->
        <div class="detail-score-row">
          <div class="detail-score-circle" :class="`score-${selected.risk_level}`">
            {{ selected.total_risk_score }}
          </div>
          <div class="detail-score-info">
            <div class="detail-svc-name">{{ selected.name }}</div>
            <div class="detail-provider">{{ selected.provider }}</div>
            <el-tag :type="riskTagType(selected.risk_level)" effect="dark" size="large">
              {{ riskLabel(selected.risk_level) }}
            </el-tag>
          </div>
        </div>

        <!-- 描述 -->
        <p class="detail-desc">{{ selected.description }}</p>

        <!-- 各维度得分 -->
        <div class="detail-scores">
          <div
            v-for="(dim, key) in selected.scores"
            :key="key"
            class="dim-card"
          >
            <div class="dim-header">
              <span class="dim-name">{{ dimLabel(key) }}</span>
              <span class="dim-score">{{ dim.value }} / {{ dim.max }}</span>
            </div>
            <el-progress
              :percentage="Math.round((dim.value / dim.max) * 100)"
              :color="dimColor(dim.value, dim.max)"
              :stroke-width="8"
              :show-text="false"
            />
            <p class="dim-detail">{{ dim.detail }}</p>
            <a
              v-if="dim.evidence_url"
              :href="dim.evidence_url"
              target="_blank"
              rel="noopener noreferrer"
              class="dim-source-link"
            >📎 查看信息来源</a>
          </div>
        </div>

        <!-- 建议 -->
        <div class="detail-recommendations">
          <div class="rec-title">💡 安全使用建议</div>
          <p>{{ selected.recommendations }}</p>
        </div>

        <!-- 标签 -->
        <div class="detail-tags">
          <el-tag
            v-for="tag in selected.tags"
            :key="tag"
            :type="tagType(tag)"
            style="margin: 4px"
          >{{ tag }}</el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh, RefreshRight } from '@element-plus/icons-vue';
import request from '../api/request';
import { shouldUseApiFallback } from '../api/fallback';

// ── State ─────────────────────────────────────────────────────────────────────
const services  = ref([]);
const loading   = ref(false);
const refreshing= ref(false);
const showDetail= ref(false);
const selected  = ref(null);
const updatedAt = ref(null);

// ── Mock fallback data ────────────────────────────────────────────────────────
// 当 Python 推理服务不可用时使用静态 mock，保证演示可用
const MOCK_SERVICES = [
  { id: 'gemini',   name: 'Gemini',          provider: 'Google',           logo: '✨', category: 'chat',   total_risk_score: 62, risk_level: 'high',   tags: ['境外存储', '默认训练', '多项安全认证'] },
  { id: 'chatgpt',  name: 'ChatGPT',         provider: 'OpenAI',           logo: '🤖', category: 'chat',   total_risk_score: 55, risk_level: 'medium', tags: ['境外存储', '默认训练', 'SOC2认证'] },
  { id: 'doubao',   name: '豆包',            provider: '字节跳动',          logo: '🫘', category: 'chat',   total_risk_score: 43, risk_level: 'medium', tags: ['境内存储', '默认训练', '等保三级'] },
  { id: 'claude',   name: 'Claude',          provider: 'Anthropic',        logo: '🧡', category: 'chat',   total_risk_score: 44, risk_level: 'medium', tags: ['境外存储', 'SOC2认证', '安全对齐'] },
  { id: 'copilot',  name: 'GitHub Copilot',  provider: 'Microsoft/OpenAI', logo: '🐙', category: 'coding', total_risk_score: 41, risk_level: 'medium', tags: ['境外存储', '企业版不训练', '多项安全认证'] },
  { id: 'wenxin',   name: '文心一言',         provider: '百度',              logo: '🔮', category: 'chat',   total_risk_score: 36, risk_level: 'low',    tags: ['境内存储', '等保三级', 'ISO27001'] },
  { id: 'kimi',     name: 'Kimi',            provider: '月之暗面',           logo: '🌙', category: 'chat',   total_risk_score: 35, risk_level: 'low',    tags: ['境内存储', 'ISO27001', '长文档'] },
  { id: 'tongyi',   name: '通义千问',         provider: '阿里云',             logo: '☁️', category: 'chat',   total_risk_score: 30, risk_level: 'low',    tags: ['境内存储', '企业版不训练', '多项安全认证', '等保三级'] },
];

// ── API ───────────────────────────────────────────────────────────────────────
async function loadList() {
  loading.value = true;
  try {
    // 通过 Java 后端代理调用 Python 推理服务 /api/ai-risk/list
    const data = await request.get('/ai-risk/list');
    services.value = Array.isArray(data?.services) ? data.services : MOCK_SERVICES;
    updatedAt.value = data?.updated_at || null;
  } catch (err) {
    if (shouldUseApiFallback(err)) {
      services.value = MOCK_SERVICES;
    } else {
      ElMessage.warning('风险评级服务暂不可用，已使用内置数据');
      services.value = MOCK_SERVICES;
    }
  } finally {
    loading.value = false;
  }
}

async function refreshData() {
  refreshing.value = true;
  try {
    await request.post('/ai-risk/refresh', {});
    ElMessage.success('风险数据已从数据源重新加载');
    await loadList();
  } catch (err) {
    ElMessage.warning('刷新失败，推理服务暂不可用');
  } finally {
    refreshing.value = false;
  }
}

async function openDetail(svc) {
  showDetail.value = true;
  // 若卡片中已有 scores 字段（从详情 API 获取过），直接展示
  if (svc.scores) {
    selected.value = svc;
    return;
  }
  try {
    const data = await request.get(`/ai-risk/score?service=${encodeURIComponent(svc.id)}`);
    selected.value = data || svc;
    // 更新缓存
    const idx = services.value.findIndex(s => s.id === svc.id);
    if (idx >= 0) services.value[idx] = { ...services.value[idx], ...data };
  } catch {
    selected.value = svc;
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function riskTagType(level) {
  return { high: 'danger', medium: 'warning', low: 'success' }[level] || 'info';
}

function riskLabel(level) {
  return { high: '⚠️ 高风险', medium: '⚡ 中风险', low: '✅ 低风险' }[level] || '未知';
}

function riskColor(level) {
  return { high: '#f53f3f', medium: '#fa8c16', low: '#52c41a' }[level] || '#8297bf';
}

/**
 * 将 0–100 的风险分数转换为 1–5 的星级（星数越多表示该服务越"安全"）。
 * 0–20 → 5 星（极低风险）
 * 21–40 → 4 星
 * 41–60 → 3 星
 * 61–80 → 2 星
 * 81–100 → 1 星（高风险）
 */
function scoreToStars(score) {
  if (score <= 20) return 5;
  if (score <= 40) return 4;
  if (score <= 60) return 3;
  if (score <= 80) return 2;
  return 1;
}

function categoryLabel(cat) {
  return { chat: '对话AI', coding: '编程助手', image: '图像生成', search: 'AI搜索' }[cat] || cat;
}

function dimLabel(key) {
  return {
    trains_on_data: '🔵 隐私政策（数据训练）',
    data_location:  '🟠 数据存储地',
    security_cert:  '🟢 安全认证',
    breach_history: '🔴 历史泄露事件',
  }[key] || key;
}

function dimColor(value, max) {
  const ratio = value / max;
  if (ratio > 0.7) return '#f53f3f';
  if (ratio > 0.4) return '#fa8c16';
  return '#52c41a';
}

function tagType(tag) {
  if (tag.includes('境外') || tag.includes('默认训练')) return 'danger';
  if (tag.includes('境内') || tag.includes('认证') || tag.includes('等保')) return 'success';
  return 'info';
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────
onMounted(loadList);
</script>

<style scoped>
/* ── Layout ─────────────────────────────────────────────────────────────────── */
.risk-rating-page {
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

.updated-tag {
  font-size: 11px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

/* ── Score Legend ────────────────────────────────────────────────────────────── */
.score-legend {
  padding: 16px 20px;
  border-radius: 10px;
  margin-bottom: 24px;
}

.legend-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-soft);
  margin-bottom: 10px;
}

.legend-items {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ── Service Grid ────────────────────────────────────────────────────────────── */
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.service-card {
  padding: 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  border: 1px solid var(--color-border);
}

.service-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.risk-border-high   { border-left: 3px solid #f53f3f; }
.risk-border-medium { border-left: 3px solid #fa8c16; }
.risk-border-low    { border-left: 3px solid #52c41a; }

.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.svc-logo {
  font-size: 28px;
  flex-shrink: 0;
}

.svc-title-group { flex: 1; }

.svc-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.svc-provider {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* ── Score Circle ────────────────────────────────────────────────────────────── */
.risk-score-circle {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
  flex-shrink: 0;
}

.score-high   { background: rgba(245, 63, 63, 0.15); color: #f53f3f; border: 2px solid #f53f3f; }
.score-medium { background: rgba(250, 140, 22, 0.15); color: #fa8c16; border: 2px solid #fa8c16; }
.score-low    { background: rgba(82, 196, 26, 0.15); color: #52c41a; border: 2px solid #52c41a; }

.risk-badge-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

/* ── Star Rating ─────────────────────────────────────────────────────────────── */
.star-rating {
  display: flex;
  gap: 1px;
  cursor: default;
}

.star {
  font-size: 13px;
  line-height: 1;
}

.star-filled  { color: #faad14; }
.star-empty   { color: rgba(250, 173, 20, 0.25); }

.svc-category {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.svc-tags { margin-bottom: 10px; }

/* ── Mini Bar ────────────────────────────────────────────────────────────────── */
.mini-bar-group { margin-top: 6px; }

.mini-bar-row {
  display: flex;
  align-items: center;
  font-size: 11px;
  color: var(--color-text-muted);
  gap: 4px;
}

.card-click-hint {
  margin-top: 10px;
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-align: right;
  opacity: 0.7;
}

/* ── Detail Dialog ───────────────────────────────────────────────────────────── */
.detail-body { color: var(--color-text); }

.detail-score-row {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

.detail-score-circle {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  flex-shrink: 0;
}

.detail-svc-name {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
}

.detail-provider {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.detail-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.7;
  margin-bottom: 16px;
  padding: 10px 14px;
  background: var(--color-bg-alt);
  border-radius: 6px;
}

.detail-scores {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.dim-card {
  background: var(--color-bg-alt);
  border-radius: 8px;
  padding: 12px;
}

.dim-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.dim-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-soft);
}

.dim-score {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary-light);
}

.dim-detail {
  font-size: 11px;
  color: var(--color-text-muted);
  margin: 6px 0 4px;
  line-height: 1.5;
}

.dim-source-link {
  font-size: 11px;
  color: var(--color-primary);
  text-decoration: none;
}

.dim-source-link:hover { text-decoration: underline; }

.detail-recommendations {
  background: rgba(95, 135, 255, 0.08);
  border: 1px solid rgba(95, 135, 255, 0.2);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.rec-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary-light);
  margin-bottom: 6px;
}

.detail-recommendations p {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.6;
  margin: 0;
}

.detail-tags { margin-top: 8px; }

/* ── Responsive ──────────────────────────────────────────────────────────────── */
@media (max-width: 600px) {
  .detail-scores { grid-template-columns: 1fr; }
  .service-grid  { grid-template-columns: 1fr; }
}
</style>
