<template>
  <div
    ref="cardRef"
    class="border-glow-card"
    :class="{ 'sweep-active': animated }"
    :style="cardStyle"
    @pointermove="handlePointerMove"
  >
    <span class="edge-light" />
    <div class="border-glow-inner">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  edgeSensitivity: { type: Number, default: 30 },
  glowColor: { type: String, default: '40 80 80' },
  backgroundColor: { type: String, default: '#060010' },
  borderRadius: { type: Number, default: 28 },
  glowRadius: { type: Number, default: 40 },
  glowIntensity: { type: Number, default: 1 },
  coneSpread: { type: Number, default: 25 },
  animated: { type: Boolean, default: false },
  colors: {
    type: Array,
    default: () => ['#c084fc', '#f472b6', '#38bdf8'],
  },
  fillOpacity: { type: Number, default: 0.5 },
});

const cardRef = ref(null);

function parseHSL(hslStr) {
  const match = String(hslStr || '').match(/([\d.]+)\s*([\d.]+)%?\s*([\d.]+)%?/);
  if (!match) return { h: 40, s: 80, l: 80 };
  return { h: Number(match[1]), s: Number(match[2]), l: Number(match[3]) };
}

function buildGlowVars(glowColor, intensity) {
  const { h, s, l } = parseHSL(glowColor);
  const base = `${h}deg ${s}% ${l}%`;
  const pairs = [
    ['', 100],
    ['-60', 60],
    ['-50', 50],
    ['-40', 40],
    ['-30', 30],
    ['-20', 20],
    ['-10', 10],
  ];
  const vars = {};
  pairs.forEach(([key, opacity]) => {
    vars[`--glow-color${key}`] = `hsl(${base} / ${Math.min(opacity * intensity, 100)}%)`;
  });
  return vars;
}

function buildGradientVars(colors) {
  const list = Array.isArray(colors) && colors.length ? colors : ['#c084fc', '#f472b6', '#38bdf8'];
  return {
    '--gradient-one': `radial-gradient(at 80% 55%, ${list[0]} 0px, transparent 50%)`,
    '--gradient-two': `radial-gradient(at 69% 34%, ${list[1] || list[0]} 0px, transparent 50%)`,
    '--gradient-three': `radial-gradient(at 8% 6%, ${list[2] || list[0]} 0px, transparent 50%)`,
    '--gradient-four': `radial-gradient(at 41% 38%, ${list[0]} 0px, transparent 50%)`,
    '--gradient-five': `radial-gradient(at 86% 85%, ${list[1] || list[0]} 0px, transparent 50%)`,
    '--gradient-six': `radial-gradient(at 82% 18%, ${list[2] || list[0]} 0px, transparent 50%)`,
    '--gradient-seven': `radial-gradient(at 51% 4%, ${list[1] || list[0]} 0px, transparent 50%)`,
  };
}

const cardStyle = computed(() => ({
  '--card-bg': props.backgroundColor,
  '--edge-sensitivity': String(props.edgeSensitivity),
  '--border-radius': `${props.borderRadius}px`,
  '--glow-padding': `${props.glowRadius}px`,
  '--cone-spread': String(props.coneSpread),
  '--fill-opacity': String(props.fillOpacity),
  ...buildGlowVars(props.glowColor, props.glowIntensity),
  ...buildGradientVars(props.colors),
}));

function handlePointerMove(e) {
  const card = cardRef.value;
  if (!card) return;

  const rect = card.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  const cx = rect.width / 2;
  const cy = rect.height / 2;

  const dx = x - cx;
  const dy = y - cy;
  const dist = Math.sqrt(dx * dx + dy * dy);
  const maxDist = Math.sqrt(cx * cx + cy * cy);
  const edgeProximity = Math.min(100, Math.max(0, (dist / (maxDist || 1)) * 100));

  let angle = Math.atan2(dy, dx) * (180 / Math.PI) + 90;
  if (angle < 0) angle += 360;

  card.style.setProperty('--edge-proximity', edgeProximity.toFixed(3));
  card.style.setProperty('--cursor-angle', `${angle.toFixed(3)}deg`);
}
</script>

<style scoped>
.border-glow-card {
  --edge-proximity: 0;
  --cursor-angle: 45deg;
  --edge-sensitivity: 30;
  --color-sensitivity: calc(var(--edge-sensitivity) + 20);
  --border-radius: 28px;
  --glow-padding: 40px;
  --cone-spread: 25;

  position: relative;
  border-radius: var(--border-radius);
  isolation: isolate;
  display: grid;
  border: 1px solid rgb(255 255 255 / 12%);
  background: var(--card-bg, #060010);
  overflow: visible;
}

.border-glow-card::before,
.border-glow-card::after,
.border-glow-card > .edge-light {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  transition: opacity 0.25s ease-out;
  z-index: -1;
}

.border-glow-card:not(:hover):not(.sweep-active)::before,
.border-glow-card:not(:hover):not(.sweep-active)::after,
.border-glow-card:not(:hover):not(.sweep-active) > .edge-light {
  opacity: 0;
}

.border-glow-card::before {
  border: 1px solid transparent;
  background:
    linear-gradient(var(--card-bg, #060010) 0 100%) padding-box,
    var(--gradient-one) border-box,
    var(--gradient-two) border-box,
    var(--gradient-three) border-box,
    var(--gradient-four) border-box,
    var(--gradient-five) border-box,
    var(--gradient-six) border-box,
    var(--gradient-seven) border-box;
  opacity: calc((var(--edge-proximity) - var(--color-sensitivity)) / (100 - var(--color-sensitivity)));
  mask-image:
    conic-gradient(
      from var(--cursor-angle) at center,
      black calc(var(--cone-spread) * 1%),
      transparent calc((var(--cone-spread) + 14) * 1%),
      transparent calc((100 - var(--cone-spread) - 14) * 1%),
      black calc((100 - var(--cone-spread)) * 1%)
    );
}

.border-glow-card::after {
  border: 1px solid transparent;
  background:
    var(--gradient-one) padding-box,
    var(--gradient-two) padding-box,
    var(--gradient-three) padding-box,
    var(--gradient-four) padding-box,
    var(--gradient-five) padding-box,
    var(--gradient-six) padding-box,
    var(--gradient-seven) padding-box;
  opacity: calc(var(--fill-opacity, 0.5) * (var(--edge-proximity) - var(--color-sensitivity)) / (100 - var(--color-sensitivity)));
  mix-blend-mode: soft-light;
}

.border-glow-card > .edge-light {
  inset: calc(var(--glow-padding) * -1);
  pointer-events: none;
  z-index: 0;
  mask-image:
    conic-gradient(from var(--cursor-angle) at center, black 2.5%, transparent 10%, transparent 90%, black 97.5%);
  opacity: calc((var(--edge-proximity) - var(--edge-sensitivity)) / (100 - var(--edge-sensitivity)));
  mix-blend-mode: plus-lighter;
}

.border-glow-card > .edge-light::before {
  content: "";
  position: absolute;
  inset: var(--glow-padding);
  border-radius: inherit;
  box-shadow:
    inset 0 0 0 1px var(--glow-color),
    inset 0 0 4px 0 var(--glow-color-50),
    inset 0 0 12px 0 var(--glow-color-30),
    0 0 8px 0 var(--glow-color-40),
    0 0 20px 2px var(--glow-color-20);
}

.border-glow-inner {
  position: relative;
  z-index: 1;
}
</style>
