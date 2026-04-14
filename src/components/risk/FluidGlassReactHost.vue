<template>
  <section class="fluid-glass-react-host card-glass">
    <div class="host-head">
      <div>
        <div class="host-kicker">REACT BITS FLUID GLASS</div>
        <h3>AI 服务流体玻璃 3D 阵列</h3>
        <p>采用 React + @react-three/fiber 技术岛渲染，卡牌数跟随 AI 评级服务数量变化。</p>
      </div>
      <div class="host-count">{{ safeCards.length }} 张卡牌</div>
    </div>
    <div v-if="webglReady" ref="mountRef" class="fluid-canvas-wrap"></div>
    <FluidGlassRiskDeck
      v-else
      class="fluid-fallback"
      :items="safeCards"
      :mode="mode"
      :lens-props="lensProps"
      :bar-props="barProps"
      :cube-props="cubeProps"
    />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import React from 'react';
import { createRoot } from 'react-dom/client';
import FluidGlass from './react/FluidGlass.jsx';
import FluidGlassRiskDeck from './FluidGlassRiskDeck.vue';

const props = defineProps({
  cards: {
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

const mountRef = ref(null);
const webglReady = ref(true);
let reactRoot = null;

const safeCards = computed(() => (Array.isArray(props.cards) ? props.cards : []));

function renderReactIsland() {
  if (!reactRoot) return;
  reactRoot.render(
    React.createElement(FluidGlass, {
      mode: props.mode,
      lensProps: props.lensProps,
      barProps: props.barProps,
      cubeProps: props.cubeProps,
      cards: safeCards.value,
    })
  );
}

onMounted(() => {
  webglReady.value = detectWebglSupport();
  if (!webglReady.value) return;
  if (!mountRef.value) return;
  reactRoot = createRoot(mountRef.value);
  renderReactIsland();
});

watch(
  () => [safeCards.value, props.mode, props.lensProps, props.barProps, props.cubeProps],
  () => {
    renderReactIsland();
  },
  { deep: true }
);

onBeforeUnmount(() => {
  if (reactRoot) {
    reactRoot.unmount();
    reactRoot = null;
  }
});

function detectWebglSupport() {
  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2') || canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    return !!gl;
  } catch {
    return false;
  }
}
</script>

<style scoped>
.fluid-glass-react-host {
  border-radius: 14px;
  border: 1px solid rgba(129, 178, 247, 0.26);
  padding: 14px;
  background:
    radial-gradient(circle at 14% 10%, rgba(100, 170, 255, 0.2), transparent 34%),
    linear-gradient(160deg, rgba(8, 20, 39, 0.9), rgba(6, 14, 29, 0.92));
}

.host-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.host-kicker {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #89bdff;
}

.host-head h3 {
  margin: 4px 0;
  color: #f1f6ff;
  font-size: 17px;
}

.host-head p {
  margin: 0;
  color: #9cb8db;
  font-size: 12px;
}

.host-count {
  align-self: flex-start;
  font-size: 12px;
  color: #d2e5ff;
  border: 1px solid rgba(144, 193, 255, 0.34);
  border-radius: 999px;
  padding: 5px 10px;
  background: rgba(11, 30, 56, 0.56);
}

.fluid-canvas-wrap {
  height: 620px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(132, 176, 236, 0.24);
  background: #060d1a;
}

.fluid-fallback {
  margin-top: 2px;
}

@media (max-width: 768px) {
  .fluid-canvas-wrap {
    height: 520px;
  }
}
</style>
