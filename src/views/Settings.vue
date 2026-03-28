<template>
  <el-card class="card-glass" v-loading="loading">
    <div class="card-header">系统设置</div>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本设置" name="basic">
        <el-form :model="settings.basic" label-width="120px">
          <el-form-item label="系统名称">
            <el-input v-model="settings.basic.systemName" />
          </el-form-item>
          <el-form-item label="系统版本">
            <el-input v-model="settings.basic.version" disabled />
          </el-form-item>
          <el-form-item label="API地址">
            <el-input v-model="settings.basic.apiUrl" />
          </el-form-item>
          <el-form-item label="数据备份频率">
            <el-select v-model="settings.basic.backupFrequency">
              <el-option label="每天" value="daily" />
              <el-option label="每周" value="weekly" />
              <el-option label="每月" value="monthly" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveBasicSettings">保存设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <el-tab-pane label="安全设置" name="security">
        <el-form :model="settings.security" label-width="120px">
          <el-form-item label="密码策略">
            <el-select v-model="settings.security.passwordPolicy">
              <el-option label="低" value="low" />
              <el-option label="中" value="medium" />
              <el-option label="高" value="high" />
            </el-select>
          </el-form-item>
          <el-form-item label="登录失败限制">
            <el-input-number v-model="settings.security.loginAttempts" :min="1" :max="10" />
          </el-form-item>
          <el-form-item label="会话超时时间">
            <el-input-number v-model="settings.security.sessionTimeout" :min="5" :max="120" />
            <span style="margin-left: 8px;">分钟</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveSecuritySettings">保存设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <el-tab-pane label="通知设置" name="notification">
        <el-form :model="settings.notification" label-width="120px">
          <el-form-item label="邮件通知">
            <el-switch v-model="settings.notification.email" />
          </el-form-item>
          <el-form-item label="短信通知">
            <el-switch v-model="settings.notification.sms" />
          </el-form-item>
          <el-form-item label="系统通知">
            <el-switch v-model="settings.notification.system" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveNotificationSettings">保存设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { systemConfigApi, SYSTEM_CONFIG_KEYS } from '../api/systemConfig';

const activeTab = ref('basic');
const loading = ref(false);

const settings = ref({
  basic: {
    systemName: 'Aegis Workbench',
    version: 'v1.0.0',
    apiUrl: 'http://localhost:8080/api',
    backupFrequency: 'daily'
  },
  security: {
    passwordPolicy: 'medium',
    loginAttempts: 5,
    sessionTimeout: 30
  },
  notification: {
    email: true,
    sms: false,
    system: true
  }
});

function applyConfigs(configs) {
  const map = Object.fromEntries((configs || []).map(item => [item.configKey, item.configValue]));
  settings.value.basic.systemName = map[SYSTEM_CONFIG_KEYS.systemName] || settings.value.basic.systemName;
  settings.value.basic.apiUrl = map[SYSTEM_CONFIG_KEYS.apiUrl] || settings.value.basic.apiUrl;
  settings.value.basic.backupFrequency = map[SYSTEM_CONFIG_KEYS.backupFrequency] || settings.value.basic.backupFrequency;
  settings.value.security.passwordPolicy = map[SYSTEM_CONFIG_KEYS.passwordPolicy] || settings.value.security.passwordPolicy;
  settings.value.security.loginAttempts = Number(map[SYSTEM_CONFIG_KEYS.loginAttempts] || settings.value.security.loginAttempts);
  settings.value.security.sessionTimeout = Number(map[SYSTEM_CONFIG_KEYS.sessionTimeout] || settings.value.security.sessionTimeout);
  settings.value.notification.email = String(map[SYSTEM_CONFIG_KEYS.notificationEmail] || settings.value.notification.email) === 'true';
  settings.value.notification.sms = String(map[SYSTEM_CONFIG_KEYS.notificationSms] || settings.value.notification.sms) === 'true';
  settings.value.notification.system = String(map[SYSTEM_CONFIG_KEYS.notificationSystem] || settings.value.notification.system) === 'true';
}

async function loadSettings() {
  loading.value = true;
  try {
    const configs = await systemConfigApi.list();
    applyConfigs(configs);
  } catch (error) {
    ElMessage.warning(error?.message || '系统配置读取失败，已展示默认值');
  } finally {
    loading.value = false;
  }
}

async function saveBasicSettings() {
  try {
    await Promise.all([
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.systemName, settings.value.basic.systemName, '系统名称'),
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.apiUrl, settings.value.basic.apiUrl, '后端 API 地址'),
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.backupFrequency, settings.value.basic.backupFrequency, '数据备份频率')
    ]);
    await loadSettings();
    ElMessage.success('基本设置保存成功');
  } catch (error) {
    ElMessage.error(error?.message || '基本设置保存失败');
  }
}

async function saveSecuritySettings() {
  try {
    await Promise.all([
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.passwordPolicy, settings.value.security.passwordPolicy, '密码策略等级'),
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.loginAttempts, settings.value.security.loginAttempts, '登录失败限制'),
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.sessionTimeout, settings.value.security.sessionTimeout, '会话超时时间（分钟）')
    ]);
    await loadSettings();
    ElMessage.success('安全设置保存成功');
  } catch (error) {
    ElMessage.error(error?.message || '安全设置保存失败');
  }
}

async function saveNotificationSettings() {
  try {
    await Promise.all([
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.notificationEmail, settings.value.notification.email, '邮件通知开关'),
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.notificationSms, settings.value.notification.sms, '短信通知开关'),
      systemConfigApi.upsert(SYSTEM_CONFIG_KEYS.notificationSystem, settings.value.notification.system, '系统通知开关')
    ]);
    await loadSettings();
    ElMessage.success('通知设置保存成功');
  } catch (error) {
    ElMessage.error(error?.message || '通知设置保存失败');
  }
}

onMounted(loadSettings);
</script>

<style scoped>
.card-header {
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--color-text);
  font-size: 18px;
}
</style>