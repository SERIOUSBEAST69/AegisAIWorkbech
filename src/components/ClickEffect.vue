<template>
  <div class="click-effect-container">
    <div
      v-for="(effect, index) in effects"
      :key="index"
      class="click-effect"
      :style="{
        left: effect.x + 'px',
        top: effect.y + 'px',
        animationDuration: effect.duration + 'ms'
      }"
      @animationend="removeEffect(index)"
    >
      <!-- 核心光晕 -->
      <div class="click-effect-core"></div>
      <!-- 多层波纹 -->
      <div class="click-effect-ripple ripple-1"></div>
      <div class="click-effect-ripple ripple-2"></div>
      <div class="click-effect-ripple ripple-3"></div>
      <!-- 粒子系统 -->
      <div class="click-effect-particles">
        <div 
          v-for="(particle, i) in 12" 
          :key="i" 
          class="click-effect-particle"
          :style="{
            animationDelay: i * 0.03 + 's',
            '--angle': (i * 30) + 'deg'
          }"
        ></div>
      </div>
      <!-- 能量波 -->
      <div class="click-effect-energy"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';

const effects = ref([]);

function createEffect(e) {
  const x = e.clientX;
  const y = e.clientY;
  
  effects.value.push({
    x,
    y,
    duration: 1500
  });
}

function removeEffect(index) {
  effects.value.splice(index, 1);
}

function handleClick(e) {
  createEffect(e);
}

onMounted(() => {
  document.addEventListener('click', handleClick);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClick);
});
</script>

<style scoped>
.click-effect-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 9999;
}

.click-effect {
  position: absolute;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%);
  overflow: hidden;
}

/* 核心光晕 */
.click-effect-core {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5f87ff, #00d4ff);
  box-shadow: 
    0 0 20px rgba(95, 135, 255, 0.8),
    0 0 40px rgba(0, 212, 255, 0.6),
    0 0 60px rgba(95, 135, 255, 0.4);
  animation: coreEffect 1.5s ease-out forwards;
  z-index: 5;
  position: relative;
}

/* 多层波纹 */
.click-effect-ripple {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid rgba(95, 135, 255, 0.6);
  animation: rippleEffect 1.5s ease-out forwards;
  z-index: 4;
}

.ripple-1 {
  animation-delay: 0s;
  border-color: rgba(95, 135, 255, 0.8);
}

.ripple-2 {
  animation-delay: 0.2s;
  border-color: rgba(0, 212, 255, 0.6);
  border-width: 1.5px;
}

.ripple-3 {
  animation-delay: 0.4s;
  border-color: rgba(170, 212, 255, 0.4);
  border-width: 1px;
}

/* 粒子系统 */
.click-effect-particles {
  position: absolute;
  width: 100%;
  height: 100%;
  z-index: 3;
}

.click-effect-particle {
  position: absolute;
  width: 3px;
  height: 3px;
  background: #5f87ff;
  border-radius: 50%;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: particleEffect 1.5s ease-out forwards;
  box-shadow: 0 0 10px rgba(95, 135, 255, 0.8);
}

/* 粒子位置和动画 */
.click-effect-particle:nth-child(1) { animation-delay: 0s; }
.click-effect-particle:nth-child(2) { animation-delay: 0.05s; }
.click-effect-particle:nth-child(3) { animation-delay: 0.1s; }
.click-effect-particle:nth-child(4) { animation-delay: 0.15s; }
.click-effect-particle:nth-child(5) { animation-delay: 0.2s; }
.click-effect-particle:nth-child(6) { animation-delay: 0.25s; }
.click-effect-particle:nth-child(7) { animation-delay: 0.3s; }
.click-effect-particle:nth-child(8) { animation-delay: 0.35s; }
.click-effect-particle:nth-child(9) { animation-delay: 0.4s; }
.click-effect-particle:nth-child(10) { animation-delay: 0.45s; }
.click-effect-particle:nth-child(11) { animation-delay: 0.5s; }
.click-effect-particle:nth-child(12) { animation-delay: 0.55s; }

/* 能量波 */
.click-effect-energy {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(95, 135, 255, 0.2) 0%, rgba(0, 212, 255, 0.1) 50%, transparent 70%);
  animation: energyEffect 1.5s ease-out forwards;
  z-index: 2;
}

/* 动画定义 */
@keyframes coreEffect {
  0% {
    transform: scale(0);
    opacity: 1;
    box-shadow: 0 0 0 rgba(95, 135, 255, 0.8);
  }
  30% {
    transform: scale(2);
    opacity: 1;
    box-shadow: 
      0 0 30px rgba(95, 135, 255, 0.9),
      0 0 60px rgba(0, 212, 255, 0.7),
      0 0 90px rgba(95, 135, 255, 0.5);
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
    box-shadow: 0 0 0 rgba(95, 135, 255, 0);
  }
}

@keyframes rippleEffect {
  0% {
    transform: scale(0);
    opacity: 1;
    border-width: 2px;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
    border-width: 0;
  }
}

@keyframes particleEffect {
  0% {
    transform: translate(-50%, -50%) rotate(var(--angle)) translateY(0) scale(0);
    opacity: 1;
    box-shadow: 0 0 0 rgba(95, 135, 255, 0.8);
  }
  50% {
    opacity: 1;
    box-shadow: 0 0 20px rgba(95, 135, 255, 0.8);
  }
  100% {
    transform: translate(-50%, -50%) rotate(var(--angle)) translateY(-60px) scale(1);
    opacity: 0;
    box-shadow: 0 0 0 rgba(95, 135, 255, 0);
  }
}

@keyframes energyEffect {
  0% {
    transform: scale(0);
    opacity: 0.8;
  }
  70% {
    transform: scale(1.2);
    opacity: 0.3;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
  }
}
</style>