<template>
  <div
    class="staggered-menu-wrapper"
    :data-open="open || undefined"
    :data-position="position"
    :data-lite="isLiteMode || undefined"
  >
    <button
      ref="toggleBtnRef"
      type="button"
      class="sm-toggle clickable"
      :aria-label="open ? 'Close navigation menu' : 'Open navigation menu'"
      :aria-expanded="open"
      aria-controls="staggered-menu-panel"
      @click="toggleMenu"
    >
      <span class="sm-toggle-text-wrap" aria-hidden="true">
        <span ref="textInnerRef" class="sm-toggle-text-inner">
          <span class="sm-toggle-line">Menu</span>
          <span class="sm-toggle-line">Close</span>
        </span>
      </span>
      <span ref="iconRef" class="sm-icon" aria-hidden="true">
        <span ref="plusHRef" class="sm-icon-line"></span>
        <span ref="plusVRef" class="sm-icon-line sm-icon-line-v"></span>
      </span>
    </button>

    <Teleport to="body">
      <button
        type="button"
        class="sm-backdrop"
        :class="{ active: open }"
        :tabindex="open ? 0 : -1"
        :aria-hidden="!open"
        aria-label="Close navigation menu"
        @click="closeMenu"
      >
        <span class="sm-backdrop-fog" aria-hidden="true"></span>
        <span class="sm-backdrop-grid" aria-hidden="true"></span>
        <span class="sm-backdrop-emblem" aria-hidden="true">
          <img src="../assets/logo.svg" alt="Aegis Logo Overlay" style="width: 100%; height: 100%; object-fit: contain; opacity: 0.8;" />
        </span>
      </button>

      <div ref="preLayersRef" class="sm-prelayers" aria-hidden="true">
        <div v-for="(color, index) in normalizedColors" :key="`${color}-${index}`" class="sm-prelayer" :style="{ background: color }"></div>
      </div>

      <aside id="staggered-menu-panel" ref="panelRef" class="staggered-menu-panel" :aria-hidden="!open" :inert="!open || undefined">
        <div class="sm-panel-noise"></div>
        <div class="sm-panel-grid"></div>
        <div class="sm-panel-scroll">
          <div class="sm-panel-inner">
            <section class="sm-panel-brief">
              <span class="sm-panel-kicker">{{ panelKicker }}</span>
              <strong>{{ panelTitle }}</strong>
              <p>{{ panelSubtitle }}</p>
              <div v-if="highlights.length" class="sm-brief-grid">
                <article v-for="item in highlights" :key="item.title" class="sm-brief-item">
                  <span>{{ item.metric }}</span>
                  <strong>{{ item.title }}</strong>
                </article>
              </div>
            </section>

            <ul class="sm-panel-list" :data-numbering="displayItemNumbering || undefined">
              <li v-for="(item, index) in items" :key="`${item.path || item.label}-${index}`" class="sm-panel-item-wrap" :class="{ 'sm-panel-item-wrap-has-children': Array.isArray(item.children) && item.children.length }">
                <button type="button" class="sm-panel-item clickable" :class="{ 'sm-panel-item-active': isItemActive(item) }" :style="getItemRhythm(index)" @click="handleItemClick(item)">
                  <span class="sm-panel-item-aura" aria-hidden="true"></span>
                  <span class="sm-panel-item-meta">{{ item.section }}</span>
                  <span class="sm-panel-item-label" :data-number="formatNumber(index + 1)">
                    {{ item.label }}
                    <svg class="sm-item-pattern" viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
                      <rect x="7" y="0" width="2" height="16" fill="currentColor" fill-opacity="0.3"></rect>
                      <rect x="0" y="7" width="16" height="2" fill="currentColor" fill-opacity="0.3"></rect>
                      <circle cx="8" cy="8" r="2" fill="currentColor"></circle>
                    </svg>
                  </span>
                  <span v-if="item.description" class="sm-panel-item-description">{{ item.description }}</span>
                  <span class="sm-panel-item-arrow" aria-hidden="true">North East</span>
                </button>
                <div v-if="Array.isArray(item.children) && item.children.length" class="sm-submenu">
                  <button
                    v-for="(child, childIdx) in item.children"
                    :key="`${child.path || child.label}-${childIdx}`"
                    type="button"
                    class="sm-submenu-item clickable"
                    @click.stop="handleChildItemClick(child)"
                  >
                    <span>{{ child.label }}</span>
                  </button>
                </div>
              </li>
            </ul>

            <div v-if="displaySocials && socialItems.length" class="sm-socials">
              <h3 class="sm-socials-title">Quick Access</h3>
              <div class="sm-socials-list">
                <button
                  v-for="(item, index) in socialItems"
                  :key="`${item.label}-${index}`"
                  type="button"
                  class="sm-socials-link clickable"
                  @click="handleUtilityClick(item)"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import gsap from 'gsap';

const route = useRoute();

const props = defineProps({
  position: { type: String, default: 'right' },
  colors: { type: Array, default: () => ['#0a1120', '#16305c', '#77a8ff'] },
  items: { type: Array, default: () => [] },
  socialItems: { type: Array, default: () => [] },
  highlights: { type: Array, default: () => [] },
  displaySocials: { type: Boolean, default: true },
  displayItemNumbering: { type: Boolean, default: true },
  panelKicker: { type: String, default: 'Navigation Layers' },
  panelTitle: { type: String, default: 'Aegis Navigation' },
  panelSubtitle: { type: String, default: 'Select the module you want to command.' },
  menuButtonColor: { type: String, default: '#f6fbff' },
  openMenuButtonColor: { type: String, default: '#0a1220' },
  changeMenuColorOnOpen: { type: Boolean, default: true },
  closeOnClickAway: { type: Boolean, default: true },
});

const emit = defineEmits(['select', 'utility-select', 'open', 'close']);

const open = ref(false);
const panelRef = ref(null);
const preLayersRef = ref(null);
const toggleBtnRef = ref(null);
const textInnerRef = ref(null);
const iconRef = ref(null);
const plusHRef = ref(null);
const plusVRef = ref(null);
const reduceMotionPreferred = ref(false);
let previousBodyOverflow = '';
let previousBodyPaddingRight = '';
let previousHtmlOverflow = '';

const isLiteMode = computed(() => {
  if (reduceMotionPreferred.value) {
    return true;
  }
  const itemCount = Array.isArray(props.items) ? props.items.length : 0;
  const lowSpecCpu = typeof navigator !== 'undefined' && Number(navigator.hardwareConcurrency || 0) > 0 && Number(navigator.hardwareConcurrency || 0) <= 4;
  return itemCount >= 12 || lowSpecCpu;
});

const normalizedColors = computed(() => {
  const palette = props.colors.length ? props.colors.slice(0, 4) : ['#0a1120', '#16305c', '#77a8ff'];
  return palette.length > 1 ? palette : [palette[0], '#18376f'];
});

function formatNumber(value) {
  return String(value).padStart(2, '0');
}

function isItemActive(item) {
  if (!item.path) return false;
  return route.path === item.path || route.path.startsWith(item.path + '/');
}

function getItemRhythm(index) {
  const item = props.items[index];
  const isImportant = item.label === '首页' || item.label.endsWith('管理');
  const isManagementEntry = String(item.path || '').includes('-manage') || item.label.endsWith('管理');
  const isActive = isItemActive(item);

  // 统一对齐，更符合逻辑审美的排版 (Unified alignment, logical aesthetics)
  const size = isImportant ? 'clamp(36px, 4vw, 50px)' : 'clamp(24px, 2.5vw, 32px)';
  const offset = '0px'; 
  const desc = '100%';
  const padTop = isImportant ? '24px' : '14px';
  const accent = isActive
    ? (isManagementEntry ? '#f6d472' : '#5f87ff')
    : (isImportant ? '#FFD700' : '#e0edff');

  return {
    '--sm-item-size': size,
    '--sm-item-offset': offset,
    '--sm-item-desc-width': desc,
    '--sm-item-pad-top': padTop,
    '--sm-item-accent': accent,
  };
}

function setClosedState() {
  const panel = panelRef.value;
  const prelayers = preLayersRef.value ? Array.from(preLayersRef.value.querySelectorAll('.sm-prelayer')) : [];
  const offscreen = props.position === 'left' ? -100 : 100;
  if (panel) {
    gsap.set(panel, { xPercent: offscreen });
  }
  if (prelayers.length) {
    gsap.set(prelayers, { xPercent: offscreen });
  }
  if (iconRef.value) {
    gsap.set(iconRef.value, { rotate: 0, transformOrigin: '50% 50%' });
  }
  if (textInnerRef.value) {
    gsap.set(textInnerRef.value, { yPercent: 0 });
  }
  if (toggleBtnRef.value) {
    gsap.set(toggleBtnRef.value, { color: props.menuButtonColor });
  }
}

function playOpen() {
  const panel = panelRef.value;
  const prelayers = preLayersRef.value ? Array.from(preLayersRef.value.querySelectorAll('.sm-prelayer')) : [];
  if (!panel) return;

  const labels = Array.from(panel.querySelectorAll('.sm-panel-item-label'));
  const metas = Array.from(panel.querySelectorAll('.sm-panel-item-meta, .sm-panel-item-description, .sm-panel-item-arrow'));
  const brief = panel.querySelector('.sm-panel-brief');
  const socialsTitle = panel.querySelector('.sm-socials-title');
  const socials = Array.from(panel.querySelectorAll('.sm-socials-link'));
  const offscreen = props.position === 'left' ? -100 : 100;

  if (isLiteMode.value) {
    gsap.set(panel, { xPercent: offscreen });
    gsap.set(prelayers, { xPercent: offscreen });
    if (iconRef.value) {
      gsap.to(iconRef.value, { rotate: 225, duration: 0.2, ease: 'power2.out' });
    }
    if (textInnerRef.value) {
      gsap.to(textInnerRef.value, { yPercent: -50, duration: 0.2, ease: 'power2.out' });
    }
    if (toggleBtnRef.value && props.changeMenuColorOnOpen) {
      gsap.to(toggleBtnRef.value, { color: props.openMenuButtonColor, duration: 0.16, ease: 'power1.out' });
    }
    gsap.to(prelayers, {
      xPercent: 0,
      duration: 0.2,
      ease: 'power2.out',
      stagger: 0.03,
      overwrite: 'auto',
    });
    gsap.to(panel, {
      xPercent: 0,
      duration: 0.24,
      ease: 'power2.out',
      overwrite: 'auto',
    });
    gsap.to([brief, socialsTitle, ...labels, ...metas, ...socials].filter(Boolean), {
      opacity: 1,
      y: 0,
      yPercent: 0,
      rotate: 0,
      duration: 0.18,
      ease: 'power1.out',
      overwrite: 'auto',
    });
    return;
  }

  gsap.set(panel, { xPercent: offscreen });
  gsap.set(prelayers, { xPercent: offscreen });
  gsap.set(labels, { yPercent: 120, rotate: 8, opacity: 0 });
  gsap.set(metas, { y: 18, opacity: 0 });
  if (brief) gsap.set(brief, { y: 24, opacity: 0 });
  if (socialsTitle) gsap.set(socialsTitle, { y: 12, opacity: 0 });
  if (socials.length) gsap.set(socials, { y: 14, opacity: 0 });

  const tl = gsap.timeline();
  prelayers.forEach((layer, index) => {
    tl.to(layer, { xPercent: 0, duration: 0.52, ease: 'power4.out' }, index * 0.07);
  });
  tl.to(panel, { xPercent: 0, duration: 0.66, ease: 'power4.out' }, prelayers.length * 0.07 + 0.08);
  if (brief) {
    tl.to(brief, { y: 0, opacity: 1, duration: 0.56, ease: 'power3.out' }, 0.22);
  }
  if (labels.length) {
    tl.to(labels, {
      yPercent: 0,
      rotate: 0,
      opacity: 1,
      duration: 0.92,
      ease: 'power4.out',
      stagger: 0.08,
    }, 0.3);
  }
  if (metas.length) {
    tl.to(metas, {
      y: 0,
      opacity: 1,
      duration: 0.5,
      ease: 'power2.out',
      stagger: 0.04,
    }, 0.42);
  }
  if (socialsTitle) {
    tl.to(socialsTitle, { y: 0, opacity: 1, duration: 0.42, ease: 'power2.out' }, 0.54);
  }
  if (socials.length) {
    tl.to(socials, {
      y: 0,
      opacity: 1,
      duration: 0.48,
      ease: 'power3.out',
      stagger: 0.06,
    }, 0.58);
  }

  if (iconRef.value) {
    gsap.to(iconRef.value, { rotate: 225, duration: 0.8, ease: 'power4.out' });
  }
  if (textInnerRef.value) {
    gsap.to(textInnerRef.value, { yPercent: -50, duration: 0.5, ease: 'power4.out' });
  }
  if (toggleBtnRef.value && props.changeMenuColorOnOpen) {
    gsap.to(toggleBtnRef.value, { color: props.openMenuButtonColor, duration: 0.3, ease: 'power2.out' });
  }
}

function playClose() {
  const panel = panelRef.value;
  const prelayers = preLayersRef.value ? Array.from(preLayersRef.value.querySelectorAll('.sm-prelayer')) : [];
  if (!panel) return;
  const offscreen = props.position === 'left' ? -100 : 100;
  gsap.to([panel, ...prelayers], {
    xPercent: offscreen,
    duration: isLiteMode.value ? 0.2 : 0.34,
    ease: 'power3.in',
    overwrite: 'auto',
  });
  if (iconRef.value) {
    gsap.to(iconRef.value, { rotate: 0, duration: isLiteMode.value ? 0.2 : 0.34, ease: 'power3.out' });
  }
  if (textInnerRef.value) {
    gsap.to(textInnerRef.value, { yPercent: 0, duration: isLiteMode.value ? 0.18 : 0.32, ease: 'power3.out' });
  }
  if (toggleBtnRef.value) {
    gsap.to(toggleBtnRef.value, { color: props.menuButtonColor, duration: isLiteMode.value ? 0.16 : 0.26, ease: 'power2.out' });
  }
}

function lockViewportScroll() {
  const scrollbarWidth = Math.max(window.innerWidth - document.documentElement.clientWidth, 0);
  previousBodyOverflow = document.body.style.overflow;
  previousBodyPaddingRight = document.body.style.paddingRight;
  previousHtmlOverflow = document.documentElement.style.overflow;
  document.documentElement.style.overflow = 'hidden';
  document.body.style.overflow = 'hidden';
  if (scrollbarWidth > 0) {
    document.body.style.paddingRight = `${scrollbarWidth}px`;
  }
}

function unlockViewportScroll() {
  document.documentElement.style.overflow = previousHtmlOverflow;
  document.body.style.overflow = previousBodyOverflow;
  document.body.style.paddingRight = previousBodyPaddingRight;
}

function toggleMenu() {
  open.value = !open.value;
}

function closeMenu() {
  open.value = false;
}

function handleItemClick(item) {
  if (Array.isArray(item?.children) && item.children.length) {
    return;
  }
  emit('select', item);
  closeMenu();
}

function handleChildItemClick(item) {
  emit('select', item);
  closeMenu();
}

function handleUtilityClick(item) {
  emit('utility-select', item);
  closeMenu();
}

function onDocumentPointer(event) {
  if (!props.closeOnClickAway || !open.value) return;
  const panel = panelRef.value;
  const toggle = toggleBtnRef.value;
  if (panel?.contains(event.target) || toggle?.contains(event.target)) {
    return;
  }
  closeMenu();
}

function onEscape(event) {
  if (event.key === 'Escape' && open.value) {
    closeMenu();
  }
}

watch(open, async value => {
  if (!value) {
    // Return focus to the toggle button BEFORE Vue applies aria-hidden/inert to
    // the DOM, so assistive technology never sees a focused element inside a
    // hidden ancestor. This prevents the "Blocked aria-hidden on an element
    // because its descendant retained focus" console error.
    const panel = panelRef.value;
    if (panel && panel.contains(document.activeElement)) {
      toggleBtnRef.value?.focus();
    }
  }
  await nextTick();
  if (value) {
    lockViewportScroll();
    emit('open');
    playOpen();
  } else {
    unlockViewportScroll();
    emit('close');
    playClose();
  }
});

onMounted(() => {
  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    reduceMotionPreferred.value = mediaQuery.matches;
    const listener = event => {
      reduceMotionPreferred.value = event.matches;
    };
    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', listener);
    } else if (typeof mediaQuery.addListener === 'function') {
      mediaQuery.addListener(listener);
    }
  }
  setClosedState();
  document.addEventListener('pointerdown', onDocumentPointer);
  document.addEventListener('keydown', onEscape);
});

onBeforeUnmount(() => {
  unlockViewportScroll();
  document.removeEventListener('pointerdown', onDocumentPointer);
  document.removeEventListener('keydown', onEscape);
});
</script>

<style scoped>
.staggered-menu-wrapper {
  position: relative;
  z-index: 60;
  --sm-panel-width: min(44vw, 620px);
  --sm-prelayer-width: min(40vw, 540px);
}

.sm-toggle {
  position: relative;
  z-index: 64;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-height: 46px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(169, 196, 255, 0.18);
  background: rgba(8, 14, 24, 0.72);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.28);
  color: #f6fbff;
  transition: background 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
}

.staggered-menu-wrapper[data-open] .sm-toggle {
  background: rgba(243, 248, 255, 0.92);
  border-color: rgba(255, 255, 255, 0.64);
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.34);
}

.sm-toggle-text-wrap {
  position: relative;
  height: 1em;
  overflow: hidden;
  line-height: 1;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.sm-toggle-text-inner {
  display: flex;
  flex-direction: column;
}

.sm-toggle-line {
  display: block;
  height: 1em;
  line-height: 1;
}

.sm-icon {
  position: relative;
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
}

.sm-icon-line {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 14px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
  transform: translate(-50%, -50%);
}

.sm-icon-line-v {
  transform: translate(-50%, -50%) rotate(90deg);
}

.sm-prelayers,
.staggered-menu-panel {
  position: fixed;
  top: 0;
  bottom: 0;
  right: 0;
}

[data-position='left'] .sm-prelayers,
[data-position='left'] .staggered-menu-panel {
  right: auto;
  left: 0;
}

.sm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 58;
  border: none;
  padding: 0;
  background: transparent;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.28s ease;
}

.sm-backdrop.active {
  opacity: 1;
  pointer-events: auto;
}

.sm-backdrop-fog,
.sm-backdrop-grid,
.sm-backdrop-emblem {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.sm-backdrop-fog {
  background:
    radial-gradient(circle at 18% 22%, rgba(126, 169, 255, 0.16), transparent 22%),
    radial-gradient(circle at 72% 46%, rgba(221, 235, 255, 0.08), transparent 26%),
    linear-gradient(90deg, rgba(2, 6, 12, 0.9) 0%, rgba(4, 9, 16, 0.8) 42%, rgba(4, 9, 16, 0.7) 100%);
  backdrop-filter: blur(18px) saturate(118%);
}

.sm-backdrop-grid {
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.06) 1px, transparent 1px);
  background-size: 84px 84px;
  mask-image: linear-gradient(90deg, rgba(0,0,0,0.84), rgba(0,0,0,0.42));
}

.sm-backdrop-emblem {
  inset: auto auto 9vh 6vw;
  width: 168px;
  height: 168px;
  opacity: 0.45; /* Slightly higher opacity so the user can see the new logo */
  filter: drop-shadow(0 20px 48px rgba(0, 0, 0, 0.4));
}

.sm-backdrop-emblem img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.sm-prelayers {
  width: var(--sm-prelayer-width);
  pointer-events: none;
  z-index: 61;
}

.sm-prelayer {
  position: absolute;
  inset: 0;
  box-shadow: inset 1px 0 0 rgba(255,255,255,0.06);
}

.sm-prelayer:nth-child(2) {
  transform: scaleX(0.97);
  transform-origin: right center;
}

.sm-prelayer:nth-child(3) {
  transform: scaleX(0.93);
  transform-origin: right center;
}

.staggered-menu-panel {
  width: var(--sm-panel-width);
  overflow: visible;
  background: linear-gradient(180deg, rgba(7, 12, 22, 0.98), rgba(3, 6, 12, 0.98));
  box-shadow: -40px 0 120px rgba(0, 0, 0, 0.54);
  z-index: 62;
}

.sm-panel-noise,
.sm-panel-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.sm-panel-noise {
  opacity: 0.08;
  background-image: radial-gradient(rgba(255,255,255,0.16) 0.8px, transparent 0.8px);
  background-size: 4px 4px;
  mix-blend-mode: screen;
}

.sm-panel-grid {
  opacity: 0.12;
  background-image: linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.06) 1px, transparent 1px);
  background-size: 84px 84px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,0.88), transparent 100%);
}

.sm-panel-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  contain: layout;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}

.sm-panel-scroll::-webkit-scrollbar {
  display: none;
}

.sm-panel-inner {
  position: relative;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 112px 34px 32px;
}

.sm-panel-brief {
  padding: 22px;
  border-radius: 28px;
  border: 1px solid rgba(169, 196, 255, 0.12);
  background: linear-gradient(145deg, rgba(87, 132, 255, 0.12), rgba(10, 19, 35, 0.8));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.06);
}

.sm-panel-kicker,
.sm-panel-item-meta,
.sm-socials-title,
.sm-brief-item span {
  color: #9abaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.sm-panel-brief strong {
  display: block;
  margin-top: 12px;
  color: #f6fbff;
  font-size: clamp(28px, 3.3vw, 40px);
  line-height: 0.98;
}

.sm-panel-brief p {
  margin: 12px 0 0;
  color: #99aac7;
  line-height: 1.8;
}

.sm-brief-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}

.sm-brief-item {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(169, 196, 255, 0.1);
  background: rgba(255,255,255,0.04);
}

.sm-brief-item strong {
  display: block;
  margin-top: 8px;
  color: #ecf4ff;
  font-size: 14px;
  line-height: 1.5;
}

.sm-panel-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sm-panel-item-wrap {
  overflow: hidden;
  position: relative;
}

.sm-panel-item-wrap-has-children {
  overflow: visible;
  z-index: 8;
}

.sm-panel-item-wrap-has-children:hover,
.sm-panel-item-wrap-has-children:focus-within {
  z-index: 120;
}

.sm-panel-item {
  position: relative;
  width: 100%;
  display: grid;
  gap: 6px;
  padding: var(--sm-item-pad-top, 16px) 22px 18px calc(20px + var(--sm-item-offset, 0px));
  border-radius: 28px;
  text-align: left;
  background: transparent;
  border: none;
  color: inherit;
  overflow: hidden;
  isolation: isolate;
  transition: transform 0.24s ease, opacity 0.24s ease, filter 0.24s ease, background 0.24s ease, box-shadow 0.24s ease;
  will-change: transform, opacity;
}

.sm-panel-item::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    radial-gradient(circle at 0 50%, rgba(119, 168, 255, 0.18), transparent 52%),
    linear-gradient(135deg, rgba(255,255,255,0.08), rgba(255,255,255,0.02));
  opacity: 0;
  transform: translateX(-20px) scale(0.98);
  transition: transform 0.34s ease, opacity 0.34s ease;
}

.sm-panel-item-aura {
  position: absolute;
  left: 0;
  top: 18px;
  bottom: 18px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--sm-item-accent, #9fc4ff), rgba(159, 196, 255, 0));
  box-shadow: 0 0 26px rgba(119, 168, 255, 0.22);
  opacity: 0.54;
  transform: scaleY(0.72);
  transform-origin: center top;
  transition: transform 0.34s ease, opacity 0.34s ease;
}

.sm-panel-item-label {
  position: relative;
  display: inline-block;
  padding-right: 78px;
  color: transparent;
  background: linear-gradient(135deg, #ffffff 0%, #edf4ff 40%, #aab8d0 100%);
  -webkit-background-clip: text;
  background-clip: text;
  font-size: var(--sm-item-size, clamp(42px, 5vw, 68px));
  font-weight: 900;
  line-height: 1.05;
  letter-spacing: -0.04em;
  text-wrap: balance;
  transition: transform 0.34s ease, filter 0.34s ease, text-shadow 0.34s ease;
}

.sm-item-pattern {
  display: inline-block;
  vertical-align: middle;
  margin-left: 12px;
  color: var(--sm-item-accent, #e0edff);
  opacity: 0.6;
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.sm-panel-list:hover .sm-panel-item:hover .sm-item-pattern {
  transform: rotate(90deg) scale(1.2);
  opacity: 1;
}

.sm-panel-item-label::after {
  content: attr(data-number);
  position: absolute;
  top: 0.14em;
  right: 0;
  color: rgba(255, 255, 255, 0.4);
  -webkit-text-fill-color: initial;
  background: none;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.02em;
  opacity: 0.8;
}

.sm-panel-item-description {
  color: #8ea2c1;
  line-height: 1.7;
  max-width: var(--sm-item-desc-width, 85%);
  transition: color 0.34s ease, transform 0.34s ease, opacity 0.34s ease;
}

.sm-panel-item-arrow {
  position: absolute;
  right: 22px;
  top: 24px;
  color: var(--sm-item-accent, #9fc4ff);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  opacity: 0.44;
  transform: translateX(-8px);
  transition: opacity 0.34s ease, transform 0.34s ease;
}

.sm-panel-list:hover .sm-panel-item {
  opacity: 0.4;
  filter: blur(1.2px) saturate(0.8);
  transform: scale(0.985);
}

.sm-panel-list:hover .sm-panel-item:hover {
  opacity: 1;
  filter: none;
  transform: translateX(12px) scale(1.005);
  background: rgba(255,255,255,0.03);
  box-shadow: inset 0 0 0 1px rgba(169, 196, 255, 0.12), 0 18px 36px rgba(2, 6, 12, 0.34);
}

.sm-panel-list:hover .sm-panel-item:hover::before {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.sm-panel-list:hover .sm-panel-item:hover .sm-panel-item-aura {
  opacity: 1;
  transform: scaleY(1);
}

.sm-panel-list:hover .sm-panel-item:hover .sm-panel-item-label {
  background: linear-gradient(135deg, #ffffff 0%, #b3dcff 40%, #7db3ff 100%);
  -webkit-background-clip: text;
  color: transparent;
  transform: translateX(10px);
  filter: drop-shadow(0 8px 16px rgba(103, 151, 255, 0.4));
}

.sm-panel-list:hover .sm-panel-item:hover .sm-panel-item-description,
.sm-panel-list:hover .sm-panel-item:hover .sm-panel-item-meta {
  color: #c7d8f4;
  transform: translateX(10px);
}

.sm-panel-list:hover .sm-panel-item:hover .sm-panel-item-arrow {
  opacity: 1;
  transform: translateX(0);
}

.sm-submenu {
  position: absolute;
  left: 0;
  top: calc(100% + 10px);
  transform: translateY(-6px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 312px;
  padding: 16px;
  border-radius: 14px;
  background: rgba(9, 18, 31, 0.98);
  border: 1px solid rgba(169, 196, 255, 0.24);
  box-shadow: 0 16px 32px rgba(2, 6, 12, 0.42);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.22s ease, transform 0.22s ease;
  z-index: 60;
}

.sm-panel-item-wrap:hover .sm-submenu,
.sm-panel-item-wrap:focus-within .sm-submenu {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.sm-submenu-item {
  border: 1px solid rgba(169, 196, 255, 0.18);
  background: rgba(255, 255, 255, 0.03);
  color: #d8e7ff;
  text-align: left;
  border-radius: 10px;
  padding: 13px 14px;
  font-size: 16px;
  line-height: 1.5;
  transition: background 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.sm-submenu-item:hover {
  background: rgba(95, 135, 255, 0.22);
  border-color: rgba(149, 186, 255, 0.42);
  transform: translateX(-2px);
}

/* Active menu item styles */
.sm-panel-item-active {
  background: rgba(95, 135, 255, 0.12);
  border-left: 3px solid #5f87ff;
  box-shadow: 0 12px 24px rgba(95, 135, 255, 0.18);
}

.sm-panel-item-active .sm-panel-item-label {
  background: linear-gradient(135deg, #ffffff 0%, #b3dcff 40%, #7db3ff 100%);
  -webkit-background-clip: text;
  color: transparent;
  filter: drop-shadow(0 8px 16px rgba(103, 151, 255, 0.4));
}

.sm-panel-item-active .sm-panel-item-description,
.sm-panel-item-active .sm-panel-item-meta {
  color: #c7d8f4;
}

.sm-panel-item-active .sm-panel-item-arrow {
  opacity: 1;
  transform: translateX(0);
  color: #5f87ff;
}

.sm-panel-list:hover .sm-panel-item-active {
  opacity: 1;
  filter: none;
  transform: translateX(12px) scale(1.005);
  background: rgba(95, 135, 255, 0.15);
  box-shadow: inset 0 0 0 1px rgba(169, 196, 255, 0.24), 0 18px 36px rgba(95, 135, 255, 0.25);
}

.staggered-menu-wrapper[data-lite='true'] .sm-backdrop-fog {
  backdrop-filter: blur(10px) saturate(110%);
}

.staggered-menu-wrapper[data-lite='true'] .sm-panel-item,
.staggered-menu-wrapper[data-lite='true'] .sm-panel-item::before,
.staggered-menu-wrapper[data-lite='true'] .sm-panel-item-aura,
.staggered-menu-wrapper[data-lite='true'] .sm-panel-item-label,
.staggered-menu-wrapper[data-lite='true'] .sm-panel-item-description,
.staggered-menu-wrapper[data-lite='true'] .sm-panel-item-arrow {
  transition-duration: 0.16s;
}

.staggered-menu-wrapper[data-lite='true'] .sm-panel-list:hover .sm-panel-item {
  opacity: 0.92;
  filter: none;
  transform: none;
}

.staggered-menu-wrapper[data-lite='true'] .sm-panel-list:hover .sm-panel-item:hover,
.staggered-menu-wrapper[data-lite='true'] .sm-panel-list:hover .sm-panel-item-active {
  transform: translateX(6px);
  box-shadow: inset 0 0 0 1px rgba(169, 196, 255, 0.16), 0 10px 20px rgba(2, 6, 12, 0.22);
}

.sm-panel-item:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 1px rgba(169, 196, 255, 0.18), 0 0 0 3px rgba(119, 168, 255, 0.16);
}

.sm-panel-item-wrap:nth-child(1) .sm-panel-item,
.sm-panel-item-wrap:nth-child(5) .sm-panel-item,
.sm-panel-item-wrap:nth-child(9) .sm-panel-item {
  min-height: 132px;
}

.sm-panel-item-wrap:nth-child(2n) .sm-panel-item {
  padding-right: 34px;
}

.sm-panel-item-wrap:nth-child(3n) .sm-panel-item {
  border-top: 1px solid rgba(169, 196, 255, 0.08);
}

.sm-socials {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 24px;
  border-top: 1px solid rgba(169, 196, 255, 0.1);
}

.sm-socials-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.sm-socials-link {
  min-height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid rgba(169, 196, 255, 0.14);
  background: rgba(255,255,255,0.04);
  color: #dbe7fb;
}

.sm-socials-link:hover {
  background: rgba(119, 168, 255, 0.14);
  color: #f6fbff;
}

@media (max-width: 1100px) {
  .staggered-menu-wrapper {
    --sm-panel-width: min(58vw, 620px);
    --sm-prelayer-width: min(52vw, 560px);
  }
}

@media (max-width: 768px) {
  .staggered-menu-wrapper {
    --sm-panel-width: 100vw;
    --sm-prelayer-width: 100vw;
  }

  .sm-backdrop {
    inset: 0;
  }

  .sm-backdrop-emblem {
    display: none;
  }

  .sm-panel-inner {
    padding: 96px 22px 24px;
  }

  .sm-panel-item-label {
    font-size: clamp(28px, 10vw, 44px);
  }

  .sm-panel-item-description {
    max-width: 100%;
  }

  .sm-brief-grid {
    grid-template-columns: 1fr;
  }
}
</style>