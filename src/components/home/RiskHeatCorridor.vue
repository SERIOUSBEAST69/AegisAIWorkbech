<template>
  <section class="corridor" :class="[`motion-tier-${motionTierSafe}`, { 'reduce-motion': reducedMotion }]">
    <div class="corridor-header">
      <h3 class="corridor-title">风险热力走廊</h3>
      <p class="corridor-tip">拖拽走廊可横向浏览，点击门牌进入维度证据。</p>
    </div>

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
        @click="handleGateClick(gate)"
      >
        <div class="gate-lamp"></div>
        <div class="gate-copy">
          <strong>{{ gate.label }}</strong>
          <span>{{ gate.value }}%</span>
        </div>
        <div class="gate-heat-bar">
          <i :style="{ width: `${gate.value}%`, backgroundColor: gate.heatColor }"></i>
        </div>
        <em>{{ gate.tip }}</em>
        <div class="gate-trend">
          <span class="trend-label">趋势</span>
          <span class="trend-value" :class="gate.trendClass">{{ gate.trend }}%</span>
        </div>
      </button>
    </div>

    <footer class="corridor-foot">
      <div class="corridor-stats">
        <span>当前峰值：{{ peakLabel }}</span>
        <span>高风险门牌：{{ highGateCount }} / {{ gates.length }}</span>
        <span>平均风险：{{ averageRisk }}%</span>
      </div>
      <div class="corridor-controls">
        <button 
          class="control-btn" 
          @click="scrollToStart"
          title="滚动到开始"
        >
          ← 开始
        </button>
        <button 
          class="control-btn" 
          @click="scrollToEnd"
          title="滚动到末尾"
        >
          末尾 →
        </button>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue';

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
const realTimeDimensions = ref([]);
const trends = ref({});

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
  if (key.includes('leak') || key.includes('泄露')) return 10;
  return 0;
}

function getHeatColor(value) {
  if (value >= 70) return '#ff6f6f';
  if (value >= 40) return '#f4b74a';
  return '#4ea4ff';
}

function getTrendClass(trend) {
  if (trend > 0) return 'trend-up';
  if (trend < 0) return 'trend-down';
  return 'trend-stable';
}

const gates = computed(() => {
  const dims = Array.isArray(props.dimensions) ? props.dimensions : [];
  const list = dims.length ? dims : [
    { code: 'asset', label: '资产暴露面', value: 66 },
    { code: 'approval', label: '审批拥塞', value: 49 },
    { code: 'privacy', label: '隐私违规', value: 72 },
    { code: 'drift', label: '模型漂移', value: 58 },
    { code: 'external', label: '外部调用', value: 44 },
    { code: 'data-leak', label: '数据泄露', value: 78 },
    { code: 'shadow-ai', label: '影子AI', value: 65 },
    { code: 'compliance', label: '合规风险', value: 52 },
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
    const trend = trends.value[code] || 0;
    
    return {
      code,
      label: String(item?.label || '风险维度'),
      value,
      levelClass: value >= 70 ? 'danger' : (value >= 40 ? 'warning' : 'safe'),
      tip: value >= 70 ? '建议立即复核' : (value >= 40 ? '建议跟踪观察' : '整体可控'),
      heatColor: getHeatColor(value),
      trend: trend,
      trendClass: getTrendClass(trend),
    };
  });
});

const highGateCount = computed(() => gates.value.filter(item => item.value >= 70).length);
const peakLabel = computed(() => {
  if (!gates.value.length) return '-';
  const gate = [...gates.value].sort((a, b) => b.value - a.value)[0];
  return `${gate.label} (${gate.value}%)`;
});

const averageRisk = computed(() => {
  if (!gates.value.length) return 0;
  const total = gates.value.reduce((sum, gate) => sum + gate.value, 0);
  return Math.round(total / gates.value.length);
});

function onPointerDown(event) {
  if (!trackRef.value) return;
  dragging.value = true;
  dragStartX.value = event.clientX;
  dragStartScroll.value = trackRef.value.scrollLeft;
  trackRef.value.setPointerCapture(event.pointerId);
  trackRef.value.classList.add('dragging');
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
    trackRef.value.classList.remove('dragging');
  }
}

function handleGateClick(gate) {
  emit('detail', { kind: 'radar-dimension', key: gate.code, label: gate.label });
  
  // 添加点击效果
  const element = document.querySelector(`[key="${gate.code}"]`);
  if (element) {
    element.classList.add('clicked');
    setTimeout(() => element.classList.remove('clicked'), 300);
  }
}

function scrollToStart() {
  if (trackRef.value) {
    trackRef.value.scrollTo({ left: 0, behavior: 'smooth' });
  }
}

function scrollToEnd() {
  if (trackRef.value) {
    trackRef.value.scrollTo({ 
      left: trackRef.value.scrollWidth - trackRef.value.clientWidth, 
      behavior: 'smooth' 
    });
  }
}

// 模拟实时数据更新
onMounted(() => {
  // 初始加载模拟数据
  realTimeDimensions.value = [
    { code: 'asset', label: '资产暴露面', value: 66 + Math.random() * 10 - 5 },
    { code: 'approval', label: '审批拥塞', value: 49 + Math.random() * 10 - 5 },
    { code: 'privacy', label: '隐私违规', value: 72 + Math.random() * 10 - 5 },
    { code: 'drift', label: '模型漂移', value: 58 + Math.random() * 10 - 5 },
    { code: 'external', label: '外部调用', value: 44 + Math.random() * 10 - 5 },
    { code: 'data-leak', label: '数据泄露', value: 78 + Math.random() * 10 - 5 },
    { code: 'shadow-ai', label: '影子AI', value: 65 + Math.random() * 10 - 5 },
    { code: 'compliance', label: '合规风险', value: 52 + Math.random() * 10 - 5 },
  ];
  
  // 初始化趋势数据
  realTimeDimensions.value.forEach(item => {
    trends.value[item.code] = Math.round(Math.random() * 10 - 5);
  });
  
  // 每5秒更新一次数据
  const interval = setInterval(() => {
    realTimeDimensions.value = realTimeDimensions.value.map(item => {
      const oldValue = item.value;
      const newValue = Math.max(20, Math.min(95, item.value + Math.random() * 8 - 4));
      trends.value[item.code] = Math.round(newValue - oldValue);
      return {
        ...item,
        value: newValue,
      };
    });
  }, 5000);
  
  // 清理定时器
  return () => clearInterval(interval);
});
</script>

<style scoped>
.corridor {
  display: grid;
  gap: 12px;
}

.corridor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.corridor-title {
  margin: 0;
  color: #f5fbff;
  font-size: 14px;
  font-weight: 700;
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
  scrollbar-width: thin;
  scrollbar-color: rgba(120, 175, 248, 0.3) rgba(8, 18, 34, 0.7);
}

.corridor-track:hover {
  border-color: rgba(99, 151, 228, 0.4);
}

.corridor-track:active {
  cursor: grabbing;
}

.corridor-track.dragging {
  user-select: none;
}

.corridor-track::-webkit-scrollbar {
  height: 6px;
}

.corridor-track::-webkit-scrollbar-track {
  background: rgba(8, 18, 34, 0.7);
  border-radius: 999px;
}

.corridor-track::-webkit-scrollbar-thumb {
  background: rgba(120, 175, 248, 0.3);
  border-radius: 999px;
}

.corridor-track::-webkit-scrollbar-thumb:hover {
  background: rgba(120, 175, 248, 0.5);
}

.gate-card {
  border: 1px solid rgba(124, 175, 245, 0.24);
  border-radius: 12px;
  background: linear-gradient(155deg, rgba(9, 18, 35, 0.9), rgba(6, 13, 25, 0.82));
  color: #dfedff;
  padding: 12px;
  min-height: 160px;
  display: grid;
  gap: 8px;
  align-content: start;
  text-align: left;
  transform: rotateY(-7deg);
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
  position: relative;
  overflow: hidden;
}

.gate-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--gate-color, #67b0ff), transparent);
  opacity: 0.6;
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

.gate-card.clicked {
  animation: clickEffect 0.3s ease-in-out;
}

.gate-lamp {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 10px rgba(119, 175, 255, 0.6);
  background: #67b0ff;
  transition: all 0.3s ease;
}

.gate-card.warning .gate-lamp {
  background: #f0bd62;
  box-shadow: 0 0 10px rgba(240, 189, 98, 0.6);
}

.gate-card.danger .gate-lamp {
  background: #ff7070;
  box-shadow: 0 0 12px rgba(255, 112, 112, 0.72);
  animation: pulse 2s ease-in-out infinite;
}

.gate-copy {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.gate-copy strong {
  font-size: 13px;
  font-weight: 600;
}

.gate-copy span {
  color: #9ec8ff;
  font-size: 12px;
  font-weight: 500;
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
  transition: width 0.5s ease;
}

.gate-card em {
  color: #9bbce3;
  font-size: 11px;
  font-style: normal;
}

.gate-trend {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.trend-label {
  color: #9bbce3;
  font-size: 11px;
}

.trend-value {
  font-size: 11px;
  font-weight: 600;
}

.trend-up {
  color: #4eff8f;
}

.trend-down {
  color: #ff7070;
}

.trend-stable {
  color: #9ec8ff;
}

.corridor-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.corridor-stats {
  display: flex;
  gap: 15px;
  color: #a8c7eb;
  font-size: 12px;
}

.corridor-controls {
  display: flex;
  gap: 8px;
}

.control-btn {
  border: 1px solid rgba(120, 175, 248, 0.32);
  background: rgba(8, 18, 34, 0.7);
  color: #cae1fb;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.control-btn:hover {
  border-color: rgba(120, 175, 248, 0.6);
  background: rgba(8, 18, 34, 0.9);
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

@keyframes pulse {
  0% { box-shadow: 0 0 12px rgba(255, 112, 112, 0.72); }
  50% { box-shadow: 0 0 20px rgba(255, 112, 112, 0.9); }
  100% { box-shadow: 0 0 12px rgba(255, 112, 112, 0.72); }
}

@keyframes clickEffect {
  0% { transform: scale(1); }
  50% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

@media (max-width: 900px) {
  .corridor-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .corridor-foot {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .corridor-stats {
    flex-wrap: wrap;
  }
}
</style>
