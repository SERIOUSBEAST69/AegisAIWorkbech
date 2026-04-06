<template>
  <div class="stat-card card-glass" :style="{ '--card-color': color }">
    <div class="stat-header">
      <div class="label">{{ title }}</div>
      <div class="stat-icon" v-if="icon">
        <component :is="getIconComponent(icon)" />
      </div>
    </div>
    <div class="stat-body">
      <div class="value">{{ displayValue }}<span class="suffix">{{ suffix }}</span></div>
      <div class="trend" v-if="trend">
        <span class="trend-indicator" :class="{ positive: trend > 0, negative: trend <= 0 }">
          {{ trend > 0 ? '↑' : '↓' }} {{ Math.abs(trend) }}%
        </span>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, watch } from 'vue';
import {
  DataAnalysis,
  StarFilled,
  Warning,
  UserFilled,
  Timer,
  Money,
  Search,
  Share,
  Lock,
  Key,
  Check,
  Document
} from '@element-plus/icons-vue';

const props = defineProps({
  title: String,
  value: [String, Number],
  suffix: { type: String, default: '' },
  icon: { type: String, default: '' },
  color: { type: String, default: 'var(--color-primary)' },
  trend: { type: Number, default: 0 }
});

const displayValue = ref(props.value ?? 0);

function toNumber(input) {
  if (typeof input === 'number') return Number.isFinite(input) ? input : NaN;
  const parsed = Number(String(input ?? '').replace(/,/g, ''));
  return Number.isFinite(parsed) ? parsed : NaN;
}

function animateValue(from, to) {
  const duration = 850;
  const start = performance.now();
  const run = now => {
    const ratio = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - ratio, 3);
    const next = Math.round(from + (to - from) * eased);
    displayValue.value = String(next);
    if (ratio < 1) {
      requestAnimationFrame(run);
    }
  };
  requestAnimationFrame(run);
}

watch(
  () => props.value,
  nextVal => {
    const nextNum = toNumber(nextVal);
    const currentNum = toNumber(displayValue.value);
    if (!Number.isNaN(nextNum) && !Number.isNaN(currentNum)) {
      animateValue(currentNum, nextNum);
      return;
    }
    displayValue.value = nextVal;
  },
  { immediate: true }
);

const getIconComponent = (iconName) => {
  const iconMap = {
    DataAnalysis,
    StarFilled,
    Warning,
    UserFilled,
    Timer,
    Money,
    Search,
    Share,
    Lock,
    Key,
    Check,
    Document
  };
  return iconMap[iconName] || null;
};
</script>
<style scoped>
.stat-card {
  padding: 20px;
  border-radius: var(--radius-lg);
  position: relative;
  overflow: hidden;
  transition: all var(--transition-normal);
  border: 1px solid rgba(109, 162, 235, 0.28);
  background:
    radial-gradient(circle at 100% 0%, rgba(84, 141, 255, 0.19), transparent 35%),
    linear-gradient(140deg, rgba(9, 17, 33, 0.92), rgba(12, 23, 44, 0.9));
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: var(--card-color, var(--color-primary));
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-xl);
}

.stat-card::after {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  border: 1px solid rgba(122, 174, 243, 0.24);
  opacity: 0;
  animation: statBreath 2.2s ease-in-out infinite;
  pointer-events: none;
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.label {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--card-color, var(--color-primary));
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.stat-body {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.value {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
}

.suffix {
  margin-left: 8px;
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 400;
}

@keyframes statBreath {
  0% { opacity: 0.1; }
  50% { opacity: 0.72; }
  100% { opacity: 0.1; }
}

.trend {
  display: flex;
  align-items: center;
  gap: 4px;
}

.trend-indicator {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  gap: 4px;
}

.trend-indicator.positive {
  color: var(--color-danger);
  background: rgba(245, 63, 63, 0.1);
}

.trend-indicator.negative {
  color: var(--color-success);
  background: rgba(0, 180, 42, 0.1);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .stat-card {
    padding: 16px;
  }
  
  .value {
    font-size: 24px;
  }
  
  .stat-icon {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }
}
</style>
