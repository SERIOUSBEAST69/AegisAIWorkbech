<template>
  <div class="office-sim-wrap" :class="{ 'alarm-active': alarmPulse }">
    <div ref="containerRef" class="office-sim-canvas"></div>
    <div class="alarm-edge" aria-hidden="true"></div>
    <div class="sim-caption">
      <strong>3D 办公楼层态势</strong>
      <span>红色光柱与流线代表异常行为已汇聚到安全指挥中心。</span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
import gsap from 'gsap';

const props = defineProps({
  riskMap: {
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

let renderer = null;
let scene = null;
let camera = null;
let frameId = 0;
let pulseTimeout = null;

const seatNodes = [];
const flowParticles = [];
let commandCenter = null;
let flowCurve = null;
let flowTube = null;

const RISK_COLOR = {
  low: 0x42c77f,
  medium: 0xf6c24b,
  high: 0xff7d45,
  critical: 0xff3058,
};

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

  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(20, 14, 24, 18),
    new THREE.MeshStandardMaterial({
      color: 0x0e1f33,
      metalness: 0.15,
      roughness: 0.85,
      emissive: 0x081524,
      emissiveIntensity: 0.5,
      wireframe: false,
    })
  );
  floor.rotation.x = -Math.PI / 2;
  floor.position.y = -0.05;
  scene.add(floor);

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

  const seatGeo = new THREE.BoxGeometry(0.7, 0.38, 0.7);
  const beamGeo = new THREE.BoxGeometry(0.45, 1.0, 0.45);
  const markTexture = createMarkTexture();

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
        opacity: 0.72,
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

      seatNodes.push({
        id: `seat-${idx + 1}`,
        seat,
        beam,
        mark,
      });
    }
  }

  renderLoop();
}

function updateSeatRiskColors() {
  for (const node of seatNodes) {
    const risk = props.riskMap?.[node.id] || 'low';
    const color = new THREE.Color(resolveRiskColor(risk));
    node.seat.material.color.set(color);
    node.seat.material.emissive.set(color.clone().multiplyScalar(0.28));
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
    emissiveIntensity: 1.4,
    transparent: true,
    opacity: 0.82,
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

  const seatId = String(event.seatId || '');
  const fallbackIndex = hashSeatIndex(event.employeeKey || event.simId, seatNodes.length);
  const target = seatNodes.find((node) => node.id === seatId) || seatNodes[fallbackIndex];
  const alertColor = new THREE.Color(resolveRiskColor(event.severity || 'critical'));

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
  target.mark.material.opacity = 1;

  buildFlowPath(target.seat.position);
  pulseAlarmEdge();

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
  () => props.activeEvent?.simId,
  () => {
    if (props.activeEvent) {
      playAlertAnimation(props.activeEvent);
    }
  }
);

onMounted(() => {
  buildOfficeScene();
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
    node.mark.material.map?.dispose();
    node.mark.material.dispose();
  }
  seatNodes.length = 0;
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
});
</script>

<style scoped>
.office-sim-wrap {
  position: relative;
  border-radius: 18px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 20%, rgba(52, 138, 255, 0.26), transparent 34%),
    radial-gradient(circle at 82% 78%, rgba(255, 76, 108, 0.24), transparent 36%),
    linear-gradient(145deg, #050d18, #08121f 48%, #0f1a2f);
  border: 1px solid rgba(122, 171, 255, 0.28);
  box-shadow: 0 18px 45px rgba(3, 8, 20, 0.42);
}

.office-sim-canvas {
  width: 100%;
  height: 360px;
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
  background: rgba(6, 14, 28, 0.7);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #d9e7ff;
  font-size: 12px;
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

@media (max-width: 900px) {
  .office-sim-canvas {
    height: 300px;
  }

  .sim-caption {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
