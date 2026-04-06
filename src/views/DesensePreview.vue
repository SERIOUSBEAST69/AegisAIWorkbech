<template>
  <div class="page-grid">
    <el-card class="card-glass">
      <div class="card-header">全局脱敏规则配置（独立模块）</div>
      <p class="section-subtitle">仅支持手机号、身份证号、邮箱三类规则。正则+前端预览，无 NLP/OCR/深度学习。</p>

      <div class="switch-row">
        <el-switch v-model="config.globalEnabled" :disabled="readOnly" active-text="全局脱敏已开启" inactive-text="全局脱敏已关闭" />
      </div>

      <el-divider />

      <el-form label-position="top">
        <el-form-item label="手机号规则（保留前3后4：138****1234）">
          <el-switch v-model="config.phone.enabled" :disabled="readOnly" active-text="启用" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="身份证规则（保留前6后4：110101********123X）">
          <el-switch v-model="config.idCard.enabled" :disabled="readOnly" active-text="启用" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="邮箱规则（用户名中间隐藏：te****st@qq.com）">
          <el-switch v-model="config.email.enabled" :disabled="readOnly" active-text="启用" inactive-text="关闭" />
        </el-form-item>
      </el-form>

      <div class="panel-actions">
        <el-button :loading="loading" @click="loadConfig">读取配置</el-button>
        <el-button
          v-if="!readOnly"
          type="primary"
          :disabled="!isDirty || saving || loading"
          :loading="saving"
          @click="saveConfig"
        >
          {{ saving ? '下发中' : '保存下发' }}
        </el-button>
      </div>
    </el-card>

    <el-card class="card-glass">
      <div class="card-header">实时预览</div>
      <el-input v-model="previewInput" type="textarea" :rows="4" placeholder="输入包含手机号、身份证号、邮箱的文本" />
      <el-alert style="margin-top: 12px;" type="info" :closable="false" show-icon>
        <template #title>脱敏输出</template>
        <div class="preview-result">{{ previewOutput }}</div>
      </el-alert>

      <el-divider />
      <div class="format-note">
        <p>格式说明：</p>
        <p>1. 手机号：保留前3位、后4位，中间****</p>
        <p>2. 身份证号：保留前6位、后4位，中间8位遮蔽</p>
        <p>3. 邮箱：用户名中间隐藏为****，保留后缀</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { desenseApi } from '../api/desense';
import { useUserStore } from '../store/user';
import { isGovernanceAdmin } from '../utils/roleBoundary';

const userStore = useUserStore();
const readOnly = computed(() => !isGovernanceAdmin(userStore.userInfo));

const loading = ref(false);
const saving = ref(false);
const initialConfigSnapshot = ref('');
const previewInput = ref('手机号13812341234，身份证11010119900101123X，邮箱test@qq.com');
const config = ref({
  globalEnabled: true,
  phone: { enabled: true },
  idCard: { enabled: true },
  email: { enabled: true },
});

function normalizeConfig(raw) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const existing = source.desenseRules && typeof source.desenseRules === 'object' ? source.desenseRules : {};
  return {
    globalEnabled: source.desenseGlobalEnabled !== false,
    phone: { enabled: existing.phone?.enabled !== false },
    idCard: { enabled: existing.idCard?.enabled !== false },
    email: { enabled: existing.email?.enabled !== false },
  };
}

function applyPhoneMask(text) {
  return text.replace(/(1\d{2})\d{4}(\d{4})/g, '$1****$2');
}

function applyIdCardMask(text) {
  return text
    .replace(/(\d{6})(\d{5})(\d{4})\b/g, (_, left, middle, right) => `${left}${'*'.repeat(middle.length)}${right}`)
    .replace(/(\d{6})(\d{8})(\d{4}|\d{3}[Xx])\b/g, (_, left, middle, right) => `${left}${'*'.repeat(middle.length)}${right}`);
}

function applyEmailMask(text) {
  return text.replace(/([A-Za-z0-9._%+-]{2})([A-Za-z0-9._%+-]*)([A-Za-z0-9._%+-]{2})@([A-Za-z0-9.-]+\.[A-Za-z]{2,})/g, '$1****$3@$4');
}

const previewOutput = computed(() => {
  let output = String(previewInput.value || '');
  if (!config.value.globalEnabled) {
    return output;
  }
  if (config.value.phone.enabled) {
    output = applyPhoneMask(output);
  }
  if (config.value.idCard.enabled) {
    output = applyIdCardMask(output);
  }
  if (config.value.email.enabled) {
    output = applyEmailMask(output);
  }
  return output;
});

function snapshotConfig(value) {
  return JSON.stringify({
    desenseGlobalEnabled: value.globalEnabled,
    desenseRules: {
      phone: { enabled: value.phone.enabled },
      idCard: { enabled: value.idCard.enabled },
      email: { enabled: value.email.enabled },
    },
  });
}

const isDirty = computed(() => snapshotConfig(config.value) !== initialConfigSnapshot.value);

async function loadConfig() {
  loading.value = true;
  try {
    const data = await desenseApi.getGlobalConfig();
    config.value = normalizeConfig(data);
    initialConfigSnapshot.value = snapshotConfig(config.value);
  } catch (error) {
    ElMessage.error(error?.message || '加载脱敏配置失败');
  } finally {
    loading.value = false;
  }
}

async function saveConfig() {
  if (readOnly.value) {
    return;
  }
  saving.value = true;
  try {
    const payload = {
      desenseGlobalEnabled: config.value.globalEnabled,
      desenseRules: {
        phone: { enabled: config.value.phone.enabled, pattern: '(1\\d{2})\\d{4}(\\d{4})', format: '$1****$2' },
        idCard: { enabled: config.value.idCard.enabled, pattern: '(\\d{6})\\d{8}(\\d{3}[\\dXx])', format: '$1********$2' },
        email: { enabled: config.value.email.enabled, pattern: '([A-Za-z0-9._%+-]{2})([A-Za-z0-9._%+-]*)([A-Za-z0-9._%+-]{2})@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})', format: '$1****$3@$4' },
      },
    };
    await desenseApi.saveGlobalConfig(payload);
    initialConfigSnapshot.value = snapshotConfig(config.value);
    ElMessage.success('全局脱敏规则已保存并下发');
  } catch (error) {
    ElMessage.error(error?.message || '保存脱敏配置失败');
  } finally {
    saving.value = false;
  }
}

onMounted(loadConfig);
</script>

<style scoped>
.page-grid { display: grid; gap: 16px; }
.card-header { font-weight: 600; margin-bottom: 8px; }
.section-subtitle { color: var(--color-text-muted); margin-bottom: 14px; }
.switch-row { margin: 4px 0 8px; }
.panel-actions { display: flex; gap: 10px; margin-top: 8px; }
.preview-result { white-space: pre-wrap; word-break: break-word; line-height: 1.6; }
.format-note p { margin: 4px 0; color: var(--color-text-secondary); }
</style>

