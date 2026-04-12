<template>
  <div ref="containerRef" class="security-3d-canvas"></div>
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
let shieldMesh = null;
let pulseLine = null;
const departmentNodes = [];

function buildScene() {
  const el = containerRef.value;
  if (!el) return;

  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(48, Math.max(1, el.clientWidth) / Math.max(1, el.clientHeight), 0.1, 100);
  camera.position.set(0, 3.2, 10.2);

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
  renderer.setSize(Math.max(1, el.clientWidth), Math.max(1, el.clientHeight));
  renderer.setClearColor(0x000000, 0);
  el.appendChild(renderer.domElement);

  const ambient = new THREE.AmbientLight(0x7fb8ff, 0.55);
  scene.add(ambient);

  const key = new THREE.PointLight(0x87ceff, 1.05, 40);
  key.position.set(5, 8, 8);
  scene.add(key);

  const coreGeo = new THREE.SphereGeometry(0.72, 24, 24);
  const coreMat = new THREE.MeshStandardMaterial({ color: 0x37a6ff, emissive: 0x102f5a, metalness: 0.25, roughness: 0.35 });
  coreMesh = new THREE.Mesh(coreGeo, coreMat);
  coreMesh.position.set(0, 0.2, 0);
  scene.add(coreMesh);

  const shieldGeo = new THREE.TorusGeometry(1.45, 0.08, 18, 80);
  const shieldMat = new THREE.MeshStandardMaterial({ color: 0x8ed6ff, emissive: 0x12314a, metalness: 0.35, roughness: 0.3 });
  shieldMesh = new THREE.Mesh(shieldGeo, shieldMat);
  shieldMesh.rotation.x = 1.2;
  scene.add(shieldMesh);

  const lineGeo = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(-4, 0.2, 0),
    new THREE.Vector3(0, 0.2, 0),
  ]);
  const lineMat = new THREE.LineBasicMaterial({ color: 0xff4961, transparent: true, opacity: 0 });
  pulseLine = new THREE.Line(lineGeo, lineMat);
  scene.add(pulseLine);

  const nodeGeo = new THREE.BoxGeometry(0.55, 0.55, 0.55);
  for (let i = 0; i < 6; i += 1) {
    const angle = (Math.PI * 2 * i) / 6;
    const mat = new THREE.MeshStandardMaterial({ color: 0x2f8fff, emissive: 0x11243b, metalness: 0.2, roughness: 0.55 });
    const node = new THREE.Mesh(nodeGeo, mat);
    node.position.set(Math.cos(angle) * 3.2, 0.15 + ((i % 2) * 0.35), Math.sin(angle) * 2.4);
    scene.add(node);
    departmentNodes.push(node);
  }

  renderLoop();
}

function renderLoop() {
  if (!renderer || !scene || !camera) return;
  frameId = window.requestAnimationFrame(renderLoop);
  if (shieldMesh) {
    shieldMesh.rotation.z += 0.005;
  }
  if (coreMesh) {
    coreMesh.rotation.y += 0.008;
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
  } catch (e) {
    // Fall through to fallback color.
  }
  return new THREE.Color(fallback);
}

function playAnimation(ev) {
  if (!ev || !coreMesh || !shieldMesh || !pulseLine) {
    emit('animation-complete', ev?.eventId || null);
    return;
  }

  const targetNode = nodeByTarget(ev.targetKey);
  const severity = String(ev.severity || 'high').toLowerCase();
  const effectProfile = ev.effectProfile || ev.effect_profile || {};
  const fallbackDanger = severity === 'critical' ? 0xff213f : 0xff5f3d;
  const danger = parseColor(effectProfile.primaryColor, fallbackDanger);
  const calmColor = 0x2f8fff;
  const scaleBoost = String(effectProfile.intensity || '').toLowerCase() === 'high' ? 1.52 : 1.42;
  const animationDuration = Math.max(520, Number(effectProfile.durationMs || 680));

  const lineMat = pulseLine.material;
  const targetPos = targetNode ? targetNode.position : new THREE.Vector3(2.8, 0.2, 0);
  pulseLine.geometry.setFromPoints([
    new THREE.Vector3(-4.2, 0.2, 0),
    new THREE.Vector3(targetPos.x, targetPos.y, targetPos.z),
  ]);

  gsap.timeline({
    onComplete: () => {
      emit('animation-complete', ev.eventId || null);
    },
  })
    .to(lineMat, { opacity: 0.9, duration: animationDuration / 3400 })
    .to(lineMat, { opacity: 0, duration: animationDuration / 3000 }, '+=0.08')
    .to(coreMesh.scale, { x: 1.2, y: 1.2, z: 1.2, duration: animationDuration / 3800 }, 0)
    .to(coreMesh.scale, { x: 1, y: 1, z: 1, duration: animationDuration / 2600 }, '+=0.16')
    .to(shieldMesh.material.color, {
      r: danger.r,
      g: danger.g,
      b: danger.b,
      duration: animationDuration / 3800,
    }, 0.04)
    .to(shieldMesh.material.color, {
      r: new THREE.Color(calmColor).r,
      g: new THREE.Color(calmColor).g,
      b: new THREE.Color(calmColor).b,
      duration: animationDuration / 1700,
    }, '+=0.18');

  if (targetNode) {
    gsap.timeline()
      .to(targetNode.scale, { x: scaleBoost, y: scaleBoost, z: scaleBoost, duration: animationDuration / 4200 })
      .to(targetNode.material.color, {
        r: danger.r,
        g: danger.g,
        b: danger.b,
        duration: animationDuration / 5600,
      }, 0)
      .to(targetNode.scale, { x: 1, y: 1, z: 1, duration: animationDuration / 2600 }, '+=0.22')
      .to(targetNode.material.color, {
        r: new THREE.Color(calmColor).r,
        g: new THREE.Color(calmColor).g,
        b: new THREE.Color(calmColor).b,
        duration: animationDuration / 2100,
      }, '+=0.05');
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
    if (props.activeEvent) {
      playAnimation(props.activeEvent);
    }
  }
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
  if (renderer) {
    renderer.dispose();
    renderer = null;
  }
  if (containerRef.value) {
    containerRef.value.innerHTML = '';
  }
  departmentNodes.length = 0;
  scene = null;
  camera = null;
  coreMesh = null;
  shieldMesh = null;
  pulseLine = null;
});
</script>

<style scoped>
.security-3d-canvas {
  width: 100%;
  height: 280px;
  border-radius: 12px;
  background:
    radial-gradient(circle at 20% 20%, rgba(46, 129, 255, 0.18), transparent 35%),
    radial-gradient(circle at 80% 80%, rgba(118, 189, 255, 0.14), transparent 32%),
    linear-gradient(140deg, rgba(5, 17, 34, 0.95), rgba(4, 10, 20, 0.92));
  border: 1px solid rgba(118, 170, 255, 0.22);
}
</style>
