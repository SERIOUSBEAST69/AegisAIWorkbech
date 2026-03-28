<template>
  <div class="command-page">
    <section class="command-hero card-glass">
      <div class="command-copy">
        <div class="command-eyebrow">OPERATIONS COMMAND CENTER</div>
        <h1>运营指挥台</h1>
        <p>
          把风险事件、审批流和数据共享请求压到同一张实战屏里，让值守席位先看压力排序，再执行动作，避免在三个演示式 CRUD 页面之间来回切换。
        </p>
        <div class="command-tags">
          <span>风险压降</span>
          <span>共享放行</span>
          <span>流程卡点</span>
          <span>责任可追溯</span>
        </div>
      </div>

      <div class="hero-aside">
        <div class="operator-card">
          <span>当前值守</span>
          <strong>{{ userStore.displayName }}</strong>
          <em>{{ userStore.identityLine }}</em>
        </div>
        <div class="hero-brief-grid">
          <article v-for="card in heroBriefs" :key="card.label" class="hero-brief-item">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <em>{{ card.note }}</em>
          </article>
        </div>
      </div>
    </section>

    <section class="command-summary">
      <article v-for="card in summaryCards" :key="card.key" class="summary-card card-glass">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <p>{{ card.description }}</p>
      </article>
    </section>

    <section class="command-toolbar card-glass">
      <div class="lane-switch">
        <button
          v-for="lane in laneOptions"
          :key="lane.key"
          type="button"
          :class="['lane-button', { active: lane.key === activeLane }]"
          @click="setLane(lane.key)"
        >
          <span>{{ lane.label }}</span>
          <strong>{{ laneCountMap[lane.key] }}</strong>
        </button>
      </div>

      <div class="toolbar-actions">
        <el-select v-model="statusFilter" class="toolbar-select">
          <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="searchText" placeholder="搜索资产ID、状态、风险类型或申请理由" clearable class="toolbar-input" />
        <el-button type="primary" :loading="loading" @click="loadAll">刷新态势</el-button>
      </div>
    </section>

    <section class="command-grid">
      <el-card class="dispatch-card card-glass" v-loading="loading">
        <div class="panel-head">
          <div>
            <div class="card-header">{{ activeLaneMeta.title }}</div>
            <p class="panel-subtitle">{{ activeLaneMeta.subtitle }}</p>
          </div>
          <div class="panel-badge">{{ visibleItems.length }} 项</div>
        </div>

        <div v-if="visibleItems.length" class="dispatch-list">
          <article v-for="item in visibleItems" :key="item.key" class="dispatch-item">
            <div class="dispatch-head">
              <div>
                <span class="dispatch-kicker">{{ item.kicker }}</span>
                <strong>{{ item.title }}</strong>
              </div>
              <span :class="['status-pill', item.tone]">{{ item.statusLabel }}</span>
            </div>
            <p>{{ item.description }}</p>
            <div class="dispatch-meta">
              <span v-for="meta in item.meta" :key="meta" class="dispatch-meta-chip">{{ meta }}</span>
            </div>
            <div class="dispatch-actions">
              <el-button
                v-for="action in item.actions"
                :key="action.key"
                :type="action.type"
                size="small"
                :plain="action.plain !== false"
                :loading="actionLoadingKey === action.key"
                @click="action.run"
              >
                {{ action.label }}
              </el-button>
            </div>
          </article>
        </div>
        <el-empty v-else description="当前筛选下没有待处理事项" />
      </el-card>

      <div class="command-side-stack">
        <el-card class="composer-card card-glass">
          <div class="panel-head">
            <div>
              <div class="card-header">快速动作台</div>
              <p class="panel-subtitle">直接在这一屏发起共享、审批或风险处置动作，不再跳到独立页面。</p>
            </div>
          </div>

          <div class="composer-tabs">
            <button
              v-for="lane in composerOptions"
              :key="lane.key"
              type="button"
              :class="['composer-tab', { active: composerLane === lane.key }]"
              @click="composerLane = lane.key"
            >
              {{ lane.label }}
            </button>
          </div>

          <div v-if="composerLane === 'risk'" class="composer-form">
            <el-input v-model="riskForm.type" placeholder="风险类型，例如：敏感数据异常导出" />
            <el-select v-model="riskForm.level">
              <el-option label="高风险" value="high" />
              <el-option label="中风险" value="medium" />
              <el-option label="低风险" value="low" />
            </el-select>
            <el-select v-model="riskForm.status">
              <el-option label="待处置" value="open" />
              <el-option label="处理中" value="processing" />
              <el-option label="已闭环" value="closed" />
            </el-select>
            <el-input v-model="riskForm.processLog" type="textarea" :rows="4" placeholder="补充发现路径、影响范围和第一步处置说明" />
            <el-button type="primary" :loading="submitLoading === 'risk'" @click="submitRisk">创建风险事件</el-button>
          </div>

          <div v-else-if="composerLane === 'approval'" class="composer-form">
            <el-input v-model="approvalForm.assetId" placeholder="资产ID" />
            <el-input v-model="approvalForm.reason" type="textarea" :rows="4" placeholder="填写审批原因与业务背景，申请人会自动绑定当前登录账号" />
            <el-button type="primary" :loading="submitLoading === 'approval'" @click="submitApproval">发起审批申请</el-button>
          </div>

          <div v-else class="composer-form">
            <el-input v-model="shareForm.assetId" placeholder="资产ID" />
            <el-input v-model="shareForm.collaborators" placeholder="协作人ID，多个以逗号分隔" />
            <el-input v-model="shareForm.reason" type="textarea" :rows="4" placeholder="填写共享范围、期限和业务用途，申请人会自动绑定当前登录账号" />
            <el-button type="primary" :loading="submitLoading === 'share'" @click="submitShare">发起共享申请</el-button>
          </div>
        </el-card>

        <el-card class="signal-card card-glass">
          <div class="panel-head">
            <div>
              <div class="card-header">指挥信号</div>
              <p class="panel-subtitle">基于当前队列给出值守动作建议。</p>
            </div>
          </div>
          <div class="signal-list">
            <article v-for="signal in commandSignals" :key="signal.title" class="signal-item">
              <strong>{{ signal.title }}</strong>
              <span>{{ signal.value }}</span>
              <p>{{ signal.description }}</p>
            </article>
          </div>
        </el-card>
      </div>
    </section>

    <section class="lane-board">
      <el-card v-for="panel in lanePanels" :key="panel.key" class="lane-panel card-glass">
        <div class="panel-head compact">
          <div>
            <div class="card-header">{{ panel.title }}</div>
            <p class="panel-subtitle">{{ panel.subtitle }}</p>
          </div>
          <el-button text type="primary" @click="setLane(panel.key)">聚焦此队列</el-button>
        </div>
        <div v-if="panel.items.length" class="lane-panel-list">
          <article v-for="item in panel.items" :key="item.key" class="lane-panel-item">
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <span :class="['status-pill', item.tone]">{{ item.statusLabel }}</span>
          </article>
        </div>
        <el-empty v-else description="当前无事项" />
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

function hasAnyRole(user, ...roleCodes) {
  const currentRole = String(user?.roleCode || '').toUpperCase();
  return roleCodes.some(code => code === currentRole);
}

const laneOptions = [
  { key: 'all', label: '全局压测' },
  { key: 'risk', label: '风险事件' },
  { key: 'approval', label: '审批流' },
  { key: 'share', label: '共享请求' },
];

const loading = ref(false);
const searchText = ref('');
const statusFilter = ref('all');
const activeLane = ref(resolveLane(route));
const composerLane = ref(activeLane.value === 'all' ? 'risk' : activeLane.value);
const actionLoadingKey = ref('');
const submitLoading = ref('');

const approvals = ref([]);
const risks = ref([]);
const shares = ref([]);

const approvalForm = ref({ assetId: '', reason: '' });
const shareForm = ref({ assetId: '', collaborators: '', reason: '' });
const riskForm = ref({ type: '', level: 'high', status: 'open', processLog: '' });

const currentRoleCode = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase());
const canManageRisk = computed(() => hasAnyRole(userStore.userInfo, 'ADMIN', 'SECOPS'));
const canApproveApprovalFlow = computed(() => hasAnyRole(userStore.userInfo, 'ADMIN', 'DATA_ADMIN', 'BUSINESS_OWNER'));
const canApproveShareFlow = computed(() => hasAnyRole(userStore.userInfo, 'ADMIN', 'DATA_ADMIN'));
const composerOptions = computed(() => ([
  { key: 'risk', label: '风险事件', enabled: canManageRisk.value },
  { key: 'approval', label: '审批申请', enabled: true },
  { key: 'share', label: '共享申请', enabled: true },
]).filter(item => item.enabled));

watch(
  () => [route.path, route.query.lane, route.meta?.lane],
  () => {
    const lane = resolveLane(route);
    activeLane.value = lane;
    if (lane !== 'all' && composerOptions.value.some(item => item.key === lane)) {
      composerLane.value = lane;
    } else if (!composerOptions.value.some(item => item.key === composerLane.value)) {
      composerLane.value = composerOptions.value[0]?.key || 'approval';
    }
    statusFilter.value = 'all';
  }
);

const laneCountMap = computed(() => ({
  all: approvals.value.length + risks.value.length + shares.value.length,
  risk: risks.value.length,
  approval: approvals.value.length,
  share: shares.value.length,
}));

const activeLaneMeta = computed(() => {
  const meta = {
    all: { title: '统一调度池', subtitle: '跨风险、审批与共享队列统一排序，先处理最容易失控的事项。' },
    risk: { title: '风险闭环队列', subtitle: '优先拉平高危与处理中事件，减少风险堆积。' },
    approval: { title: '审批卡点队列', subtitle: '把待审批、已驳回和已通过的关键流程放在一处查看。' },
    share: { title: '共享放行队列', subtitle: '聚焦共享申请、审批结果和令牌放行状态。' },
  };
  return meta[activeLane.value] || meta.all;
});

const statusOptions = computed(() => {
  if (activeLane.value === 'risk') {
    return [
      { label: '全部状态', value: 'all' },
      { label: '待处置', value: 'open' },
      { label: '处理中', value: 'processing' },
      { label: '已闭环', value: 'closed' },
    ];
  }
  if (activeLane.value === 'approval' || activeLane.value === 'share') {
    return [
      { label: '全部状态', value: 'all' },
      { label: '待处理', value: 'pending' },
      { label: '已通过', value: 'approved' },
      { label: '已拒绝', value: 'rejected' },
    ];
  }
  return [{ label: '全部状态', value: 'all' }];
});

const filteredRisks = computed(() => risks.value.filter(item => {
  const state = riskState(item.status);
  if (activeLane.value !== 'all' && activeLane.value !== 'risk') return false;
  if (statusFilter.value !== 'all' && state !== statusFilter.value) return false;
  return matchesSearch([item.type, item.level, item.status, item.processLog, item.id]);
}));

const filteredApprovals = computed(() => approvals.value.filter(item => {
  const state = approvalState(item.status);
  if (activeLane.value !== 'all' && activeLane.value !== 'approval') return false;
  if (statusFilter.value !== 'all' && state !== statusFilter.value) return false;
  return matchesSearch([item.id, item.assetId, item.reason, item.status, item.applicantId]);
}));

const filteredShares = computed(() => shares.value.filter(item => {
  const state = shareState(item.status);
  if (activeLane.value !== 'all' && activeLane.value !== 'share') return false;
  if (statusFilter.value !== 'all' && state !== statusFilter.value) return false;
  return matchesSearch([item.id, item.assetId, item.reason, item.status, item.collaborators, item.shareToken]);
}));

const heroBriefs = computed(() => [
  { label: '高压事项', value: `${highPriorityCount.value} 项`, note: '需要在本班次优先处理' },
  { label: '待审批 / 待共享', value: `${pendingApprovals.value + pendingShares.value} 单`, note: '直接影响业务放行速度' },
  { label: '已闭环率', value: `${closureRate.value}%`, note: '风险事件处置完成占比' },
]);

const summaryCards = computed(() => [
  { key: 'risk', label: '高危风险事件', value: `${highRiskCount.value}`, description: '高风险或 critical 事件数量，决定当前第一优先级。' },
  { key: 'approval', label: '待审批流程', value: `${pendingApprovals.value}`, description: '仍停留在待审批或部门审批阶段的申请。' },
  { key: 'share', label: '待放行共享', value: `${pendingShares.value}`, description: '尚未通过的共享请求，会直接拖慢跨部门协同。' },
  { key: 'token', label: '有效共享令牌', value: `${activeShareTokens.value}`, description: '当前仍可访问的已批准共享令牌数量。' },
]);

const mappedRisks = computed(() => filteredRisks.value.map(item => ({
  key: `risk-${item.id}`,
  lane: 'risk',
  kicker: 'RISK EVENT',
  title: item.type || `风险事件 #${item.id}`,
  description: item.processLog || '尚未补充处置说明，建议尽快完善影响面与第一步动作。',
  statusLabel: riskStatusLabel(item.status, item.level),
  tone: riskTone(item.level, item.status),
  meta: [
    `编号 ${item.id}`,
    `等级 ${riskLevelLabel(item.level)}`,
    `状态 ${item.status || 'open'}`,
    item.updateTime ? `更新于 ${formatTime(item.updateTime)}` : `创建于 ${formatTime(item.createTime)}`,
  ].filter(Boolean),
  order: riskPriority(item.level, item.status),
  actions: [
    ...(!canManageRisk.value || ['processing', 'closed'].includes(riskState(item.status)) ? [] : [{
      key: `risk-processing-${item.id}`,
      label: '转处理中',
      type: 'primary',
      run: () => updateRisk(item, 'processing'),
    }]),
    ...(!canManageRisk.value || riskState(item.status) === 'closed' ? [] : [{
      key: `risk-closed-${item.id}`,
      label: '标记闭环',
      type: 'success',
      run: () => updateRisk(item, 'closed'),
    }]),
    ...(!canManageRisk.value ? [] : [{
      key: `risk-delete-${item.id}`,
      label: '删除',
      type: 'danger',
      run: () => deleteRisk(item.id),
    }]),
  ],
})).sort((left, right) => right.order - left.order));

const mappedApprovals = computed(() => filteredApprovals.value.map(item => ({
  key: `approval-${item.id}`,
  lane: 'approval',
  kicker: 'APPROVAL FLOW',
  title: `审批申请 #${item.id}`,
  description: item.reason || '未填写审批原因。',
  statusLabel: approvalStatusLabel(item.status),
  tone: approvalTone(item.status),
  meta: [
    `资产 ${item.assetId || '-'}`,
    `申请人 ${item.applicantId || '-'}`,
    item.approverId ? `审批人 ${item.approverId}` : '尚未审批',
    item.updateTime ? `更新于 ${formatTime(item.updateTime)}` : `创建于 ${formatTime(item.createTime)}`,
  ],
  order: approvalPriority(item.status),
  actions: [
    ...(!canApproveApprovalFlow.value || approvalState(item.status) !== 'pending' ? [] : [{
      key: `approval-pass-${item.id}`,
      label: '通过',
      type: 'success',
      run: () => approveRequest(item.id, '通过'),
    }, {
      key: `approval-reject-${item.id}`,
      label: '拒绝',
      type: 'warning',
      run: () => approveRequest(item.id, '拒绝'),
    }]),
    {
      key: `approval-delete-${item.id}`,
      label: '删除',
      type: 'danger',
      run: () => deleteApproval(item.id),
    },
  ],
})).sort((left, right) => right.order - left.order));

const mappedShares = computed(() => filteredShares.value.map(item => ({
  key: `share-${item.id}`,
  lane: 'share',
  kicker: 'SHARE REQUEST',
  title: `共享申请 #${item.id}`,
  description: item.reason || '未填写共享原因。',
  statusLabel: shareStatusLabel(item.status),
  tone: shareTone(item.status),
  meta: [
    `资产 ${item.assetId || '-'}`,
    `申请人 ${item.applicantId || '-'}`,
    item.collaborators ? `协作人 ${item.collaborators}` : '未填写协作人',
    item.shareToken ? `令牌 ${item.shareToken.slice(0, 8)}...` : '尚未签发令牌',
  ],
  order: sharePriority(item.status),
  actions: [
    ...(!canApproveShareFlow.value || shareState(item.status) !== 'pending' ? [] : [{
      key: `share-pass-${item.id}`,
      label: '放行',
      type: 'success',
      run: () => approveShare(item.id, 'approved'),
    }, {
      key: `share-reject-${item.id}`,
      label: '拒绝',
      type: 'warning',
      run: () => approveShare(item.id, 'rejected'),
    }]),
    ...(item.shareToken ? [{
      key: `share-copy-${item.id}`,
      label: '复制令牌',
      type: 'primary',
      run: () => copyShareToken(item.shareToken),
    }] : []),
    {
      key: `share-delete-${item.id}`,
      label: '删除',
      type: 'danger',
      run: () => deleteShare(item.id),
    },
  ],
})).sort((left, right) => right.order - left.order));

const visibleItems = computed(() => {
  const items = activeLane.value === 'risk'
    ? mappedRisks.value
    : activeLane.value === 'approval'
      ? mappedApprovals.value
      : activeLane.value === 'share'
        ? mappedShares.value
        : [...mappedRisks.value, ...mappedApprovals.value, ...mappedShares.value].sort((left, right) => right.order - left.order);
  return items.slice(0, 12);
});

const lanePanels = computed(() => ([
  {
    key: 'risk',
    title: '风险战情面板',
    subtitle: '适合看处置节奏和高危压降。',
    items: mappedRisks.value.slice(0, 4),
  },
  {
    key: 'approval',
    title: '审批流面板',
    subtitle: '适合识别谁在卡流程。',
    items: mappedApprovals.value.slice(0, 4),
  },
  {
    key: 'share',
    title: '共享放行面板',
    subtitle: '适合跟踪放行效率和令牌下发。',
    items: mappedShares.value.slice(0, 4),
  },
]));

const commandSignals = computed(() => [
  {
    title: '首要动作',
    value: highRiskCount.value > 0 ? '压降高危风险' : pendingApprovals.value > 0 ? '清空审批积压' : '维持平稳值守',
    description: highRiskCount.value > 0
      ? `当前有 ${highRiskCount.value} 个高危风险事件，应优先处理。`
      : pendingApprovals.value > 0
        ? `当前有 ${pendingApprovals.value} 个待审批流程，建议优先消除卡点。`
        : '当前三条队列没有明显堵点，可以转入例行巡检。',
  },
  {
    title: '共享放行压力',
    value: `${pendingShares.value} 单待处理`,
    description: pendingShares.value > 0 ? '共享申请仍在排队，可能直接影响跨部门业务进度。' : '共享放行队列当前平稳。',
  },
  {
    title: '最后刷新',
    value: lastRefreshLabel.value,
    description: '点击“刷新态势”可重新抓取三条队列的最新状态。',
  },
]);

const pendingApprovals = computed(() => approvals.value.filter(item => approvalState(item.status) === 'pending').length);
const pendingShares = computed(() => shares.value.filter(item => shareState(item.status) === 'pending').length);
const highRiskCount = computed(() => risks.value.filter(item => ['high', 'critical', '高'].includes(normalize(item.level))).length);
const activeShareTokens = computed(() => shares.value.filter(item => Boolean(item.shareToken) && shareState(item.status) === 'approved').length);
const highPriorityCount = computed(() => highRiskCount.value + pendingApprovals.value + pendingShares.value);
const closureRate = computed(() => {
  if (!risks.value.length) {
    return 100;
  }
  const closed = risks.value.filter(item => riskState(item.status) === 'closed').length;
  return Math.round((closed / risks.value.length) * 100);
});
const lastRefresh = ref(null);
const lastRefreshLabel = computed(() => lastRefresh.value ? formatTime(lastRefresh.value) : '尚未刷新');

function resolveLane(currentRoute) {
  const lane = currentRoute.query?.lane || currentRoute.meta?.lane || 'all';
  return laneOptions.some(item => item.key === lane) ? lane : 'all';
}

function normalize(value) {
  return String(value || '').trim().toLowerCase();
}

function matchesSearch(values) {
  const keyword = normalize(searchText.value);
  if (!keyword) return true;
  return values.some(value => normalize(value).includes(keyword));
}

function riskState(status) {
  const value = normalize(status);
  if (['closed', 'done', 'resolved', '已闭环', 'closed'].includes(value)) return 'closed';
  if (['processing', 'investigating', '处理中'].includes(value)) return 'processing';
  return 'open';
}

function approvalState(status) {
  const value = normalize(status);
  if (!value || value.includes('待') || value.includes('部门审批') || value.includes('合规审批')) return 'pending';
  if (value.includes('拒') || value.includes('驳') || value === 'reject') return 'rejected';
  if (value.includes('通过') || value.includes('approved') || value.includes('approve')) return 'approved';
  return 'pending';
}

function shareState(status) {
  const value = normalize(status);
  if (value === 'approved') return 'approved';
  if (value === 'rejected') return 'rejected';
  return 'pending';
}

function riskPriority(level, status) {
  const levelValue = normalize(level);
  const stateValue = riskState(status);
  return (levelValue === 'critical' || levelValue === 'high' || levelValue === '高' ? 300 : levelValue === 'medium' || levelValue === '中' ? 220 : 140)
    + (stateValue === 'open' ? 60 : stateValue === 'processing' ? 30 : 0);
}

function approvalPriority(status) {
  const stateValue = approvalState(status);
  if (stateValue === 'pending') return 230;
  if (stateValue === 'rejected') return 150;
  return 120;
}

function sharePriority(status) {
  const stateValue = shareState(status);
  if (stateValue === 'pending') return 210;
  if (stateValue === 'rejected') return 140;
  return 110;
}

function riskLevelLabel(level) {
  const value = normalize(level);
  if (['critical', 'high', '高'].includes(value)) return '高风险';
  if (['medium', '中'].includes(value)) return '中风险';
  if (['low', '低'].includes(value)) return '低风险';
  return level || '待评估';
}

function riskStatusLabel(status, level) {
  const state = riskState(status);
  const prefix = riskLevelLabel(level);
  if (state === 'closed') return `${prefix} · 已闭环`;
  if (state === 'processing') return `${prefix} · 处理中`;
  return `${prefix} · 待处置`;
}

function approvalStatusLabel(status) {
  const state = approvalState(status);
  if (state === 'approved') return '已通过';
  if (state === 'rejected') return '已拒绝';
  return status || '待审批';
}

function shareStatusLabel(status) {
  const state = shareState(status);
  if (state === 'approved') return '已放行';
  if (state === 'rejected') return '已拒绝';
  return '待放行';
}

function riskTone(level, status) {
  if (riskState(status) === 'closed') return 'safe';
  return ['critical', 'high', '高'].includes(normalize(level)) ? 'danger' : 'warning';
}

function approvalTone(status) {
  const state = approvalState(status);
  if (state === 'approved') return 'safe';
  if (state === 'rejected') return 'danger';
  return 'warning';
}

function shareTone(status) {
  const state = shareState(status);
  if (state === 'approved') return 'safe';
  if (state === 'rejected') return 'danger';
  return 'warning';
}

function formatTime(value) {
  if (!value) return '未知时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '未知时间';
  return `${date.getMonth() + 1}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function setLane(lane) {
  activeLane.value = lane;
  if (lane !== 'all') {
    composerLane.value = lane;
  }
  statusFilter.value = 'all';
  router.replace({ path: '/operations-command', query: lane === 'all' ? {} : { lane } });
}

async function loadAll() {
  loading.value = true;
  try {
    const [approvalResult, riskResult] = await Promise.allSettled([
      request.get('/approval/list'),
      request.get('/risk-event/list'),
    ]);

    approvals.value = approvalResult.status === 'fulfilled' ? (approvalResult.value || []) : [];
    risks.value = riskResult.status === 'fulfilled' ? (riskResult.value || []) : [];
    shares.value = [];

    const failed = [approvalResult, riskResult].filter(item => item.status === 'rejected');
    const hardFailure = failed.find(item => item.status === 'rejected' && item.reason?.code !== 40300);
    if (hardFailure && hardFailure.status === 'rejected') {
      throw hardFailure.reason;
    }
    lastRefresh.value = new Date();
  } catch (error) {
    ElMessage.error(error?.message || '运营指挥台加载失败');
  } finally {
    loading.value = false;
  }
}

async function withAction(key, action) {
  actionLoadingKey.value = key;
  try {
    await action();
    await loadAll();
  } catch (error) {
    ElMessage.error(error?.message || '操作失败');
  } finally {
    actionLoadingKey.value = '';
  }
}

function submitApproval() {
  const assetId = String(approvalForm.value.assetId || '').trim();
  const reason = String(approvalForm.value.reason || '').trim();
  if (!assetId || !reason) {
    ElMessage.warning('请填写资产ID和审批原因');
    return;
  }
  submitLoading.value = 'approval';
  request.post('/approval/apply', { assetId, reason })
    .then(async () => {
      ElMessage.success('审批申请已提交');
      approvalForm.value = { assetId: '', reason: '' };
      composerLane.value = 'approval';
      await loadAll();
    })
    .catch(error => {
      ElMessage.error(error?.message || '审批申请提交失败');
    })
    .finally(() => {
      submitLoading.value = '';
    });
}

function submitShare() {
  ElMessage.warning('数据共享模块已下线，请使用审批管理流程');
}

function submitRisk() {
  if (!canManageRisk.value) {
    ElMessage.warning('当前身份仅可查看风险态势，不能创建或处置风险事件');
    return;
  }
  const type = String(riskForm.value.type || '').trim();
  if (!type) {
    ElMessage.warning('请填写风险类型');
    return;
  }
  submitLoading.value = 'risk';
  request.post('/risk-event/add', { ...riskForm.value, type })
    .then(async () => {
      ElMessage.success('风险事件已创建');
      riskForm.value = { type: '', level: 'high', status: 'open', processLog: '' };
      composerLane.value = 'risk';
      await loadAll();
    })
    .catch(error => {
      ElMessage.error(error?.message || '风险事件创建失败');
    })
    .finally(() => {
      submitLoading.value = '';
    });
}

function approveRequest(id, status) {
  return withAction(`approval-${id}-${status}`, async () => {
    const endpoint = status === '拒绝' ? '/approval/reject' : '/approval/approve';
    await request.post(endpoint, { requestId: id, status });
    ElMessage.success('审批流已更新');
  });
}

function deleteApproval(id) {
  return withAction(`approval-delete-${id}`, async () => {
    await ElMessageBox.confirm('确认删除该审批申请吗？', '提示', { type: 'warning' });
    await request.post('/approval/delete', { id });
    ElMessage.success('审批申请已删除');
  });
}

function updateRisk(item, status) {
  return withAction(`risk-${item.id}-${status}`, async () => {
    await request.post('/risk-event/update', { ...item, status });
    ElMessage.success(status === 'closed' ? '风险事件已闭环' : '风险事件已转处理中');
  });
}

function deleteRisk(id) {
  return withAction(`risk-delete-${id}`, async () => {
    await ElMessageBox.confirm('确认删除该风险事件吗？', '提示', { type: 'warning' });
    await request.post('/risk-event/delete', { id });
    ElMessage.success('风险事件已删除');
  });
}

function approveShare(id, status) {
  ElMessage.warning('数据共享模块已下线，请使用审批管理流程');
  return Promise.resolve();
}

function deleteShare(id) {
  ElMessage.warning('数据共享模块已下线');
  return Promise.resolve();
}

async function copyShareToken(token) {
  try {
    await navigator.clipboard.writeText(token);
    ElMessage.success('共享令牌已复制');
  } catch {
    ElMessage.warning('当前环境不支持复制，请手动复制令牌');
  }
}

onMounted(loadAll);
</script>

<style scoped>
.command-page {
  display: grid;
  gap: 20px;
}

.command-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 22px;
  padding: 26px;
}

.command-eyebrow {
  font-size: 12px;
  letter-spacing: 0.28em;
  color: #9bb0d9;
}

.command-copy h1 {
  margin: 14px 0 10px;
  font-size: 38px;
  line-height: 1.08;
}

.command-copy p {
  max-width: 760px;
  color: #95a8c8;
  line-height: 1.8;
}

.command-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.command-tags span,
.dispatch-meta-chip,
.panel-badge {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(169, 196, 255, 0.15);
  background: rgba(100, 138, 255, 0.08);
  color: #e2ebff;
}

.hero-aside {
  display: grid;
  gap: 16px;
}

.operator-card,
.hero-brief-item,
.summary-card,
.dispatch-item,
.signal-item,
.lane-panel-item {
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 18px;
}

.operator-card {
  padding: 18px;
  display: grid;
  gap: 6px;
}

.operator-card span,
.hero-brief-item span,
.summary-card span,
.signal-item span {
  color: #8ea3c6;
}

.operator-card strong,
.hero-brief-item strong,
.summary-card strong,
.signal-item strong,
.dispatch-head strong,
.lane-panel-item strong {
  color: #f7fbff;
}

.operator-card em,
.hero-brief-item em,
.summary-card p,
.signal-item p,
.lane-panel-item p,
.dispatch-item p,
.panel-subtitle {
  color: #8ea0bc;
  font-style: normal;
  line-height: 1.7;
}

.hero-brief-grid,
.command-summary,
.lane-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.hero-brief-item,
.summary-card {
  padding: 18px;
}

.summary-card strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 32px;
}

.command-toolbar {
  padding: 18px 22px;
  display: grid;
  gap: 16px;
}

.lane-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.lane-button,
.composer-tab {
  min-width: 130px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.02);
  color: #cdd9ee;
  cursor: pointer;
  display: grid;
  gap: 4px;
  text-align: left;
  transition: 0.2s ease;
}

.lane-button strong,
.composer-tab.active {
  color: #f7fbff;
}

.lane-button.active,
.composer-tab.active {
  border-color: rgba(154, 186, 255, 0.26);
  background: linear-gradient(135deg, rgba(113, 154, 255, 0.18), rgba(53, 90, 201, 0.34));
  box-shadow: 0 18px 36px rgba(38, 74, 171, 0.22);
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-select {
  width: 180px;
}

.toolbar-input {
  flex: 1;
  min-width: 260px;
}

.command-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(340px, 0.7fr);
  gap: 20px;
}

.dispatch-card,
.composer-card,
.signal-card,
.lane-panel {
  min-height: 100%;
}

.dispatch-list,
.signal-list,
.lane-panel-list,
.composer-form,
.command-side-stack {
  display: grid;
  gap: 14px;
}

.dispatch-item,
.signal-item,
.lane-panel-item {
  padding: 16px;
}

.dispatch-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.dispatch-kicker {
  display: block;
  margin-bottom: 6px;
  color: #91a8d7;
  font-size: 11px;
  letter-spacing: 0.22em;
}

.dispatch-meta,
.dispatch-actions,
.composer-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.status-pill.danger {
  background: rgba(255, 95, 95, 0.14);
  color: #ffd7d7;
}

.status-pill.warning {
  background: rgba(111, 156, 255, 0.14);
  color: #dfe9ff;
}

.status-pill.safe {
  background: rgba(79, 180, 155, 0.14);
  color: #dcfff6;
}

.composer-form :deep(.el-input),
.composer-form :deep(.el-select) {
  width: 100%;
}

.panel-head.compact {
  align-items: center;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

@media (max-width: 1200px) {
  .command-hero,
  .command-grid,
  .hero-brief-grid,
  .command-summary,
  .lane-board {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .command-hero,
  .command-toolbar,
  .dispatch-card,
  .composer-card,
  .signal-card,
  .lane-panel {
    padding: 16px;
  }

  .lane-button,
  .composer-tab {
    min-width: 0;
    flex: 1;
  }

  .dispatch-head {
    flex-direction: column;
  }
}
</style>