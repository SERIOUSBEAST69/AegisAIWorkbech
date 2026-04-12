<template>
  <div class="command-deck">
    <button class="deck-trigger" type="button" @click="openPalette">
      <el-icon><Search /></el-icon>
      <span class="deck-label">搜索页面、功能、动作</span>
      <span class="deck-shortcut">Ctrl+K</span>
    </button>

    <div class="deck-actions" role="toolbar" aria-label="快捷操作">
      <button
        v-for="action in quickActions"
        :key="action.id"
        type="button"
        class="deck-action-btn"
        @click="runAction(action)"
      >
        <el-icon><component :is="action.icon" /></el-icon>
        <span>{{ action.title }}</span>
      </button>
    </div>

    <Teleport to="body">
      <button v-if="isOpen" class="deck-backdrop" type="button" @click="closePalette" />
      <Transition name="deck-fade">
        <aside v-if="isOpen" class="deck-palette" role="dialog" aria-modal="true">
          <div class="palette-head">
            <el-icon class="palette-search-icon"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="palette-input"
              type="text"
              placeholder="输入关键字快速跳转"
              @keydown="onKeydown"
            />
            <button type="button" class="palette-close" @click="closePalette">
              <el-icon><Close /></el-icon>
            </button>
          </div>

          <div class="palette-tabs">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              class="palette-tab"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
              <span>{{ tabCount(tab.key) }}</span>
            </button>
          </div>

          <div v-if="recentItems.length" class="palette-recent">
            <button
              v-for="item in recentItems"
              :key="`recent-${item.id}`"
              type="button"
              class="recent-chip"
              @click="execute(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </button>
          </div>

          <div class="palette-list">
            <button
              v-for="(item, index) in filteredItems"
              :key="item.id"
              type="button"
              class="palette-item"
              :class="{ active: selectedIndex === index }"
              @mouseenter="selectedIndex = index"
              @click="execute(item)"
            >
              <el-icon class="item-icon"><component :is="item.icon" /></el-icon>
              <div class="item-copy">
                <strong>{{ item.title }}</strong>
                <p>{{ item.description }}</p>
              </div>
              <span class="item-type">{{ item.type }}</span>
            </button>
            <div v-if="filteredItems.length === 0" class="palette-empty">暂无匹配项</div>
          </div>

          <div class="palette-footer">
            <span>Enter 执行</span>
            <span>↑/↓ 切换</span>
            <span>Esc 关闭</span>
          </div>
        </aside>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '../store/user';
import { getVisibleMenuSections } from '../utils/persona';
import {
  HomeFilled,
  Grid,
  Search,
  Close,
  Monitor,
  Warning,
  UserFilled,
  Lock,
  Tools,
  Histogram,
  Operation,
  DataLine,
  DocumentChecked,
  View,
  Aim,
  AlarmClock,
  Timer,
  Document,
  Finished,
  Key,
  Avatar,
} from '@element-plus/icons-vue';

const router = useRouter();
const userStore = useUserStore();
const isOpen = ref(false);
const query = ref('');
const activeTab = ref('all');
const inputRef = ref(null);
const selectedIndex = ref(0);
const recentRoutes = ref([]);

const RECENT_KEY = 'aegis.commandDeck.recent.v1';

const iconByName = {
  HomeFilled,
  Grid,
  DataAnalysis: DataLine,
  Lock,
  View,
  AlarmClock,
  Histogram,
  Timer,
  Document,
  Search,
  Finished,
  Warning,
  UserFilled,
  Avatar,
  Key,
};

const routeMeta = {
  '/': { description: '回到首页总控视图', icon: HomeFilled },
  '/operations-command': { description: '查看运营治理动作入口', icon: Operation },
  '/shadow-ai': { description: '快速进入影子AI治理页面', icon: Aim },
  '/threat-monitor': { description: '实时监测并处置AI攻击威胁', icon: Monitor },
  '/ai/anomaly': { description: '查看员工AI使用合规异常事件', icon: AlarmClock },
  '/audit-log': { description: '追踪关键审计证据链', icon: Histogram },
  '/audit-report': { description: '查看和导出审计报告', icon: Document },
  '/approval-center': { description: '处理共享与治理审批卡点', icon: DocumentChecked },
  '/risk-event-manage': { description: '优先处理高风险告警事件', icon: Warning },
  '/policy-manage': { description: '配置平台策略与门禁', icon: Document },
  '/user-manage': { description: '管理组织成员与账号', icon: UserFilled },
  '/role-manage': { description: '配置角色和权限边界', icon: Avatar },
  '/permission-manage': { description: '维护细粒度权限点', icon: Key },
  '/profile': { description: '查看个人资料', icon: UserFilled },
  '/settings': { description: '更新平台参数与策略', icon: Tools },
};

const actionTemplates = [
  { id: 'action-risk', title: '进入风险编排', route: '/risk-event-manage', icon: Warning, description: '优先处理高风险告警事件' },
  { id: 'action-approval', title: '处理审批积压', route: '/approval-center', icon: DocumentChecked, description: '进入审批中心待办列表' },
  { id: 'action-threat', title: '打开AI攻击防御', route: '/threat-monitor', icon: Monitor, description: '关注AI攻击实时变化' },
  { id: 'action-audit', title: '查看审计证据链', route: '/audit-log', icon: Histogram, description: '进入审计日志快速回放' },
  { id: 'action-shadow-ai', title: '启动影子AI排查', route: '/shadow-ai', icon: Aim, description: '定位未授权AI工具接入' },
];

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'page', label: '页面' },
  { key: 'action', label: '动作' },
];

const visibleSections = computed(() => getVisibleMenuSections(userStore.userInfo));

const pageItems = computed(() => (
  visibleSections.value.flatMap(section =>
    section.items.map(item => {
      const route = item.path;
      const meta = routeMeta[route] || {};
      return {
        id: `page-${route}`,
        type: '页面',
        category: 'page',
        title: item.label,
        description: meta.description || `进入${item.label}`,
        route,
        icon: meta.icon || iconByName[item.icon] || Search,
      };
    })
  )
));

const allowedRoutes = computed(() => new Set(pageItems.value.map(item => item.route)));

const actionItems = computed(() => (
  actionTemplates
    .filter(item => allowedRoutes.value.has(item.route))
    .map(item => ({
      id: item.id,
      type: '动作',
      category: 'action',
      title: item.title,
      description: item.description,
      route: item.route,
      icon: item.icon,
    }))
));

const items = computed(() => [...pageItems.value, ...actionItems.value]);

const quickActions = computed(() => {
  const pool = [...actionItems.value];
  pool.sort((left, right) => {
    const leftIndex = recentRoutes.value.indexOf(left.route);
    const rightIndex = recentRoutes.value.indexOf(right.route);
    const a = leftIndex === -1 ? Number.MAX_SAFE_INTEGER : leftIndex;
    const b = rightIndex === -1 ? Number.MAX_SAFE_INTEGER : rightIndex;
    return a - b;
  });
  return pool.slice(0, 4).map(item => ({
    id: item.id,
    title: item.title,
    route: item.route,
    icon: item.icon,
  }));
});

const recentItems = computed(() => (
  recentRoutes.value
    .map(route => items.value.find(item => item.route === route))
    .filter(Boolean)
    .slice(0, 5)
));

const normalizedQuery = computed(() => query.value.trim().toLowerCase());

const filteredItems = computed(() => {
  let candidates = items.value;
  if (activeTab.value !== 'all') {
    candidates = candidates.filter(item => item.category === activeTab.value);
  }
  if (!normalizedQuery.value) {
    return candidates;
  }
  return candidates.filter(item => {
    const text = `${item.title} ${item.description}`.toLowerCase();
    return text.includes(normalizedQuery.value);
  });
});

function tabCount(tab) {
  if (tab === 'all') return items.value.length;
  return items.value.filter(item => item.category === tab).length;
}

function loadRecentRoutes() {
  try {
    const raw = window.localStorage.getItem(RECENT_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    recentRoutes.value = Array.isArray(parsed) ? parsed.filter(item => typeof item === 'string') : [];
  } catch {
    recentRoutes.value = [];
  }
}

function persistRecentRoutes() {
  try {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(recentRoutes.value.slice(0, 8)));
  } catch {
    // Ignore storage failures in restricted environments.
  }
}

function recordRecentRoute(route) {
  if (!route) return;
  const next = [route, ...recentRoutes.value.filter(item => item !== route)].slice(0, 8);
  recentRoutes.value = next;
  persistRecentRoutes();
}

function openPalette() {
  isOpen.value = true;
  selectedIndex.value = 0;
  nextTick(() => inputRef.value?.focus());
}

function closePalette() {
  isOpen.value = false;
  query.value = '';
  activeTab.value = 'all';
  selectedIndex.value = 0;
}

function execute(item) {
  closePalette();
  recordRecentRoute(item.route);
  router.push(item.route);
}

function runAction(action) {
  recordRecentRoute(action.route);
  router.push(action.route);
}

function onKeydown(event) {
  if (event.key === 'Escape') {
    closePalette();
    return;
  }
  if (!filteredItems.value.length) return;
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    selectedIndex.value = (selectedIndex.value + 1) % filteredItems.value.length;
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault();
    selectedIndex.value = (selectedIndex.value - 1 + filteredItems.value.length) % filteredItems.value.length;
  }
  if (event.key === 'Enter') {
    event.preventDefault();
    const target = filteredItems.value[selectedIndex.value];
    if (target) execute(target);
  }
}

function onGlobalKeydown(event) {
  if (event.ctrlKey && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    if (isOpen.value) {
      closePalette();
    } else {
      openPalette();
    }
  }
  if (event.key === 'Escape' && isOpen.value) {
    closePalette();
  }
}

onMounted(() => {
  loadRecentRoutes();
  window.addEventListener('keydown', onGlobalKeydown);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown));
</script>

<style scoped>
.command-deck {
  display: flex;
  align-items: center;
  gap: 12px;
}

.deck-trigger {
  height: 38px;
  min-width: 280px;
  border-radius: 12px;
  border: 1px solid rgba(129, 153, 178, 0.34);
  background: linear-gradient(135deg, rgba(8, 15, 25, 0.92), rgba(11, 22, 36, 0.82));
  color: #e9f2ff;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  font-size: 13px;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: border-color 0.24s ease, transform 0.24s ease, box-shadow 0.24s ease;
}

.deck-trigger:hover {
  border-color: rgba(141, 186, 255, 0.68);
  transform: translateY(-1px);
  box-shadow: 0 12px 30px rgba(2, 9, 16, 0.42);
}

.deck-label {
  flex: 1;
  text-align: left;
  color: rgba(233, 242, 255, 0.88);
}

.deck-shortcut {
  border: 1px solid rgba(170, 193, 220, 0.35);
  border-radius: 6px;
  padding: 2px 6px;
  font-size: 11px;
  color: rgba(219, 232, 247, 0.9);
}

.deck-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.deck-action-btn {
  height: 38px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid rgba(125, 146, 173, 0.28);
  background: rgba(6, 14, 24, 0.8);
  color: rgba(229, 239, 255, 0.9);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.24s ease;
}

.deck-action-btn:hover {
  border-color: rgba(132, 188, 255, 0.65);
  background: rgba(10, 25, 40, 0.92);
}

.deck-backdrop {
  position: fixed;
  inset: 0;
  border: none;
  background: radial-gradient(circle at 20% 10%, rgba(43, 92, 150, 0.2), rgba(2, 5, 10, 0.88) 60%);
  backdrop-filter: blur(10px) saturate(120%);
  z-index: 75;
}

.deck-palette {
  position: fixed;
  top: 10vh;
  left: 50%;
  transform: translateX(-50%);
  width: min(840px, 92vw);
  background: linear-gradient(160deg, rgba(9, 17, 29, 0.98), rgba(5, 10, 18, 0.98));
  border: 1px solid rgba(141, 175, 210, 0.34);
  border-radius: 18px;
  box-shadow: 0 35px 80px rgba(0, 0, 0, 0.55);
  z-index: 80;
  overflow: hidden;
}

.palette-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border-bottom: 1px solid rgba(144, 167, 191, 0.2);
}

.palette-search-icon {
  color: rgba(177, 207, 238, 0.9);
}

.palette-input {
  flex: 1;
  background: transparent;
  border: none;
  color: #edf5ff;
  font-size: 15px;
  outline: none;
}

.palette-input::placeholder {
  color: rgba(177, 199, 223, 0.54);
}

.palette-close {
  height: 32px;
  width: 32px;
  border: 1px solid rgba(147, 174, 201, 0.26);
  border-radius: 10px;
  background: rgba(11, 23, 35, 0.75);
  color: #dceafe;
  cursor: pointer;
}

.palette-tabs {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
}

.palette-recent {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 0 16px 12px;
}

.recent-chip {
  border: 1px solid rgba(134, 165, 194, 0.28);
  background: rgba(10, 20, 33, 0.68);
  color: rgba(220, 236, 254, 0.9);
  border-radius: 999px;
  padding: 5px 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  cursor: pointer;
}

.recent-chip:hover {
  border-color: rgba(146, 198, 248, 0.62);
  background: rgba(14, 30, 49, 0.88);
}

.palette-tab {
  border: 1px solid rgba(139, 162, 187, 0.22);
  background: rgba(7, 15, 26, 0.75);
  color: rgba(213, 230, 248, 0.82);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.palette-tab span {
  font-size: 11px;
  opacity: 0.8;
}

.palette-tab.active {
  border-color: rgba(147, 201, 255, 0.72);
  background: rgba(17, 45, 72, 0.82);
  color: #f0f7ff;
}

.palette-list {
  max-height: 52vh;
  overflow: visible;
  padding: 0 10px 10px;
}

.palette-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 12px;
  background: rgba(9, 18, 30, 0.45);
  color: #e6f0ff;
  display: grid;
  grid-template-columns: 28px 1fr auto;
  gap: 12px;
  align-items: center;
  text-align: left;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
}

.palette-item:hover,
.palette-item.active {
  border-color: rgba(149, 193, 236, 0.55);
  background: rgba(14, 30, 49, 0.85);
}

.item-icon {
  color: rgba(175, 216, 255, 0.95);
}

.item-copy strong {
  font-size: 13px;
  font-weight: 700;
}

.item-copy p {
  margin: 4px 0 0;
  color: rgba(182, 206, 230, 0.78);
  font-size: 12px;
}

.item-type {
  border: 1px solid rgba(133, 160, 188, 0.34);
  border-radius: 8px;
  padding: 2px 8px;
  font-size: 11px;
  color: rgba(210, 226, 244, 0.88);
}

.palette-empty {
  text-align: center;
  padding: 36px 0;
  color: rgba(179, 199, 220, 0.72);
}

.palette-footer {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  font-size: 11px;
  color: rgba(175, 198, 222, 0.72);
  padding: 12px 16px 16px;
  border-top: 1px solid rgba(134, 156, 180, 0.16);
}

.deck-fade-enter-active,
.deck-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.deck-fade-enter-from,
.deck-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -10px);
}

@media (max-width: 1200px) {
  .deck-actions {
    display: none;
  }

  .deck-trigger {
    min-width: 220px;
  }
}

@media (max-width: 840px) {
  .command-deck {
    width: 100%;
  }

  .deck-trigger {
    width: 100%;
    min-width: 0;
  }
}
</style>
