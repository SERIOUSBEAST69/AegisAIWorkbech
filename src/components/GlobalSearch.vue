<template>
  <div class="global-search-container">
    <button class="search-trigger" @click="openSearch">
      <Search />
      <span>搜索 (Ctrl+K)</span>
    </button>

    <Teleport to="body">
      <button
        v-if="isOpen"
        type="button"
        class="search-backdrop"
        tabindex="0"
        aria-hidden="false"
        aria-label="Close search"
        @click="closeSearch"
      >
        <span class="search-backdrop-fog" aria-hidden="true"></span>
        <span class="search-backdrop-grid" aria-hidden="true"></span>
      </button>

      <Transition name="search-sidebar">
        <aside v-if="isOpen" class="search-sidebar" aria-hidden="false">
          <div class="search-noise"></div>
          <div class="search-grid"></div>
          <div class="search-scroll">
            <div class="search-inner">
              <div class="search-input-wrapper">
                <Search class="search-icon" :size="12" />
                <input
                  ref="searchInputRef"
                  v-model="searchQuery"
                  type="text"
                  class="search-input"
                  placeholder="搜索功能、页面、用户..."
                  @input="handleSearch"
                  @keydown="handleKeydown"
                />
                <button class="search-close" @click="closeSearch">
                  <Close :size="12" />
                </button>
              </div>

              <div class="search-tabs">
                <button
                  v-for="tab in tabs"
                  :key="tab.key"
                  class="search-tab"
                  :class="{ active: activeTab === tab.key }"
                  @click="activeTab = tab.key"
                >
                  {{ tab.label }}
                  <span v-if="getTabCount(tab.key) > 0" class="tab-count">{{ getTabCount(tab.key) }}</span>
                </button>
              </div>

              <div class="search-results">
                <div v-if="loading" class="search-loading">
                  <Loading :size="18" />
                  <span>搜索中...</span>
                </div>

                <div v-else-if="filteredResults.length === 0 && searchQuery" class="search-empty">
                  <DocumentDelete :size="18" />
                  <span>未找到相关结果</span>
                </div>

                <div v-else-if="filteredResults.length === 0 && !searchQuery" class="search-recent">
                  <div class="recent-header">
                    <strong>最近搜索</strong>
                    <button class="clear-recent" @click="clearRecent">清除</button>
                  </div>
                  <div class="recent-list">
                    <button
                      v-for="item in recentSearches"
                      :key="item"
                      class="recent-item"
                      @click="selectRecent(item)"
                    >
                      <Clock :size="14" />
                      <span>{{ item }}</span>
                    </button>
                  </div>
                </div>

                <div v-else class="results-list">
                  <button
                    v-for="result in filteredResults"
                    :key="result.id"
                    class="result-item"
                    :class="{ active: selectedIndex === result.id }"
                    @click="selectResult(result)"
                    @mouseenter="selectedIndex = result.id"
                  >
                    <component :is="result.icon" class="result-icon" :size="12" />
                    <div class="result-content">
                      <strong>{{ result.title }}</strong>
                      <p>{{ result.description }}</p>
                    </div>
                    <span class="result-type">{{ result.type }}</span>
                  </button>
                </div>
              </div>

              <div class="search-footer">
                <div class="search-tips">
                  <span class="tip-item">
                    <kbd>↑</kbd>
                    <kbd>↓</kbd>
                    导航
                  </span>
                  <span class="tip-item">
                    <kbd>Enter</kbd>
                    选择
                  </span>
                  <span class="tip-item">
                    <kbd>Esc</kbd>
                    关闭
                  </span>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import {
  Search,
  Close,
  Document,
  Folder,
  User,
  Setting,
  Clock,
  Loading,
  DocumentDelete,
  DataLine,
  Monitor,
  Warning,
  Key,
  ChatDotRound,
  Operation,
  Files,
  View,
  Histogram,
  Aim,
  ChatLineRound,
  DocumentChecked,
  DocumentCopy,
  Crop,
  UserFilled,
  Lock,
  Tools
} from '@element-plus/icons-vue';

const router = useRouter();

const isOpen = ref(false);
const searchQuery = ref('');
const searchInputRef = ref(null);
const activeTab = ref('all');
const selectedIndex = ref(null);
const loading = ref(false);
const recentSearches = ref([]);

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pages', label: '页面' },
  { key: 'functions', label: '功能' },
  { key: 'users', label: '用户' }
];

const mockResults = [
  { id: 1, type: '页面', title: '数据资产概览', description: '查看组织数据资产分布情况', icon: DataLine, path: '/data-asset', category: 'pages' },
  { id: 2, type: '页面', title: '威胁监测中心', description: '实时监测和阻拦安全威胁', icon: Monitor, path: '/threat-monitor', category: 'pages' },
  { id: 3, type: '功能', title: '脱敏策略校验', description: '测试和验证数据脱敏效果', icon: View, path: '/desense-preview', category: 'functions' },
  { id: 4, type: '功能', title: '影子AI发现', description: '发现组织内未经批准的AI工具', icon: Aim, path: '/shadow-ai', category: 'functions' },
  { id: 5, type: '页面', title: '审计日志', description: '查看所有操作的审计记录', icon: Histogram, path: '/audit-log', category: 'pages' },
  { id: 6, type: '页面', title: '审批流处理', description: '管理和处理各类审批请求', icon: DocumentChecked, path: '/approval-manage', category: 'pages' },
  { id: 7, type: '功能', title: 'AI服务风险评级', description: '评估AI服务的安全风险等级', icon: Warning, path: '/ai/risk-rating', category: 'functions' },
  { id: 8, type: '功能', title: '员工AI行为监控', description: '监控和分析员工使用AI的行为', icon: ChatLineRound, path: '/ai/anomaly', category: 'functions' },
  { id: 9, type: '页面', title: '用户管理', description: '管理组织用户账号', icon: UserFilled, path: '/user-manage', category: 'pages' },
  { id: 10, type: '页面', title: '角色管理', description: '配置用户角色和权限边界', icon: Lock, path: '/role-manage', category: 'pages' },
  { id: 11, type: '功能', title: '敏感暴露扫描', description: '扫描发现敏感数据暴露风险', icon: Warning, path: '/sensitive-scan', category: 'functions' },
  { id: 12, type: '页面', title: '系统设置', description: '配置系统参数和选项', icon: Tools, path: '/settings', category: 'pages' }
];

const filteredResults = computed(() => {
  if (!searchQuery.value.trim()) return [];
  
  const query = searchQuery.value.toLowerCase();
  let results = mockResults.filter(item => 
    item.title.toLowerCase().includes(query) ||
    item.description.toLowerCase().includes(query)
  );
  
  if (activeTab.value !== 'all') {
    results = results.filter(item => item.category === activeTab.value);
  }
  
  return results;
});

function getTabCount(tabKey) {
  if (tabKey === 'all') return mockResults.length;
  return mockResults.filter(item => item.category === tabKey).length;
}

function openSearch() {
  isOpen.value = true;
  nextTick(() => {
    if (searchInputRef.value) {
      searchInputRef.value.focus();
    }
  });
}

function closeSearch() {
  isOpen.value = false;
  searchQuery.value = '';
  activeTab.value = 'all';
  selectedIndex.value = null;
}

function handleSearch() {
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
    if (filteredResults.value.length > 0) {
      selectedIndex.value = filteredResults.value[0].id;
    }
  }, 200);
}

function handleKeydown(e) {
  if (!filteredResults.value.length) return;
  
  const currentIndex = filteredResults.value.findIndex(item => item.id === selectedIndex.value);
  
  if (e.key === 'ArrowDown') {
    e.preventDefault();
    const nextIndex = (currentIndex + 1) % filteredResults.value.length;
    selectedIndex.value = filteredResults.value[nextIndex].id;
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    const prevIndex = (currentIndex - 1 + filteredResults.value.length) % filteredResults.value.length;
    selectedIndex.value = filteredResults.value[prevIndex].id;
  } else if (e.key === 'Enter') {
    e.preventDefault();
    const selected = filteredResults.value.find(item => item.id === selectedIndex.value);
    if (selected) {
      selectResult(selected);
    }
  }
}

function selectResult(result) {
  if (recentSearches.value.indexOf(result.title) === -1) {
    recentSearches.value.unshift(result.title);
    if (recentSearches.value.length > 5) {
      recentSearches.value.pop();
    }
  }
  closeSearch();
  if (result.path) {
    router.push(result.path);
  }
}

function selectRecent(item) {
  searchQuery.value = item;
  handleSearch();
}

function clearRecent() {
  recentSearches.value = [];
}

function handleGlobalKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault();
    if (isOpen.value) {
      closeSearch();
    } else {
      openSearch();
    }
  }
  if (e.key === 'Escape' && isOpen.value) {
    closeSearch();
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleGlobalKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleGlobalKeydown);
});
</script>

<style scoped>
.global-search-container {
  position: relative;
  z-index: 60;
}

.search-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 12px;
}

.search-trigger svg {
  font-size: 14px;
}

.search-trigger:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(95, 135, 255, 0.3);
}

.search-backdrop {
  position: fixed;
  inset: 0;
  z-index: 58;
  border: none;
  padding: 0;
  background: transparent;
  opacity: 1;
  pointer-events: auto;
}

.search-backdrop-fog,
.search-backdrop-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.search-backdrop-fog {
  background:
    radial-gradient(circle at 18% 22%, rgba(126, 169, 255, 0.16), transparent 22%),
    radial-gradient(circle at 72% 46%, rgba(221, 235, 255, 0.08), transparent 26%),
    linear-gradient(90deg, rgba(2, 6, 12, 0.9) 0%, rgba(4, 9, 16, 0.8) 42%, rgba(4, 9, 16, 0.7) 100%);
  backdrop-filter: blur(18px) saturate(118%);
}

.search-backdrop-grid {
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.06) 1px, transparent 1px);
  background-size: 84px 84px;
  mask-image: linear-gradient(90deg, rgba(0,0,0,0.84), rgba(0,0,0,0.42));
}

.search-sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  right: 0;
  width: min(44vw, 520px);
  overflow: hidden;
  background: linear-gradient(180deg, rgba(7, 12, 22, 0.98), rgba(3, 6, 12, 0.98));
  box-shadow: -40px 0 120px rgba(0, 0, 0, 0.54);
  z-index: 62;
}

.search-noise,
.search-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.search-noise {
  inset: -10%;
  opacity: 0.08;
  mix-blend-mode: screen;
  background-image: radial-gradient(rgba(255,255,255,0.18) 0.8px, transparent 0.8px);
  background-size: 4px 4px;
}

.search-grid {
  opacity: 0.12;
  background-image:
    linear-gradient(rgba(255,255,255,0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.08) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,1), rgba(0,0,0,0.2));
}

.search-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
}

.search-inner {
  min-height: 100%;
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.search-input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(169, 196, 255, 0.18);
  border-radius: 16px;
  backdrop-filter: blur(12px);
}

.search-icon {
  color: rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}

.search-icon :deep(svg) {
  width: 8px;
  height: 8px;
  font-size: 8px;
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  letter-spacing: 0.02em;
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
  font-weight: 400;
}

.search-close {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.search-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.search-tabs {
  display: flex;
  gap: 8px;
  padding: 0;
  flex-wrap: wrap;
}

.search-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid rgba(169, 196, 255, 0.12);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.search-tab:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
  border-color: rgba(169, 196, 255, 0.24);
  transform: translateY(-1px);
}

.search-tab.active {
  background: linear-gradient(135deg, rgba(95, 135, 255, 0.28), rgba(27, 217, 180, 0.18));
  border-color: rgba(115, 188, 255, 0.4);
  color: #dcecff;
  box-shadow: 0 4px 12px rgba(95, 135, 255, 0.2);
}

.tab-count {
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.7);
}

.search-results {
  flex: 1;
  overflow-y: auto;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(169, 196, 255, 0.08);
  padding: 12px;
}

.search-loading,
.search-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 24px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  text-align: center;
}

.search-loading svg,
.search-empty svg {
  font-size: 18px;
  width: 18px;
  height: 18px;
  color: rgba(95, 135, 255, 0.4);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.6;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}

.search-recent {
  padding: 0;
}

.recent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 4px;
}

.recent-header strong {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
}

.clear-recent {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  font-size: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.2s ease;
  font-weight: 500;
}

.clear-recent:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  transform: translateY(-1px);
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(169, 196, 255, 0.08);
  border-radius: 12px;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  width: 100%;
  backdrop-filter: blur(8px);
}

.recent-item:hover {
  background: linear-gradient(135deg, rgba(95, 135, 255, 0.08), rgba(27, 217, 180, 0.04));
  border-color: rgba(169, 196, 255, 0.18);
  color: #fff;
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(95, 135, 255, 0.1);
}

.recent-item svg {
  font-size: 14px;
  width: 14px;
  height: 14px;
  color: rgba(95, 135, 255, 0.5);
  flex-shrink: 0;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(169, 196, 255, 0.08);
  border-radius: 14px;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  width: 100%;
  backdrop-filter: blur(8px);
  position: relative;
  overflow: hidden;
}

.result-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #5f87ff, #1bd9b4);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.result-item:hover,
.result-item.active {
  background: linear-gradient(135deg, rgba(95, 135, 255, 0.14), rgba(27, 217, 180, 0.06));
  border-color: rgba(115, 188, 255, 0.3);
  color: #fff;
  transform: translateX(4px);
  box-shadow: 0 6px 20px rgba(95, 135, 255, 0.15);
}

.result-item:hover::before,
.result-item.active::before {
  opacity: 1;
}

.result-icon {
  font-size: 12px;
  width: 12px;
  height: 12px;
  color: #5f87ff;
  flex-shrink: 0;
  margin-top: 2px;
  transition: transform 0.2s ease;
}

.result-item:hover .result-icon {
  transform: scale(1.1);
}

.result-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result-content strong {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}

.result-content p {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
  line-height: 1.4;
}

.result-type {
  padding: 6px 12px;
  background: linear-gradient(135deg, rgba(95, 135, 255, 0.18), rgba(27, 217, 180, 0.1));
  border-radius: 999px;
  border: 1px solid rgba(115, 188, 255, 0.2);
  font-size: 11px;
  font-weight: 700;
  color: #dcecff;
  white-space: nowrap;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 2px;
}

.search-footer {
  padding: 20px 0 0;
  margin-top: auto;
  border-top: 1px solid rgba(169, 196, 255, 0.12);
}

.search-tips {
  display: flex;
  gap: 18px;
  align-items: center;
  flex-wrap: wrap;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-weight: 500;
}

kbd {
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(169, 196, 255, 0.18);
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  color: rgba(255, 255, 255, 0.75);
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(4px);
}

.search-sidebar-enter-active,
.search-sidebar-leave-active {
  transition: transform 0.35s cubic-bezier(0.32, 0.72, 0, 1);
}

.search-sidebar-enter-from,
.search-sidebar-leave-to {
  transform: translateX(100%);
}
</style>
