<template>
  <div class="prism-host-wrap">
    <div ref="mountEl" class="prism-react-host"></div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { createRoot } from 'react-dom/client';
import React from 'react';

import Prism from './react/Prism';

const mountEl = ref(null);
let root = null;

onMounted(() => {
  if (!mountEl.value) return;
  root = createRoot(mountEl.value);
  root.render(
    React.createElement(Prism, {
      animationType: 'rotate',
      timeScale: 0.5,
      height: 3.5,
      baseWidth: 5.5,
      scale: 3.6,
      hueShift: 0,
      colorFrequency: 1,
      noise: 0,
      glow: 1,
    }),
  );
});

onBeforeUnmount(() => {
  if (root) {
    root.unmount();
    root = null;
  }
});
</script>

<style scoped>
.prism-host-wrap {
  width: 100%;
  height: 100%;
  position: relative;
}

.prism-react-host {
  width: 100%;
  height: 100%;
  position: relative;
}
</style>