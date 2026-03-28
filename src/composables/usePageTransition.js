/**
 * usePageTransition.js
 *
 * 全局页面转场总线。
 *
 * 用法：
 *   const { navigateTo, onBeforeLeave, onAfterEnter } = usePageTransition();
 *
 * 转场流程：
 *   1. navigateTo(path) 被调用
 *   2. 触发 beforeLeave 回调（App.vue / 当前页面播放离场动画）
 *   3. 动画结束后 resolve → 执行 router.push(path)
 *   4. 新页面挂载后，App.vue 调用 triggerEnter() → 触发入场动画
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';

// 使用模块级单例，确保全应用共享同一状态
const isTransitioning = ref(false);
const direction = ref('forward'); // 'forward' | 'back'

/** 离场动画回调队列 */
const leaveCallbacks = new Set();
/** 入场动画回调队列（新页面挂载后调用） */
const enterCallbacks = new Set();

export function usePageTransition() {
  const router = useRouter();

  /**
   * 注册离场钩子（在当前页面的 onMounted/setup 中调用）
   * 回调需返回 Promise（动画完成时 resolve）
   */
  function onBeforeLeave(fn) {
    leaveCallbacks.add(fn);
    // 组件卸载时自动清除，防止内存泄漏
    // （调用方通过 onUnmounted 手动 offBeforeLeave，也可不做）
  }

  function offBeforeLeave(fn) {
    leaveCallbacks.delete(fn);
  }

  /**
   * 注册入场钩子（新页面挂载后被调用）
   * 与 onBeforeLeave 对称
   */
  function onAfterEnter(fn) {
    enterCallbacks.add(fn);
  }

  function offAfterEnter(fn) {
    enterCallbacks.delete(fn);
  }

  /**
   * 触发入场动画（由 App.vue 的 router-view onLoad 时机调用）
   */
  function triggerEnter() {
    enterCallbacks.forEach(fn => fn());
    enterCallbacks.clear();
  }

  /**
   * 主函数：带转场动画的路由跳转
   * @param {string} path 目标路由
   * @param {'forward'|'back'} dir 方向
   */
  async function navigateTo(path, dir = 'forward') {
    if (isTransitioning.value) return;
    if (path === router.currentRoute.value.path) return;

    isTransitioning.value = true;
    direction.value = dir;

    // 1. 等待所有离场动画完成
    try {
      await Promise.all([...leaveCallbacks].map(fn => fn()));
    } catch (_) {
      // 动画失败不阻断跳转
    }

    // 2. 跳转路由
    leaveCallbacks.clear();
    await router.push(path);

    isTransitioning.value = false;
  }

  return {
    isTransitioning,
    direction,
    onBeforeLeave,
    offBeforeLeave,
    onAfterEnter,
    offAfterEnter,
    triggerEnter,
    navigateTo,
  };
}
