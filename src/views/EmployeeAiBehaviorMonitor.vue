<template>
  <div class="monitor-page">
    <div class="page-header scene-block card-glass">
      <div>
        <div class="page-eyebrow">EMPLOYEE AI GOVERNANCE</div>
        <h1>员工 AI 监控</h1>
        <p>已将隐私盾告警并入异常行为检测视图，并过滤掉角色为空的数据。</p>
      </div>
      <div class="header-actions">
        <el-button @click="refreshAll" :loading="anomalyLoading">刷新</el-button>
      </div>
    </div>

    <div class="caliber-strip">
      <span><strong>异常事件：</strong>{{ governanceSnapshot.anomaly }}</span>
      <span><strong>隐私告警：</strong>{{ governanceSnapshot.privacy }}</span>
      <span><strong>待处理：</strong>{{ governanceSnapshot.pending }}</span>
      <span><strong>折叠去重：</strong>{{ governanceSnapshot.collapsed }}</span>
      <span class="caliber-text">{{ governanceSnapshot.note }}</span>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-title">总事件数（已过滤空角色）</div>
        <div class="stat-value">{{ anomalyTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">异常事件数</div>
        <div class="stat-value danger">{{ anomalyCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">异常占比</div>
        <div class="stat-value">{{ anomalyRateText }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">模型状态</div>
        <div class="stat-value">{{ modelReady ? '就绪' : '未就绪' }}</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <h3>异常行为检测（含隐私盾）</h3>
        <div class="panel-actions">
          <el-tag type="danger">异常 {{ anomalyCount }}</el-tag>
          <el-tag type="info">总计 {{ anomalyTotal }}</el-tag>
        </div>
      </div>

      <div v-if="!anomalyEvents.length" class="empty">暂无可展示数据</div>

      <div v-else class="event-list">
        <div
          v-for="item in pagedEvents"
          :key="item.event_id || `${item.source_tag}-${item.employee_id}-${item.created_at}`"
          class="event-row"
          :class="item.source_tag === 'privacy' ? 'privacy' : (item.is_anomaly ? 'anomaly' : '')"
        >
          <div class="event-main">
            <el-tag size="small" :type="item.source_tag === 'privacy' ? 'primary' : 'danger'">
              {{ item.source_tag === 'privacy' ? '隐私盾' : '异常检测' }}
            </el-tag>
            <strong>{{ anomalyAccountName(item) }}</strong>
            <span>角色：{{ anomalyRole(item) }}</span>
            <span>岗位：{{ anomalyPosition(item) }}</span>
            <span>部门：{{ anomalyDepartment(item) }}</span>
          </div>
          <div class="event-meta">
            <span>账号ID：{{ anomalyAccountId(item) }}</span>
            <span>设备：{{ anomalyDevice(item) }}</span>
            <span>公司：{{ anomalyCompany(item) }}</span>
            <span>服务：{{ item.ai_service || '-' }}</span>
            <span>风险：{{ item.risk_level || '-' }}</span>
            <span>分值：{{ formatScore(item.anomaly_score) }}</span>
            <span>时间：{{ formatDate(item.created_at) }}</span>
          </div>
          <div class="masked-text">{{ sanitizeText(item.description) }}</div>
        </div>
      </div>

      <div class="pager">
        <el-pagination
          layout="total, sizes, prev, pager, next"
          v-model:current-page="anomalyQuery.page"
          :page-sizes="[10, 20, 50]"
          :page-size="anomalyQuery.pageSize"
          :total="anomalyTotal"
          @current-change="handleAnomalyPageChange"
          @size-change="handleAnomalySizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { alertCenterApi } from '../api/alertCenter';
import { privacyApi } from '../api/privacy';
import { useUserStore } from '../store/user';
import { hasAnyRole, isExecutive as isExecutiveRole } from '../utils/roleBoundary';

const userStore = useUserStore();
const isExecutive = computed(() => isExecutiveRole(userStore.userInfo));
const isPersonalView = computed(() => !hasAnyRole(userStore.userInfo, ['ADMIN', 'SECOPS', 'EXECUTIVE']));
const currentUserId = computed(() => (userStore.userInfo?.id != null ? String(userStore.userInfo.id) : ''));
const currentUsername = computed(() => String(userStore.userInfo?.username || '').toLowerCase());

const userDirectory = ref(new Map());
const anomalyLoading = ref(false);
const anomalyEvents = ref([]);
const anomalySummaryOnly = ref(false);
const anomalySummary = ref({ total: 0, anomalyCount: 0, normalCount: 0, anomalyRate: 0 });
const anomalyQuery = ref({ page: 1, pageSize: 10 });
const anomalyTotalRecords = ref(0);
const modelReady = ref(false);
const privacySummary = ref({ total: 0, today: 0, extensionCount: 0, clipboardCount: 0, summaryOnly: false });

const governanceSnapshot = ref({
  anomaly: 0,
  privacy: 0,
  pending: 0,
  collapsed: 0,
  note: '口径：governance_event_dedup_chain_v1（与首页一致）',
});

const anomalyTotal = computed(() => {
  if (anomalySummaryOnly.value) return Number(anomalySummary.value.total || 0);
  return Number(anomalyTotalRecords.value || 0);
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

const pagedEvents = computed(() => {
  const page = Number(anomalyQuery.value.page || 1);
  const size = Number(anomalyQuery.value.pageSize || 10);
  const start = (page - 1) * size;
  return anomalyEvents.value.slice(start, start + size);
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

function sanitizeText(value) {
  const text = String(value || '');
  return text.replace(/\?{2,}/g, '[编码异常文本]').trim() || '-';
}

async function ensureUserDirectory() {
  if (userDirectory.value.size > 0) return;
  try {
    const users = await request.get('/user/list');
    const map = new Map();
    (Array.isArray(users) ? users : []).forEach((item) => {
      if (item?.id != null) map.set(String(item.id), item);
      if (item?.username) map.set(`username:${String(item.username).toLowerCase()}`, item);
    });
    userDirectory.value = map;
  } catch {
    userDirectory.value = new Map();
  }
}

function userByAny(value) {
  if (value == null) return null;
  const key = String(value);
  return userDirectory.value.get(key) || userDirectory.value.get(`username:${key.toLowerCase()}`) || null;
}

function anomalyUser(ev) {
  return userByAny(ev?.employee_id || ev?.userId || ev?.username || ev?.employeeId);
}

function anomalyAccountName(ev) {
  const user = anomalyUser(ev);
  return user?.username || ev?.employee_id || ev?.userId || '-';
}

function anomalyAccountId(ev) {
  const user = anomalyUser(ev);
  return user?.id ?? '-';
}

function anomalyRole(ev) {
  const user = anomalyUser(ev);
  return user?.roleCode || '-';
}

function hasBoundRole(ev) {
  const roleCode = String(anomalyRole(ev) || '').trim();
  return roleCode.length > 0 && roleCode !== '-';
}

function anomalyPosition(ev) {
  const user = anomalyUser(ev);
  return user?.jobTitle || '-';
}

function anomalyDepartment(ev) {
  const user = anomalyUser(ev);
  return user?.department || ev?.department || '-';
}

function anomalyDevice(ev) {
  const user = anomalyUser(ev);
  return user?.deviceId || '-';
}

function anomalyCompany(ev) {
  const user = anomalyUser(ev);
  return user?.companyId != null ? String(user.companyId) : '-';
}

function normalizeAnomalyEvent(item = {}) {
  return {
    ...item,
    source_tag: 'anomaly',
    employee_id: item.employee_id || item.employeeId || item.userId || '-',
    department: item.department || item.dept || '-',
    ai_service: item.ai_service || item.aiService || item.source || '-',
    anomaly_score: item.anomaly_score ?? item.anomalyScore ?? null,
    risk_level: item.risk_level || item.riskLevel || '-',
    created_at: item.created_at || item.event_time || item.eventTime || null,
  };
}

function isCurrentUserEvent(userValue) {
  if (!isPersonalView.value) return true;
  const normalized = String(userValue || '').toLowerCase();
  if (!normalized) return false;
  if (currentUserId.value && normalized === currentUserId.value.toLowerCase()) return true;
  if (currentUsername.value && normalized === currentUsername.value) return true;
  const linkedUser = userByAny(userValue);
  if (!linkedUser) return false;
  if (currentUserId.value && String(linkedUser.id || '') === currentUserId.value) return true;
  if (currentUsername.value && String(linkedUser.username || '').toLowerCase() === currentUsername.value) return true;
  return false;
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
    const [anomalyData, privacyData] = await Promise.all([
      request.get('/anomaly/events', {
        params: {
          page: 1,
          pageSize: 500,
        },
      }),
      privacyApi.listEvents({ page: 1, pageSize: 500 }),
    ]);

    privacySummary.value = {
      total: privacyData?.total || 0,
      today: privacyData?.today || 0,
      extensionCount: privacyData?.extensionCount || 0,
      clipboardCount: privacyData?.clipboardCount || 0,
      summaryOnly: !!privacyData?.summaryOnly,
    };

    const privacyList = Array.isArray(privacyData?.list) ? privacyData.list : [];
    const normalizedPrivacy = privacyList.map((item, idx) => ({
      event_id: item?.id != null ? `privacy-${item.id}` : `privacy-${idx}`,
      employee_id: item?.userId || item?.username || item?.employeeId || '-',
      department: '-',
      ai_service: item?.source ? `privacy:${item.source}` : 'privacy-shield',
      is_anomaly: true,
      risk_level: 'high',
      anomaly_score: null,
      created_at: item?.eventTime || item?.createTime || null,
      description: `隐私盾告警 ${item?.matchedTypes || ''} ${item?.contentMasked || ''}`.trim(),
      source_tag: 'privacy',
    }));

    const normalizedAnomaly = Array.isArray(anomalyData?.events)
      ? anomalyData.events.map((item) => normalizeAnomalyEvent(item))
      : [];

    const mergedEvents = [...normalizedAnomaly, ...normalizedPrivacy].sort((a, b) => {
      const ta = new Date(String(a?.created_at || '').replace(' ', 'T')).getTime();
      const tb = new Date(String(b?.created_at || '').replace(' ', 'T')).getTime();
      return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
    });

    anomalySummaryOnly.value = !!anomalyData?.summaryOnly;
    if (anomalySummaryOnly.value) {
      const totalMerged = Number(anomalyData?.total || 0) + Number(privacySummary.value.total || 0);
      const anomalyMerged = Number(anomalyData?.anomalyCount || 0) + Number(privacySummary.value.total || 0);
      anomalySummary.value = {
        total: totalMerged,
        anomalyCount: anomalyMerged,
        normalCount: Math.max(0, totalMerged - anomalyMerged),
        anomalyRate: totalMerged > 0 ? anomalyMerged / totalMerged : 0,
      };
      anomalyTotalRecords.value = totalMerged;
      anomalyEvents.value = [];
      return;
    }

    const scopedEvents = isPersonalView.value
      ? mergedEvents.filter((item) => isCurrentUserEvent(item?.employee_id))
      : mergedEvents;
    const roleBoundEvents = scopedEvents.filter(hasBoundRole);

    anomalyTotalRecords.value = roleBoundEvents.length;
    anomalyEvents.value = roleBoundEvents;
  } catch (error) {
    anomalyTotalRecords.value = 0;
    anomalyEvents.value = [];
    ElMessage.error(error?.message || '加载异常行为失败');
  } finally {
    anomalyLoading.value = false;
  }
}

function handleAnomalyPageChange(page) {
  anomalyQuery.value.page = page;
}

function handleAnomalySizeChange(size) {
  anomalyQuery.value.pageSize = size;
  anomalyQuery.value.page = 1;
}

async function loadGovernanceSnapshot() {
  try {
    const data = await alertCenterApi.threatOverview({ windowHours: 168 });
    const byType = data?.byType || {};
    const summary = data?.summary || {};
    const dedupe = data?.dedupe || {};
    governanceSnapshot.value = {
      anomaly: Number(byType.anomaly || 0),
      privacy: Number(byType.privacy || 0),
      pending: Number(summary.pending || 0),
      collapsed: Number(dedupe.collapsed || 0),
      note: `口径：${dedupe.caliber || 'governance_event_dedup_chain_v1'}（与首页一致）`,
    };
  } catch {
    governanceSnapshot.value = {
      ...governanceSnapshot.value,
      note: '口径快照拉取失败，已保留当前页原始统计',
    };
  }
}

async function refreshAll() {
  await Promise.all([loadAnomalyStatus(), loadAnomaly(), loadGovernanceSnapshot()]);
}

onMounted(async () => {
  await ensureUserDirectory();
  await refreshAll();
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

.caliber-strip {
  margin-bottom: 14px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.caliber-strip strong {
  color: var(--color-text);
}

.caliber-text {
  color: var(--color-text-soft);
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
