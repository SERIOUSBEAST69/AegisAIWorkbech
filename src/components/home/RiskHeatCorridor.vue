<template>
  <section class="corridor" :class="[`motion-tier-${motionTierSafe}`, { 'reduce-motion': reducedMotion }]">
    <p class="corridor-tip">拖拽走廊可横向浏览，点击门牌进入维度证据。</p>

    <div
      class="corridor-track"
      ref="trackRef"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @pointerleave="onPointerUp"
    >
      <button
        v-for="gate in gates"
        :key="gate.code"
        type="button"
        class="gate-card"
        :class="[gate.levelClass, { 'is-focused': gate.code === focusCodeSafe }]"
        @click="emit('detail', { kind: 'radar-dimension', key: gate.code, label: gate.label })"
      >
        <div class="gate-lamp"></div>
        <div class="gate-copy">
          <strong>{{ gate.label }}</strong>
          <span>{{ gate.value }}%</span>
        </div>
        <div class="gate-heat-bar">
          <i :style="{ width: `${gate.value}%` }"></i>
        </div>
        <em>{{ gate.tip }}</em>
      </button>
    </div>

    <footer class="corridor-foot">
      <span>当前峰值：{{ peakLabel }}</span>
      <span>高风险门牌：{{ highGateCount }} / {{ gates.length }}</span>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  dimensions: {
    type: Array,
    default: () => [],
  },
  motionTier: {
    type: String,
    default: 'high',
  },
  reducedMotion: {
    type: Boolean,
    default: false,
  },
  focusCode: {
    type: String,
    default: '',
  },
  boostByCode: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits(['detail']);

const trackRef = ref(null);
const dragging = ref(false);
const dragStartX = ref(0);
const dragStartScroll = ref(0);

const motionTierSafe = computed(() => {
  const raw = String(props.motionTier || 'high').toLowerCase();
  if (raw === 'low' || raw === 'medium' || raw === 'high') return raw;
  return 'high';
});

const focusCodeSafe = computed(() => String(props.focusCode || '').toLowerCase());

function categorySpread(code) {
  const key = String(code || '').toLowerCase();
  if (key.includes('privacy') || key.includes('隐私')) return 12;
  if (key.includes('drift') || key.includes('漂移')) return 8;
  if (key.includes('approval') || key.includes('审批')) return 5;
  if (key.includes('asset') || key.includes('资产')) return -3;
  if (key.includes('external') || key.includes('外部')) return -6;
  return 0;
}

const gates = computed(() => {
  const dims = Array.isArray(props.dimensions) ? props.dimensions : [];
  const list = dims.length ? dims : [
    { code: 'asset', label: '资产暴露面', value: 66 },
    { code: 'approval', label: '审批拥塞', value: 49 },
    { code: 'privacy', label: '隐私违规', value: 72 },
    { code: 'drift', label: '模型漂移', value: 58 },
    { code: 'external', label: '外部调用', value: 44 },
  ];
  const sorted = [...list].sort((a, b) => Number(b?.value || 0) - Number(a?.value || 0));
  const rankMap = new Map(sorted.map((item, idx) => [String(item?.code || `gate-${idx}`), idx]));

  return list.map((item, idx) => {
    const code = String(item?.code || `gate-${idx}`);
    const boost = Number(props.boostByCode?.[code] || 0);
    const rank = Number(rankMap.get(code) || 0);
    const rankShift = Math.max(0, (list.length - rank - 1)) * 2.4;
    const wave = Math.sin((idx + 1) * 1.17) * 3.2;
    const spread = categorySpread(code);
    const value = Math.max(0, Math.min(100, Math.round(Number(item?.value || 0) + boost + rankShift + wave + spread)));
    return {
      code,
      label: String(item?.label || '风险维度'),
      value,
      levelClass: value >= 70 ? 'danger' : (value >= 40 ? 'warning' : 'safe'),
      tip: value >= 70 ? '建议立即复核' : (value >= 40 ? '建议跟踪观察' : '整体可控'),
    };
  });
});

const highGateCount = computed(() => gates.value.filter(item => item.value >= 70).length);
const peakLabel = computed(() => {
  if (!gates.value.length) return '-';
  const gate = [...gates.value].sort((a, b) => b.value - a.value)[0];
  return `${gate.label} (${gate.value}%)`;
});

function onPointerDown(event) {
  if (!trackRef.value) return;
  dragging.value = true;
  dragStartX.value = event.clientX;
  dragStartScroll.value = trackRef.value.scrollLeft;
  trackRef.value.setPointerCapture(event.pointerId);
}

function onPointerMove(event) {
  if (!dragging.value || !trackRef.value) return;
  const delta = event.clientX - dragStartX.value;
  trackRef.value.scrollLeft = dragStartScroll.value - delta;
}

function onPointerUp(event) {
  if (!trackRef.value) return;
  if (dragging.value) {
    dragging.value = false;
    if (trackRef.value.hasPointerCapture(event.pointerId)) {
      trackRef.value.releasePointerCapture(event.pointerId);
    }
  }
}
</script>

<style scoped>
.corridor {
  display: grid;
  gap: 12px;
}

.corridor-tip {
  margin: 0;
  color: #98bde8;
  font-size: 12px;
}

.corridor-track {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(180px, 1fr);
  gap: 10px;
  overflow-x: auto;
  padding: 8px;
  border: 1px solid rgba(99, 151, 228, 0.24);
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(7, 14, 28, 0.9), rgba(5, 11, 22, 0.9)),
    radial-gradient(circle at 50% 16%, rgba(100, 150, 225, 0.15), transparent 58%);
  perspective: 900px;
  cursor: grab;
}

.corridor-track:active {
  cursor: grabbing;
}

.gate-card {
  border: 1px solid rgba(124, 175, 245, 0.24);
  border-radius: 12px;
  background: linear-gradient(155deg, rgba(9, 18, 35, 0.9), rgba(6, 13, 25, 0.82));
  color: #dfedff;
  padding: 10px;
  min-height: 154px;
  display: grid;
  gap: 8px;
  align-content: start;
  text-align: left;
  transform: rotateY(-7deg);
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.gate-card:nth-child(even) {
  transform: rotateY(7deg);
}

.gate-card:hover {
  transform: rotateY(-5deg) translateY(-3px);
  border-color: rgba(162, 206, 255, 0.8);
  box-shadow: 0 10px 22px rgba(3, 10, 22, 0.4);
}

.gate-card:nth-child(even):hover {
  transform: rotateY(5deg) translateY(-3px);
}

.gate-card.is-focused {
  border-color: rgba(255, 194, 120, 0.92);
  box-shadow: 0 0 0 1px rgba(255, 194, 120, 0.3), 0 14px 28px rgba(12, 24, 46, 0.46);
}

.gate-lamp {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 10px rgba(119, 175, 255, 0.6);
  background: #67b0ff;
}

.gate-card.warning .gate-lamp {
  background: #f0bd62;
  box-shadow: 0 0 10px rgba(240, 189, 98, 0.6);
}

.gate-card.danger .gate-lamp {
  background: #ff7070;
  box-shadow: 0 0 12px rgba(255, 112, 112, 0.72);
}

.gate-copy {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.gate-copy strong {
  font-size: 13px;
}

.gate-copy span {
  color: #9ec8ff;
  font-size: 12px;
}

.gate-heat-bar {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(171, 207, 255, 0.12);
  border: 1px solid rgba(154, 198, 255, 0.24);
}

.gate-heat-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6caeff 0%, #58d0ff 100%);
  animation: heatPulse 2.8s linear infinite;
}

.gate-card em {
  color: #9bbce3;
  font-size: 11px;
  font-style: normal;
}

.corridor-foot {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #a8c7eb;
  font-size: 12px;
}

.motion-tier-low .gate-card,
.reduce-motion .gate-card {
  transform: none;
  transition: none;
}

.motion-tier-medium .gate-card {
  transform: rotateY(-5deg);
}

.motion-tier-medium .gate-card:nth-child(even) {
  transform: rotateY(5deg);
}

.motion-tier-low .corridor-track,
.reduce-motion .corridor-track {
  perspective: none;
}

.motion-tier-low .gate-heat-bar i,
.reduce-motion .gate-heat-bar i {
  animation: none;
}

@keyframes heatPulse {
  0% { filter: brightness(0.9) saturate(1.02); }
  50% { filter: brightness(1.12) saturate(1.24); }
  100% { filter: brightness(0.9) saturate(1.02); }
}

@media (max-width: 900px) {
  .corridor-foot {
    flex-direction: column;
  }
}
</style>
