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
      <div class="stat-card stat-card-total">
        <div class="stat-head-row">
          <div class="stat-title">总事件数（已过滤空角色）</div>
          <div class="stat-kicker">EVENTS</div>
        </div>
        <div class="stat-value">{{ anomalyTotal }}</div>
        <div class="stat-foot">覆盖异常+隐私联动样本</div>
      </div>
      <div class="stat-card stat-card-danger">
        <div class="stat-head-row">
          <div class="stat-title">异常事件数</div>
          <div class="stat-kicker">ALERT</div>
        </div>
        <div class="stat-value danger">{{ anomalyCount }}</div>
        <div class="stat-foot">高危行为优先进入治理链路</div>
      </div>
      <div class="stat-card stat-card-rate">
        <div class="stat-head-row">
          <div class="stat-title">异常占比</div>
          <div class="stat-kicker">RATIO</div>
        </div>
        <div class="stat-value">{{ anomalyRateText }}</div>
        <div class="stat-meter">
          <i :style="{ width: `${anomalyRatePercent}%` }"></i>
        </div>
      </div>
      <div class="stat-card stat-card-ready" :class="{ 'stat-card-ready-off': !modelReady }">
        <div class="stat-head-row">
          <div class="stat-title">模型状态</div>
          <div class="stat-kicker">MODEL</div>
        </div>
        <div class="stat-value">{{ modelReady ? '就绪' : '未就绪' }}</div>
        <div class="stat-foot">{{ modelReady ? '可用于实时异常判别' : '待加载或待校验' }}</div>
      </div>
    </div>

    <section class="simulation-lab">
      <div class="sim-head">
        <div>
          <h3>员工AI异常行为监控</h3>
          <p>基于 CircularGallery 的员工卡片流，按治理部门分组展示；支持模拟演练与客户端真实上报叠加联动。</p>
        </div>
        <div class="sim-actions">
          <el-button type="danger" plain :loading="simulationTriggering" @click="triggerComplianceSimulation">触发异常告警（模拟）</el-button>
          <el-tag type="warning" effect="plain">真实检测与模拟演练同时显示</el-tag>
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
            <div class="dept-card-orbit" aria-hidden="true"></div>
            <div class="dept-card-head">
              <strong>{{ dept.department }}</strong>
              <span class="dept-card-metric">{{ dept.totalCount }} / {{ dept.anomalyCount }}</span>
            </div>
            <div class="dept-card-meta-row">
              <span class="dept-card-tier-badge">{{ departmentTierLabel(dept.priorityTier) }}</span>
              <span class="dept-card-ratio">异常占比 {{ departmentAnomalyRatio(dept) }}%</span>
            </div>
            <div class="dept-role-tags">
              <span
                v-for="role in (isDepartmentExpanded(dept.key) ? dept.roles : dept.roles.slice(0, 3))"
                :key="`${dept.key}-${role.code}`"
                class="dept-role-tag"
                :class="{ alert: role.anomalyCount > 0 }"
              >
                {{ role.label }}
              </span>
            </div>
            <div class="dept-card-meter" aria-hidden="true">
              <i :style="{ width: `${departmentAnomalyRatio(dept)}%` }"></i>
            </div>
            <p class="dept-card-hint">{{ isDepartmentExpanded(dept.key) ? '收起角色' : `展开角色（${dept.roles.length}）` }}</p>
          </button>
        </div>
      </div>
      <div v-if="!displayedGalleryCards.length" class="sim-empty">当前筛选分组暂无可用于模拟的员工卡片</div>
      <div v-else class="sim-canvas-wrap">
        <div v-if="showPrismLayer" class="sim-prism-layer" aria-hidden="true">
          <PrismReactHost v-if="visualLayersReady" />
        </div>
        <div class="sim-gallery-layer">
          <CircularGalleryReactHost v-if="visualLayersReady" :items="displayedGalleryCards" :active-key="activeGalleryCardKey" :show-titles="false" />
        </div>
      </div>
    </section>

    <el-card class="table-card card-glass scene-block" shadow="never">
      <div class="panel-head">
        <div>
          <div class="card-header">异常行为事件明细</div>
          <p class="panel-subtitle">口径：当前视图去重后事件明细，字段统一中文展示，健康状态可直接筛读。</p>
        </div>
      </div>

      <el-table
        :data="pagedAnomalyTableRows"
        border
        stripe
        size="small"
        empty-text="暂无事件明细"
        style="width: 100%"
      >
        <el-table-column type="index" label="序号" width="72" />
        <el-table-column prop="accountName" label="账号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="department" label="部门" min-width="130" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" min-width="130" show-overflow-tooltip />
        <el-table-column prop="aiService" label="AI服务" min-width="140" show-overflow-tooltip />
        <el-table-column prop="riskLevelText" label="风险等级" width="110">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.riskTagType">{{ scope.row.riskLevelText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="healthStatus" label="健康状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.healthStatus === '健康' ? 'success' : 'danger'" effect="dark">
              {{ scope.row.healthStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventSource" label="事件来源" width="110" />
        <el-table-column prop="eventTime" label="事件时间" min-width="168" />
        <el-table-column prop="description" label="事件描述" min-width="260" show-overflow-tooltip />
      </el-table>

      <div class="table-pagination-wrap">
        <el-pagination
          v-model:current-page="tablePagination.page"
          v-model:page-size="tablePagination.pageSize"
          :total="anomalyTableRows.length"
          :page-sizes="[10, 20, 30, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          small
          background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { alertCenterApi } from '../api/alertCenter';
import { privacyApi } from '../api/privacy';
import { useUserStore } from '../store/user';
import { hasAnyRole, isExecutive as isExecutiveRole } from '../utils/roleBoundary';
import { getUserDirectory } from '../utils/userDirectoryCache';
import CircularGalleryReactHost from '../components/anomaly/CircularGalleryReactHost.vue';
import PrismReactHost from '../components/anomaly/PrismReactHost.vue';

const userStore = useUserStore();
const isExecutive = computed(() => isExecutiveRole(userStore.userInfo));
const isCompanyScopeView = computed(() => hasAnyRole(userStore.userInfo, ['ADMIN', 'ADMIN_REVIEWER', 'SECOPS']));
const isDepartmentScopeView = computed(() => hasAnyRole(userStore.userInfo, ['BUSINESS_OWNER']));
const currentUserId = computed(() => (userStore.userInfo?.id != null ? String(userStore.userInfo.id) : ''));
const currentUsername = computed(() => String(userStore.userInfo?.username || '').toLowerCase());
const currentDepartment = computed(() => String(userStore.userInfo?.department || '').trim());

const userDirectory = ref(new Map());
let userDirectoryRequest = null;
const anomalyLoading = ref(false);
const pendingAnomalyReload = ref(false);
const anomalyEvents = ref([]);
const anomalySummaryOnly = ref(false);
const anomalySummary = ref({ total: 0, anomalyCount: 0, normalCount: 0, anomalyRate: 0 });
const anomalyTotalRecords = ref(0);
const modelReady = ref(false);
const simulationTriggering = ref(false);
const simulatedAnomalyEvents = ref([]);
const visualLayersReady = ref(false);
let visualMountTimer = null;
const privacySummary = ref({ total: 0, today: 0, extensionCount: 0, clipboardCount: 0, summaryOnly: false });
const activeSimulationMeta = ref(null);
const activeGalleryCardKey = ref('');
const activeDepartmentKey = ref('');
const expandedDepartmentKeys = ref([]);
// Keep one lightweight visual layer by default to avoid page-open jank.
const showPrismLayer = true;
const MAX_GALLERY_CARDS = 12;
const tablePagination = ref({
  page: 1,
  pageSize: 10,
});

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

const anomalyRatePercent = computed(() => {
  if (anomalySummaryOnly.value) {
    const rate = Number(anomalySummary.value.anomalyRate || 0) * 100;
    return Math.max(0, Math.min(100, Number.isFinite(rate) ? rate : 0));
  }
  if (!anomalyEvents.value.length) return 0;
  const rate = (anomalyCount.value / anomalyEvents.value.length) * 100;
  return Math.max(0, Math.min(100, Number.isFinite(rate) ? rate : 0));
});

const anomalyTableRows = computed(() => {
  const dedup = new Map();
  for (const item of anomalyEvents.value) {
    const accountId = String(anomalyAccountId(item) || '-');
    const accountName = String(anomalyAccountName(item) || '-');
    const department = String(anomalyDepartment(item) || '-');
    const role = String(anomalyRole(item) || '-');
    const aiService = String(item?.ai_service || '-');
    const sourceTag = String(item?.source_tag || 'anomaly').toLowerCase();
    const eventSource = sourceTagToLabel(sourceTag);
    const riskLevel = normalizeRiskLevel(item);
    const riskLevelText = riskLevel === 'critical' ? '严重' : riskLevel === 'high' ? '高' : riskLevel === 'medium' ? '中' : '低';
    const riskTagType = riskLevel === 'critical' || riskLevel === 'high' ? 'danger' : riskLevel === 'medium' ? 'warning' : 'success';
    const healthStatus = item?.is_anomaly || riskWeight(riskLevel) >= 3 ? '异常' : '健康';
    const description = sanitizeText(item?.description || (sourceTag === 'privacy' ? '隐私防护告警事件' : '员工行为检测事件'));
    const eventTimeRaw = String(item?.created_at || '');
    const eventTime = formatDate(eventTimeRaw);
    const companyId = String(anomalyCompany(item) || '-');

    const uniqueKey = String(item?.event_id || '').trim() || [
      accountId,
      accountName.toLowerCase(),
      department,
      role,
      aiService,
      sourceTag,
      riskLevel,
      description,
      eventTimeRaw,
      companyId,
    ].join('|').toLowerCase();

    if (dedup.has(uniqueKey)) {
      continue;
    }

    const eventTs = new Date(eventTimeRaw.replace(' ', 'T')).getTime();
    dedup.set(uniqueKey, {
      accountName,
      department,
      role,
      aiService,
      riskLevelText,
      riskTagType,
      healthStatus,
      eventSource,
      eventTime,
      description,
      __eventTs: Number.isNaN(eventTs) ? 0 : eventTs,
    });
  }

  return [...dedup.values()]
    .sort((a, b) => b.__eventTs - a.__eventTs)
    .map(({ __eventTs, ...rest }) => rest);
});

const pagedAnomalyTableRows = computed(() => {
  const page = Math.max(1, Number(tablePagination.value.page) || 1);
  const pageSize = Math.max(1, Number(tablePagination.value.pageSize) || 10);
  const start = (page - 1) * pageSize;
  return anomalyTableRows.value.slice(start, start + pageSize);
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

  const currentUser = userStore.userInfo || null;
  const currentUsernameValue = String(currentUser?.username || '').toLowerCase();
  const currentUserIdValue = currentUser?.id != null ? String(currentUser.id) : '';
  if (currentUser && currentUsernameValue) {
    const hasCurrent = employees.some((candidate) => {
      const sameUsername = String(candidate?.username || '').toLowerCase() === currentUsernameValue;
      const sameUserId = currentUserIdValue && String(candidate?.id || '') === currentUserIdValue;
      return sameUsername || sameUserId;
    });
    if (!hasCurrent) {
      employees.push({
        id: currentUserIdValue || currentUsernameValue,
        username: currentUser.username,
        roleCode: currentUser.roleCode,
        roleCodes: Array.isArray(currentUser.roleCodes) ? currentUser.roleCodes : (currentUser.roleCode ? [currentUser.roleCode] : []),
        department: currentUser.department || '-',
      });
    }
  }

  if (isCompanyScopeView.value) return employees;
  if (isDepartmentScopeView.value) {
    const dept = currentDepartment.value;
    if (!dept) return [];
    return employees.filter((candidate) => String(candidate?.department || '').trim() === dept);
  }
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

  const ranked = [...sorted]
    .sort((a, b) => {
      const riskGap = riskWeight(b.risk) - riskWeight(a.risk);
      if (riskGap !== 0) return riskGap;
      if (a.departmentOrder !== b.departmentOrder) return a.departmentOrder - b.departmentOrder;
      return a.employeeName.localeCompare(b.employeeName, 'zh-CN');
    });

  let selected = ranked.slice(0, MAX_GALLERY_CARDS);
  const currentCandidate = sorted.find((card) => {
    const sameUsername = currentUsername.value && String(card?.username || '').toLowerCase() === currentUsername.value;
    const sameUserId = currentUserId.value && String(card?.employeeId || '') === currentUserId.value;
    return sameUsername || sameUserId;
  });

  if (currentCandidate && !selected.some(item => item.key === currentCandidate.key)) {
    selected = selected.slice(0, Math.max(0, MAX_GALLERY_CARDS - 1));
    selected.push(currentCandidate);
  }

  return selected
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

const currentUserRiskCard = computed(() => {
  const username = currentUsername.value;
  const userId = currentUserId.value;
  if (!username && !userId) {
    return null;
  }
  let best = null;
  let bestWeight = 0;
  for (const card of displayedGalleryCards.value) {
    const sameUsername = username && String(card?.username || '').toLowerCase() === username;
    const sameUserId = userId && String(card?.employeeId || '') === userId;
    if (!sameUsername && !sameUserId) {
      continue;
    }
    const weight = riskWeight(card?.risk);
    if (weight >= 3 && weight >= bestWeight) {
      best = card;
      bestWeight = weight;
    }
  }
  return best;
});

const currentUserEventRisk = computed(() => {
  const username = currentUsername.value;
  const userId = currentUserId.value;
  if (!username && !userId) {
    return { level: 'low', weight: 0 };
  }

  let bestLevel = 'low';
  let bestWeight = 0;
  for (const item of anomalyEvents.value) {
    const eventUser = String(item?.employee_id || item?.userId || item?.username || '').toLowerCase();
    if (!eventUser) {
      continue;
    }
    const sameUsername = username && eventUser === username;
    const sameUserId = userId && eventUser === userId.toLowerCase();
    if (!sameUsername && !sameUserId) {
      continue;
    }
    const level = normalizeRiskLevel(item);
    const weight = riskWeight(level);
    if (weight >= bestWeight) {
      bestWeight = weight;
      bestLevel = level;
    }
  }

  return {
    level: bestLevel,
    weight: bestWeight,
  };
});

const preferredAlertGalleryCard = computed(() => currentUserRiskCard.value || highestRiskGalleryCard.value);

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
  if (list.length > 0) {
    scheduleVisualLayerMount(180);
  }
});

watch(anomalyTableRows, (rows) => {
  const total = rows.length;
  const pageSize = Math.max(1, Number(tablePagination.value.pageSize) || 10);
  const maxPage = Math.max(1, Math.ceil(total / pageSize));
  if (tablePagination.value.page > maxPage) {
    tablePagination.value.page = maxPage;
  }
}, { immediate: true });

watch(() => tablePagination.value.pageSize, () => {
  tablePagination.value.page = 1;
});

watch(preferredAlertGalleryCard, (card) => {
  if (currentUserEventRisk.value.weight >= 3) {
    const username = currentUsername.value;
    const userId = currentUserId.value;
    const currentCard = displayedGalleryCards.value.find((item) => {
      const sameUsername = username && String(item?.username || '').toLowerCase() === username;
      const sameUserId = userId && String(item?.employeeId || '') === userId;
      return sameUsername || sameUserId;
    }) || currentUserRiskCard.value;

    if (currentCard?.key) {
      activeGalleryCardKey.value = currentCard.key;
    }

    activeSimulationMeta.value = {
      seatId: currentCard?.key || activeGalleryCardKey.value || 'self',
      employeeLabel: currentCard?.employeeName || String(userStore.userInfo?.username || '当前账号'),
      severity: currentUserEventRisk.value.level,
      department: currentCard?.department || String(userStore.userInfo?.department || '-'),
      role: currentCard?.role || String(userStore.userInfo?.roleCode || '-'),
    };
    return;
  }

  if (!card) return;
  const current = displayedGalleryCards.value.find(item => item.key === activeGalleryCardKey.value);
  if (current && riskWeight(current.risk) >= 3 && current.key === card.key) return;

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

function departmentAnomalyRatio(dept) {
  const total = Math.max(0, Number(dept?.totalCount || 0));
  if (!total) return 0;
  const anomaly = Math.max(0, Number(dept?.anomalyCount || 0));
  return Math.max(0, Math.min(100, Math.round((anomaly / total) * 100)));
}

function departmentTierLabel(tier) {
  if (tier === 'top') return '核心治理';
  if (tier === 'mid') return '重点管控';
  return '基础覆盖';
}

function sourceTagToLabel(sourceTag) {
  const tag = String(sourceTag || '').toLowerCase();
  if (tag === 'privacy') return '隐私告警';
  if (tag === 'simulation') return '模拟演练';
  if (tag === 'shadow_ai_browser') return '浏览器告警';
  if (tag === 'shadow_ai_network') return '网络告警';
  if (tag === 'shadow_ai_process') return '进程告警';
  if (tag.startsWith('shadow_ai')) return '影子AI告警';
  return '异常行为';
}

function normalizeRiskLevel(item) {
  const level = String(item?.risk_level || item?.severity || '').toLowerCase();
  if (level.includes('critical') || level.includes('严重')) return 'critical';
  if (level.includes('high') || level.includes('高')) return 'high';
  if (level.includes('medium') || level.includes('中')) return 'medium';
  return item?.is_anomaly ? 'high' : 'low';
}

function buildSyntheticAnomalyEvent(sample = null) {
  const now = new Date();
  const base = sample || {
    key: `sim-${now.getTime()}`,
    employeeName: currentUsername.value || 'demo-employee',
    department: currentDepartment.value || '治理中心',
    role: String(userStore.userInfo?.roleCode || 'EMPLOYEE'),
  };

  const scenarios = [
    { eventType: 'CLIPBOARD_EXFIL', service: 'clipboard-guard', risk: 'critical', description: '模拟：检测到敏感内容复制并疑似外传' },
    { eventType: 'WINDOW_SWITCH_BURST', service: 'desktop-monitor', risk: 'high', description: '模拟：检测到短时窗口高频切换与可疑操作链' },
    { eventType: 'BULK_ACCESS_WITH_AI_ACTIVE', service: 'policy-hub', risk: 'high', description: '模拟：检测到AI活跃期间批量访问敏感资源' },
  ];
  const picked = scenarios[Math.floor(Math.random() * scenarios.length)];

  return {
    event_id: `simulation-${now.getTime()}`,
    employee_id: base.employeeId || base.employeeName,
    username: base.employeeName,
    department: base.department,
    role: base.role,
    ai_service: picked.service,
    is_anomaly: true,
    risk_level: picked.risk,
    anomaly_score: picked.risk === 'critical' ? 0.99 : 0.92,
    created_at: now.toISOString().replace('T', ' ').slice(0, 19),
    description: `${picked.description}（${picked.eventType}）`,
    source_tag: 'simulation',
  };
}

async function triggerComplianceSimulation() {
  if (simulationTriggering.value) {
    return;
  }
  simulationTriggering.value = true;
  try {
    const cards = displayedGalleryCards.value;
    const fallbackCard = {
      key: `synthetic-${Date.now()}`,
      employeeName: currentUsername.value || 'demo-employee',
      employeeId: currentUserId.value || (currentUsername.value || 'demo-employee'),
      department: currentDepartment.value || '治理中心',
      role: String(userStore.userInfo?.roleCode || 'EMPLOYEE'),
      risk: 'critical',
    };
    const sample = cards.length ? cards[Math.floor(Math.random() * cards.length)] : fallbackCard;
    const syntheticEvent = buildSyntheticAnomalyEvent(sample);
    simulatedAnomalyEvents.value = [syntheticEvent, ...simulatedAnomalyEvents.value].slice(0, 30);

    activeGalleryCardKey.value = sample.key;
    activeSimulationMeta.value = {
      seatId: sample.key,
      employeeLabel: sample.employeeName,
      severity: syntheticEvent.risk_level,
      department: sample.department,
      role: sample.role,
    };
    ElMessage.success('已触发模拟异常告警，真实事件数据保持不变');
  } catch (err) {
    ElMessage.error('异常模拟触发失败：' + (err?.message || '未知错误'));
  } finally {
    simulationTriggering.value = false;
  }
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
  if (userDirectoryRequest) {
    await userDirectoryRequest;
    return;
  }
  userDirectoryRequest = (async () => {
    try {
      userDirectory.value = await getUserDirectory();
    } catch {
      userDirectory.value = new Map();
    } finally {
      userDirectoryRequest = null;
    }
  })();
  try {
    await userDirectoryRequest;
  } catch {
    // Ignore; fallback rendering path will continue with unresolved user mapping.
  }
}

function scheduleVisualLayerMount(delayMs = 650) {
  if (visualLayersReady.value) {
    return;
  }
  if (visualMountTimer) {
    window.clearTimeout(visualMountTimer);
  }
  const mount = () => {
    visualLayersReady.value = true;
    visualMountTimer = null;
  };

  const safeDelay = Math.max(0, Number(delayMs) || 0);
  visualMountTimer = window.setTimeout(() => {
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(() => mount(), { timeout: 420 });
    } else {
      mount();
    }
  }, safeDelay);
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
  if (!user) {
    const eventUser = String(ev?.employee_id || ev?.userId || ev?.username || '').toLowerCase();
    if (currentUsername.value && eventUser === currentUsername.value) {
      return true;
    }
    if (currentUserId.value && eventUser === currentUserId.value.toLowerCase()) {
      return true;
    }
    return false;
  }
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
    source_tag: item.source_tag || item.sourceTag || 'anomaly',
    employee_id: item.employee_id || item.employeeId || item.userId || item.username || item.osUsername || '-',
    department: item.department || item.dept || '-',
    ai_service: item.ai_service || item.aiService || item.source || item.hostname || '-',
    anomaly_score: item.anomaly_score ?? item.anomalyScore ?? null,
    risk_level: item.risk_level || item.riskLevel || item.level || '-',
    created_at: item.created_at || item.event_time || item.eventTime || null,
    description: item.description || item.title || item.message || '',
  };
}

function parseClientDiscoveredServices(value) {
  if (Array.isArray(value)) {
    return value;
  }
  const raw = String(value || '').trim();
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function normalizeShadowSource(value) {
  const text = String(value || '').toLowerCase();
  if (text.includes('browser')) return 'browser';
  if (text.includes('network')) return 'network';
  if (text.includes('process')) return 'process';
  return '';
}

function sourceDisplayLabel(sourceKey) {
  if (sourceKey === 'browser') return '浏览器历史';
  if (sourceKey === 'network') return '网络连接';
  if (sourceKey === 'process') return '本机进程';
  return '多维扫描';
}

function summarizeShadowSources(services) {
  const sourceCount = new Map([
    ['browser', 0],
    ['network', 0],
    ['process', 0],
  ]);
  for (const service of services) {
    const parts = String(service?.source || '')
      .split('|')
      .map(item => normalizeShadowSource(item))
      .filter(Boolean);
    if (!parts.length) {
      continue;
    }
    const uniqueSources = [...new Set(parts)];
    uniqueSources.forEach((source) => {
      sourceCount.set(source, Number(sourceCount.get(source) || 0) + 1);
    });
  }
  return [...sourceCount.entries()].filter(([, count]) => Number(count) > 0);
}

function normalizeShadowAiClientEvents(clientRows = []) {
  const events = [];
  clientRows.forEach((row, rowIndex) => {
    const services = parseClientDiscoveredServices(row?.discoveredServices);
    const sources = summarizeShadowSources(services);
    const employeeId = String(row?.osUsername || row?.username || row?.userId || '-').trim() || '-';
    const hostname = String(row?.hostname || 'unknown-host').trim() || 'unknown-host';
    const risk = String(row?.riskLevel || 'low').toLowerCase();
    const aiCount = Number(row?.shadowAiCount || services.length || 0);
    const createdAt = row?.scanTime || row?.updateTime || row?.createTime || null;
    const clientId = String(row?.clientId || `client-${rowIndex}`);
    const isAnomaly = riskWeight(risk) >= 2 || aiCount > 0;

    if (!sources.length) {
      events.push({
        event_id: `shadow-ai-${clientId}-${rowIndex}`,
        employee_id: employeeId,
        department: '-',
        ai_service: 'shadow-scan',
        is_anomaly: isAnomaly,
        risk_level: risk,
        anomaly_score: null,
        created_at: createdAt,
        description: `客户端影子AI扫描告警：设备 ${hostname} 命中 ${aiCount} 个可疑服务`,
        source_tag: 'shadow_ai',
      });
      return;
    }

    sources.forEach(([sourceKey, count], sourceIndex) => {
      events.push({
        event_id: `shadow-ai-${clientId}-${sourceKey}-${sourceIndex}`,
        employee_id: employeeId,
        department: '-',
        ai_service: `shadow-scan:${sourceKey}`,
        is_anomaly: isAnomaly,
        risk_level: risk,
        anomaly_score: null,
        created_at: createdAt,
        description: `客户端${sourceDisplayLabel(sourceKey)}告警：设备 ${hostname} 命中 ${count} 个可疑服务`,
        source_tag: `shadow_ai_${sourceKey}`,
      });
    });
  });
  return events;
}

function isCurrentUserEvent(userValue) {
  if (isCompanyScopeView.value) return true;
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

function isCurrentDepartmentEvent(item) {
  const department = currentDepartment.value;
  if (!department) return false;
  const user = anomalyUser(item);
  const mappedDepartment = String(user?.department || '').trim();
  const eventDepartment = String(item?.department || '').trim();
  return mappedDepartment === department || eventDepartment === department;
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
  if (anomalyLoading.value) {
    pendingAnomalyReload.value = true;
    return;
  }
  anomalyLoading.value = true;
  try {
    const [anomalyData, privacyData, clientRowsRaw] = await Promise.all([
      request.get('/anomaly/events', {
        params: {
          page: 1,
          pageSize: 120,
        },
      }),
      privacyApi.listEvents({ page: 1, pageSize: 120 }),
      request.get('/client/list').catch(() => []),
    ]);

    const clientRows = Array.isArray(clientRowsRaw) ? clientRowsRaw : [];

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

    const normalizedShadowAi = normalizeShadowAiClientEvents(clientRows);

    const mergedEvents = [...simulatedAnomalyEvents.value, ...normalizedAnomaly, ...normalizedPrivacy, ...normalizedShadowAi].sort((a, b) => {
      const ta = new Date(String(a?.created_at || '').replace(' ', 'T')).getTime();
      const tb = new Date(String(b?.created_at || '').replace(' ', 'T')).getTime();
      return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
    });

    anomalySummaryOnly.value = !!anomalyData?.summaryOnly;
    if (anomalySummaryOnly.value) {
      const shadowTotal = normalizedShadowAi.length;
      const shadowAnomaly = normalizedShadowAi.filter(item => !!item.is_anomaly).length;
      const totalMerged = Number(anomalyData?.total || 0) + Number(privacySummary.value.total || 0) + shadowTotal;
      const anomalyMerged = Number(anomalyData?.anomalyCount || 0) + Number(privacySummary.value.total || 0) + shadowAnomaly;
      const simulatedCount = simulatedAnomalyEvents.value.length;
      anomalySummary.value = {
        total: totalMerged + simulatedCount,
        anomalyCount: anomalyMerged + simulatedCount,
        normalCount: Math.max(0, totalMerged - anomalyMerged),
        anomalyRate: (totalMerged + simulatedCount) > 0 ? (anomalyMerged + simulatedCount) / (totalMerged + simulatedCount) : 0,
      };
      anomalyTotalRecords.value = totalMerged + simulatedCount;
      anomalyEvents.value = [...simulatedAnomalyEvents.value, ...normalizedAnomaly, ...normalizedShadowAi];
      return;
    }

    const scopedEvents = isCompanyScopeView.value
      ? mergedEvents
      : (isDepartmentScopeView.value
        ? mergedEvents.filter((item) => isCurrentDepartmentEvent(item))
        : mergedEvents.filter((item) => isCurrentUserEvent(item?.employee_id)));
    const roleBoundEvents = scopedEvents.filter(hasBoundRole);

    anomalyTotalRecords.value = roleBoundEvents.length;
    anomalyEvents.value = roleBoundEvents;
  } catch (error) {
    anomalyTotalRecords.value = simulatedAnomalyEvents.value.length;
    anomalyEvents.value = [...simulatedAnomalyEvents.value];
    ElMessage.warning('API调用失败，已保留本地模拟数据并等待真实链路恢复: ' + (error?.message || '未知错误'));
  } finally {
    anomalyLoading.value = false;
    if (pendingAnomalyReload.value) {
      pendingAnomalyReload.value = false;
      void loadAnomaly();
    }
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
  scheduleVisualLayerMount(900);
  void refreshAll();
  void ensureUserDirectory().then(() => {
    void loadAnomaly();
  });
});

onBeforeUnmount(() => {
  if (visualMountTimer) {
    window.clearTimeout(visualMountTimer);
    visualMountTimer = null;
  }
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
  padding: 14px 14px 12px;
  background: linear-gradient(145deg, rgba(7, 20, 40, 0.72), rgba(9, 15, 29, 0.72));
  box-shadow: inset 0 0 14px rgba(45, 140, 255, 0.08), 0 10px 18px rgba(2, 8, 18, 0.24);
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: rgba(138, 213, 255, 0.55);
  box-shadow: inset 0 0 20px rgba(57, 162, 255, 0.14), 0 14px 24px rgba(2, 8, 18, 0.34);
}

.stat-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stat-kicker {
  font-size: 10px;
  line-height: 1;
  letter-spacing: 0.16em;
  color: rgba(196, 232, 255, 0.82);
  border: 1px solid rgba(136, 206, 255, 0.42);
  border-radius: 999px;
  padding: 4px 8px;
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

.stat-foot {
  margin-top: 8px;
  font-size: 11px;
  color: rgba(176, 208, 232, 0.86);
}

.stat-meter {
  position: relative;
  margin-top: 10px;
  height: 8px;
  border-radius: 999px;
  background: rgba(88, 148, 201, 0.2);
  overflow: hidden;
}

.stat-meter i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #4cb5ff 0%, #55deff 55%, #a3f7ff 100%);
  box-shadow: 0 0 12px rgba(110, 232, 255, 0.46);
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-card-total {
  border-color: rgba(122, 202, 255, 0.28);
}

.stat-card-danger {
  border-color: rgba(255, 138, 138, 0.36);
  background: linear-gradient(148deg, rgba(34, 16, 31, 0.76), rgba(18, 13, 28, 0.74));
}

.stat-card-danger .stat-kicker {
  color: rgba(255, 220, 220, 0.86);
  border-color: rgba(255, 152, 152, 0.4);
}

.stat-card-rate {
  border-color: rgba(127, 203, 255, 0.34);
}

.stat-card-ready {
  border-color: rgba(116, 220, 179, 0.36);
  background: linear-gradient(146deg, rgba(10, 37, 38, 0.74), rgba(10, 24, 27, 0.76));
}

.stat-card-ready .stat-kicker {
  color: rgba(200, 255, 236, 0.9);
  border-color: rgba(122, 220, 183, 0.42);
}

.stat-card-ready-off {
  border-color: rgba(255, 174, 94, 0.38);
  background: linear-gradient(146deg, rgba(45, 27, 10, 0.76), rgba(26, 18, 11, 0.74));
}

.stat-card-ready-off .stat-kicker {
  color: rgba(255, 226, 178, 0.88);
  border-color: rgba(255, 191, 120, 0.42);
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
  position: relative;
  appearance: none;
  text-align: left;
  border-radius: 12px;
  border: 1px solid rgba(125, 192, 255, 0.24);
  background: linear-gradient(160deg, rgba(6, 20, 38, 0.76), rgba(8, 18, 34, 0.82));
  padding: 10px;
  cursor: pointer;
  transition: transform 150ms ease, border-color 150ms ease, box-shadow 150ms ease, background 150ms ease;
  box-shadow: inset 0 0 0 1px rgba(104, 170, 226, 0.08), 0 8px 14px rgba(2, 8, 18, 0.22);
}

.dept-card-orbit {
  position: absolute;
  width: 132px;
  height: 132px;
  right: -28px;
  top: -42px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(138, 220, 255, 0.22) 0%, rgba(138, 220, 255, 0) 72%);
  pointer-events: none;
  z-index: 0;
}

.sim-dept-card:hover {
  transform: translateY(-1px);
  border-color: rgba(174, 228, 255, 0.5);
}

.sim-dept-card.active {
  border-color: rgba(149, 217, 255, 0.78);
  box-shadow: 0 0 0 1px rgba(120, 207, 255, 0.28) inset, 0 12px 20px rgba(7, 31, 58, 0.35);
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
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.dept-card-meta-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.dept-card-tier-badge {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  color: #d7f0ff;
  border: 1px solid rgba(143, 210, 255, 0.46);
  background: rgba(14, 46, 78, 0.55);
}

.dept-card-ratio {
  font-size: 11px;
  color: #b6daef;
}

.dept-card-head strong {
  color: #f1f9ff;
  font-size: 13px;
  letter-spacing: 0.02em;
}

.dept-card-metric {
  font-size: 12px;
  color: #ffd7d7;
  border: 1px solid rgba(255, 159, 159, 0.4);
  border-radius: 999px;
  padding: 2px 8px;
}

.dept-role-tags {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dept-card-meter {
  position: relative;
  z-index: 1;
  margin-top: 10px;
  height: 7px;
  border-radius: 999px;
  background: rgba(118, 171, 214, 0.2);
  overflow: hidden;
}

.dept-card-meter i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #4fb5ff 0%, #6de3ff 55%, #8effd4 100%);
  box-shadow: 0 0 10px rgba(110, 232, 255, 0.5);
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

.dept-card-hint {
  position: relative;
  z-index: 1;
  margin: 8px 0 0;
  font-size: 11px;
  color: #9ec4da;
}

.sim-canvas-wrap {
  position: relative;
  min-height: 420px;
  height: clamp(420px, 58vh, 620px);
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

.sim-prism-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.sim-gallery-layer {
  position: relative;
  width: 100%;
  height: 100%;
  z-index: 2;
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

.table-card {
  position: relative;
  z-index: 1;
  border: 1px solid rgba(105, 181, 255, 0.26);
  border-radius: 14px;
  background: linear-gradient(156deg, rgba(7, 19, 35, 0.84), rgba(8, 16, 31, 0.88));
}

.card-header {
  font-size: 16px;
  font-weight: 700;
  color: #dff4ff;
}

.panel-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.table-pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
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
