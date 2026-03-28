<template>
  <!--
    ExpandableCard.vue — 从卡片位置展开为全屏详情

    用法示例（见 README）：
      title    — 卡片标题
      subtitle — 副标题（可选）
      accent   — 强调色（CSS 颜色值，默认使用主色）
      插槽 #preview — 卡片预览内容
      插槽 #detail  — 全屏展开后的详细内容（未提供则复用 #preview）
  -->
  <div ref="cardEl" class="expandable-card card-glass" @click="expand">
    <div class="card-accent-bar" :style="accentBarStyle" />
    <div class="card-inner">
      <div class="card-title-row">
        <span class="card-title">{{ title }}</span>
        <span v-if="subtitle" class="card-subtitle">{{ subtitle }}</span>
        <span class="expand-hint">展开 ↗</span>
      </div>
      <div class="card-preview">
        <slot name="preview" />
      </div>
    </div>
  </div>

  <!-- 全屏遮罩 + 展开内容，通过 Teleport 挂载到 body 避免 overflow 被截断 -->
  <Teleport to="body">
    <Transition name="overlay-fade">
      <div v-if="isOpen" class="card-overlay" @click.self="collapse">
        <div ref="panelEl" class="card-panel">
          <div class="panel-header">
            <div class="panel-title-wrap">
              <div class="panel-accent" :style="accentBarStyle" />
              <div>
                <h2 class="panel-title">{{ title }}</h2>
                <p v-if="subtitle" class="panel-subtitle">{{ subtitle }}</p>
              </div>
            </div>
            <button class="close-btn clickable" @click="collapse" aria-label="关闭">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6"  y2="18" />
                <line x1="6"  y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="panel-body">
            <slot name="detail">
              <slot name="preview" />
            </slot>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue';
import gsap from 'gsap';

const props = defineProps({
  title:    { type: String, default: '' },
  subtitle: { type: String, default: '' },
  accent:   { type: String, default: 'var(--color-primary)' },
});

const cardEl  = ref(null);
const panelEl = ref(null);
const isOpen  = ref(false);

const accentBarStyle = computed(() => ({
  background: `linear-gradient(90deg, ${props.accent}, transparent)`,
}));

function expand() {
  // 获取卡片在视口中的 BRect，用于动画起点计算
  const rect = cardEl.value.getBoundingClientRect();
  isOpen.value = true;

  // 等 panel 挂载到 DOM 后开始动画
  requestAnimationFrame(() => {
    if (!panelEl.value) return;
    gsap.fromTo(
      panelEl.value,
      {
        clipPath: `inset(${rect.top}px ${window.innerWidth - rect.right}px ${window.innerHeight - rect.bottom}px ${rect.left}px round 16px)`,
        opacity: 0.6,
      },
      {
        clipPath: 'inset(0px 0px 0px 0px round 0px)',
        opacity: 1,
        duration: 0.55,
        ease: 'expo.out',
      }
    );
  });
}

function collapse() {
  if (!panelEl.value) { isOpen.value = false; return; }

  const rect = cardEl.value.getBoundingClientRect();
  gsap.to(panelEl.value, {
    clipPath: `inset(${rect.top}px ${window.innerWidth - rect.right}px ${window.innerHeight - rect.bottom}px ${rect.left}px round 16px)`,
    opacity: 0,
    duration: 0.4,
    ease: 'expo.in',
    onComplete: () => { isOpen.value = false; },
  });
}
</script>

<style scoped>
/* ── 卡片 ── */
.expandable-card {
  position: relative;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.25s cubic-bezier(0.23, 1, 0.32, 1),
              box-shadow 0.25s ease,
              border-color 0.25s ease;
}
.expandable-card:hover {
  transform: translateY(-4px) scale(1.01);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
  border-color: rgba(100, 160, 255, 0.3);
}

.card-accent-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
}

.card-inner {
  padding: 20px 24px 24px;
}

.card-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.card-subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  flex: 1;
}

.expand-hint {
  font-size: 11px;
  color: var(--color-primary-light);
  opacity: 0;
  transition: opacity 0.2s ease;
  white-space: nowrap;
}

.expandable-card:hover .expand-hint {
  opacity: 1;
}

/* ── 全屏遮罩 ── */
.card-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 14, 23, 0.75);
  backdrop-filter: blur(6px);
  z-index: 9000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-panel {
  width: min(900px, 92vw);
  max-height: 88vh;
  background: var(--color-card-strong);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.6);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 32px 24px;
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
}

.panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}

.panel-accent {
  width: 4px;
  height: 40px;
  border-radius: 4px;
  flex-shrink: 0;
}

.panel-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
}

.panel-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.close-btn {
  width: 40px;
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  color: var(--color-text);
  transform: rotate(90deg);
}

.close-btn svg {
  width: 18px;
  height: 18px;
}

.panel-body {
  padding: 32px;
  overflow-y: auto;
  flex: 1;
}

/* Overlay 淡入淡出（当 clip-path 动画不支持时的降级） */
.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.3s ease;
}
.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}
</style>
