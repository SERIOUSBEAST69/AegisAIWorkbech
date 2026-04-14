<template>
  <div class="office-sim-wrap" :class="{ 'alarm-active': alarmPulse }">
    <div class="cyber-rain" aria-hidden="true"></div>
    <div ref="containerRef" class="office-sim-canvas"></div>
    <div class="alarm-edge" aria-hidden="true"></div>
    <div class="sim-caption">
      <strong>全息办公态势矩阵</strong>
      <span>异常节点触发后将生成高亮告警柱、量子流路径与指挥中心脉冲反馈。</span>
      <span v-if="focusContext" class="sim-live">
        告警跳转: {{ focusContext.zone }} / {{ focusContext.department }} / {{ focusContext.role }}
      </span>
    </div>
    <div class="asset-diagnostic" aria-live="polite">
      <strong>资产状态: {{ glbState.status }}</strong>
      <span v-if="glbState.message">{{ glbState.message }}</span>
      <span v-if="glbState.missing.length">缺失节点: {{ glbState.missing.slice(0, 4).join(', ') }}</span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
import gsap from 'gsap';
import {
  describeDeptNode,
  describeRoleTier,
  makeDeskId,
  normalizeDeskId,
  OFFICE_DESK_COUNT,
  mapDeptNodeToZoneCode,
} from '../utils/officeDigitalTwin';

const props = defineProps({
  riskMap: {
    type: Object,
    default: () => ({}),
  },
  seatMetaMap: {
    type: Object,
    default: () => ({}),
  },
  activeEvent: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['animation-complete']);

const containerRef = ref(null);
const alarmPulse = ref(false);
const focusContext = ref(null);
const glbState = ref({ status: '待加载', message: '', missing: [] });

let renderer = null;
let scene = null;
let camera = null;
let frameId = 0;
let pulseTimeout = null;

const seatNodes = [];
const flowParticles = [];
const zoneMeshes = [];
const zoneLabels = [];
let commandCenter = null;
let flowCurve = null;
let flowTube = null;
let starField = null;
let warningRing = null;
const departmentDecor = [];
const severityTextureCache = new Map();

const RISK_COLOR = {
  low: 0x42c77f,
  medium: 0xf6c24b,
  high: 0xff7d45,
  critical: 0xff3058,
};

const ZONE_COLOR = {
  strategy: 0x29a6ff,
  finance: 0x27d3a3,
  operation: 0xff7d45,
  engineering: 0xbe75ff,
};

function normalizeText(value) {
  return String(value || '').trim();
}

function resolveZone(department) {
  const text = normalizeText(department).toLowerCase();
  if (text.includes('财') || text.includes('finance') || text.includes('审计')) return 'finance';
  if (text.includes('运维') || text.includes('运营') || text.includes('ops')) return 'operation';
  if (text.includes('研发') || text.includes('技术') || text.includes('engineer') || text.includes('it')) return 'engineering';
  return 'strategy';
}

function zoneLabel(zoneCode) {
  const map = {
    strategy: '战略管理区',
    finance: '财务合规区',
    operation: '运营执行区',
    engineering: '研发工程区',
  };
  return map[String(zoneCode || 'strategy')] || '战略管理区';
}

function roleTone(role, roleTier) {
  const text = normalizeText(role).toUpperCase();
  const tier = normalizeText(roleTier).toLowerCase();
  if (tier === 'management' || text.includes('ADMIN') || text.includes('EXECUTIVE')) return { metalness: 0.52, roughness: 0.22 };
  if (tier === 'security' || text.includes('SECOPS')) return { metalness: 0.42, roughness: 0.3 };
  if (tier === 'data') return { metalness: 0.34, roughness: 0.34 };
  if (tier === 'audit') return { metalness: 0.24, roughness: 0.42 };
  return { metalness: 0.18, roughness: 0.52 };
}

function resolveRiskColor(level) {
  const key = String(level || 'low').toLowerCase();
  return RISK_COLOR[key] || RISK_COLOR.low;
}

function hashSeatIndex(value, count) {
  const text = String(value || 'seat');
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 9973;
  }
  return Math.abs(hash) % Math.max(1, count);
}

function createMarkTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return new THREE.CanvasTexture(canvas);
  }
  ctx.clearRect(0, 0, 128, 128);
  ctx.fillStyle = 'rgba(255, 56, 88, 0.96)';
  ctx.beginPath();
  ctx.arc(64, 64, 52, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 84px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('!', 64, 72);
  const texture = new THREE.CanvasTexture(canvas);
  texture.needsUpdate = true;
  return texture;
}

function createTextSprite(text, color = '#8fe3ff') {
  const canvas = document.createElement('canvas');
  canvas.width = 320;
  canvas.height = 70;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return new THREE.Sprite(new THREE.SpriteMaterial({ color: 0xffffff }));
  }
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = 'rgba(5, 14, 30, 0.72)';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = 'rgba(120, 210, 255, 0.65)';
  ctx.lineWidth = 2;
  ctx.strokeRect(3, 3, canvas.width - 6, canvas.height - 6);
  ctx.fillStyle = color;
  ctx.font = 'bold 28px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, canvas.width / 2, canvas.height / 2);
  const texture = new THREE.CanvasTexture(canvas);
  texture.needsUpdate = true;
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, transparent: true }));
  sprite.scale.set(3.6, 0.75, 1);
  return sprite;
}

function addZoneMesh({ code, x, z, width, depth }) {
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(width, depth),
    new THREE.MeshBasicMaterial({
      color: ZONE_COLOR[code] || ZONE_COLOR.strategy,
      transparent: true,
      opacity: 0.1,
      side: THREE.DoubleSide,
    }),
  );
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.set(x, 0.03, z);
  scene.add(mesh);
  zoneMeshes.push(mesh);

  const label = createTextSprite(zoneLabel(code), '#b4f4ff');
  label.position.set(x, 0.6, z - depth * 0.42);
  scene.add(label);
  zoneLabels.push(label);
}

function disposeMaterial(material) {
  if (!material) return;
  material.map?.dispose?.();
  material.normalMap?.dispose?.();
  material.roughnessMap?.dispose?.();
  material.metalnessMap?.dispose?.();
  material.aoMap?.dispose?.();
  material.emissiveMap?.dispose?.();
  material.dispose?.();
}

function disposeObjectMaterials(obj) {
  const materials = Array.isArray(obj.material) ? obj.material : [obj.material];
  materials.forEach(disposeMaterial);
}

function buildSeverityTexture(level) {
  const key = String(level || 'low').toLowerCase();
  if (severityTextureCache.has(key)) {
    return severityTextureCache.get(key);
  }
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    const fallback = new THREE.CanvasTexture(canvas);
    severityTextureCache.set(key, fallback);
    return fallback;
  }

  ctx.clearRect(0, 0, 128, 128);
  if (key === 'critical') {
    ctx.fillStyle = 'rgba(220, 16, 40, 0.95)';
    ctx.beginPath();
    ctx.arc(64, 64, 56, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(50, 58, 8, 0, Math.PI * 2);
    ctx.arc(78, 58, 8, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillRect(46, 78, 36, 12);
    ctx.fillStyle = '#ff7d91';
    ctx.fillRect(40, 96, 48, 8);
  } else if (key === 'high' || key === 'medium') {
    const bg = key === 'high' ? 'rgba(255, 204, 36, 0.95)' : 'rgba(255, 157, 61, 0.92)';
    ctx.fillStyle = bg;
    ctx.beginPath();
    ctx.arc(64, 64, 54, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#2a1a00';
    ctx.font = 'bold 84px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('!', 64, 70);
  } else {
    ctx.fillStyle = 'rgba(89, 221, 157, 0.88)';
    ctx.beginPath();
    ctx.arc(64, 64, 52, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 10;
    ctx.beginPath();
    ctx.moveTo(40, 66);
    ctx.lineTo(58, 84);
    ctx.lineTo(88, 46);
    ctx.stroke();
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.needsUpdate = true;
  severityTextureCache.set(key, texture);
  return texture;
}

function addDepartmentEnvironmentDecor() {
  const configs = [
    { code: 'strategy', items: [
      { type: 'screen', x: -7.6, y: 0.65, z: -4.8, sx: 1.4, sy: 0.8, sz: 0.08, color: 0x5ec9ff },
      { type: 'desk', x: -2.6, y: 0.24, z: -5.2, sx: 1.0, sy: 0.38, sz: 0.55, color: 0x2b6fa6 },
    ]},
    { code: 'finance', items: [
      { type: 'pillar', x: 6.8, y: 0.9, z: -4.5, sx: 0.35, sy: 1.8, sz: 0.35, color: 0x27d3a3 },
      { type: 'vault', x: 3.8, y: 0.3, z: -2.3, sx: 1.2, sy: 0.6, sz: 0.8, color: 0x1f8f70 },
      { type: 'ledger', x: 5.2, y: 0.55, z: -5.3, sx: 0.7, sy: 0.42, sz: 0.26, color: 0x2dcf9c },
    ]},
    { code: 'operation', items: [
      { type: 'crate', x: -7.2, y: 0.22, z: 4.8, sx: 0.8, sy: 0.44, sz: 0.8, color: 0xff8d4a },
      { type: 'crate', x: -5.4, y: 0.22, z: 5.2, sx: 0.7, sy: 0.4, sz: 0.7, color: 0xff9f63 },
      { type: 'antenna', x: -2.8, y: 1.05, z: 2.2, sx: 0.12, sy: 2.1, sz: 0.12, color: 0xffa24f },
    ]},
    { code: 'engineering', items: [
      { type: 'rack', x: 7.0, y: 0.86, z: 4.5, sx: 0.65, sy: 1.72, sz: 0.5, color: 0xbe75ff },
      { type: 'rack', x: 4.9, y: 0.86, z: 5.1, sx: 0.62, sy: 1.72, sz: 0.5, color: 0xa969ff },
      { type: 'node', x: 2.9, y: 0.38, z: 2.8, sx: 0.52, sy: 0.52, sz: 0.52, color: 0xd19aff },
    ]},
  ];

  for (const group of configs) {
    for (const item of group.items) {
      const geo = new THREE.BoxGeometry(item.sx, item.sy, item.sz);
      const mat = new THREE.MeshStandardMaterial({
        color: item.color,
        emissive: item.color,
        emissiveIntensity: 0.24,
        metalness: 0.3,
        roughness: 0.42,
      });
      const mesh = new THREE.Mesh(geo, mat);
      mesh.position.set(item.x, item.y, item.z);
      scene.add(mesh);
      departmentDecor.push(mesh);
    }
  }
}

function createAvatarForSeat() {
  const group = new THREE.Group();
  const legGeo = new THREE.CylinderGeometry(0.07, 0.08, 0.34, 8);
  const bodyGeo = new THREE.CapsuleGeometry(0.16, 0.36, 4, 8);
  const headGeo = new THREE.SphereGeometry(0.14, 14, 10);
  const roleGeo = new THREE.TorusGeometry(0.22, 0.04, 8, 22);
  const deptGeo = new THREE.BoxGeometry(0.26, 0.08, 0.08);
  const strategyGeo = new THREE.ConeGeometry(0.11, 0.18, 5);
  const financeGeo = new THREE.BoxGeometry(0.07, 0.2, 0.04);
  const operationGeo = new THREE.BoxGeometry(0.2, 0.26, 0.1);
  const engineeringGeo = new THREE.CylinderGeometry(0.015, 0.015, 0.24, 8);

  const legMat = new THREE.MeshStandardMaterial({ color: 0x29435e, roughness: 0.6, metalness: 0.1 });
  const bodyMat = new THREE.MeshStandardMaterial({ color: 0x4ca9ff, emissive: 0x103153, emissiveIntensity: 0.35, roughness: 0.5, metalness: 0.22 });
  const headMat = new THREE.MeshStandardMaterial({ color: 0xffddb5, roughness: 0.85, metalness: 0.04 });
  const roleMat = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0x355c88, emissiveIntensity: 0.7, roughness: 0.3, metalness: 0.34 });
  const deptMat = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0x2a2a2a, emissiveIntensity: 0.4, roughness: 0.42, metalness: 0.24 });

  const leftLeg = new THREE.Mesh(legGeo, legMat);
  const rightLeg = new THREE.Mesh(legGeo, legMat);
  leftLeg.position.set(-0.08, 0.18, 0);
  rightLeg.position.set(0.08, 0.18, 0);

  const body = new THREE.Mesh(bodyGeo, bodyMat);
  body.position.set(0, 0.56, 0);

  const head = new THREE.Mesh(headGeo, headMat);
  head.position.set(0, 0.92, 0);

  const roleHalo = new THREE.Mesh(roleGeo, roleMat);
  roleHalo.rotation.x = Math.PI / 2;
  roleHalo.position.set(0, 1.14, 0);

  const deptBadge = new THREE.Mesh(deptGeo, deptMat);
  deptBadge.position.set(0, 0.62, 0.2);

  const strategyAddon = new THREE.Mesh(strategyGeo, deptMat.clone());
  strategyAddon.position.set(0, 1.12, 0);
  const financeAddon = new THREE.Mesh(financeGeo, deptMat.clone());
  financeAddon.position.set(0, 0.48, 0.2);
  const operationAddon = new THREE.Mesh(operationGeo, deptMat.clone());
  operationAddon.position.set(0, 0.58, -0.14);
  const engineeringAddon = new THREE.Mesh(engineeringGeo, deptMat.clone());
  engineeringAddon.position.set(0.14, 1.0, -0.02);

  group.add(leftLeg, rightLeg, body, head, roleHalo, deptBadge, strategyAddon, financeAddon, operationAddon, engineeringAddon);
  return { group, body, roleHalo, deptBadge, strategyAddon, financeAddon, operationAddon, engineeringAddon };
}

function resolveSeatDisplayMeta(seatNode) {
  const meta = props.seatMetaMap?.[seatNode.id] || {};
  const deptNode = String(meta.deptNode || '').trim();
  const roleTier = String(meta.roleTier || '').trim();
  const deptLabel = deptNode ? describeDeptNode(deptNode) : normalizeText(meta.department || zoneLabel(seatNode.zoneCode));
  const zoneCode = deptNode ? mapDeptNodeToZoneCode(deptNode) : resolveZone(meta.department || zoneLabel(seatNode.zoneCode));
  const roleLabel = roleTier ? describeRoleTier(roleTier) : normalizeText(meta.role || '业务层');
  return { meta, deptNode, roleTier, deptLabel, zoneCode, roleLabel };
}

function initializeIdentitySceneState() {
  glbState.value = {
    status: '身份驱动已启用',
    message: '使用系统数据库身份生成差异化人形与部门环境（JS内置场景）。',
    missing: [],
  };
}

function buildOfficeScene() {
  const el = containerRef.value;
  if (!el) return;

  scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x060d18, 18, 45);

  const width = Math.max(1, el.clientWidth);
  const height = Math.max(1, el.clientHeight);
  camera = new THREE.PerspectiveCamera(42, width / height, 0.1, 120);
  camera.position.set(0, 12, 15);
  camera.lookAt(0, 0, 0);

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
  renderer.setSize(width, height);
  renderer.setClearColor(0x000000, 0);
  el.appendChild(renderer.domElement);

  const ambient = new THREE.AmbientLight(0x9db8ff, 0.5);
  scene.add(ambient);

  const topLight = new THREE.DirectionalLight(0xaecbff, 0.75);
  topLight.position.set(4, 10, 6);
  scene.add(topLight);

  const redFill = new THREE.PointLight(0xff2f6d, 0.7, 26);
  redFill.position.set(-5, 2, 3);
  scene.add(redFill);

  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(20, 14, 24, 18),
    new THREE.MeshStandardMaterial({
      color: 0x0e1f33,
      metalness: 0.15,
      roughness: 0.85,
      emissive: 0x081524,
      emissiveIntensity: 0.8,
      wireframe: false,
    })
  );
  floor.rotation.x = -Math.PI / 2;
  floor.position.y = -0.05;
  scene.add(floor);

  const holoGrid = new THREE.Mesh(
    new THREE.PlaneGeometry(20, 14, 60, 42),
    new THREE.MeshBasicMaterial({
      color: 0x2ca4ff,
      wireframe: true,
      transparent: true,
      opacity: 0.14,
    }),
  );
  holoGrid.rotation.x = -Math.PI / 2;
  holoGrid.position.y = 0.01;
  scene.add(holoGrid);

  addZoneMesh({ code: 'strategy', x: -5, z: -3.5, width: 9, depth: 6 });
  addZoneMesh({ code: 'finance', x: 5, z: -3.5, width: 9, depth: 6 });
  addZoneMesh({ code: 'operation', x: -5, z: 3.5, width: 9, depth: 6 });
  addZoneMesh({ code: 'engineering', x: 5, z: 3.5, width: 9, depth: 6 });
  addDepartmentEnvironmentDecor();

  const centerGeo = new THREE.CylinderGeometry(0.95, 1.15, 1.8, 18);
  const centerMat = new THREE.MeshStandardMaterial({
    color: 0x4aa9ff,
    emissive: 0x11365b,
    emissiveIntensity: 0.95,
    metalness: 0.2,
    roughness: 0.34,
  });
  commandCenter = new THREE.Mesh(centerGeo, centerMat);
  commandCenter.position.set(0, 0.95, 0);
  scene.add(commandCenter);

  warningRing = new THREE.Mesh(
    new THREE.TorusGeometry(1.8, 0.08, 20, 90),
    new THREE.MeshStandardMaterial({
      color: 0x67d9ff,
      emissive: 0x1a5c8b,
      emissiveIntensity: 1.08,
      transparent: true,
      opacity: 0.92,
      metalness: 0.35,
      roughness: 0.18,
    }),
  );
  warningRing.rotation.x = Math.PI / 2;
  warningRing.position.set(0, 1.2, 0);
  scene.add(warningRing);

  const seatGeo = new THREE.BoxGeometry(0.7, 0.38, 0.7);
  const beamGeo = new THREE.BoxGeometry(0.45, 1.0, 0.45);
  const markTexture = buildSeverityTexture('low');

  const columns = 6;
  const rows = 4;
  for (let row = 0; row < rows; row += 1) {
    for (let col = 0; col < columns; col += 1) {
      const idx = row * columns + col;
      const x = (col - (columns - 1) / 2) * 2.2;
      const z = (row - (rows - 1) / 2) * 2.2;

      const seatMaterial = new THREE.MeshStandardMaterial({
        color: resolveRiskColor('low'),
        emissive: 0x0c1a2a,
        emissiveIntensity: 0.45,
        metalness: 0.14,
        roughness: 0.52,
      });
      const seat = new THREE.Mesh(seatGeo, seatMaterial);
      seat.position.set(x, 0.2, z);
      scene.add(seat);

      const beamMaterial = new THREE.MeshStandardMaterial({
        color: 0xff3858,
        emissive: 0xff1f4d,
        emissiveIntensity: 1.2,
        transparent: true,
        opacity: 0.82,
      });
      const beam = new THREE.Mesh(beamGeo, beamMaterial);
      beam.position.set(x, 0.78, z);
      beam.scale.set(1, 0.0001, 1);
      beam.visible = false;
      scene.add(beam);

      const markMaterial = new THREE.SpriteMaterial({
        map: markTexture,
        transparent: true,
        opacity: 0,
      });
      const mark = new THREE.Sprite(markMaterial);
      mark.position.set(x, 2.0, z);
      mark.scale.set(0.9, 0.9, 0.9);
      scene.add(mark);

      const avatar = createAvatarForSeat();
      avatar.group.position.set(x, 0.02, z - 0.08);
      scene.add(avatar.group);

      seatNodes.push({
        id: makeDeskId(idx + 1),
        seat,
        beam,
        mark,
        avatar,
        zoneCode: row < 2 ? (col < 3 ? 'strategy' : 'finance') : (col < 3 ? 'operation' : 'engineering'),
      });
    }
  }

  const starsGeo = new THREE.BufferGeometry();
  const starsCount = 220;
  const starsPos = new Float32Array(starsCount * 3);
  for (let i = 0; i < starsCount; i += 1) {
    starsPos[i * 3] = (Math.random() - 0.5) * 24;
    starsPos[i * 3 + 1] = Math.random() * 8.8;
    starsPos[i * 3 + 2] = (Math.random() - 0.5) * 18;
  }
  starsGeo.setAttribute('position', new THREE.BufferAttribute(starsPos, 3));
  starField = new THREE.Points(
    starsGeo,
    new THREE.PointsMaterial({
      color: 0x7fddff,
      size: 0.055,
      transparent: true,
      opacity: 0.74,
    }),
  );
  scene.add(starField);

  renderLoop();
}

function updateSeatRiskColors() {
  for (const node of seatNodes) {
    const risk = props.riskMap?.[node.id] || 'low';
    const { meta, deptLabel, roleTier, zoneCode, roleLabel } = resolveSeatDisplayMeta(node);
    const color = new THREE.Color(resolveRiskColor(risk));
    node.seat.material.color.set(color);
    const zoneColor = new THREE.Color(ZONE_COLOR[zoneCode] || ZONE_COLOR.strategy);
    const roleStyle = roleTone(meta.role, roleTier);
    node.seat.material.emissive.set(color.clone().multiply(zoneColor).multiplyScalar(0.3));
    node.seat.material.metalness = roleStyle.metalness;
    node.seat.material.roughness = roleStyle.roughness;
    node.seat.scale.setScalar(roleStyle.metalness > 0.5 ? 1.08 : 1);
    node.seat.userData = {
      ...(node.seat.userData || {}),
      departmentLabel: deptLabel,
      roleLabel,
      zoneCode,
      roleTier,
    };

    const zoneHex = zoneColor.getHex();
    if (node.avatar) {
      node.avatar.body.material.color.setHex(zoneHex);
      node.avatar.deptBadge.material.color.setHex(zoneHex);
      node.avatar.deptBadge.material.emissive.setHex(zoneHex);
      node.avatar.strategyAddon.visible = zoneCode === 'strategy';
      node.avatar.financeAddon.visible = zoneCode === 'finance';
      node.avatar.operationAddon.visible = zoneCode === 'operation';
      node.avatar.engineeringAddon.visible = zoneCode === 'engineering';
      node.avatar.strategyAddon.material.color.setHex(0x7bc7ff);
      node.avatar.financeAddon.material.color.setHex(0x66e0b4);
      node.avatar.operationAddon.material.color.setHex(0xffb866);
      node.avatar.engineeringAddon.material.color.setHex(0xd2a1ff);
      node.avatar.roleHalo.visible = roleTier === 'management' || roleTier === 'security' || roleTier === 'audit';
      node.avatar.roleHalo.material.color.setHex(roleTier === 'security' ? 0xff4b66 : roleTier === 'management' ? 0x95e0ff : 0xffcf6b);
      node.avatar.group.scale.setScalar(roleTier === 'management' ? 1.08 : roleTier === 'security' ? 1.02 : 0.96);
      if (zoneCode === 'engineering') {
        node.avatar.group.rotation.y = 0.24;
      } else if (zoneCode === 'finance') {
        node.avatar.group.rotation.y = -0.2;
      } else if (zoneCode === 'operation') {
        node.avatar.group.rotation.y = 0.08;
      } else {
        node.avatar.group.rotation.y = -0.06;
      }
    }

    node.mark.material.map = buildSeverityTexture(risk);
    node.mark.material.color.setHex(0xffffff);
    if (risk === 'critical') {
      node.mark.material.opacity = 0.98;
      node.mark.scale.set(1.06, 1.06, 1);
    } else if (risk === 'high') {
      node.mark.material.opacity = 0.94;
      node.mark.scale.set(0.94, 0.94, 1);
    } else if (risk === 'medium') {
      node.mark.material.opacity = 0.82;
      node.mark.scale.set(0.86, 0.86, 1);
    } else {
      node.mark.material.opacity = 0.62;
      node.mark.scale.set(0.7, 0.7, 1);
    }
  }
}

function clearFlowPath() {
  if (flowTube) {
    scene.remove(flowTube);
    flowTube.geometry.dispose();
    flowTube.material.dispose();
    flowTube = null;
  }
  for (const particle of flowParticles) {
    scene.remove(particle.mesh);
    particle.mesh.geometry.dispose();
    particle.mesh.material.dispose();
  }
  flowParticles.length = 0;
  flowCurve = null;
}

function buildFlowPath(fromPosition) {
  clearFlowPath();
  const start = new THREE.Vector3(fromPosition.x, 0.28, fromPosition.z);
  const end = new THREE.Vector3(commandCenter.position.x, 0.95, commandCenter.position.z);
  const ctrl = new THREE.Vector3(
    (start.x + end.x) * 0.5,
    1.65,
    (start.z + end.z) * 0.5,
  );

  flowCurve = new THREE.CatmullRomCurve3([start, ctrl, end]);
  const tubeGeo = new THREE.TubeGeometry(flowCurve, 52, 0.06, 12, false);
  const tubeMat = new THREE.MeshStandardMaterial({
    color: 0xff3e5c,
    emissive: 0xff2b52,
    emissiveIntensity: 1.7,
    transparent: true,
    opacity: 0.86,
  });
  flowTube = new THREE.Mesh(tubeGeo, tubeMat);
  scene.add(flowTube);

  const particleGeo = new THREE.SphereGeometry(0.07, 10, 10);
  for (let i = 0; i < 10; i += 1) {
    const mesh = new THREE.Mesh(
      particleGeo,
      new THREE.MeshStandardMaterial({
        color: 0xffe2e8,
        emissive: 0xff8aa0,
        emissiveIntensity: 1.45,
        transparent: true,
        opacity: 0.95,
      })
    );
    scene.add(mesh);
    flowParticles.push({ mesh, progress: i / 10 });
  }
}

function pulseAlarmEdge() {
  alarmPulse.value = true;
  if (pulseTimeout) {
    window.clearTimeout(pulseTimeout);
  }
  pulseTimeout = window.setTimeout(() => {
    alarmPulse.value = false;
    pulseTimeout = null;
  }, 1600);
}

function playAlertAnimation(event) {
  if (!event || !seatNodes.length || !commandCenter) {
    emit('animation-complete', event?.simId || null);
    return;
  }

  const seatId = normalizeDeskId(event.seatId || '');
  const fallbackIndex = hashSeatIndex(event.employeeKey || event.simId, seatNodes.length);
  const target = seatNodes.find((node) => node.id === seatId) || seatNodes[fallbackIndex];
  const alertColor = new THREE.Color(resolveRiskColor(event.severity || 'critical'));
  const displayMeta = resolveSeatDisplayMeta(target);
  focusContext.value = {
    zone: zoneLabel(displayMeta.zoneCode),
    department: displayMeta.deptLabel || normalizeText(event.department) || '未知部门',
    role: displayMeta.roleLabel || normalizeText(event.role) || '未知角色',
  };

  seatNodes.forEach((node) => {
    gsap.killTweensOf(node.beam.scale);
    gsap.killTweensOf(node.beam.material);
    gsap.killTweensOf(node.mark.material);
    gsap.killTweensOf(node.mark.rotation);
    if (node !== target) {
      node.beam.visible = false;
      node.beam.scale.y = 0.0001;
      node.mark.material.opacity = 0;
    }
  });

  target.beam.visible = true;
  target.beam.material.color.set(alertColor);
  target.beam.material.emissive.set(alertColor);
  target.mark.material.map = buildSeverityTexture(event.severity || 'critical');
  target.mark.material.color.setHex(0xffffff);
  target.mark.material.opacity = 1;

  buildFlowPath(target.seat.position);
  pulseAlarmEdge();

  const targetCam = new THREE.Vector3(target.seat.position.x * 0.38, 8.8, target.seat.position.z + 8.6);
  gsap.to(camera.position, {
    x: targetCam.x,
    y: targetCam.y,
    z: targetCam.z,
    duration: 0.62,
    ease: 'power2.out',
    onUpdate: () => camera.lookAt(target.seat.position),
  });
  gsap.to(camera.position, {
    x: 0,
    y: 12,
    z: 15,
    duration: 0.85,
    delay: 1.15,
    ease: 'power2.inOut',
    onUpdate: () => camera.lookAt(commandCenter.position),
  });

  gsap.timeline({
    onComplete: () => {
      emit('animation-complete', event.simId || null);
    },
  })
    .fromTo(
      target.beam.scale,
      { y: 0.0001 },
      { y: 4.0, duration: 0.42, ease: 'power2.out' }
    )
    .to(target.beam.position, { y: 2.25, duration: 0.48, ease: 'sine.inOut', yoyo: true, repeat: 3 }, 0.45)
    .to(target.beam.material, { opacity: 0.88, duration: 0.18 }, 0.12)
    .to(target.beam.material, { opacity: 0.62, duration: 0.28 }, 0.8)
    .to(commandCenter.material.emissive, { r: 1, g: 0.2, b: 0.36, duration: 0.22 }, 0.2)
    .to(commandCenter.material.emissive, { r: 0.07, g: 0.21, b: 0.36, duration: 0.44 }, 0.56)
    .to(warningRing.material.color, { r: alertColor.r, g: alertColor.g, b: alertColor.b, duration: 0.25 }, 0.12)
    .to(warningRing.scale, { x: 1.22, y: 1.22, z: 1.22, duration: 0.24 }, 0.16)
    .to(warningRing.scale, { x: 1, y: 1, z: 1, duration: 0.26 }, 0.52)
    .to(warningRing.material.color, { r: 0.4, g: 0.85, b: 1, duration: 0.4 }, 0.72)
    .to(target.mark.material, { opacity: 0.98, duration: 0.14 }, 0.1)
    .to(target.mark.rotation, { z: Math.PI * 2.4, duration: 1.0, ease: 'linear' }, 0.18)
    .to(target.mark.material, { opacity: 0.18, duration: 0.4 }, 1.35)
    .to(target.mark.material, { opacity: 0, duration: 0.38 }, 1.78)
    .to(target.beam.scale, { y: 0.001, duration: 0.32, ease: 'power2.in' }, 1.82)
    .add(() => {
      target.beam.visible = false;
    });
}

function renderLoop() {
  if (!renderer || !scene || !camera) return;
  frameId = window.requestAnimationFrame(renderLoop);

  if (commandCenter) {
    commandCenter.rotation.y += 0.008;
  }

  if (warningRing) {
    warningRing.rotation.z += 0.007;
  }

  if (starField) {
    starField.rotation.y += 0.0008;
  }

  if (flowCurve && flowParticles.length) {
    for (const particle of flowParticles) {
      particle.progress = (particle.progress + 0.009) % 1;
      const point = flowCurve.getPointAt(particle.progress);
      particle.mesh.position.copy(point);
    }
  }

  renderer.render(scene, camera);
}

function handleResize() {
  const el = containerRef.value;
  if (!renderer || !camera || !el) return;
  const width = Math.max(1, el.clientWidth);
  const height = Math.max(1, el.clientHeight);
  renderer.setSize(width, height);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
}

watch(
  () => props.riskMap,
  () => {
    updateSeatRiskColors();
  },
  { deep: true }
);

watch(
  () => props.seatMetaMap,
  () => {
    updateSeatRiskColors();
  },
  { deep: true },
);

watch(
  () => props.activeEvent?.simId,
  () => {
    if (props.activeEvent) {
      playAlertAnimation(props.activeEvent);
    }
  }
);

onMounted(() => {
  buildOfficeScene();
  initializeIdentitySceneState();
  updateSeatRiskColors();
  window.addEventListener('resize', handleResize, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  if (pulseTimeout) {
    window.clearTimeout(pulseTimeout);
    pulseTimeout = null;
  }
  if (frameId) {
    window.cancelAnimationFrame(frameId);
    frameId = 0;
  }
  clearFlowPath();
  for (const node of seatNodes) {
    node.seat.geometry.dispose();
    node.seat.material.dispose();
    node.beam.geometry.dispose();
    node.beam.material.dispose();
    node.mark.material.dispose();
    if (node.avatar?.group) {
      node.avatar.group.traverse((obj) => {
        if (obj.isMesh) {
          obj.geometry?.dispose?.();
          disposeObjectMaterials(obj);
        }
      });
      scene?.remove(node.avatar.group);
    }
  }
  seatNodes.length = 0;
  zoneMeshes.forEach((mesh) => {
    mesh.geometry.dispose();
    mesh.material.dispose();
  });
  zoneMeshes.length = 0;
  zoneLabels.forEach((label) => {
    label.material?.map?.dispose();
    label.material?.dispose();
  });
  zoneLabels.length = 0;
  departmentDecor.forEach((mesh) => {
    mesh.geometry?.dispose?.();
    mesh.material?.dispose?.();
    scene?.remove(mesh);
  });
  departmentDecor.length = 0;
  severityTextureCache.forEach((texture) => {
    texture.dispose?.();
  });
  severityTextureCache.clear();
  starField?.geometry?.dispose();
  starField?.material?.dispose();
  warningRing?.geometry?.dispose();
  warningRing?.material?.dispose();
  if (renderer) {
    renderer.dispose();
    renderer = null;
  }
  if (containerRef.value) {
    containerRef.value.innerHTML = '';
  }
  scene = null;
  camera = null;
  commandCenter = null;
  warningRing = null;
  starField = null;
});
</script>

<style scoped>
.office-sim-wrap {
  position: relative;
  border-radius: 18px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 20%, rgba(52, 138, 255, 0.32), transparent 34%),
    radial-gradient(circle at 82% 78%, rgba(255, 76, 108, 0.28), transparent 36%),
    linear-gradient(145deg, #040a16, #081120 42%, #0d1730);
  border: 1px solid rgba(122, 171, 255, 0.32);
  box-shadow: 0 0 0 1px rgba(61, 144, 255, 0.22) inset, 0 20px 48px rgba(2, 6, 18, 0.56);
}

.cyber-rain {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(transparent 0%, rgba(64, 255, 189, 0.1) 50%, transparent 100%),
    repeating-linear-gradient(
      90deg,
      rgba(74, 202, 255, 0.08) 0px,
      rgba(74, 202, 255, 0.08) 1px,
      transparent 1px,
      transparent 22px
    );
  mix-blend-mode: screen;
  animation: matrixFlow 8.2s linear infinite;
}

.office-sim-canvas {
  width: 100%;
  height: 390px;
}

.alarm-edge {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(circle, transparent 58%, rgba(255, 52, 84, 0.28) 100%);
  opacity: 0;
  transition: opacity 180ms ease;
}

.office-sim-wrap.alarm-active .alarm-edge {
  opacity: 1;
  animation: edgePulse 820ms ease 2;
}

.sim-caption {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 12px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(5, 14, 30, 0.76);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(124, 194, 255, 0.28);
  color: #d9f3ff;
  font-size: 12px;
  text-shadow: 0 0 10px rgba(64, 211, 255, 0.35);
}

.sim-live {
  color: #ffbed7;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-shadow: 0 0 14px rgba(255, 78, 146, 0.58);
}

.asset-diagnostic {
  position: absolute;
  right: 12px;
  top: 12px;
  max-width: min(42vw, 420px);
  border-radius: 10px;
  padding: 8px 10px;
  display: grid;
  gap: 4px;
  font-size: 11px;
  color: #d7f3ff;
  border: 1px solid rgba(107, 189, 255, 0.3);
  background: rgba(6, 14, 30, 0.75);
  backdrop-filter: blur(4px);
  text-shadow: 0 0 8px rgba(78, 202, 255, 0.35);
}

.asset-diagnostic strong {
  color: #9ee8ff;
}

.sim-caption strong {
  color: #ffffff;
  letter-spacing: 0.03em;
}

@keyframes edgePulse {
  0% { opacity: 0.25; }
  50% { opacity: 1; }
  100% { opacity: 0.2; }
}

@keyframes matrixFlow {
  0% { transform: translateY(-10%); }
  100% { transform: translateY(14%); }
}

@media (max-width: 900px) {
  .office-sim-canvas {
    height: 320px;
  }

  .sim-caption {
    flex-direction: column;
    align-items: flex-start;
  }

  .asset-diagnostic {
    max-width: calc(100% - 24px);
  }
}
</style>
