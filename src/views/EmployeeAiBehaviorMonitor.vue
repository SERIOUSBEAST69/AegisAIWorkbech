<template>
  <div class="monitor-page">
    <div class="page-header scene-block card-glass">
      <div>
        <div class="page-eyebrow">EMPLOYEE AI BEHAVIOR MONITOR</div>
        <h1>员工 AI 行为监控</h1>
        <p>统一展示异常行为检测与隐私盾告警。管理员/安全运维可查看全量，管理层仅看摘要，其他角色仅看个人数据。</p>
      </div>
      <div class="header-actions">
        <el-tag v-if="isExecutive" type="warning" effect="dark">管理层摘要视图</el-tag>
        <el-tag v-else-if="isPersonalView" type="info" effect="dark">个人视角</el-tag>
        <el-tag v-else type="success" effect="dark">全量视角</el-tag>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="scene-block" @tab-change="handleTabChange">
      <el-tab-pane label="异常行为检测" name="anomaly">
        <div class="stats-grid">
          <div class="stat-card card-glass">
            <div class="stat-title">模型状态</div>
            <div class="stat-value">{{ modelReady ? '已就绪' : '未就绪' }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">总行为记录</div>
            <div class="stat-value">{{ anomalyTotal }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">异常行为数</div>
            <div class="stat-value danger">{{ anomalyCount }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">异常占比</div>
            <div class="stat-value">{{ anomalyRateText }}</div>
          </div>
        </div>

        <div class="panel card-glass">
          <div class="panel-head">
            <h3>异常行为事件</h3>
            <el-button :loading="anomalyLoading" @click="loadAnomaly">刷新</el-button>
          </div>

          <div v-if="anomalyLoading" class="empty">加载中...</div>
          <div v-else-if="isExecutive" class="empty">管理层仅提供摘要，不展示具体事件详情。</div>
          <div v-else-if="anomalyEvents.length === 0" class="empty">暂无事件。</div>
          <div v-else class="event-list">
            <div v-for="(ev, idx) in anomalyEvents" :key="ev.id || idx" class="event-row" :class="{ anomaly: !!ev.is_anomaly }">
              <div class="event-main">
                <strong>{{ ev.employee_id || '-' }}</strong>
                <span>{{ ev.department || '-' }}</span>
                <span>{{ ev.ai_service || '-' }}</span>
              </div>
              <div class="event-meta">
                <span>分数 {{ formatScore(ev.anomaly_score) }}</span>
                <span>{{ ev.risk_level || '-' }}</span>
                <span>{{ formatDate(ev.created_at) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="隐私盾告警" name="privacy">
        <div class="stats-grid">
          <div class="stat-card card-glass">
            <div class="stat-title">总告警</div>
            <div class="stat-value">{{ privacySummary.total || 0 }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">今日新增</div>
            <div class="stat-value">{{ privacySummary.today || 0 }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">扩展上报</div>
            <div class="stat-value">{{ privacySummary.extensionCount || 0 }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">剪贴板上报</div>
            <div class="stat-value">{{ privacySummary.clipboardCount || 0 }}</div>
          </div>
        </div>

        <div class="panel card-glass">
          <div class="panel-head">
            <h3>隐私盾告警事件</h3>
            <div class="panel-actions">
              <el-button v-if="canExportPrivacy" @click="exportPrivacyCsv">导出CSV</el-button>
              <el-button :loading="privacyLoading" @click="loadPrivacyEvents">刷新</el-button>
            </div>
          </div>

          <div v-if="privacyLoading" class="empty">加载中...</div>
          <div v-else-if="privacySummary.summaryOnly" class="empty">管理层仅提供统计摘要，不展示具体隐私事件详情。</div>
          <div v-else-if="privacyEvents.length === 0" class="empty">暂无隐私盾告警。</div>
          <div v-else class="event-list">
            <div v-for="ev in privacyEvents" :key="ev.id" class="event-row privacy">
              <div class="event-main">
                <strong>{{ ev.userId || '-' }}</strong>
                <span>{{ ev.source || '-' }}</span>
                <span>{{ ev.action || '-' }}</span>
              </div>
              <div class="event-meta">
                <span>{{ ev.matchedTypes || '-' }}</span>
                <span>{{ ev.windowTitle || '-' }}</span>
                <span>{{ formatDate(ev.eventTime) }}</span>
              </div>
              <div class="masked-text">{{ ev.contentMasked || '-' }}</div>
            </div>
          </div>

          <div class="pager" v-if="!privacySummary.summaryOnly">
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="privacyQuery.page"
              :page-size="privacyQuery.pageSize"
              :total="privacyTotal"
              @current-change="handlePrivacyPageChange"
            />
          </div>
        </div>

        <div v-if="canManagePrivacyConfig" class="panel card-glass">
          <div class="panel-head">
            <h3>隐私盾配置管理</h3>
            <div class="panel-actions">
              <el-button :loading="configLoading" @click="loadPrivacyConfig">读取配置</el-button>
              <el-button type="primary" :loading="configSaving" @click="savePrivacyConfig">保存配置</el-button>
            </div>
          </div>
          <el-input
            v-model="privacyConfigText"
            type="textarea"
            :rows="12"
            placeholder="JSON 配置"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="员工画像闭环" name="profile">
        <div class="stats-grid">
          <div class="stat-card card-glass">
            <div class="stat-title">隐私告警</div>
            <div class="stat-value">{{ profileData.counters?.privacy || 0 }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">行为异常</div>
            <div class="stat-value danger">{{ profileData.counters?.anomaly || 0 }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">影子AI</div>
            <div class="stat-value">{{ profileData.counters?.shadowAi || 0 }}</div>
          </div>
          <div class="stat-card card-glass">
            <div class="stat-title">安全威胁</div>
            <div class="stat-value">{{ profileData.counters?.security || 0 }}</div>
          </div>
        </div>

        <div class="panel card-glass">
          <div class="panel-head">
            <h3>员工事件画像</h3>
            <div class="panel-actions">
              <el-input
                v-if="canQueryOthers"
                v-model="profileQueryUser"
                placeholder="输入用户名筛选"
                style="width: 180px"
                clearable
              />
              <el-button :loading="profileLoading" @click="loadProfile">刷新</el-button>
            </div>
          </div>

          <div v-if="profileLoading" class="empty">加载中...</div>
          <div v-else>
            <p class="profile-target">当前画像用户：{{ profileData.username || '-' }}</p>

            <el-table :data="profileData.events || []" style="margin-bottom: 12px">
              <el-table-column prop="eventType" label="类型" width="130">
                <template #default="{ row }">{{ profileEventType(row.eventType) }}</template>
              </el-table-column>
              <el-table-column prop="severity" label="级别" width="100" />
              <el-table-column prop="title" label="标题" min-width="190" />
              <el-table-column prop="sourceModule" label="模块" width="120" />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column prop="eventTime" label="时间" min-width="160" />
            </el-table>

            <h4 class="subhead">攻防验证记录</h4>
            <el-table :data="profileData.adversarialRecords || []">
              <el-table-column prop="scenario" label="场景" min-width="160" />
              <el-table-column prop="policyVersion" label="策略版本" width="110" />
              <el-table-column prop="effectivenessAnalysis" label="有效性分析" min-width="260" />
              <el-table-column prop="createTime" label="时间" min-width="160" />
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { alertCenterApi } from '../api/alertCenter';
import { privacyApi } from '../api/privacy';
import { useUserStore } from '../store/user';

const userStore = useUserStore();
const roleCode = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase());
const isExecutive = computed(() => roleCode.value === 'EXECUTIVE');
const isPersonalView = computed(() => !['ADMIN', 'SECOPS', 'EXECUTIVE'].includes(roleCode.value));
const canManagePrivacyConfig = computed(() => ['ADMIN', 'SECOPS'].includes(roleCode.value));
const canExportPrivacy = computed(() => ['ADMIN', 'SECOPS'].includes(roleCode.value));
const canQueryOthers = computed(() => ['ADMIN', 'SECOPS'].includes(roleCode.value));

const activeTab = ref('anomaly');

const anomalyLoading = ref(false);
const anomalyEvents = ref([]);
const anomalySummaryOnly = ref(false);
const anomalySummary = ref({ total: 0, anomalyCount: 0, normalCount: 0, anomalyRate: 0 });
const modelReady = ref(false);

const privacyLoading = ref(false);
const privacyEvents = ref([]);
const privacySummary = ref({ total: 0, today: 0, extensionCount: 0, clipboardCount: 0, summaryOnly: false });
const privacyTotal = ref(0);
const privacyQuery = ref({ page: 1, pageSize: 10 });

const privacyConfigText = ref('');
const configLoading = ref(false);
const configSaving = ref(false);
const profileLoading = ref(false);
const profileQueryUser = ref('');
const profileData = ref({
  userId: null,
  username: '',
  counters: { privacy: 0, anomaly: 0, shadowAi: 0, security: 0 },
  events: [],
  adversarialRecords: [],
});

const anomalyTotal = computed(() => {
  if (anomalySummaryOnly.value) return Number(anomalySummary.value.total || 0);
  return anomalyEvents.value.length;
});
const anomalyCount = computed(() => {
  if (anomalySummaryOnly.value) return Number(anomalySummary.value.anomalyCount || 0);
  return anomalyEvents.value.filter((item) => !!item.is_anomaly).length;
});
const anomalyRateText = computed(() => {
  if (anomalySummaryOnly.value) {
    const rate = Number(anomalySummary.value.anomalyRate || 0);
    return `${(rate * 100).toFixed(1)}%`;
  }
  if (!anomalyEvents.value.length) return '0%';
  return `${((anomalyCount.value / anomalyEvents.value.length) * 100).toFixed(1)}%`;
});

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}

function formatScore(value) {
  const num = Number(value);
  if (Number.isNaN(num)) return '-';
  return num.toFixed(4);
}

function normalizeAnomalyEvent(item = {}) {
  return {
    ...item,
    employee_id: item.employee_id || item.employeeId || item.userId || '-',
    department: item.department || item.dept || '-',
    ai_service: item.ai_service || item.aiService || item.source || '-',
    anomaly_score: item.anomaly_score ?? item.anomalyScore ?? null,
    risk_level: item.risk_level || item.riskLevel || '-',
    created_at: item.created_at || item.event_time || item.eventTime || null,
  };
}

async function loadAnomalyStatus() {
  try {
    const data = await request.get('/anomaly/status');
    modelReady.value = !!data?.model_ready;
  } catch {
    modelReady.value = false;
  }
}

async function loadAnomaly() {
  anomalyLoading.value = true;
  try {
    const data = await request.get('/anomaly/events');
    anomalySummaryOnly.value = !!data?.summaryOnly;
    if (anomalySummaryOnly.value) {
      anomalySummary.value = {
        total: data.total || 0,
        anomalyCount: data.anomalyCount || 0,
        normalCount: data.normalCount || 0,
        anomalyRate: data.anomalyRate || 0,
      };
      anomalyEvents.value = [];
      return;
    }
    anomalyEvents.value = Array.isArray(data?.events)
      ? data.events.map((item) => normalizeAnomalyEvent(item))
      : [];
  } catch (error) {
    anomalyEvents.value = [];
    ElMessage.error(error?.message || '加载异常行为失败');
  } finally {
    anomalyLoading.value = false;
  }
}

async function loadPrivacyEvents() {
  privacyLoading.value = true;
  try {
    const data = await privacyApi.listEvents({
      page: privacyQuery.value.page,
      pageSize: privacyQuery.value.pageSize,
    });
    privacySummary.value = {
      total: data?.total || 0,
      today: data?.today || 0,
      extensionCount: data?.extensionCount || 0,
      clipboardCount: data?.clipboardCount || 0,
      summaryOnly: !!data?.summaryOnly,
    };
    privacyTotal.value = Number(data?.total || 0);
    privacyEvents.value = Array.isArray(data?.list) ? data.list : [];
  } catch (error) {
    privacyEvents.value = [];
    ElMessage.error(error?.message || '加载隐私告警失败');
  } finally {
    privacyLoading.value = false;
  }
}

async function loadPrivacyConfig() {
  if (!canManagePrivacyConfig.value) return;
  configLoading.value = true;
  try {
    const data = await privacyApi.getConfig();
    privacyConfigText.value = JSON.stringify(data || {}, null, 2);
  } catch (error) {
    ElMessage.error(error?.message || '读取配置失败');
  } finally {
    configLoading.value = false;
  }
}

async function savePrivacyConfig() {
  if (!canManagePrivacyConfig.value) return;
  let payload = {};
  try {
    payload = JSON.parse(privacyConfigText.value || '{}');
  } catch {
    ElMessage.error('配置 JSON 格式不正确');
    return;
  }

  configSaving.value = true;
  try {
    const data = await privacyApi.updateConfig(payload);
    privacyConfigText.value = JSON.stringify(data || {}, null, 2);
    ElMessage.success('配置已保存');
  } catch (error) {
    ElMessage.error(error?.message || '保存配置失败');
  } finally {
    configSaving.value = false;
  }
}

function handlePrivacyPageChange(page) {
  privacyQuery.value.page = page;
  loadPrivacyEvents();
}

function exportPrivacyCsv() {
  if (!privacyEvents.value.length) {
    ElMessage.warning('暂无可导出数据');
    return;
  }
  const headers = ['id', 'userId', 'eventType', 'source', 'action', 'matchedTypes', 'eventTime', 'contentMasked'];
  const rows = privacyEvents.value.map((item) => headers.map((h) => JSON.stringify(item[h] ?? '')).join(','));
  const csv = [headers.join(','), ...rows].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `privacy-events-${Date.now()}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function handleTabChange(name) {
  if (name === 'privacy') {
    loadPrivacyEvents();
    if (canManagePrivacyConfig.value && !privacyConfigText.value) {
      loadPrivacyConfig();
    }
  }
  if (name === 'profile') {
    loadProfile();
  }
}

function profileEventType(type) {
  const mapping = {
    PRIVACY_ALERT: '隐私告警',
    ANOMALY_ALERT: '行为异常',
    SHADOW_AI_ALERT: '影子AI',
    SECURITY_ALERT: '安全威胁',
  };
  return mapping[type] || type || '-';
}

async function loadProfile() {
  profileLoading.value = true;
  try {
    const params = { limit: 40 };
    if (canQueryOthers.value && profileQueryUser.value.trim()) {
      params.username = profileQueryUser.value.trim();
    }
    const data = await alertCenterApi.userHistory(params);
    profileData.value = {
      userId: data?.userId,
      username: data?.username || profileQueryUser.value || '-',
      counters: data?.counters || { privacy: 0, anomaly: 0, shadowAi: 0, security: 0 },
      events: Array.isArray(data?.events) ? data.events : [],
      adversarialRecords: Array.isArray(data?.adversarialRecords) ? data.adversarialRecords : [],
    };
  } catch (error) {
    ElMessage.error(error?.message || '加载员工画像失败');
  } finally {
    profileLoading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadAnomalyStatus(), loadAnomaly(), loadProfile()]);
});
</script>

<style scoped>
.monitor-page {
  padding: 24px 30px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.page-eyebrow {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--color-primary);
}

.page-header h1 {
  margin: 6px 0;
  font-size: 24px;
}

.page-header p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stat-card {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 14px;
}

.stat-title {
  font-size: 12px;
  color: var(--color-text-muted);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}

.stat-value.danger {
  color: #f56c6c;
}

.panel {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 14px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.empty {
  padding: 20px 0;
  color: var(--color-text-muted);
  text-align: center;
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-row {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px;
}

.event-row.anomaly {
  border-color: rgba(245, 108, 108, 0.4);
  background: rgba(245, 108, 108, 0.05);
}

.event-row.privacy {
  border-color: rgba(64, 158, 255, 0.4);
}

.event-main {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 4px;
}

.event-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--color-text-muted);
  font-size: 12px;
}

.masked-text {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-soft);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.profile-target {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.subhead {
  margin: 8px 0;
  font-size: 14px;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .monitor-page {
    padding: 16px;
  }
  .page-header {
    flex-direction: column;
  }
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
