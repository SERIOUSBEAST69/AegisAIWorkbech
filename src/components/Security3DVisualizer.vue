<template>
  <div class="security-visual-shell">
    <div class="scanlines" aria-hidden="true"></div>
    <div ref="containerRef" class="security-3d-canvas"></div>
    <div class="hud-overlay" aria-hidden="true">
      <span>SOC HOLOGRAM</span>
      <span>THREAT LINK MATRIX</span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
import gsap from 'gsap';

const props = defineProps({
  activeEvent: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['animation-complete']);
const containerRef = ref(null);

let renderer = null;
let scene = null;
let camera = null;
let frameId = 0;
let coreMesh = null;
let shieldRingA = null;
let shieldRingB = null;
let pulseLine = null;
let gridPlane = null;
let stars = null;
let stream = null;
const departmentNodes = [];

function makeGridTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 512;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return new THREE.CanvasTexture(canvas);
  }
  ctx.fillStyle = '#050913';
  ctx.fillRect(0, 0, 512, 512);
  ctx.strokeStyle = 'rgba(64,180,255,0.26)';
  ctx.lineWidth = 1;
  for (let i = 0; i <= 32; i += 1) {
    const p = i * 16;
    ctx.beginPath();
    ctx.moveTo(p, 0);
    ctx.lineTo(p, 512);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(0, p);
    ctx.lineTo(512, p);
    ctx.stroke();
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(2, 2);
  return texture;
}

function buildScene() {
  const el = containerRef.value;
  if (!el) return;

  scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x05070d, 12, 42);
  camera = new THREE.PerspectiveCamera(48, Math.max(1, el.clientWidth) / Math.max(1, el.clientHeight), 0.1, 120);
  camera.position.set(0, 5.2, 11.6);

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
  renderer.setSize(Math.max(1, el.clientWidth), Math.max(1, el.clientHeight));
  renderer.setClearColor(0x000000, 0);
  el.appendChild(renderer.domElement);

  const ambient = new THREE.AmbientLight(0x65a6ff, 0.45);
  scene.add(ambient);

  const key = new THREE.PointLight(0x5dd1ff, 1.2, 45);
  key.position.set(4, 8, 6);
  scene.add(key);

  const rim = new THREE.PointLight(0xff2a66, 0.68, 26);
  rim.position.set(-6, 3, -3);
  scene.add(rim);

  const gridTex = makeGridTexture();
  gridPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(24, 16),
    new THREE.MeshStandardMaterial({
      map: gridTex,
      color: 0x0b1531,
      emissive: 0x0c2d4a,
      emissiveIntensity: 0.72,
      metalness: 0.24,
      roughness: 0.72,
      transparent: true,
      opacity: 0.9,
    }),
  );
  gridPlane.rotation.x = -Math.PI / 2;
  gridPlane.position.y = -0.7;
  scene.add(gridPlane);

  const coreGeo = new THREE.IcosahedronGeometry(1.02, 1);
  const coreMat = new THREE.MeshStandardMaterial({
    color: 0x54c9ff,
    emissive: 0x102f56,
    emissiveIntensity: 1.1,
    metalness: 0.42,
    roughness: 0.18,
  });
  coreMesh = new THREE.Mesh(coreGeo, coreMat);
  coreMesh.position.set(0, 0.8, 0);
  scene.add(coreMesh);

  const ringMat = new THREE.MeshStandardMaterial({
    color: 0x69d0ff,
    emissive: 0x134266,
    emissiveIntensity: 0.95,
    metalness: 0.52,
    roughness: 0.2,
    transparent: true,
    opacity: 0.95,
  });
  shieldRingA = new THREE.Mesh(new THREE.TorusGeometry(1.95, 0.08, 22, 120), ringMat.clone());
  shieldRingA.rotation.x = 1.22;
  scene.add(shieldRingA);

  shieldRingB = new THREE.Mesh(new THREE.TorusGeometry(2.28, 0.05, 18, 100), ringMat.clone());
  shieldRingB.rotation.set(Math.PI / 2, 0, 0.44);
  scene.add(shieldRingB);

  pulseLine = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-5.6, 0.2, 0),
      new THREE.Vector3(0, 0.8, 0),
    ]),
    new THREE.LineBasicMaterial({ color: 0xff4e8f, transparent: true, opacity: 0 }),
  );
  scene.add(pulseLine);

  const nodeGeo = new THREE.OctahedronGeometry(0.5, 0);
  for (let i = 0; i < 7; i += 1) {
    const angle = (Math.PI * 2 * i) / 7;
    const radius = 4 + (i % 2) * 0.7;
    const mat = new THREE.MeshStandardMaterial({
      color: 0x2e8dff,
      emissive: 0x132b4c,
      emissiveIntensity: 0.78,
      metalness: 0.28,
      roughness: 0.4,
    });
    const node = new THREE.Mesh(nodeGeo, mat);
    node.position.set(Math.cos(angle) * radius, 0.25 + ((i % 3) * 0.5), Math.sin(angle) * 2.8);
    scene.add(node);
    departmentNodes.push(node);
  }

  const starCount = 160;
  const starGeo = new THREE.BufferGeometry();
  const starPos = new Float32Array(starCount * 3);
  for (let i = 0; i < starCount; i += 1) {
    starPos[i * 3] = (Math.random() - 0.5) * 22;
    starPos[i * 3 + 1] = Math.random() * 8 + 1;
    starPos[i * 3 + 2] = (Math.random() - 0.5) * 16;
  }
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
  stars = new THREE.Points(
    starGeo,
    new THREE.PointsMaterial({ color: 0x8ad9ff, size: 0.06, transparent: true, opacity: 0.82 }),
  );
  scene.add(stars);

  const streamGeo = new THREE.BufferGeometry();
  const streamCount = 70;
  const streamPos = new Float32Array(streamCount * 3);
  for (let i = 0; i < streamCount; i += 1) {
    streamPos[i * 3] = (Math.random() - 0.5) * 18;
    streamPos[i * 3 + 1] = Math.random() * 6.5;
    streamPos[i * 3 + 2] = (Math.random() - 0.5) * 12;
  }
  streamGeo.setAttribute('position', new THREE.BufferAttribute(streamPos, 3));
  stream = new THREE.Points(
    streamGeo,
    new THREE.PointsMaterial({ color: 0x40ffa7, size: 0.048, transparent: true, opacity: 0.68 }),
  );
  scene.add(stream);

  renderLoop();
}

function renderLoop() {
  if (!renderer || !scene || !camera) return;
  frameId = window.requestAnimationFrame(renderLoop);

  if (shieldRingA) shieldRingA.rotation.z += 0.007;
  if (shieldRingB) shieldRingB.rotation.y -= 0.005;
  if (coreMesh) {
    coreMesh.rotation.y += 0.01;
    coreMesh.rotation.x += 0.003;
  }
  if (stars) {
    stars.rotation.y += 0.0009;
  }
  if (stream) {
    const attr = stream.geometry.getAttribute('position');
    for (let i = 0; i < attr.count; i += 1) {
      const y = attr.getY(i) - 0.035;
      attr.setY(i, y < 0 ? 7 : y);
    }
    attr.needsUpdate = true;
  }

  renderer.render(scene, camera);
}

function nodeByTarget(targetKey) {
  const key = String(targetKey || 'core-network');
  let hash = 0;
  for (let i = 0; i < key.length; i += 1) {
    hash = (hash + key.charCodeAt(i)) % 997;
  }
  return departmentNodes[hash % Math.max(1, departmentNodes.length)] || null;
}

function parseColor(value, fallback) {
  try {
    if (typeof value === 'string' && value.trim()) {
      return new THREE.Color(value.trim());
    }
  } catch {
    // use fallback
  }
  return new THREE.Color(fallback);
}

function playAnimation(ev) {
  if (!ev || !coreMesh || !shieldRingA || !shieldRingB || !pulseLine) {
    emit('animation-complete', ev?.eventId || null);
    return;
  }

  const targetNode = nodeByTarget(ev.targetKey);
  const severity = String(ev.severity || 'high').toLowerCase();
  const effectProfile = ev.effectProfile || ev.effect_profile || {};
  const fallbackDanger = severity === 'critical' ? 0xff1d6f : 0xff5f3d;
  const danger = parseColor(effectProfile.primaryColor, fallbackDanger);
  const calm = new THREE.Color(0x69d0ff);
  const scaleBoost = String(effectProfile.intensity || '').toLowerCase() === 'high' ? 1.62 : 1.48;
  const duration = Math.max(580, Number(effectProfile.durationMs || 760));

  const lineMat = pulseLine.material;
  const targetPos = targetNode ? targetNode.position : new THREE.Vector3(3.4, 0.6, 0);
  pulseLine.geometry.setFromPoints([
    new THREE.Vector3(-5.8, 0.2, -0.2),
    new THREE.Vector3(targetPos.x, targetPos.y, targetPos.z),
  ]);

  gsap.timeline({ onComplete: () => emit('animation-complete', ev.eventId || null) })
    .to(lineMat, { opacity: 1, duration: duration / 3200 })
    .to(lineMat, { opacity: 0, duration: duration / 2600 }, '+=0.08')
    .to(coreMesh.scale, { x: 1.3, y: 1.3, z: 1.3, duration: duration / 3300 }, 0)
    .to(coreMesh.scale, { x: 1, y: 1, z: 1, duration: duration / 2400 }, '+=0.2')
    .to(shieldRingA.material.color, { r: danger.r, g: danger.g, b: danger.b, duration: duration / 3000 }, 0.02)
    .to(shieldRingB.material.color, { r: danger.r, g: danger.g, b: danger.b, duration: duration / 2800 }, 0.04)
    .to(shieldRingA.material.color, { r: calm.r, g: calm.g, b: calm.b, duration: duration / 1700 }, '+=0.2')
    .to(shieldRingB.material.color, { r: calm.r, g: calm.g, b: calm.b, duration: duration / 1600 }, '<');

  if (targetNode) {
    gsap.timeline()
      .to(targetNode.scale, { x: scaleBoost, y: scaleBoost, z: scaleBoost, duration: duration / 3900 })
      .to(targetNode.material.color, { r: danger.r, g: danger.g, b: danger.b, duration: duration / 4400 }, 0)
      .to(targetNode.scale, { x: 1, y: 1, z: 1, duration: duration / 2200 }, '+=0.26')
      .to(targetNode.material.color, { r: calm.r, g: calm.g, b: calm.b, duration: duration / 1900 }, '+=0.06');
  }
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
  () => props.activeEvent?.eventId,
  () => {
    if (props.activeEvent) playAnimation(props.activeEvent);
  },
);

onMounted(() => {
  buildScene();
  window.addEventListener('resize', handleResize, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  if (frameId) {
    window.cancelAnimationFrame(frameId);
    frameId = 0;
  }
  departmentNodes.forEach((node) => {
    node.geometry.dispose();
    node.material.dispose();
  });
  departmentNodes.length = 0;
  stars?.geometry?.dispose();
  stars?.material?.dispose();
  stream?.geometry?.dispose();
  stream?.material?.dispose();
  gridPlane?.material?.map?.dispose();
  gridPlane?.geometry?.dispose();
  gridPlane?.material?.dispose();
  pulseLine?.geometry?.dispose();
  pulseLine?.material?.dispose();
  coreMesh?.geometry?.dispose();
  coreMesh?.material?.dispose();
  shieldRingA?.geometry?.dispose();
  shieldRingA?.material?.dispose();
  shieldRingB?.geometry?.dispose();
  shieldRingB?.material?.dispose();
  if (renderer) {
    renderer.dispose();
    renderer = null;
  }
  if (containerRef.value) containerRef.value.innerHTML = '';
  scene = null;
  camera = null;
  coreMesh = null;
  shieldRingA = null;
  shieldRingB = null;
  pulseLine = null;
  gridPlane = null;
  stars = null;
  stream = null;
});
</script>

<style scoped>
.security-visual-shell {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(94, 214, 255, 0.28);
  box-shadow: 0 0 0 1px rgba(67, 132, 255, 0.22) inset, 0 22px 42px rgba(0, 8, 25, 0.55);
}

.security-3d-canvas {
  width: 100%;
  height: 320px;
  border-radius: 14px;
  background:
    radial-gradient(circle at 16% 18%, rgba(36, 134, 255, 0.22), transparent 42%),
    radial-gradient(circle at 84% 82%, rgba(255, 37, 115, 0.2), transparent 40%),
    linear-gradient(150deg, rgba(3, 7, 16, 0.98), rgba(4, 11, 23, 0.98));
}

.scanlines {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    rgba(196, 244, 255, 0.04) 0px,
    rgba(196, 244, 255, 0.04) 1px,
    transparent 1px,
    transparent 4px
  );
  mix-blend-mode: screen;
  animation: scanMove 7s linear infinite;
}

.hud-overlay {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 10px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 11px;
  letter-spacing: 0.09em;
  color: rgba(142, 236, 255, 0.92);
  text-shadow: 0 0 12px rgba(76, 203, 255, 0.66);
  pointer-events: none;
}

@keyframes scanMove {
  0% { transform: translateY(0); }
  100% { transform: translateY(16px); }
}

@media (max-width: 900px) {
  .security-3d-canvas {
    height: 280px;
  }

  .hud-overlay {
    font-size: 10px;
    letter-spacing: 0.06em;
  }
}
</style>
