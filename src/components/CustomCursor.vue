<template>
  <!-- 外环（阻尼跟随，较慢） -->
  <div ref="ringEl" class="cursor-ring" :class="ringClass" aria-hidden="true" />
  <!-- 内核（贴近鼠标，近乎即时） -->
  <div ref="dotEl" class="cursor-dot" :class="dotClass" aria-hidden="true" />
</template>

<script setup>
/**
 * CustomCursor.vue — 阻尼跟随双层光标
 *
 * 原理：
 *  · 内核 (dot) 每帧直接跟随鼠标，零延迟，保证"手感准确"。
 *  · 外环 (ring) 使用指数平滑向鼠标位置靠近，产生优雅的惯性拖拽感。
 *
 * 状态：
 *  · 悬停可点击元素时 → .is-pointer（环放大、变色）
 *  · 悬停输入框时    → .is-text（环变细竖线状）
 *  · 鼠标按下时      → .is-press（两层都缩小）
 */
import { ref, onMounted, onUnmounted } from 'vue';
import gsap from 'gsap';

const ringEl = ref(null);
const dotEl  = ref(null);

// 当前状态
const ringClass = ref('');
const dotClass  = ref('');

// 鼠标真实坐标
let mx = window.innerWidth  / 2;
let my = window.innerHeight / 2;

// 环的插值坐标（阻尼用）
let rx = mx;
let ry = my;

// 阻尼系数 0~1，越大越"跟手"，越小越"飘"
const EASE = 0.12;

let rafId = null;

function tick() {
  // 指数平滑插值
  rx += (mx - rx) * EASE;
  ry += (my - ry) * EASE;

  gsap.set(ringEl.value, { x: rx, y: ry, xPercent: -50, yPercent: -50 });
  gsap.set(dotEl.value,  { x: mx, y: my, xPercent: -50, yPercent: -50 });

  rafId = requestAnimationFrame(tick);
}

function onMouseMove(e) {
  mx = e.clientX;
  my = e.clientY;
}

const CLICKABLE = 'a, button, [role="button"], label, select, input[type="checkbox"], input[type="radio"], .clickable, .nav-item, .el-menu-item, .el-button, .el-dropdown, .el-link, [data-cursor="pointer"]';
const TEXT_INPUT = 'input:not([type="checkbox"]):not([type="radio"]):not([type="range"]), textarea, [contenteditable]';

function updateCursorState(e) {
  const el = e.target;
  if (el.matches(TEXT_INPUT) || el.closest(TEXT_INPUT)) {
    ringClass.value = 'is-text';
    dotClass.value  = 'is-text';
  } else if (el.matches(CLICKABLE) || el.closest(CLICKABLE)) {
    ringClass.value = 'is-pointer';
    dotClass.value  = 'is-pointer';
  } else {
    ringClass.value = '';
    dotClass.value  = '';
  }
}

function onMouseDown() {
  ringClass.value += ' is-press';
  dotClass.value  += ' is-press';
}

function onMouseUp() {
  // 重新触发悬停检测
  ringClass.value = ringClass.value.replace(' is-press', '');
  dotClass.value  = dotClass.value.replace(' is-press', '');
}

// 鼠标离开视口时隐藏
function onMouseLeave() {
  gsap.to([ringEl.value, dotEl.value], { opacity: 0, duration: 0.2 });
}
function onMouseEnter() {
  gsap.to([ringEl.value, dotEl.value], { opacity: 1, duration: 0.2 });
}

onMounted(() => {
  // 隐藏系统光标
  document.documentElement.style.cursor = 'none';

  window.addEventListener('mousemove', onMouseMove,     { passive: true });
  window.addEventListener('mousemove', updateCursorState, { passive: true });
  window.addEventListener('mousedown', onMouseDown);
  window.addEventListener('mouseup',   onMouseUp);
  document.documentElement.addEventListener('mouseleave', onMouseLeave);
  document.documentElement.addEventListener('mouseenter', onMouseEnter);

  // 初始定位（避免首帧闪烁在左上角）
  gsap.set([ringEl.value, dotEl.value], { x: mx, y: my, xPercent: -50, yPercent: -50 });

  rafId = requestAnimationFrame(tick);
});

onUnmounted(() => {
  document.documentElement.style.cursor = '';
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('mousemove', updateCursorState);
  window.removeEventListener('mousedown', onMouseDown);
  window.removeEventListener('mouseup',   onMouseUp);
  document.documentElement.removeEventListener('mouseleave', onMouseLeave);
  document.documentElement.removeEventListener('mouseenter', onMouseEnter);
  if (rafId) cancelAnimationFrame(rafId);
});
</script>

<style scoped>
/* 两个光标元素固定在视口，pointer-events:none 避免干扰点击 */
.cursor-ring,
.cursor-dot {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 99999;
  border-radius: 50%;
  will-change: transform;
  transition:
    width  0.25s cubic-bezier(0.23,1,0.32,1),
    height 0.25s cubic-bezier(0.23,1,0.32,1),
    background 0.25s ease,
    border-color 0.25s ease,
    opacity 0.2s ease;
}

/* ── 外环 ── */
.cursor-ring {
  width: 36px;
  height: 36px;
  border: 1.5px solid rgba(100, 160, 255, 0.55);
  background: transparent;
  mix-blend-mode: screen; /* 在深色背景上发光 */
}

/* ── 内核 ── */
.cursor-dot {
  width: 6px;
  height: 6px;
  background: #64a0ff;
  box-shadow: 0 0 8px 2px rgba(100, 160, 255, 0.7);
}

/* ── 悬停可点击元素 ── */
.cursor-ring.is-pointer {
  width: 52px;
  height: 52px;
  border-color: rgba(100, 160, 255, 0.8);
  background: rgba(100, 160, 255, 0.06);
}
.cursor-dot.is-pointer {
  width: 8px;
  height: 8px;
  background: #a0c4ff;
  box-shadow: 0 0 12px 3px rgba(100, 160, 255, 0.9);
}

/* ── 悬停文本输入框 ── */
.cursor-ring.is-text {
  width: 2px;
  height: 28px;
  border-radius: 2px;
  border-color: rgba(100, 160, 255, 0.7);
}
.cursor-dot.is-text {
  opacity: 0;
}

/* ── 按下 ── */
.cursor-ring.is-press {
  transform: scale(0.75);
}
.cursor-dot.is-press {
  transform: scale(0.6);
}
</style>
