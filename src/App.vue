<template>
  <!-- 全局自定义光标（始终存在，登录页与主应用均生效） -->
  <CustomCursor />
  <!-- 鼠标点击动画 -->
  <ClickEffect />

  <!-- App Static Background (Liquid Chrome) -->
  <LiquidChrome 
    v-if="!isLogin && userStore.initialized"
    :baseColor="[0.01, 0.02, 0.04]" 
    :interactive="false"
    :staticTimeOffset="850.0"
  />

  <div
    ref="shellEl"
    class="app-shell"
    v-if="!isLogin && userStore.initialized"
  >
    <header class="app-header card-glass">
      <div class="brand" @click="go('/')">
        <div class="logo">
          <span class="brand-mark" aria-hidden="true">
            <img src="./assets/logo.svg" alt="Aegis Logo" class="brand-mark-svg" style="width: 100%; height: 100%; object-fit: contain;" />
          </span>
          <span ref="brandTitleEl" class="logo-text">Aegis Workbench</span>
        </div>
        <span class="subtitle">守护数据与隐私合规平台</span>
      </div>
      <div class="header-actions">
        <StaggeredMenu
          position="right"
          :items="staggeredMenuItems"
          :social-items="staggeredQuickLinks"
          :highlights="personaHighlights"
          panel-kicker="Navigation Layers"
          :panel-title="menuPanelTitle"
          :panel-subtitle="menuPanelSubtitle"
          :colors="menuLayerColors"
          :display-socials="true"
          :display-item-numbering="true"
          menu-button-color="#f6fbff"
          open-menu-button-color="#0b1220"
          :change-menu-color-on-open="true"
          @select="handleMenuSelect"
          @utility-select="handleUtilitySelect"
        />
        <CommandDeck />
        <el-dropdown trigger="click" @command="handleDropdown">
          <div class="user-info">
            <el-avatar :size="32" :src="userAvatar || undefined" :icon="UserFilled" />
            <div class="user-copy">
              <span class="user-name">{{ userDisplayName }}</span>
              <span class="user-meta">{{ userIdentityLine }}</span>
            </div>
            <span class="user-role-chip">{{ userRoleName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item command="settings">系统设置</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <div class="layout">
      <main class="app-main">
        <router-view v-slot="{ Component, route }">
          <div class="route-stage">
            <transition :name="transitionName" @enter="onPageEnter">
              <component :is="Component" :key="route.fullPath" class="route-layer" />
            </transition>
          </div>
        </router-view>
      </main>
    </div>
  </div>
  <div v-if="showWorkbenchIntro" ref="introOverlayEl" class="workbench-intro">
    <div class="workbench-intro-grid"></div>
    <div class="workbench-intro-noise"></div>
    <div ref="introGlowEl" class="workbench-intro-glow"></div>
    <div class="workbench-intro-copy">
      <div ref="introKickerEl" class="workbench-intro-kicker">{{ personaExperience.kicker }}</div>
      <h1 ref="introTitleEl" class="workbench-intro-title workbench-title-core">Aegis Workbench</h1>
      <p ref="introSubtitleEl" class="workbench-intro-subtitle">{{ personaExperience.introSubtitle }}</p>
    </div>
  </div>
  <div v-else-if="!isLogin && !userStore.initialized" class="app-boot">
    <div class="boot-panel card-glass">
      <div class="boot-title">Aegis Workbench</div>
      <div class="boot-subtitle">正在恢复工作台上下文...</div>
    </div>
  </div>
  <router-view v-else-if="isLogin" />
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import gsap from 'gsap';
import { useUserStore } from './store/user';
import CustomCursor from './components/CustomCursor.vue';
import ClickEffect from './components/ClickEffect.vue';
import CommandDeck from './components/CommandDeck.vue';
import StaggeredMenu from './components/StaggeredMenu.vue';
import LiquidChrome from './components/LiquidChrome.vue';
import { usePageTransition } from './composables/usePageTransition';
import { getPersonaExperience, getVisibleMenuSections } from './utils/persona';
import {
  UserFilled,
  ArrowDown,
} from '@element-plus/icons-vue';

const route  = useRoute();
const router = useRouter();
const userStore = useUserStore();
const shellEl = ref(null);
const brandTitleEl = ref(null);
const introOverlayEl = ref(null);
const introGlowEl = ref(null);
const introKickerEl = ref(null);
const introTitleEl = ref(null);
const introSubtitleEl = ref(null);
const introConsumed = ref(false);
const showWorkbenchIntro = ref(false);
const isLogin  = computed(() => route.path === '/login');
const userDisplayName = computed(() => userStore.displayName);
const userAvatar = computed(() => userStore.avatar);
const userRoleName = computed(() => userStore.roleName);
const userIdentityLine = computed(() => userStore.identityLine);
const personaExperience = computed(() => getPersonaExperience(userStore.userInfo));
const visibleMenuSections = computed(() => getVisibleMenuSections(userStore.userInfo));
const personaHighlights = computed(() => personaExperience.value.benefits.slice(0, 2));
const menuLayerColors = ['#08101b', '#12315f', '#274f97', '#88bfff'];
const menuDescriptions = {
  '/': '总控视窗',
  '/operations-command': '治理关键动作',
  '/data-asset': '资产与敏感治理一体化',
  '/sensitive-data-governance': '已并入数据资产与敏感治理',
  '/shadow-ai': '影子AI发现与风险评级',
  '/threat-monitor': 'AI数据防泄漏实时威胁告警',
  '/ai/risk-rating': '已并入影子AI发现与风险评级',
  '/ai/anomaly': '员工AI行为监控',
  '/audit-center': '审计日志与报告中枢',
  '/approval-center': '待办审批与我发起',
  '/risk-event-manage': '合规风险记录',
  '/subject-request': '履约请求处理',
  '/policy-manage': '治理规则配置',
  '/user-manage': '组织与账号',
  '/role-manage': '角色能力边界',
  '/permission-manage': '细粒度访问',
};

const staggeredMenuItems = computed(() => (
  visibleMenuSections.value.flatMap(section => {
    if (section.key === 'system') {
      const children = section.items.map(item => ({
        ...item,
        section: section.title,
        description: menuDescriptions[item.path] || `进入 ${item.label}`,
      }));
      return [{
        path: '/user-manage',
        label: '系统管理',
        section: section.title,
        description: '统一维护用户、角色、权限与策略配置',
        children,
      }];
    }
    return section.items.map(item => ({
      ...item,
      section: section.title,
      description: menuDescriptions[item.path] || `进入 ${item.label}`,
    }));
  })
));
const staggeredQuickLinks = computed(() => ([
  { label: '个人资料', command: 'profile' },
  { label: '系统设置', command: 'settings' },
  { label: '退出登录', command: 'logout' },
]));
const menuPanelTitle = computed(() => `${personaExperience.value.label} 导航剧场`);
const menuPanelSubtitle = computed(() => personaExperience.value.signature);
const go = (path) => router.push(path);
const isElectronClient = typeof window !== 'undefined' && !!window.aegisClient;

localStorage.removeItem('theme');
document.documentElement.classList.remove('light-mode');
document.body.classList.remove('light-mode');
document.documentElement.classList.add('dark');

const logout = async () => {
  await userStore.logout();
  await syncElectronAuthState();
  router.push('/login');
};

async function syncElectronAuthState() {
  if (!isElectronClient || !window.aegisClient?.setAuthState) {
    return;
  }
  try {
    await window.aegisClient.setAuthState({
      authenticated: Boolean(userStore.token && userStore.userInfo),
      user: userStore.userInfo || null,
    });
  } catch {
    // Ignore bridge failures to keep web flow unaffected.
  }
}

function handleMenuSelect(item) {
  if (item?.path) {
    router.push(item.path);
  }
}

function handleUtilitySelect(item) {
  if (!item?.command) {
    return;
  }
  handleDropdown(item.command);
}

// ── 页面转场集成 ──────────────────────────────
const { direction, triggerEnter } = usePageTransition();
const transitionName = computed(() =>
  direction.value === 'back' ? 'page-slide-right' : 'page-slide-left'
);
function onPageEnter() {
  triggerEnter();
}

function getTitleGhost() {
  return document.body.querySelector('[data-transition-title="login"]');
}

function removeTitleGhost() {
  const ghost = getTitleGhost();
  if (ghost) {
    ghost.remove();
  }
}

function removeTransitionArtifacts() {
  removeTitleGhost();
}

function getIntroTitleTargetEl() {
  return shellEl.value?.querySelector('[data-workbench-title-anchor="home"]') || brandTitleEl.value;
}

function getElementFlight(sourceEl, targetEl) {
  if (!sourceEl || !targetEl) {
    return null;
  }
  const sourceRect = sourceEl.getBoundingClientRect();
  const targetRect = targetEl.getBoundingClientRect();
  return {
    x: targetRect.left + targetRect.width / 2 - (sourceRect.left + sourceRect.width / 2),
    y: targetRect.top + targetRect.height / 2 - (sourceRect.top + sourceRect.height / 2),
    scale: targetRect.width / Math.max(sourceRect.width, 1),
  };
}

async function playWorkbenchIntro() {
  if (document.startViewTransition) {
    showWorkbenchIntro.value = false;
    removeTransitionArtifacts();
    return;
  }

  if (!introOverlayEl.value || !shellEl.value) {
    showWorkbenchIntro.value = false;
    removeTransitionArtifacts();
    return;
  }

  if (document.startViewTransition) {
    showWorkbenchIntro.value = false;
    return;
  }

  const titleGhost = getTitleGhost();
  const targetTitle = getIntroTitleTargetEl();

  gsap.killTweensOf([
    introOverlayEl.value,
    introGlowEl.value,
    introKickerEl.value,
    introTitleEl.value,
    introSubtitleEl.value,
    shellEl.value,
    titleGhost,
  ]);

  gsap.set(introOverlayEl.value, { opacity: 1 });
  gsap.set(shellEl.value, {
    opacity: 0.82,
  });
  gsap.set(introKickerEl.value, { opacity: 0, y: 14, letterSpacing: '0.24em' });
  gsap.set(introTitleEl.value, {
    opacity: titleGhost ? 0 : 0,
    y: '18vh',
    scale: 0.94,
    filter: 'blur(8px)',
    transformOrigin: '50% 50%',
  });
  gsap.set(introSubtitleEl.value, { opacity: titleGhost ? 0 : 0, y: 10, filter: 'blur(4px)' });
  gsap.set(introGlowEl.value, { opacity: 0, scale: 0.84, filter: 'blur(18px)' });
  if (titleGhost) {
    gsap.set(titleGhost, {
      opacity: 1,
      x: 0,
      y: 0,
      scale: 1,
      filter: 'blur(0px)',
      transformOrigin: '50% 50%',
    });
  }

  let titleFlight = null;

  gsap.timeline({
    onComplete: () => {
      showWorkbenchIntro.value = false;
      removeTransitionArtifacts();
    }
  })
    .to(shellEl.value, {
      opacity: 1,
      duration: 0.22,
      ease: 'power2.out'
    }, 0)
    .to(introGlowEl.value, { opacity: 0.34, scale: 1.04, filter: 'blur(24px)', duration: 0.24, ease: 'power2.out' }, 0)
    .add(() => {
      titleFlight = getElementFlight(titleGhost || introTitleEl.value, targetTitle);
    }, 0)
    .to(titleGhost || introTitleEl.value, {
      opacity: 1,
      x: () => titleFlight?.x ?? 0,
      y: () => titleFlight?.y ?? -window.innerHeight * 0.34,
      scale: () => titleFlight?.scale ?? 0.34,
      transformOrigin: '50% 50%',
      duration: 0.46,
      ease: 'expo.out'
    }, 0)
    .to(introGlowEl.value, {
      opacity: 0,
      scale: 1.1,
      filter: 'blur(28px)',
      duration: 0.22,
      ease: 'power2.out'
    }, 0.16)
    .to(introOverlayEl.value, { opacity: 0, duration: 0.2, ease: 'power2.out' }, 0.18);
}

watch(isLogin, value => {
  if (value) {
    introConsumed.value = false;
    showWorkbenchIntro.value = false;
    removeTransitionArtifacts();
  }
});

watch(
  () => [userStore.token, userStore.userInfo?.username, userStore.userInfo?.roleCode],
  () => {
    syncElectronAuthState();
  },
  { immediate: true }
);

watch(
  () => [isLogin.value, userStore.initialized, route.fullPath],
  async ([login, initialized]) => {
    if (login || !initialized || introConsumed.value) {
      return;
    }
    if (sessionStorage.getItem('aegis.transition.origin') !== 'login') {
      removeTransitionArtifacts();
      return;
    }
    introConsumed.value = true;
    if (document.startViewTransition) {
      showWorkbenchIntro.value = false;
      return;
    }
    showWorkbenchIntro.value = true;
    await nextTick();
    playWorkbenchIntro();
  }
);

const handleDropdown = (command) => {
  switch (command) {
    case 'profile':
      router.push('/profile');
      break;
    case 'settings':
      router.push('/settings');
      break;
    case 'logout':
      logout();
      break;
  }
};
</script>

<style scoped>
.app-shell { 
  min-height: 100vh; 
  padding: 24px; 
  box-sizing: border-box; 
  transition: all var(--transition-normal);
}

.app-boot {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.boot-panel {
  min-width: 320px;
  padding: 28px 32px;
  text-align: center;
}

.boot-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
}

.boot-subtitle {
  margin-top: 10px;
  color: var(--color-text-muted);
}

.app-header { 
  height: 72px; 
  display: flex; 
  align-items: center; 
  justify-content: space-between; 
  padding: 0 24px; 
  position: relative;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 20px;
  cursor: pointer;
  flex: 1;
  min-width: 0;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  position: relative;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: radial-gradient(circle at 30% 24%, rgba(146, 199, 255, 0.18), rgba(8, 14, 24, 0.95) 66%);
  border: 1px solid rgba(169, 196, 255, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 18px 40px rgba(0, 0, 0, 0.28);
  overflow: hidden;
}

.brand-mark::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 15px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0));
  pointer-events: none;
}

.brand-mark-svg {
  width: 30px;
  height: 30px;
  filter: drop-shadow(0 6px 14px rgba(47, 112, 255, 0.24));
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--color-text), var(--color-text-secondary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 400;
  margin-left: 20px;
  padding-left: 20px;
  border-left: 1px solid var(--color-border-light);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  flex: 0 0 auto;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: pointer;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--color-border-light);
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.04);
  border-color: var(--color-border);
  box-shadow: var(--shadow-sm);
}

.user-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.user-meta {
  font-size: 11px;
  letter-spacing: 0.03em;
  color: var(--color-text-muted);
}

.user-role-chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(115, 188, 255, 0.24);
  background: linear-gradient(135deg, rgba(34, 116, 255, 0.24), rgba(27, 217, 180, 0.1));
  color: #dcecff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.layout { 
  display: block;
  margin-top: 20px; 
  min-height: calc(100vh - 116px); 
  transition: all var(--transition-normal);
}

.app-main { 
  padding: 0;
  position: relative;
  overflow: hidden;
  isolation: isolate;
}

.app-main::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, var(--color-success), transparent);
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.2;
  z-index: 0;
}

.app-main > * { 
  background: transparent;
  position: relative;
  z-index: 1;
}

.route-stage {
  position: relative;
  min-height: calc(100vh - 116px);
  isolation: isolate;
}

.route-layer {
  width: 100%;
}

.workbench-intro {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 54%, rgba(52, 110, 255, 0.18), transparent 24%),
    linear-gradient(180deg, rgba(4, 8, 18, 0.64), rgba(5, 7, 13, 0.34));
  pointer-events: none;
}

.workbench-intro-grid,
.workbench-intro-noise,
.workbench-intro-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.workbench-intro-grid {
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.06) 1px, transparent 1px);
  background-size: 86px 86px;
  mask-image: radial-gradient(circle at center, rgba(0,0,0,1), transparent 82%);
}

.workbench-intro-noise {
  inset: -10%;
  opacity: 0.08;
  mix-blend-mode: screen;
  background-image: radial-gradient(rgba(255,255,255,0.18) 0.8px, transparent 0.8px);
  background-size: 4px 4px;
}

.workbench-intro-glow {
  inset: auto 20% 24% 20%;
  height: 24vh;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(49, 102, 255, 0), rgba(105, 205, 255, 0.9), rgba(49, 102, 255, 0));
}

.workbench-intro-copy {
  position: relative;
  z-index: 1;
  width: min(92vw, 1600px);
  text-align: center;
  will-change: transform, opacity;
}

.workbench-intro-kicker {
  color: #c7dcff;
  font-size: clamp(12px, 1.25vw, 18px);
  font-weight: 800;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.workbench-intro-title {
  margin: 26px 0 18px;
  font-size: clamp(78px, 13vw, 210px);
  color: #f8fbff;
  text-shadow: 0 0 32px rgba(112, 176, 255, 0.22), 0 18px 80px rgba(0, 0, 0, 0.72);
}

.workbench-intro-subtitle {
  margin: 0;
  color: #9db0c9;
  font-size: clamp(14px, 1.5vw, 24px);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .app-shell {
    padding: 16px;
  }
  
  .app-header {
    padding: 0 16px;
  }
  
  .subtitle {
    display: none;
  }
}

@media (max-width: 768px) {
  .workbench-intro-title {
    font-size: clamp(58px, 16vw, 108px);
  }
  
  .brand {
    gap: 12px;
  }

  .brand-mark {
    width: 40px;
    height: 40px;
    border-radius: 14px;
  }
  
  .logo-text {
    font-size: 16px;
  }
}
</style>
