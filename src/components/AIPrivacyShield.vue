<template>
  <Transition name="privacy-shield-slide">
    <div v-if="visible" class="privacy-shield" :class="shieldClass">
      <!-- Shield icon + header -->
      <div class="ps-header">
        <span class="ps-icon">{{ blocked ? '🛡️' : '✅' }}</span>
        <span class="ps-title">{{ blocked ? 'AegisAI 隐私盾已拦截' : 'AegisAI 隐私盾守护中' }}</span>
        <button class="ps-close" @click="dismiss" aria-label="关闭">×</button>
      </div>

      <!-- Blocked state -->
      <div v-if="blocked" class="ps-body">
        <p class="ps-msg">{{ message }}</p>
        <div v-if="detected.length" class="ps-tags">
          <span v-for="tag in detected" :key="tag" class="ps-tag">{{ tag }}</span>
        </div>
        <p class="ps-hint">请对输入内容脱敏后再发送给 AI 模型，以保护个人隐私安全。</p>
        <div class="ps-actions">
          <button class="ps-btn ps-btn-danger" @click="emit('block')">禁止发送</button>
          <button class="ps-btn ps-btn-ghost" @click="dismiss">我知道了</button>
        </div>
      </div>

      <!-- Safe / monitoring state -->
      <div v-else class="ps-body">
        <p class="ps-msg">实时输入监控已启用，未检测到个人隐私信息，可安全发送。</p>
      </div>
    </div>
  </Transition>
</template>

<script setup>
/**
 * AIPrivacyShield
 * ───────────────────────────────────────────────────────────────────────────
 * 在用户向 AI 工具（豆包、恶意AI模拟器等）输入内容时，实时调用后端隐私检测接口。
 * 若检测到个人隐私字段（身份证、手机、银行卡、邮箱等），立即展示红色拦截横幅
 * 并阻止发送。
 *
 * 用法：
 *   <AIPrivacyShield ref="shield" />
 *   // 在输入框 input 事件处调用：
 *   shield.value.check(inputText)
 *
 * 也可以作为独立组件嵌入任何 AI 工具面板。
 */
import { ref, computed } from 'vue';
import request from '../api/request';
import { quickPrivacyCheck, PRIVACY_CHECK_DEBOUNCE_MS } from '../utils/privacyPatterns';

// ── State ───────────────────────────────────────────────────────────────────
const visible  = ref(false);
const blocked  = ref(false);
const message  = ref('');
const detected = ref([]);

// Debounce timer
let _debounceTimer = null;

const emit = defineEmits(['block', 'safe']);

const shieldClass = computed(() => ({
  'ps-blocked': blocked.value,
  'ps-safe':    !blocked.value,
}));

// ── Public API ───────────────────────────────────────────────────────────────

/**
 * 对输入文本进行隐私检测（防抖 500ms）。
 * @param {string} text - 用户当前输入内容
 */
function check(text) {
  if (_debounceTimer) clearTimeout(_debounceTimer);
  if (!text || text.trim().length < 3) {
    hide();
    return;
  }
  _debounceTimer = setTimeout(() => _doCheck(text), PRIVACY_CHECK_DEBOUNCE_MS);
}

function dismiss() {
  visible.value = false;
}

function hide() {
  visible.value = false;
}

// ── Internal ─────────────────────────────────────────────────────────────────

async function _doCheck(text) {
  try {
    // 客户端快速正则预检（减少不必要的网络请求）
    const localDetected = quickPrivacyCheck(text);
    if (localDetected.length > 0) {
      _showBlocked(localDetected, '检测到输入中含个人隐私信息：' + localDetected.join('、'));
      emit('block');
      return;
    }

    // 后端精确检测
    const resp = await request.post('/ai/privacy-check', { text });
    if (!resp.safe) {
      _showBlocked(resp.detected || [], resp.message || '检测到隐私信息');
      emit('block');
    } else {
      _showSafe();
      emit('safe');
    }
  } catch {
    // 网络不可用时降级到本地检测
    const localDetected = quickPrivacyCheck(text);
    if (localDetected.length > 0) {
      _showBlocked(localDetected, '（离线模式）检测到输入中含个人隐私信息：' + localDetected.join('、'));
      emit('block');
    }
  }
}

function _showBlocked(fields, msg) {
  blocked.value  = true;
  detected.value = fields;
  message.value  = msg;
  visible.value  = true;
}

function _showSafe() {
  // 仅在用户主动关注时展示安全提示（不自动弹出绿色横幅，避免打扰）
  blocked.value  = false;
  detected.value = [];
  message.value  = '';
  // 只有在之前处于拦截状态时才展示安全确认，否则保持隐藏
  if (visible.value) {
    setTimeout(() => { visible.value = false; }, 2000);
  }
}

defineExpose({ check, dismiss, hide });
</script>

<style scoped>
.privacy-shield {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  width: 380px;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.48);
  overflow: hidden;
  font-family: inherit;
}

.privacy-shield.ps-blocked {
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.92), rgba(153, 27, 27, 0.88));
  border-color: rgba(248, 113, 113, 0.4);
}

.privacy-shield.ps-safe {
  background: linear-gradient(135deg, rgba(5, 150, 105, 0.92), rgba(6, 95, 70, 0.88));
  border-color: rgba(52, 211, 153, 0.4);
}

.ps-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.ps-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.ps-title {
  flex: 1;
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.02em;
}

.ps-close {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  font-size: 18px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
  border-radius: 4px;
  transition: color 0.15s;
}
.ps-close:hover { color: #fff; }

.ps-body {
  padding: 12px 16px 14px;
}

.ps-msg {
  margin: 0 0 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1.5;
}

.ps-hint {
  margin: 6px 0 10px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.65);
  line-height: 1.6;
}

.ps-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.ps-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.ps-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.ps-btn {
  padding: 7px 16px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: opacity 0.15s;
}
.ps-btn:hover { opacity: 0.85; }

.ps-btn-danger {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.ps-btn-ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.8);
}

/* Transition */
.privacy-shield-slide-enter-active,
.privacy-shield-slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.25s ease;
}
.privacy-shield-slide-enter-from,
.privacy-shield-slide-leave-to {
  transform: translateY(24px) scale(0.96);
  opacity: 0;
}
</style>
