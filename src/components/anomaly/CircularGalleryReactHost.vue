<template>
  <div ref="mountEl" class="circular-gallery-react-host"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { createRoot } from 'react-dom/client';
import React from 'react';

import CircularGallery from './react/CircularGallery';

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  activeKey: {
    type: String,
    default: '',
  },
  showTitles: {
    type: Boolean,
    default: false,
  },
});

const mountEl = ref(null);
let root = null;
let renderRafId = 0;

const renderReact = () => {
  if (!root) return;
  root.render(
    React.createElement(CircularGallery, {
      items: props.items,
      activeKey: props.activeKey,
      bend: 1.34,
      textColor: '#e6f4ff',
      borderRadius: 0.06,
      font: '700 32px Segoe UI',
      scrollSpeed: 1.25,
      scrollEase: 0.035,
      showTitles: props.showTitles,
    }),
  );
};

const scheduleRender = () => {
  if (!root) return;
  if (renderRafId) return;
  renderRafId = window.requestAnimationFrame(() => {
    renderRafId = 0;
    renderReact();
  });
};

onMounted(() => {
  if (!mountEl.value) return;
  root = createRoot(mountEl.value);
  renderReact();
});

watch(
  () => [props.items, props.activeKey, props.showTitles],
  () => {
    scheduleRender();
  },
);

onBeforeUnmount(() => {
  if (renderRafId) {
    window.cancelAnimationFrame(renderRafId);
    renderRafId = 0;
  }
  if (root) {
    root.unmount();
    root = null;
  }
});
</script>

<style scoped>
.circular-gallery-react-host {
  width: 100%;
  height: 100%;
}
</style>
