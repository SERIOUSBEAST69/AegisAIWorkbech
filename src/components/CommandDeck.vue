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
import {
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
} from '@element-plus/icons-vue';

const router = useRouter();
const isOpen = ref(false);
const query = ref('');
const activeTab = ref('all');
const inputRef = ref(null);
const selectedIndex = ref(0);

const quickActions = [
  { id: 'risk', title: '风险编排', route: '/risk-event-manage', icon: Warning },
  { id: 'approval', title: '审批处理', route: '/approval-manage', icon: DocumentChecked },
  { id: 'threat', title: '威胁监测', route: '/threat-monitor', icon: Monitor },
  { id: 'audit', title: '审计日志', route: '/audit-log', icon: Histogram },
];

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'page', label: '页面' },
  { key: 'action', label: '动作' },
];

const items = [
  { id: 'p1', type: '页面', category: 'page', title: '数据资产概览', description: '查看资产敏感级别与分布', route: '/data-asset', icon: DataLine },
  { id: 'p2', type: '页面', category: 'page', title: '威胁监测中心', description: '实时监测并处置安全威胁', route: '/threat-monitor', icon: Monitor },
  { id: 'p3', type: '页面', category: 'page', title: '用户管理', description: '管理成员账号与状态', route: '/user-manage', icon: UserFilled },
  { id: 'p4', type: '页面', category: 'page', title: '角色管理', description: '配置角色和权限边界', route: '/role-manage', icon: Lock },
  { id: 'p5', type: '页面', category: 'page', title: '系统设置', description: '更新平台参数与策略', route: '/settings', icon: Tools },
  { id: 'a1', type: '动作', category: 'action', title: '启动影子AI排查', description: '快速进入影子AI治理页面', route: '/shadow-ai', icon: Aim },
  { id: 'a2', type: '动作', category: 'action', title: '执行脱敏预览', description: '验证当前脱敏规则命中', route: '/desense-preview', icon: View },
  { id: 'a3', type: '动作', category: 'action', title: '进入风险编排', description: '优先处理高风险告警事件', route: '/risk-event-manage', icon: Warning },
  { id: 'a4', type: '动作', category: 'action', title: '打开运营指挥台', description: '查看全链路治理操作入口', route: '/operations-command', icon: Operation },
];

const normalizedQuery = computed(() => query.value.trim().toLowerCase());

const filteredItems = computed(() => {
  let candidates = items;
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
  if (tab === 'all') return items.length;
  return items.filter(item => item.category === tab).length;
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
  router.push(item.route);
}

function runAction(action) {
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

onMounted(() => window.addEventListener('keydown', onGlobalKeydown));
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
  overflow: auto;
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
