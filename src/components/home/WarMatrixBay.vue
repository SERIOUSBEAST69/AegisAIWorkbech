<template>
  <section class="war-bay" :class="[`motion-tier-${motionTierSafe}`, { 'reduce-motion': reducedMotion }]">
    <div class="war-bay-kpis">
      <article>
        <span>高热单元</span>
        <strong>{{ hotCellCount }}</strong>
      </article>
      <article>
        <span>平均风险</span>
        <strong>{{ averageRisk }}%</strong>
      </article>
      <article>
        <span>峰值风险</span>
        <strong>{{ peakRisk }}%</strong>
      </article>
      <article>
        <span>当前层级</span>
        <strong>{{ activeLayerMeta.tag }}</strong>
      </article>
    </div>

    <div class="matrix-stage">
      <span class="matrix-scanline" aria-hidden="true"></span>
      <div class="matrix-grid-3d" :style="gridTransformStyle">
        <button
          v-for="cell in visibleCells"
          :key="cell.key"
          type="button"
          class="matrix-voxel"
          :class="{ 'is-hot': cell.risk >= 80, 'is-selected': cell.id === selectedCellId }"
          :style="voxelStyle(cell)"
          :title="`${cell.label} · 风险${cell.risk}%`"
          @mouseenter="selectedCellId = cell.id"
          @focus="selectedCellId = cell.id"
          @click="handleCellClick(cell)"
        >
          <span class="voxel-glow"></span>
          <span class="voxel-label">{{ cell.shortLabel }}</span>
          <span class="voxel-risk">{{ cell.risk }}</span>
        </button>
      </div>
    </div>

    <footer class="war-bay-footer">
      <div class="layer-tabs">
        <button
          v-for="layer in layerCount"
          :key="`layer-${layer}`"
          type="button"
          :class="{ active: activeLayer === layer - 1 }"
          @click="activeLayer = layer - 1"
        >
          L{{ layer }}
        </button>
      </div>
      <div class="legend-row">
        <span class="layer-tip">{{ activeLayerMeta.tip }}</span>
        <span><i class="lv-safe"></i>0-39</span>
        <span><i class="lv-warn"></i>40-69</span>
        <span><i class="lv-danger"></i>70-100</span>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  rows: {
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
});

const emit = defineEmits(['detail', 'select']);

const activeLayer = ref(0);
const selectedCellId = ref('');

const motionTierSafe = computed(() => {
  const raw = String(props.motionTier || 'high').toLowerCase();
  if (raw === 'low' || raw === 'medium' || raw === 'high') return raw;
  return 'high';
});

const gridSize = computed(() => {
  if (props.reducedMotion || motionTierSafe.value === 'low') return 3;
  if (motionTierSafe.value === 'medium') return 4;
  return 5;
});

const layerCount = computed(() => gridSize.value);
const layerProfiles = computed(() => {
  const base = [
    { tag: 'L1', tip: '资产暴露与基础面', mult: 0.76, bias: -12 },
    { tag: 'L2', tip: '账号权限与审批摩擦', mult: 0.9, bias: -3 },
    { tag: 'L3', tip: '模型与调用链波动', mult: 1.05, bias: 4 },
    { tag: 'L4', tip: '高危异常聚合层', mult: 1.2, bias: 12 },
    { tag: 'L5', tip: '全域极值与突发层', mult: 1.3, bias: 18 },
  ];
  return base.slice(0, layerCount.value);
});
const activeLayerMeta = computed(() => layerProfiles.value[activeLayer.value] || { tag: '-', tip: '-' });

const seedRows = computed(() => {
  const rows = Array.isArray(props.rows) ? props.rows : [];
  if (rows.length) return rows;
  return [
    { id: 'seed-risk', label: '风险事件', score: 76 },
    { id: 'seed-asset', label: '敏感资产', score: 68 },
    { id: 'seed-approval', label: '审批阻塞', score: 54 },
    { id: 'seed-model', label: '模型漂移', score: 61 },
    { id: 'seed-shadow', label: '影子AI', score: 70 },
  ];
});

const allCells = computed(() => {
  const cells = [];
  for (let z = 0; z < gridSize.value; z += 1) {
    for (let y = 0; y < gridSize.value; y += 1) {
      for (let x = 0; x < gridSize.value; x += 1) {
        const seed = seedRows.value[(x + y + z) % seedRows.value.length] || { id: 'seed', label: '风险单元', score: 50 };
        const profile = layerProfiles.value[z] || layerProfiles.value[0] || { mult: 1, bias: 0 };
        const jitter = ((x * 7 + y * 11 + z * 13) % 17) - 8;
        const laneShift = ((x + y) % 3 === 0 ? 5 : ((x + y) % 3 === 1 ? -2 : 2));
        const risk = Math.max(8, Math.min(100, Math.round((Number(seed.score || 0) * profile.mult) + profile.bias + jitter + laneShift)));
        const label = `${seed.label} · ${x + 1}-${y + 1}-${z + 1}`;
        cells.push({
          key: `${seed.id}-${x}-${y}-${z}`,
          id: `${seed.id}:${x}:${y}:${z}`,
          shortLabel: String(seed.label || '单元').slice(0, 4),
          label,
          risk,
          x,
          y,
          z,
        });
      }
    }
  }
  return cells;
});

const visibleCells = computed(() => allCells.value.filter(cell => cell.z === activeLayer.value));
const hotCellCount = computed(() => allCells.value.filter(cell => cell.risk >= 80).length);
const averageRisk = computed(() => {
  if (!allCells.value.length) return 0;
  const total = allCells.value.reduce((sum, cell) => sum + cell.risk, 0);
  return Math.round(total / allCells.value.length);
});
const peakRisk = computed(() => {
  if (!allCells.value.length) return 0;
  return allCells.value.reduce((max, cell) => Math.max(max, cell.risk), 0);
});

const gridTransformStyle = computed(() => {
  if (motionTierSafe.value === 'low' || props.reducedMotion) {
    return { transform: 'perspective(900px) rotateX(18deg) rotateZ(0deg)' };
  }
  if (motionTierSafe.value === 'medium') {
    return { transform: 'perspective(980px) rotateX(24deg) rotateZ(-6deg)' };
  }
  return { transform: 'perspective(1100px) rotateX(28deg) rotateZ(-9deg)' };
});

function voxelStyle(cell) {
  const span = Math.max(1, gridSize.value - 1);
  const left = 8 + ((cell.x / span) * 72);
  const top = 8 + ((cell.y / span) * 62);
  const hue = Math.max(5, 140 - Math.round(cell.risk * 1.2));
  return {
    left: `${left}%`,
    top: `${top}%`,
    '--risk-hue': hue,
  };
}

function inferCode(cell) {
  const raw = `${cell?.id || ''} ${cell?.label || ''}`.toLowerCase();
  if (raw.includes('privacy') || raw.includes('隐私')) return 'privacy';
  if (raw.includes('drift') || raw.includes('漂移')) return 'drift';
  if (raw.includes('shadow') || raw.includes('影子')) return 'shadow';
  if (raw.includes('approval') || raw.includes('审批')) return 'approval';
  if (raw.includes('asset') || raw.includes('资产')) return 'asset';
  if (raw.includes('risk') || raw.includes('风险')) return 'risk';
  return '';
}

function handleCellClick(cell) {
  emit('detail', { kind: 'graph-node', key: cell.id, label: cell.label });
  emit('select', {
    id: cell.id,
    label: cell.label,
    risk: cell.risk,
    codeHint: inferCode(cell),
    layer: cell.z,
  });
}
</script>

<style scoped>
.war-bay {
  display: grid;
  gap: 12px;
}

.war-bay-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.war-bay-kpis article {
  border: 1px solid rgba(118, 172, 246, 0.25);
  border-radius: 10px;
  padding: 6px 8px;
  background: rgba(7, 16, 31, 0.6);
}

.war-bay-kpis span {
  color: #9ab5da;
  font-size: 11px;
}

.war-bay-kpis strong {
  display: block;
  color: #f5fbff;
  margin-top: 4px;
}

.matrix-stage {
  position: relative;
  min-height: 320px;
  border: 1px solid rgba(96, 150, 222, 0.24);
  border-radius: 16px;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% -10%, rgba(86, 145, 231, 0.2), transparent 52%),
    linear-gradient(180deg, rgba(7, 14, 28, 0.92), rgba(5, 10, 20, 0.88));
}

.matrix-scanline {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, transparent 0%, rgba(121, 192, 255, 0.18) 50%, transparent 100%);
  mix-blend-mode: screen;
  opacity: 0.4;
  animation: scanlineMove 5.8s linear infinite;
}

.matrix-grid-3d {
  position: absolute;
  inset: 0;
  transform-style: preserve-3d;
}

.matrix-voxel {
  position: absolute;
  width: 70px;
  height: 56px;
  border: 1px solid hsla(var(--risk-hue), 90%, 62%, 0.65);
  border-radius: 10px;
  background: linear-gradient(160deg, hsla(var(--risk-hue), 88%, 56%, 0.56), rgba(5, 10, 18, 0.86));
  color: #f3f8ff;
  cursor: pointer;
  display: grid;
  align-content: space-between;
  padding: 7px;
  text-align: left;
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.matrix-voxel:hover,
.matrix-voxel.is-selected {
  transform: translateY(-4px) scale(1.03);
  border-color: rgba(161, 208, 255, 0.88);
  box-shadow: 0 10px 24px rgba(2, 8, 20, 0.45);
}

.matrix-voxel.is-hot {
  box-shadow: 0 0 0 1px rgba(255, 126, 126, 0.5), 0 12px 28px rgba(107, 12, 12, 0.3);
}

.voxel-glow {
  position: absolute;
  inset: auto -20% -55% -20%;
  height: 58%;
  background: radial-gradient(ellipse at center, rgba(126, 186, 255, 0.36), transparent 70%);
  pointer-events: none;
}

.voxel-label {
  position: relative;
  z-index: 1;
  font-size: 11px;
}

.voxel-risk {
  position: relative;
  z-index: 1;
  justify-self: end;
  font-size: 12px;
  font-weight: 600;
}

.war-bay-footer {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: center;
}

.layer-tabs {
  display: flex;
  gap: 8px;
}

.layer-tabs button {
  border: 1px solid rgba(120, 175, 248, 0.32);
  background: rgba(8, 18, 34, 0.7);
  color: #cae1fb;
  border-radius: 999px;
  padding: 4px 10px;
  cursor: pointer;
}

.layer-tabs button.active {
  background: linear-gradient(120deg, #2f72d9, #57a0ff);
  border-color: rgba(164, 207, 255, 0.82);
  color: #06182f;
  font-weight: 700;
}

.legend-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #a6c6eb;
  font-size: 11px;
}

.layer-tip {
  color: #d1e6ff;
  margin-right: 8px;
}

.legend-row i {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-right: 4px;
}

.lv-safe { background: #4ea4ff; }
.lv-warn { background: #f4b74a; }
.lv-danger { background: #ff6f6f; }

.motion-tier-low .matrix-voxel,
.reduce-motion .matrix-voxel {
  transition: none;
}

.motion-tier-low .matrix-scanline,
.reduce-motion .matrix-scanline {
  animation: none;
  opacity: 0.18;
}

@keyframes scanlineMove {
  0% { transform: translateY(-85%); }
  100% { transform: translateY(85%); }
}

@media (max-width: 980px) {
  .war-bay-kpis {
    width: 100%;
    min-width: 0;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .war-bay-footer {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>
