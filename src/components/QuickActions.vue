<template>
  <div class="quick-actions-container">
    <div class="quick-actions-trigger" @click="toggleMenu">
      <Lightning />
      <span>快捷操作</span>
    </div>

    <Teleport to="body">
      <button
        v-if="isOpen"
        type="button"
        class="quick-actions-backdrop"
        tabindex="0"
        aria-hidden="false"
        aria-label="Close quick actions"
        @click="closeMenu"
      >
        <span class="quick-actions-backdrop-fog" aria-hidden="true"></span>
        <span class="quick-actions-backdrop-grid" aria-hidden="true"></span>
      </button>

      <Transition name="quick-actions">
        <aside v-if="isOpen" class="quick-actions-sidebar" aria-hidden="false">
          <div class="quick-actions-noise"></div>
          <div class="quick-actions-grid-overlay"></div>
          <div class="quick-actions-scroll">
            <div class="quick-actions-inner">
              <div class="quick-actions-header">
                <strong>快捷操作</strong>
                <button class="close-btn" @click="closeMenu">
                  <Close />
                </button>
              </div>

              <div class="quick-actions-grid">
                <button
                  v-for="action in quickActions"
                  :key="action.id"
                  class="quick-action-item"
                  :class="action.color"
                  @click="executeAction(action)"
                >
                  <component :is="action.icon" :size="20" />
                  <div class="action-info">
                    <strong>{{ action.title }}</strong>
                    <span>{{ action.description }}</span>
                  </div>
                  <span class="action-shortcut">{{ action.shortcut }}</span>
                </button>
              </div>

              <div class="quick-actions-footer">
                <div class="recent-actions">
                  <strong>最近操作</strong>
                  <div class="recent-list">
                    <button
                      v-for="action in recentActions"
                      :key="action.id"
                      class="recent-action-item"
                      @click="executeAction(action)"
                    >
                      <component :is="action.icon" :size="20" />
                      <span>{{ action.title }}</span>
                    </button>
                  </div>
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
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { Lightning, Close, Plus, Document, User, Setting, DataAnalysis, Monitor, Warning, Download, Upload } from '@element-plus/icons-vue';

const router = useRouter();
const isOpen = ref(false);
const recentActions = ref([]);

const quickActions = [
  {
    id: 1,
    title: '新建用户',
    description: '添加新的系统用户',
    icon: Plus,
    color: 'primary',
    shortcut: 'Ctrl+N',
    action: () => router.push('/user-manage')
  },
  {
    id: 2,
    title: '生成报告',
    description: '导出审计报告',
    icon: Document,
    color: 'success',
    shortcut: 'Ctrl+R',
    action: () => router.push('/audit-report')
  },
  {
    id: 3,
    title: '用户管理',
    description: '管理系统用户',
    icon: User,
    color: 'info',
    shortcut: 'Ctrl+U',
    action: () => router.push('/user-manage')
  },
  {
    id: 4,
    title: '系统设置',
    description: '配置系统参数',
    icon: Setting,
    color: 'warning',
    shortcut: 'Ctrl+S',
    action: () => router.push('/user-manage')
  },
  {
    id: 5,
    title: '数据分析',
    description: '查看数据统计',
    icon: DataAnalysis,
    color: 'primary',
    shortcut: 'Ctrl+D',
    action: () => router.push('/audit-report')
  },
  {
    id: 6,
    title: '实时威胁告警',
    description: '实时安全监控',
    icon: Monitor,
    color: 'danger',
    shortcut: 'Ctrl+T',
    action: () => router.push('/threat-monitor')
  },
  {
    id: 7,
    title: '合规风险记录',
    description: '管理合规风险记录',
    icon: Warning,
    color: 'warning',
    shortcut: 'Ctrl+E',
    action: () => router.push('/risk-event-manage')
  },
  {
    id: 8,
    title: '导出数据',
    description: '导出系统数据',
    icon: Download,
    color: 'success',
    shortcut: 'Ctrl+X',
    action: () => {}
  },
  {
    id: 9,
    title: '导入数据',
    description: '导入外部数据',
    icon: Upload,
    color: 'info',
    shortcut: 'Ctrl+I',
    action: () => {}
  }
];

function toggleMenu() {
  isOpen.value = !isOpen.value;
}

function closeMenu() {
  isOpen.value = false;
}

function executeAction(action) {
  if (action.action) {
    action.action();
    addToRecent(action);
  }
  closeMenu();
}

function addToRecent(action) {
  const index = recentActions.value.findIndex(a => a.id === action.id);
  if (index > -1) {
    recentActions.value.splice(index, 1);
  }
  recentActions.value.unshift(action);
  if (recentActions.value.length > 5) {
    recentActions.value.pop();
  }
}
</script>

<style scoped>
.quick-actions-container {
  position: relative;
  z-index: 60;
}

.quick-actions-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 12px;
  min-width: 90px;
  justify-content: center;
  height: 32px;
}

.quick-actions-trigger :deep(svg) {
  font-size: 30px;
  width: 30px;
  height: 30px;
}

.quick-actions-trigger:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(95, 135, 255, 0.3);
}

.quick-actions-backdrop {
  position: fixed;
  inset: 0;
  z-index: 58;
  border: none;
  padding: 0;
  background: transparent;
  opacity: 1;
  pointer-events: auto;
}

.quick-actions-backdrop-fog,
.quick-actions-backdrop-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.quick-actions-backdrop-fog {
  background:
    radial-gradient(circle at 18% 22%, rgba(126, 169, 255, 0.16), transparent 22%),
    radial-gradient(circle at 72% 46%, rgba(221, 235, 255, 0.08), transparent 26%),
    linear-gradient(90deg, rgba(2, 6, 12, 0.9) 0%, rgba(4, 9, 16, 0.8) 42%, rgba(4, 9, 16, 0.7) 100%);
  backdrop-filter: blur(18px) saturate(118%);
}

.quick-actions-backdrop-grid {
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.06) 1px, transparent 1px);
  background-size: 84px 84px;
  mask-image: linear-gradient(90deg, rgba(0,0,0,0.84), rgba(0,0,0,0.42));
}

.quick-actions-sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  right: 0;
  width: min(44vw, 480px);
  overflow: hidden;
  background: linear-gradient(180deg, rgba(7, 12, 22, 0.98), rgba(3, 6, 12, 0.98));
  box-shadow: -40px 0 120px rgba(0, 0, 0, 0.54);
  z-index: 62;
}

.quick-actions-noise,
.quick-actions-grid-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.quick-actions-noise {
  inset: -10%;
  opacity: 0.08;
  mix-blend-mode: screen;
  background-image: radial-gradient(rgba(255,255,255,0.18) 0.8px, transparent 0.8px);
  background-size: 4px 4px;
}

.quick-actions-grid-overlay {
  opacity: 0.12;
  background-image:
    linear-gradient(rgba(255,255,255,0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.08) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,1), rgba(0,0,0,0.2));
}

.quick-actions-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
}

.quick-actions-inner {
  min-height: 100%;
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.quick-actions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}

.quick-actions-header strong {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff, #dcecff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 0.02em;
}

.close-btn {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(169, 196, 255, 0.18);
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  padding: 10px;
  border-radius: 12px;
  transition: all 0.2s ease;
  backdrop-filter: blur(12px);
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(169, 196, 255, 0.3);
  color: #fff;
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(95, 135, 255, 0.15);
}

.close-btn svg {
  font-size: 20px;
  transition: transform 0.2s ease;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.quick-action-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(169, 196, 255, 0.08);
  border-radius: 16px;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
  backdrop-filter: blur(8px);
}

.quick-action-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.quick-action-item.primary::before {
  background: linear-gradient(180deg, #5f87ff, #1bd9b4);
}

.quick-action-item.success::before {
  background: linear-gradient(180deg, #4caf50, #81c784);
}

.quick-action-item.info::before {
  background: linear-gradient(180deg, #2196f3, #64b5f6);
}

.quick-action-item.warning::before {
  background: linear-gradient(180deg, #ff9800, #ffb74d);
}

.quick-action-item.danger::before {
  background: linear-gradient(180deg, #f44336, #e57373);
}

.quick-action-item:hover {
  transform: translateX(8px);
  box-shadow: 0 8px 24px rgba(95, 135, 255, 0.2);
  border-color: rgba(169, 196, 255, 0.24);
  color: #fff;
}

.quick-action-item:hover::before {
  opacity: 1;
}

.quick-action-item.primary:hover {
  background: linear-gradient(135deg, rgba(95, 135, 255, 0.16), rgba(27, 217, 180, 0.08));
}

.quick-action-item.success:hover {
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.16), rgba(129, 199, 132, 0.08));
}

.quick-action-item.info:hover {
  background: linear-gradient(135deg, rgba(33, 150, 243, 0.16), rgba(100, 181, 246, 0.08));
}

.quick-action-item.warning:hover {
  background: linear-gradient(135deg, rgba(255, 152, 0, 0.16), rgba(255, 183, 77, 0.08));
}

.quick-action-item.danger:hover {
  background: linear-gradient(135deg, rgba(244, 67, 54, 0.16), rgba(229, 115, 115, 0.08));
}

.quick-action-item svg {
  font-size: 20px;
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.quick-action-item:hover svg {
  transform: scale(1.1) rotate(5deg);
}

.quick-action-item.primary svg {
  color: #5f87ff;
}

.quick-action-item.success svg {
  color: #4caf50;
}

.quick-action-item.info svg {
  color: #2196f3;
}

.quick-action-item.warning svg {
  color: #ff9800;
}

.quick-action-item.danger svg {
  color: #f44336;
}

.action-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-info strong {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
}

.action-info span {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  line-height: 1.4;
}

.action-shortcut {
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(169, 196, 255, 0.18);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  color: rgba(255, 255, 255, 0.75);
  white-space: nowrap;
  backdrop-filter: blur(4px);
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 4px;
}

.quick-actions-footer {
  padding: 24px 0 0;
  margin-top: 16px;
  border-top: 1px solid rgba(169, 196, 255, 0.12);
}

.recent-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recent-actions strong {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  padding: 0 4px;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px;
}

.recent-action-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
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

.recent-action-item:hover {
  background: linear-gradient(135deg, rgba(95, 135, 255, 0.08), rgba(27, 217, 180, 0.04));
  border-color: rgba(169, 196, 255, 0.18);
  color: #fff;
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(95, 135, 255, 0.1);
}

.recent-action-item svg {
  font-size: 20px;
  color: #5f87ff;
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.recent-action-item:hover svg {
  transform: scale(1.1);
}

.recent-action-item span {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
  font-weight: 500;
}

.quick-actions-enter-active,
.quick-actions-leave-active {
  transition: transform 0.35s cubic-bezier(0.32, 0.72, 0, 1);
}

.quick-actions-enter-from,
.quick-actions-leave-to {
  transform: translateX(100%);
}
</style>