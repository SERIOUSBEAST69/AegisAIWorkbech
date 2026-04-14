<template>
  <div class="monitor-page">
    <div class="page-header scene-block card-glass">
      <div>
        <div class="page-eyebrow">EMPLOYEE AI GOVERNANCE</div>
        <h1>AI使用合规监控</h1>
        <p>聚焦员工AI使用合规态势，融合异常行为、隐私告警与模拟演练联动视图。</p>
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

    <section class="simulation-lab">
      <div class="sim-head">
        <div>
          <h3>员工AI异常行为监控</h3>
          <p>基于 CircularGallery 的员工卡片流，按治理部门分组展示；异常触发后部门与角色联动高亮告警。</p>
        </div>
        <div class="sim-actions">
          <el-button type="danger" plain :loading="simulationTriggering" @click="triggerComplianceSimulation">触发异常告警</el-button>
          <el-tag type="danger" effect="dark" v-if="activeSimulationMeta">
            当前告警：{{ activeSimulationMeta.employeeLabel }} / {{ activeSimulationMeta.department }} · {{ activeSimulationMeta.role }}
          </el-tag>
          <el-tag type="info" effect="plain">员工卡 {{ displayedGalleryCards.length }} / {{ galleryCards.length }}</el-tag>
          <el-tag type="success" effect="plain">部门 {{ galleryDepartmentCount }}</el-tag>
        </div>
      </div>
      <div v-if="departmentCards.length" class="sim-dept-strip">
        <button
          type="button"
          class="sim-dept-all"
          :class="{ active: activeDepartmentKey === '' }"
          @click="setDepartmentFilter('')"
        >全部部门（{{ galleryCards.length }}）</button>

        <div class="sim-dept-grid">
          <button
            v-for="dept in departmentCards"
            :key="dept.key"
            type="button"
            class="sim-dept-card"
            :class="[
              { active: activeDepartmentKey === dept.key },
              dept.priorityTier === 'top' ? 'dept-card-tier-top' : (dept.priorityTier === 'mid' ? 'dept-card-tier-mid' : 'dept-card-tier-base'),
              dept.anomalyCount > 0 ? 'dept-card-alert' : ''
            ]"
            @click="toggleDepartmentCard(dept.key)"
          >
            <div class="dept-card-head">
              <strong>{{ dept.department }}</strong>
              <span class="dept-card-metric">{{ dept.totalCount }} / {{ dept.anomalyCount }}</span>
            </div>
            <div class="dept-role-tags">
              <span
                v-for="role in (isDepartmentExpanded(dept.key) ? dept.roles : dept.roles.slice(0, 3))"
                :key="`${dept.key}-${role.code}`"
                class="dept-role-tag"
                :class="{ alert: role.anomalyCount > 0, pulse: role.anomalyCount > 0 && dept.anomalyCount > 0 }"
              >
                {{ role.label }}
              </span>
            </div>
            <p class="dept-card-hint">{{ isDepartmentExpanded(dept.key) ? '收起角色' : `展开角色（${dept.roles.length}）` }}</p>
          </button>
        </div>
      </div>
      <div v-if="!displayedGalleryCards.length" class="sim-empty">当前筛选分组暂无可用于模拟的员工卡片</div>
      <div v-else class="sim-canvas-wrap">
        <CircularGalleryReactHost :items="displayedGalleryCards" :active-key="activeGalleryCardKey" :show-titles="false" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { alertCenterApi } from '../api/alertCenter';
import { privacyApi } from '../api/privacy';
import { useUserStore } from '../store/user';
import { hasAnyRole, isExecutive as isExecutiveRole } from '../utils/roleBoundary';
import { getUserDirectory } from '../utils/userDirectoryCache';
import CircularGalleryReactHost from '../components/anomaly/CircularGalleryReactHost.vue';

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
const anomalyTotalRecords = ref(0);
const modelReady = ref(false);
const privacySummary = ref({ total: 0, today: 0, extensionCount: 0, clipboardCount: 0, summaryOnly: false });
const activeSimulationMeta = ref(null);
const activeGalleryCardKey = ref('');
const activeDepartmentKey = ref('');
const expandedDepartmentKeys = ref([]);
const simulationTriggering = ref(false);
const MAX_GALLERY_CARDS = 36;

const governanceSnapshot = ref({
  anomaly: 0,
  privacy: 0,
  pending: 0,
  collapsed: 0,
  note: '口径：governance_event_dedup_chain_v1（与首页一致）',
});

const DEPARTMENT_ROLE_LAYOUT = [
  {
    key: 'governance-center',
    label: '治理中心',
    priorityTier: 'top',
    order: 1,
    roles: ['ADMIN', 'ADMIN_OPS', 'ADMIN_REVIEWER'],
  },
  {
    key: 'executive-management',
    label: '经营管理部',
    priorityTier: 'top',
    order: 2,
    roles: ['EXECUTIVE', 'EXECUTIVE_COMPLIANCE', 'EXECUTIVE_OVERVIEW'],
  },
  {
    key: 'data-governance',
    label: '数据治理部',
    priorityTier: 'mid',
    order: 3,
    roles: ['DATA_ADMIN', 'DATA_ADMIN_APPROVER', 'DATA_ADMIN_MAINTAINER'],
  },
  {
    key: 'secops-center',
    label: '安全运营中心',
    priorityTier: 'mid',
    order: 4,
    roles: ['SECOPS', 'SECOPS_RESPONDER'],
  },
  {
    key: 'model-platform',
    label: '模型平台组',
    priorityTier: 'mid',
    order: 5,
    roles: ['AI_BUILDER', 'AI_BUILDER_AUDITOR', 'AI_BUILDER_PROMPT'],
  },
  {
    key: 'business-innovation',
    label: '业务创新部',
    priorityTier: 'base',
    order: 6,
    roles: ['BUSINESS_OWNER', 'BUSINESS_OWNER_APPROVER', 'BUSINESS_OWNER_REVIEWER'],
  },
  {
    key: 'frontline-business',
    label: '业务一线',
    priorityTier: 'base',
    order: 7,
    roles: ['EMPLOYEE'],
  },
];

const ROLE_DEPARTMENT_INDEX = (() => {
  const roleMap = new Map();
  for (const dept of DEPARTMENT_ROLE_LAYOUT) {
    dept.roles.forEach((roleCode, roleIndex) => {
      roleMap.set(roleCode, {
        key: dept.key,
        label: dept.label,
        priorityTier: dept.priorityTier,
        order: dept.order,
        roleIndex,
      });
    });
  }
  return roleMap;
})();

function normalizeRoleCode(value) {
  return String(value || '').trim().toUpperCase();
}

function roleDescriptor(employee) {
  if (Array.isArray(employee?.roleCodes) && employee.roleCodes.length > 0) {
    const primary = normalizeRoleCode(employee.roleCodes[0]);
    if (primary) return primary;
  }
  const normalized = normalizeRoleCode(employee?.roleCode || employee?.role);
  return normalized || '-';
}

function resolveDepartmentByRoleOrSource(roleCode, sourceDepartment) {
  const fromRole = ROLE_DEPARTMENT_INDEX.get(normalizeRoleCode(roleCode));
  if (fromRole) {
    return {
      key: fromRole.key,
      label: fromRole.label,
      order: fromRole.order,
      priorityTier: fromRole.priorityTier,
      roleOrder: fromRole.roleIndex,
    };
  }
  const fallbackLabel = String(sourceDepartment || '未分配部门').trim() || '未分配部门';
  return {
    key: `custom-${fallbackLabel}`,
    label: fallbackLabel,
    order: 999,
    priorityTier: 'base',
    roleOrder: 999,
  };
}

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

const employeeRiskMap = computed(() => {
  const map = new Map();
  for (const item of anomalyEvents.value) {
    const key = String(item?.employee_id || item?.employeeId || item?.userId || item?.username || '').toLowerCase();
    if (!key) continue;
    const nextLevel = normalizeRiskLevel(item);
    const previous = map.get(key) || { risk: 'low', weight: 0 };
    const nextWeight = riskWeight(nextLevel);
    if (nextWeight >= previous.weight) {
      map.set(key, { risk: nextLevel, weight: nextWeight });
    }
  }
  return map;
});

const directoryEmployees = computed(() => {
  const seen = new Set();
  const employees = [];
  for (const candidate of userDirectory.value.values()) {
    if (!candidate) continue;
    const idPart = candidate.id != null ? String(candidate.id) : '';
    const usernamePart = String(candidate.username || '').toLowerCase();
    const uniqueKey = `${idPart}|${usernamePart}`;
    if (!idPart && !usernamePart) continue;
    if (seen.has(uniqueKey)) continue;
    seen.add(uniqueKey);
    employees.push(candidate);
  }
  if (!isPersonalView.value) return employees;
  return employees.filter((candidate) => {
    if (currentUserId.value && String(candidate.id || '') === currentUserId.value) return true;
    if (currentUsername.value && String(candidate.username || '').toLowerCase() === currentUsername.value) return true;
    return false;
  });
});

const galleryCards = computed(() => {
  const fallbackFromEvents = [];
  if (!directoryEmployees.value.length) {
    const seen = new Set();
    for (const item of anomalyEvents.value) {
      const username = anomalyAccountName(item);
      const role = anomalyRole(item);
      const department = anomalyDepartment(item);
      const employeeId = anomalyAccountId(item);
      const unique = `${employeeId}|${String(username || '').toLowerCase()}`;
      if (seen.has(unique)) continue;
      seen.add(unique);
      fallbackFromEvents.push({ id: employeeId, username, roleCode: role, department });
    }
  }

  const source = directoryEmployees.value.length ? directoryEmployees.value : fallbackFromEvents;
  const cards = [];

  source.forEach((employee, index) => {
    const employeeName = String(employee?.username || `employee-${index + 1}`);
    const employeeId = employee?.id != null ? String(employee.id) : employeeName;
    const role = roleDescriptor(employee);
    const departmentMeta = resolveDepartmentByRoleOrSource(role, employee?.department);
    const riskEntry = employeeRiskMap.value.get(employeeId.toLowerCase())
      || employeeRiskMap.value.get(employeeName.toLowerCase())
      || { risk: 'low' };
    const risk = riskEntry.risk;
    cards.push({
      key: `${employeeId}::${employeeName}`,
      employeeId,
      employeeName,
      username: employeeName,
      department: departmentMeta.label,
      departmentKey: departmentMeta.key,
      departmentOrder: departmentMeta.order,
      priorityTier: departmentMeta.priorityTier,
      role,
      roleOrder: departmentMeta.roleOrder,
      risk,
      text: `${employeeName} · ${departmentMeta.label} · ${role}`,
      badge: `${departmentMeta.label} / ${role}`,
    });
  });

  const sorted = cards.sort((a, b) => {
    if (a.departmentOrder !== b.departmentOrder) return a.departmentOrder - b.departmentOrder;
    if (a.department !== b.department) return a.department.localeCompare(b.department, 'zh-CN');
    if (a.roleOrder !== b.roleOrder) return a.roleOrder - b.roleOrder;
    if (a.role !== b.role) return a.role.localeCompare(b.role, 'zh-CN');
    return a.employeeName.localeCompare(b.employeeName, 'zh-CN');
  });

  if (sorted.length <= MAX_GALLERY_CARDS) {
    return sorted;
  }

  return [...sorted]
    .sort((a, b) => {
      const riskGap = riskWeight(b.risk) - riskWeight(a.risk);
      if (riskGap !== 0) return riskGap;
      if (a.departmentOrder !== b.departmentOrder) return a.departmentOrder - b.departmentOrder;
      return a.employeeName.localeCompare(b.employeeName, 'zh-CN');
    })
    .slice(0, MAX_GALLERY_CARDS)
    .sort((a, b) => {
      if (a.departmentOrder !== b.departmentOrder) return a.departmentOrder - b.departmentOrder;
      if (a.department !== b.department) return a.department.localeCompare(b.department, 'zh-CN');
      if (a.roleOrder !== b.roleOrder) return a.roleOrder - b.roleOrder;
      if (a.role !== b.role) return a.role.localeCompare(b.role, 'zh-CN');
      return a.employeeName.localeCompare(b.employeeName, 'zh-CN');
    });
});

const departmentCards = computed(() => {
  const map = new Map();
  for (const card of galleryCards.value) {
    const current = map.get(card.departmentKey) || {
      key: card.departmentKey,
      department: card.department,
      totalCount: 0,
      anomalyCount: 0,
      order: card.departmentOrder,
      priorityTier: card.priorityTier,
      roleMap: new Map(),
    };
    current.totalCount += 1;
    const cardHasAnomaly = riskWeight(card.risk) >= 3;
    if (cardHasAnomaly) {
      current.anomalyCount += 1;
    }
    const roleCurrent = current.roleMap.get(card.role) || {
      code: card.role,
      label: card.role,
      count: 0,
      anomalyCount: 0,
      roleOrder: card.roleOrder,
    };
    roleCurrent.count += 1;
    if (cardHasAnomaly) {
      roleCurrent.anomalyCount += 1;
    }
    current.roleMap.set(card.role, roleCurrent);
    map.set(card.departmentKey, current);
  }

  return [...map.values()]
    .map((item) => ({
      key: item.key,
      department: item.department,
      totalCount: item.totalCount,
      anomalyCount: item.anomalyCount,
      order: item.order,
      priorityTier: item.priorityTier,
      roles: [...item.roleMap.values()]
        .sort((a, b) => {
          if (a.roleOrder !== b.roleOrder) return a.roleOrder - b.roleOrder;
          return a.label.localeCompare(b.label, 'zh-CN');
        }),
    }))
    .sort((a, b) => {
      if (a.order !== b.order) return a.order - b.order;
      return a.department.localeCompare(b.department, 'zh-CN');
    });
});

const galleryDepartmentCount = computed(() => departmentCards.value.length);

const displayedGalleryCards = computed(() => {
  if (!activeDepartmentKey.value) {
    return galleryCards.value;
  }
  return galleryCards.value.filter(card => card.departmentKey === activeDepartmentKey.value);
});

const highestRiskGalleryCard = computed(() => {
  let best = null;
  let bestWeight = 0;
  for (const card of displayedGalleryCards.value) {
    const weight = riskWeight(card?.risk);
    if (weight > bestWeight) {
      best = card;
      bestWeight = weight;
    }
  }
  return bestWeight >= 3 ? best : null;
});

watch(departmentCards, (list) => {
  const keys = new Set(list.map(item => item.key));
  if (activeDepartmentKey.value && !keys.has(activeDepartmentKey.value)) {
    activeDepartmentKey.value = '';
  }
  expandedDepartmentKeys.value = expandedDepartmentKeys.value.filter(key => keys.has(key));
});

watch(displayedGalleryCards, (list) => {
  const found = list.some(item => item.key === activeGalleryCardKey.value);
  if (!found) {
    activeGalleryCardKey.value = '';
  }
});

watch(highestRiskGalleryCard, (card) => {
  if (!card) return;
  const current = displayedGalleryCards.value.find(item => item.key === activeGalleryCardKey.value);
  if (current && riskWeight(current.risk) >= 3) return;

  activeGalleryCardKey.value = card.key;
  activeSimulationMeta.value = {
    seatId: card.key,
    employeeLabel: card.employeeName,
    severity: card.risk,
    department: card.department,
    role: card.role,
  };
}, { immediate: true });

function setDepartmentFilter(departmentKey = '') {
  activeDepartmentKey.value = String(departmentKey || '');
}

function isDepartmentExpanded(departmentKey) {
  return expandedDepartmentKeys.value.includes(departmentKey);
}

function toggleDepartmentCard(departmentKey) {
  const key = String(departmentKey || '');
  if (!key) return;
  activeDepartmentKey.value = key;
  if (expandedDepartmentKeys.value.includes(key)) {
    expandedDepartmentKeys.value = expandedDepartmentKeys.value.filter(item => item !== key);
    return;
  }
  expandedDepartmentKeys.value = [...expandedDepartmentKeys.value, key];
}

function riskWeight(level) {
  if (level === 'critical') return 4;
  if (level === 'high') return 3;
  if (level === 'medium') return 2;
  return 1;
}

function normalizeRiskLevel(item) {
  const level = String(item?.risk_level || item?.severity || '').toLowerCase();
  if (level.includes('critical') || level.includes('严重')) return 'critical';
  if (level.includes('high') || level.includes('高')) return 'high';
  if (level.includes('medium') || level.includes('中')) return 'medium';
  return item?.is_anomaly ? 'high' : 'low';
}

async function triggerComplianceSimulation() {
  if (simulationTriggering.value) {
    return;
  }
  simulationTriggering.value = true;
  const scenarios = ['CLIPBOARD_EXFIL', 'WINDOW_SWITCH_BURST', 'BULK_ACCESS_WITH_AI_ACTIVE'];
  const pickedScenario = scenarios[Math.floor(Math.random() * scenarios.length)];
  try {
    ElMessage.success(`本地异常仿真已启动：${pickedScenario}`);
  } catch (err) {
    ElMessage.error('异常模拟触发失败：' + (err?.message || '未知错误'));
  } finally {
    simulationTriggering.value = false;
  }

  const cards = displayedGalleryCards.value;
  const fallbackCard = {
    key: `synthetic-${Date.now()}`,
    employeeName: 'demo-employee',
    department: 'Security',
    role: 'Analyst',
    risk: 'critical',
  };
  const sample = cards.length ? cards[Math.floor(Math.random() * cards.length)] : fallbackCard;

  activeGalleryCardKey.value = sample.key;
  activeSimulationMeta.value = {
    seatId: sample.key,
    employeeLabel: sample.employeeName,
    severity: sample.risk,
    department: sample.department,
    role: sample.role,
  };
}

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
    userDirectory.value = await getUserDirectory();
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
  if (!user) {
    const fallback = String(ev?.roleCode || ev?.role || '').trim();
    return fallback || '-';
  }
  const primaryRoleCode = Array.isArray(user.roleCodes) && user.roleCodes.length > 0
    ? String(user.roleCodes[0] || '').trim()
    : '';
  if (primaryRoleCode) return primaryRoleCode;
  const singleRoleCode = String(user.roleCode || '').trim();
  return singleRoleCode || '-';
}

function hasBoundRole(ev) {
  // If user directory cannot be fetched (insufficient permission / transient network),
  // do not hide anomaly rows, otherwise the board appears as all-zero.
  if (userDirectory.value.size === 0) {
    return true;
  }
  const user = anomalyUser(ev);
  if (!user) return false;
  const roleCodes = Array.isArray(user.roleCodes)
    ? user.roleCodes.map(item => String(item || '').trim()).filter(Boolean)
    : [];
  if (roleCodes.length > 0) return true;
  const roleCode = String(user.roleCode || '').trim();
  return roleCode.length > 0;
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
          pageSize: 160,
        },
      }),
      privacyApi.listEvents({ page: 1, pageSize: 160 }),
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
  position: relative;
  overflow: hidden;
}

.monitor-page::before {
  content: '';
  position: absolute;
  inset: -20% -10% auto -10%;
  height: 65%;
  background:
    radial-gradient(circle at 22% 44%, rgba(40, 160, 255, 0.2), transparent 44%),
    radial-gradient(circle at 78% 36%, rgba(255, 44, 130, 0.16), transparent 42%);
  pointer-events: none;
  z-index: 0;
}

.page-header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid rgba(104, 185, 255, 0.3);
  border-radius: 12px;
  background: linear-gradient(140deg, rgba(8, 19, 38, 0.86), rgba(8, 14, 27, 0.86));
  box-shadow: 0 0 0 1px rgba(64, 144, 255, 0.16) inset;
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
  position: relative;
  z-index: 1;
  margin-bottom: 14px;
  padding: 10px 14px;
  border: 1px solid rgba(100, 176, 255, 0.24);
  border-radius: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
  background: rgba(6, 16, 31, 0.58);
}

.caliber-strip strong {
  color: var(--color-text);
}

.caliber-text {
  color: var(--color-text-soft);
}

.stats-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stat-card {
  border: 1px solid rgba(91, 176, 255, 0.2);
  border-radius: 10px;
  padding: 14px;
  background: linear-gradient(145deg, rgba(7, 20, 40, 0.72), rgba(9, 15, 29, 0.72));
  box-shadow: inset 0 0 14px rgba(45, 140, 255, 0.08);
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

.simulation-lab {
  position: relative;
  z-index: 1;
  border: 1px solid rgba(92, 188, 255, 0.36);
  border-radius: 14px;
  padding: 18px;
  margin-bottom: 14px;
  background:
    radial-gradient(circle at 18% 12%, rgba(124, 190, 255, 0.2), transparent 42%),
    radial-gradient(circle at 84% 86%, rgba(93, 146, 232, 0.16), transparent 40%),
    linear-gradient(160deg, rgba(8, 20, 39, 0.9), rgba(6, 14, 29, 0.92));
  box-shadow: 0 0 0 1px rgba(66, 169, 255, 0.15) inset, 0 18px 36px rgba(1, 7, 19, 0.5);
}

.simulation-lab::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 14px;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    rgba(168, 238, 255, 0.05) 0px,
    rgba(168, 238, 255, 0.05) 1px,
    transparent 1px,
    transparent 4px
  );
  opacity: 0.42;
}

.sim-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.sim-head h3 {
  margin: 0;
  font-size: 18px;
  color: #dff8ff;
  text-shadow: 0 0 16px rgba(84, 211, 255, 0.56);
}

.sim-head p {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.sim-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sim-dept-strip {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.sim-dept-all {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid rgba(122, 198, 255, 0.3);
  background: rgba(8, 24, 48, 0.66);
  color: #d5eeff;
  padding: 7px 12px;
  cursor: pointer;
  transition: border-color 140ms ease, transform 140ms ease, background 140ms ease;
}

.sim-dept-all:hover {
  transform: translateY(-1px);
  border-color: rgba(159, 219, 255, 0.5);
}

.sim-dept-all.active {
  background: rgba(24, 66, 118, 0.78);
  border-color: rgba(168, 226, 255, 0.66);
}

.sim-dept-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.sim-dept-card {
  appearance: none;
  text-align: left;
  border-radius: 12px;
  border: 1px solid rgba(125, 192, 255, 0.24);
  background: rgba(6, 20, 38, 0.65);
  padding: 10px;
  cursor: pointer;
  transition: transform 150ms ease, border-color 150ms ease, box-shadow 150ms ease;
}

.sim-dept-card:hover {
  transform: translateY(-1px);
  border-color: rgba(174, 228, 255, 0.5);
}

.sim-dept-card.active {
  border-color: rgba(149, 217, 255, 0.78);
  box-shadow: 0 0 0 1px rgba(120, 207, 255, 0.28) inset;
}

.dept-card-tier-top {
  transform-origin: center;
  scale: 1.03;
  background: linear-gradient(150deg, rgba(12, 40, 70, 0.84), rgba(10, 22, 44, 0.72));
}

.dept-card-tier-mid {
  background: linear-gradient(150deg, rgba(10, 30, 56, 0.8), rgba(9, 20, 40, 0.68));
}

.dept-card-tier-base {
  background: linear-gradient(150deg, rgba(8, 24, 44, 0.72), rgba(7, 18, 34, 0.62));
}

.dept-card-alert {
  border-color: rgba(255, 112, 112, 0.66);
  box-shadow: 0 0 0 1px rgba(255, 118, 118, 0.2) inset;
  background: linear-gradient(150deg, rgba(62, 18, 26, 0.72), rgba(28, 14, 30, 0.74));
}

.dept-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.dept-card-head strong {
  color: #e9f6ff;
}

.dept-card-metric {
  font-size: 12px;
  color: #ffd7d7;
  border: 1px solid rgba(255, 159, 159, 0.4);
  border-radius: 999px;
  padding: 2px 8px;
}

.dept-role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dept-role-tag {
  border-radius: 999px;
  border: 1px solid rgba(131, 208, 255, 0.35);
  padding: 2px 8px;
  font-size: 11px;
  color: #d4edff;
  background: rgba(9, 38, 66, 0.5);
}

.dept-role-tag.alert {
  border-color: rgba(255, 134, 134, 0.55);
  color: #ffd6d6;
  background: rgba(72, 25, 35, 0.6);
}

.dept-role-tag.pulse {
  animation: role-alert-pulse 1s ease-in-out infinite;
}

.dept-card-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: #9ec4da;
}

@keyframes role-alert-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 124, 124, 0.22);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(255, 124, 124, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 124, 124, 0);
  }
}

.sim-canvas-wrap {
  position: relative;
  min-height: 620px;
  height: clamp(560px, 70vh, 860px);
  border: 1px solid rgba(114, 196, 255, 0.3);
  border-radius: 12px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 12%, rgba(124, 190, 255, 0.12), transparent 42%),
    radial-gradient(circle at 84% 86%, rgba(93, 146, 232, 0.1), transparent 40%),
    linear-gradient(140deg, rgba(6, 13, 26, 0.74), rgba(7, 18, 34, 0.56));
}

.sim-canvas-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(10, 24, 45, 0.2) 0%, rgba(8, 20, 38, 0.08) 24%, rgba(5, 14, 28, 0.04) 100%);
  z-index: 1;
}

.sim-empty {
  border: 1px dashed rgba(134, 201, 255, 0.35);
  border-radius: 10px;
  color: var(--color-text-muted);
  text-align: center;
  padding: 36px 14px;
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

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .sim-dept-grid {
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
  .sim-head {
    flex-direction: column;
  }
  .sim-actions {
    justify-content: flex-start;
  }
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .sim-dept-grid {
    grid-template-columns: 1fr;
  }
}
</style>
