<template>
  <section class="fluid-risk-deck card-glass">
    <div class="deck-head">
      <div>
        <div class="deck-kicker">FLUID GLASS RISK FIELD</div>
        <h3>AI 服务流体玻璃风险阵列</h3>
        <p>以下卡牌与 AI 风险评级数量同步，支持 lens/bar/cube 三种玻璃形态参数映射。</p>
      </div>
      <div class="deck-meta">
        <span>总卡牌：{{ normalizedItems.length }}</span>
        <span>主模式：{{ mode }}</span>
      </div>
    </div>

    <div
      ref="deckRef"
      class="deck-grid"
      @pointermove="onPointerMove"
      @pointerleave="onPointerLeave"
    >
      <article
        v-for="(item, idx) in normalizedItems"
        :key="`${item.id || item.name}-${idx}`"
        class="deck-card"
        :style="cardStyle(item, idx)"
      >
        <div class="deck-card-shell" :class="`mode-${resolveMode(item, idx)}`">
          <div class="glass-noise"></div>
          <div class="glass-highlight"></div>
          <div class="card-topline">
            <span class="svc-logo">{{ item.logo || 'AI' }}</span>
            <div class="svc-copy">
              <strong>{{ item.name || '-' }}</strong>
              <em>{{ item.provider || 'Unknown Provider' }}</em>
            </div>
            <span class="risk-chip" :class="`risk-${item.risk_level || 'low'}`">
              {{ item.total_risk_score ?? 0 }}
            </span>
          </div>

          <div class="card-core">
            <div class="score-line">
              <span>风险等级</span>
              <b>{{ riskLabel(item.risk_level) }}</b>
            </div>
            <div class="risk-track">
              <i :style="{ width: `${clampScore(item.total_risk_score)}%` }"></i>
            </div>
            <div class="score-line muted">
              <span>模型分类</span>
              <b>{{ item.category || 'general' }}</b>
            </div>
            <div class="score-line muted">
              <span>数据完整性</span>
              <b :class="isCardComplete(item) ? 'complete-ok' : 'complete-warn'">{{ isCardComplete(item) ? '完整' : '待补充' }}</b>
            </div>
          </div>

          <div class="card-tags">
            <span
              v-for="tag in (item.tags || [])"
              :key="`${item.id || item.name}-${tag}`"
              class="tag-pill"
            >
              {{ tag }}
            </span>
          </div>
        </div>
      </article>

      <div v-if="normalizedItems.length === 0" class="deck-empty">
        暂无 AI 评级服务，待数据加载后自动生成玻璃卡牌。
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  mode: {
    type: String,
    default: 'lens',
  },
  lensProps: {
    type: Object,
    default: () => ({}),
  },
  barProps: {
    type: Object,
    default: () => ({}),
  },
  cubeProps: {
    type: Object,
    default: () => ({}),
  },
});

const MODE_SEQUENCE = ['lens', 'bar', 'cube'];
const pointer = ref({ x: 0.5, y: 0.5 });
const deckRef = ref(null);

const defaultsByMode = {
  lens: {
    scale: 0.25,
    ior: 1.15,
    thickness: 5,
    transmission: 1,
    roughness: 0,
    chromaticAberration: 0.05,
    anisotropy: 0.01,
  },
  bar: {
    scale: 0.24,
    ior: 1.15,
    thickness: 8,
    transmission: 1,
    roughness: 0.02,
    chromaticAberration: 0.04,
    anisotropy: 0.01,
  },
  cube: {
    scale: 0.23,
    ior: 1.2,
    thickness: 6,
    transmission: 1,
    roughness: 0.01,
    chromaticAberration: 0.06,
    anisotropy: 0.015,
  },
};

const normalizedItems = computed(() => {
  return Array.isArray(props.items) ? props.items : [];
});

function resolveMode(item, idx) {
  const explicit = String(item?.mode || '').trim().toLowerCase();
  if (MODE_SEQUENCE.includes(explicit)) {
    return explicit;
  }
  const root = String(props.mode || 'lens').trim().toLowerCase();
  const base = MODE_SEQUENCE.includes(root) ? root : 'lens';
  const offset = MODE_SEQUENCE.indexOf(base);
  return MODE_SEQUENCE[(idx + Math.max(0, offset)) % MODE_SEQUENCE.length];
}

function modeProps(modeKey) {
  const key = MODE_SEQUENCE.includes(modeKey) ? modeKey : 'lens';
  const overrides = key === 'bar'
    ? props.barProps
    : key === 'cube'
      ? props.cubeProps
      : props.lensProps;
  return {
    ...defaultsByMode[key],
    ...(overrides || {}),
  };
}

function clampScore(score) {
  const num = Number(score || 0);
  return Math.max(0, Math.min(100, Math.round(num)));
}

function riskLabel(level) {
  return {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
  }[String(level || '').toLowerCase()] || '待定';
}

function cardStyle(item, idx) {
  const modeKey = resolveMode(item, idx);
  const settings = modeProps(modeKey);
  const px = pointer.value.x;
  const py = pointer.value.y;

  const tiltX = ((0.5 - py) * (18 + settings.thickness * 0.6)).toFixed(2);
  const tiltY = ((px - 0.5) * (24 + settings.thickness * 0.8)).toFixed(2);
  const glow = Math.max(0.16, 0.2 + settings.chromaticAberration * 2.4).toFixed(3);
  const glassOpacity = Math.max(0.32, 0.54 - settings.roughness * 1.6).toFixed(3);
  const depth = Math.max(10, Math.min(42, 14 + settings.thickness * 2 + idx * 0.45)).toFixed(1);

  return {
    '--tilt-x': `${tiltX}deg`,
    '--tilt-y': `${tiltY}deg`,
    '--glass-opacity': glassOpacity,
    '--glow-strength': glow,
    '--depth': `${depth}px`,
    '--ior-scale': String(settings.ior),
    '--card-scale': String(Math.max(0.86, Math.min(1.2, settings.scale * 3.4))),
    '--aberration': String(settings.chromaticAberration),
    '--anisotropy': String(settings.anisotropy),
    '--risk-progress': `${clampScore(item.total_risk_score)}%`,
  };
}

function isCardComplete(item) {
  const tags = Array.isArray(item?.tags) ? item.tags.filter(Boolean) : [];
  return Boolean(
    String(item?.id || '').trim()
    && String(item?.name || '').trim()
    && String(item?.provider || '').trim()
    && String(item?.category || '').trim()
    && Number.isFinite(Number(item?.total_risk_score))
    && String(item?.risk_level || '').trim()
    && tags.length > 0
  );
}

function onPointerMove(event) {
  const host = deckRef.value;
  if (!host) return;
  const rect = host.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  pointer.value = {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
  };
}

function onPointerLeave() {
  pointer.value = { x: 0.5, y: 0.5 };
}
</script>

<style scoped>
.fluid-risk-deck {
  border-radius: 14px;
  padding: 16px;
  border: 1px solid rgba(119, 166, 230, 0.26);
  background:
    radial-gradient(circle at 12% 0%, rgba(95, 171, 255, 0.2), transparent 34%),
    radial-gradient(circle at 88% 100%, rgba(66, 117, 209, 0.16), transparent 30%),
    linear-gradient(160deg, rgba(9, 20, 38, 0.92), rgba(7, 15, 30, 0.9));
}

.deck-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.deck-kicker {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #8ec4ff;
}

.deck-head h3 {
  margin: 4px 0;
  color: #f2f7ff;
  font-size: 18px;
}

.deck-head p {
  margin: 0;
  font-size: 12px;
  color: #9fbadb;
  line-height: 1.6;
}

.deck-meta {
  display: grid;
  gap: 4px;
  justify-items: end;
  align-content: start;
}

.deck-meta span {
  font-size: 11px;
  color: #a5c4ea;
  border: 1px solid rgba(136, 184, 245, 0.28);
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(16, 31, 56, 0.52);
}

.deck-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.deck-card {
  perspective: 980px;
}

.deck-card-shell {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  padding: 12px;
  min-height: 190px;
  border: 1px solid rgba(142, 196, 255, calc(0.3 + var(--aberration) * 1.8));
  background:
    linear-gradient(145deg, rgba(23, 47, 84, var(--glass-opacity)), rgba(11, 28, 56, calc(var(--glass-opacity) - 0.1))),
    radial-gradient(circle at 26% 18%, rgba(190, 220, 255, calc(var(--glow-strength) + 0.08)), transparent 34%),
    radial-gradient(circle at 86% 90%, rgba(101, 171, 255, var(--glow-strength)), transparent 30%);
  transform-style: preserve-3d;
  transform: rotateX(var(--tilt-x)) rotateY(var(--tilt-y)) translateZ(0) scale(var(--card-scale));
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
  box-shadow:
    0 10px 22px rgba(1, 8, 18, 0.45),
    inset 0 0 calc(var(--depth) * 0.5) rgba(188, 223, 255, calc(var(--glow-strength) * 0.52));
}

.deck-card-shell:hover {
  border-color: rgba(174, 219, 255, 0.8);
  box-shadow:
    0 14px 30px rgba(1, 8, 18, 0.52),
    inset 0 0 calc(var(--depth) * 0.56) rgba(188, 223, 255, calc(var(--glow-strength) * 0.6));
}

.deck-card-shell.mode-lens {
  border-radius: 18px;
}

.deck-card-shell.mode-bar {
  border-radius: 10px;
}

.deck-card-shell.mode-cube {
  border-radius: 6px;
}

.glass-noise,
.glass-highlight {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.glass-noise {
  opacity: 0.22;
  background-image: radial-gradient(rgba(222, 242, 255, 0.36) 0.7px, transparent 0.7px);
  background-size: 4px 4px;
}

.glass-highlight {
  background:
    linear-gradient(115deg, rgba(228, 241, 255, 0.32), transparent 36%),
    linear-gradient(200deg, transparent 42%, rgba(74, 134, 230, 0.26) 74%, transparent 100%);
}

.card-topline {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.svc-logo {
  font-size: 20px;
}

.svc-copy strong {
  display: block;
  color: #f1f6ff;
  font-size: 14px;
  line-height: 1.2;
}

.svc-copy em {
  display: block;
  color: #a8c4e8;
  font-size: 11px;
  font-style: normal;
  margin-top: 3px;
}

.risk-chip {
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  padding: 7px 9px;
  border-radius: 999px;
  border: 1px solid rgba(142, 196, 255, 0.34);
  color: #e9f3ff;
  background: rgba(14, 30, 56, 0.68);
}

.risk-chip.risk-high {
  color: #ffd2cf;
  border-color: rgba(251, 113, 133, 0.5);
  background: rgba(103, 26, 44, 0.56);
}

.risk-chip.risk-medium {
  color: #ffe0bf;
  border-color: rgba(251, 146, 60, 0.5);
  background: rgba(104, 46, 16, 0.54);
}

.risk-chip.risk-low {
  color: #d4ffe6;
  border-color: rgba(74, 222, 128, 0.44);
  background: rgba(22, 78, 48, 0.5);
}

.card-core {
  position: relative;
  z-index: 2;
  margin-top: 12px;
  display: grid;
  gap: 6px;
}

.score-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: #d8e8ff;
}

.score-line.muted {
  color: #9bb7db;
}

.complete-ok {
  color: #8de8bc;
}

.complete-warn {
  color: #ffd08a;
}

.risk-track {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  border: 1px solid rgba(154, 200, 255, 0.3);
  background: rgba(188, 219, 255, 0.14);
}

.risk-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #7fb8ff 0%, #6de3ff 56%, #5a96f5 100%);
  width: var(--risk-progress);
  box-shadow: 0 0 12px rgba(120, 190, 255, 0.5);
}

.card-tags {
  position: relative;
  z-index: 2;
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-pill {
  font-size: 11px;
  color: #d6e9ff;
  border: 1px solid rgba(145, 192, 248, 0.3);
  border-radius: 999px;
  padding: 3px 8px;
  background: rgba(18, 37, 66, 0.58);
}

.deck-empty {
  grid-column: 1 / -1;
  border: 1px dashed rgba(132, 176, 236, 0.34);
  border-radius: 12px;
  color: #a6c0e2;
  font-size: 12px;
  padding: 14px;
  text-align: center;
}

@media (max-width: 768px) {
  .deck-grid {
    grid-template-columns: 1fr;
  }

  .deck-meta {
    justify-items: start;
  }
}
</style>
