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
      <div class="click-effect-core"></div>
      <div class="click-effect-ripple"></div>
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
    duration: 320
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
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%);
  overflow: hidden;
}

.click-effect-core {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(95, 135, 255, 0.65);
  animation: coreEffect 320ms ease-out forwards;
  z-index: 2;
  position: relative;
}

.click-effect-ripple {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 1px solid rgba(95, 135, 255, 0.35);
  animation: rippleEffect 320ms ease-out forwards;
  z-index: 1;
}

@keyframes coreEffect {
  0% {
    transform: scale(0.9);
    opacity: 1;
  }
  100% {
    transform: scale(1.2);
    opacity: 0;
  }
}

@keyframes rippleEffect {
  0% {
    transform: scale(0.7);
    opacity: 0.45;
  }
  100% {
    transform: scale(1.35);
    opacity: 0;
  }
}
</style>