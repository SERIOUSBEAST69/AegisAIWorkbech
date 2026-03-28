<template>
  <!--
    NavLink.vue — 带转场拦截的路由跳转组件
    用法：
      <NavLink to="/data-asset">数据资产</NavLink>
      <NavLink to="/data-asset" dir="back">返回</NavLink>

    自动处理：
      · active class（与 router-link-active 保持一致）
      · 点击时先播放离场动画，再跳转
      · 禁止在转场过程中重复点击
  -->
  <a
    :href="to"
    :class="['nav-link', { 'router-link-active': isActive, 'router-link-exact-active': isExact }]"
    @click.prevent="handleClick"
  >
    <slot />
  </a>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { usePageTransition } from '../composables/usePageTransition';

const props = defineProps({
  /** 目标路由路径 */
  to: { type: String, required: true },
  /** 转场方向 */
  dir: { type: String, default: 'forward' },
});

const route = useRoute();
const { navigateTo, isTransitioning } = usePageTransition();

const isActive = computed(() =>
  route.path === props.to || route.path.startsWith(props.to + '/')
);
const isExact  = computed(() => route.path === props.to);

function handleClick() {
  if (isTransitioning.value) return;
  navigateTo(props.to, props.dir);
}
</script>

<style scoped>
.nav-link {
  /* 继承父元素样式，不强加额外样式 */
  display: contents;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
}
</style>
