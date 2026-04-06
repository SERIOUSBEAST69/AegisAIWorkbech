<template>
  <el-card class="mgmt-table-exempt">
    <div class="policy-head">
      <h2>合规策略列表</h2>
      <div class="policy-count">共 {{ displayTotal }} 条，当前显示 {{ pagedDisplayPolicies.length }} 条</div>
    </div>
    <div class="engine-tip">平台所有安全能力由策略引擎驱动，修改/启用策略将直接影响其他模块的行为</div>

    <el-form :inline="true" @submit.prevent>
      <el-form-item label="业务策略名称">
        <el-input v-model="query.name" placeholder="例如：核心客户导出限制策略" clearable />
      </el-form-item>
      <el-form-item label="策略状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
          <el-option label="草稿" value="DRAFT" />
        </el-select>
      </el-form-item>
      <el-form-item label="策略类型">
        <el-select v-model="query.policyType" placeholder="全部" clearable style="width: 160px">
          <el-option label="脱敏" value="MASKING" />
          <el-option label="访问控制" value="ACCESS_CONTROL" />
          <el-option label="导出限制" value="EXPORT_LIMIT" />
          <el-option label="审计治理" value="AUDIT_GOVERNANCE" />
          <el-option label="告警治理" value="ALERT_GOVERNANCE" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效范围">
        <el-select v-model="query.scope" placeholder="全部" clearable style="width: 180px">
          <el-option label="ai_prompt（提示词拦截）" value="ai_prompt" />
          <el-option label="全平台" value="全平台" />
          <el-option label="全局" value="全局" />
          <el-option label="数据治理部" value="数据治理部" />
          <el-option label="研发部" value="研发部" />
          <el-option label="业务部门" value="业务部门" />
          <el-option label="技术部门" value="技术部门" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchPolicies">刷新</el-button>
        <el-button v-if="canManageStructure" @click="openAdd">新增策略</el-button>
      </el-form-item>
    </el-form>

    <div class="policy-toolbar" v-if="canToggleStatus">
      <el-button
        size="small"
        type="success"
        :disabled="selectedPolicies.length === 0 || batchLoading"
        :loading="batchLoading"
        @click="batchToggleStatus(true)">
        批量启用
      </el-button>
      <el-button
        size="small"
        type="warning"
        :disabled="selectedPolicies.length === 0 || batchLoading"
        :loading="batchLoading"
        @click="batchToggleStatus(false)">
        批量禁用
      </el-button>
      <el-button size="small" @click="clearSelection" :disabled="selectedPolicies.length === 0">清空选择</el-button>
      <span class="selection-count">已选 {{ selectedPolicies.length }} 条</span>
    </div>

    <el-table
      ref="tableRef"
      :data="pagedDisplayPolicies"
      style="width: 100%"
      table-layout="auto"
      v-loading="loading"
      row-key="id"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="48" />
      <el-table-column label="序号" width="70">
        <template #default="scope">
          {{ (pagination.current - 1) * pagination.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="130" show-overflow-tooltip />
      <el-table-column prop="businessName" label="业务策略名称" min-width="210" show-overflow-tooltip />
      <el-table-column label="关联模块" min-width="260" show-overflow-tooltip>
        <template #default="scope">
          <el-space wrap>
            <el-tag
              v-for="module in scope.row.relatedModules"
              :key="`${scope.row.id}-${module}`"
              size="small"
              type="info"
              effect="plain"
            >{{ module }}</el-tag>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column prop="effectiveScenario" label="生效场景" min-width="420">
        <template #default="scope">
          <div class="scenario-cell">{{ scope.row.effectiveScenario }}</div>
        </template>
      </el-table-column>
      <el-table-column label="策略类型" width="120">
        <template #default="scope">
          <el-tag type="info">{{ formatPolicyType(scope.row.policyType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="策略状态" width="110">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.statusNormalized)">{{ formatStatus(scope.row.statusNormalized) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="130">
        <template #default="scope">
          <el-tag :type="priorityTagType(scope.row.priority)">{{ priorityLabel(scope.row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="scope" label="基础范围" width="150" show-overflow-tooltip />
      <el-table-column label="生效范围详情" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          {{ formatEffectiveScope(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="规则友好" min-width="320">
        <template #default="scope">
          <el-tooltip :content="scope.row.ruleFriendlyText" placement="top" :show-after="250">
            <div class="rule-chip-wrap">
              <el-tag
                v-for="chip in scope.row.ruleHighlights"
                :key="`${scope.row.id}-${chip}`"
                class="rule-chip"
                size="small"
                effect="plain"
              >
                {{ chip }}
              </el-tag>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170" show-overflow-tooltip>
        <template #default="scope">
          {{ formatTime(scope.row.updateTime || scope.row.lastModifiedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="最近修改" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          {{ scope.row.lastModifier }} / {{ formatTime(scope.row.lastModifiedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="420">
        <template #default="scope">
          <el-space wrap>
            <el-button
              v-if="canToggleStatus && scope.row.statusNormalized === 'ENABLED'"
              size="small"
              type="warning"
              @click="togglePolicyStatus(scope.row)">
              禁用
            </el-button>
            <el-button
              v-if="canToggleStatus && scope.row.statusNormalized !== 'ENABLED'"
              size="small"
              type="success"
              @click="togglePolicyStatus(scope.row)">
              启用
            </el-button>
            <el-button
              v-if="canManageStructure"
              size="small"
              @click="editPolicy(scope.row)">
              编辑
            </el-button>
            <el-button
              v-if="canManageStructure && scope.row.statusNormalized !== 'ENABLED'"
              size="small"
              type="danger"
              @click="deletePolicy(scope.row.id)">
              删除
            </el-button>
            <el-button size="small" type="primary" plain @click="viewPolicyImpact(scope.row)">查看影响</el-button>
            <el-button size="small" @click="openAuditDrawer(scope.row)">日志</el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div class="policy-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="displayTotal"
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
        @size-change="onPageSizeChange"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog v-model="showAdd" title="新增策略" width="760px">
      <el-form :model="addForm" :rules="rules" ref="addFormRef" label-width="120px">
        <el-form-item label="业务策略名称" prop="name">
          <el-input v-model="addForm.name" placeholder="例如：研发文档对外导出限制策略" />
        </el-form-item>
        <el-form-item label="策略类型" prop="policyType">
          <el-select v-model="addForm.policyType" style="width: 220px">
            <el-option label="脱敏" value="MASKING" />
            <el-option label="访问控制" value="ACCESS_CONTROL" />
            <el-option label="导出限制" value="EXPORT_LIMIT" />
            <el-option label="审计治理" value="AUDIT_GOVERNANCE" />
            <el-option label="告警治理" value="ALERT_GOVERNANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略状态" prop="status">
          <el-select v-model="addForm.status" style="width: 220px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="addForm.priority" :min="1" :max="999" />
          <span class="field-hint">数字越小优先级越高，用于执行冲突仲裁。</span>
        </el-form-item>
        <el-form-item label="基础生效范围" prop="scope">
          <el-select v-model="addForm.scope" style="width: 220px">
            <el-option label="ai_prompt（提示词拦截）" value="ai_prompt" />
            <el-option label="全局" value="全局" />
            <el-option label="业务部门" value="业务部门" />
            <el-option label="技术部门" value="技术部门" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门范围">
          <el-input v-model="addForm.departments" placeholder="多个用逗号分隔，例如：研发部,风控部" />
        </el-form-item>
        <el-form-item label="用户组范围">
          <el-input v-model="addForm.userGroups" placeholder="多个用逗号分隔，例如：高权限组,外包组" />
        </el-form-item>
        <el-form-item label="数据类型范围">
          <el-input v-model="addForm.dataTypes" placeholder="多个用逗号分隔，例如：身份证号,手机号,银行卡" />
        </el-form-item>
        <el-form-item label="规则JSON" prop="ruleContent">
          <el-input v-model="addForm.ruleContent" type="textarea" :rows="7" placeholder='例如：{"keywords":["身份证","手机号"],"action":"mask"}' />
        </el-form-item>
        <el-form-item>
          <div class="field-hint">index 含义：系统会按规则数组中的 index 从小到大执行，index 越小越先命中。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addPolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEdit" title="编辑策略" width="760px">
      <el-form :model="editForm" :rules="rules" ref="editFormRef" label-width="120px">
        <el-form-item label="业务策略名称" prop="name">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="策略类型" prop="policyType">
          <el-select v-model="editForm.policyType" style="width: 220px">
            <el-option label="脱敏" value="MASKING" />
            <el-option label="访问控制" value="ACCESS_CONTROL" />
            <el-option label="导出限制" value="EXPORT_LIMIT" />
            <el-option label="审计治理" value="AUDIT_GOVERNANCE" />
            <el-option label="告警治理" value="ALERT_GOVERNANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略状态" prop="status">
          <el-select v-model="editForm.status" style="width: 220px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="editForm.priority" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="基础生效范围" prop="scope">
          <el-select v-model="editForm.scope" style="width: 220px">
            <el-option label="ai_prompt（提示词拦截）" value="ai_prompt" />
            <el-option label="全局" value="全局" />
            <el-option label="业务部门" value="业务部门" />
            <el-option label="技术部门" value="技术部门" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门范围">
          <el-input v-model="editForm.departments" />
        </el-form-item>
        <el-form-item label="用户组范围">
          <el-input v-model="editForm.userGroups" />
        </el-form-item>
        <el-form-item label="数据类型范围">
          <el-input v-model="editForm.dataTypes" />
        </el-form-item>
        <el-form-item label="规则JSON" prop="ruleContent">
          <el-input v-model="editForm.ruleContent" type="textarea" :rows="7" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updatePolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="auditDrawerVisible" title="策略变更审计日志" :size="isNarrowScreen ? '96%' : '52%'">
      <div class="audit-head">
        <div>策略：{{ auditTarget?.businessName || '-' }}</div>
        <div>策略ID：{{ auditTarget?.id || '-' }}</div>
      </div>
      <el-table :data="auditLogs" v-loading="auditLoading" empty-text="暂无审计记录" style="width: 100%">
        <el-table-column prop="id" label="申请ID" width="120" />
        <el-table-column prop="action" label="动作" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="发起人" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.requesterDisplay || '-' }}</template>
        </el-table-column>
        <el-table-column label="复核人" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.reviewerDisplay || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作者" width="190" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.operatorDisplay || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="180">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="复核时间" min-width="180">
          <template #default="scope">{{ formatTime(scope.row.approvedAt) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="180" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.approveNote || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { useUserStore } from '../store/user';
import { canManagePolicyStructure, canTogglePolicyStatus, canReviewGovernanceChange } from '../utils/roleBoundary';
import { getSession } from '../utils/auth';
import request from '../api/request';

const currentUsername = String(getSession()?.user?.username || '').trim().toLowerCase();
const canSubmitPolicyChange = currentUsername === 'admin';

async function promptOperatorConfirm(actionText) {
  const operatorPrompt = await ElMessageBox.prompt(`请输入当前账号密码确认${actionText}`, '治理变更发起确认', {
    inputType: 'password',
    inputPlaceholder: '请输入当前账号密码',
    inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  });
  return {
    confirmPassword: operatorPrompt.value,
  };
}

const userStore = useUserStore();
const router = useRouter();
const canManageStructure = computed(() => canManagePolicyStructure(userStore.userInfo) && canSubmitPolicyChange);
const canToggleStatus = computed(() => canTogglePolicyStatus(userStore.userInfo) && canSubmitPolicyChange);
const canViewAudit = computed(() => canReviewGovernanceChange(userStore.userInfo) || canManageStructure.value || canToggleStatus.value);

const tableRef = ref();
const policies = ref([]);
const auditIndexMap = ref(new Map());
const userDirectory = ref(new Map());
const loading = ref(false);
const saving = ref(false);
const batchLoading = ref(false);
const selectedPolicies = ref([]);
const showAdd = ref(false);
const showEdit = ref(false);
const query = ref({ name: '', status: '', policyType: '', scope: '' });
const pagination = ref({ current: 1, pageSize: 10 });
const addFormRef = ref();
const editFormRef = ref();

const auditDrawerVisible = ref(false);
const auditLoading = ref(false);
const auditLogs = ref([]);
const auditTarget = ref(null);
const isNarrowScreen = ref(false);

const defaultForm = () => ({
  id: null,
  name: '',
  policyType: 'MASKING',
  status: 'DRAFT',
  priority: 50,
  scope: 'ai_prompt',
  departments: '',
  userGroups: '',
  dataTypes: '',
  ruleContent: '{"keywords":[],"action":"mask"}'
});

const addForm = ref(defaultForm());
const editForm = ref(defaultForm());

const rules = {
  name: [{ required: true, message: '业务策略名称不能为空', trigger: 'blur' }],
  policyType: [{ required: true, message: '策略类型不能为空', trigger: 'change' }],
  status: [{ required: true, message: '策略状态不能为空', trigger: 'change' }],
  scope: [{ required: true, message: '生效范围不能为空', trigger: 'change' }],
  priority: [{ required: true, message: '优先级不能为空', trigger: 'change' }],
  ruleContent: [{ required: true, message: '规则内容不能为空', trigger: 'blur' }],
};

const normalizedPolicies = computed(() => {
  return (Array.isArray(policies.value) ? policies.value : []).map(item => enrichPolicy(item));
});

const displayPolicies = computed(() => {
  const all = normalizedPolicies.value;
  const realism = all.filter(item => !isGenericPolicyName(item.businessName || item.name));
  const source = realism.length >= 5 ? realism : all;
  const deduped = [];
  const seen = new Set();
  for (const row of source) {
    const signature = [
      String(row.businessName || row.name || '').trim(),
      String(row.scope || '').trim(),
      String(row.statusNormalized || '').trim(),
      String(row.effectiveScenario || '').trim(),
    ].join('|');
    if (seen.has(signature)) continue;
    seen.add(signature);
    deduped.push(row);
  }
  return deduped;
});

const displayTotal = computed(() => displayPolicies.value.length);

const pagedDisplayPolicies = computed(() => {
  const start = (pagination.value.current - 1) * pagination.value.pageSize;
  const end = start + pagination.value.pageSize;
  return displayPolicies.value.slice(start, end);
});

function syncViewport() {
  isNarrowScreen.value = typeof window !== 'undefined' ? window.innerWidth < 992 : false;
}

function normalizeStatus(status) {
  const value = String(status ?? '').trim().toUpperCase();
  if (['1', 'TRUE', 'ENABLED', 'ACTIVE', '启用'].includes(value)) return 'ENABLED';
  if (['0', 'FALSE', 'DISABLED', 'INACTIVE', '停用'].includes(value)) return 'DISABLED';
  return 'DRAFT';
}

function formatStatus(status) {
  if (status === 'ENABLED') return '启用';
  if (status === 'DISABLED') return '禁用';
  return '草稿';
}

function statusTagType(status) {
  if (status === 'ENABLED') return 'success';
  if (status === 'DISABLED') return 'info';
  return 'warning';
}

function formatPolicyType(type) {
  if (type === 'MASKING') return '脱敏';
  if (type === 'ACCESS_CONTROL') return '访问控制';
  if (type === 'EXPORT_LIMIT') return '导出限制';
  if (type === 'AUDIT_GOVERNANCE') return '审计治理';
  if (type === 'ALERT_GOVERNANCE') return '告警治理';
  return '未分类';
}

function priorityLevel(priority) {
  const value = Number(priority || 50);
  if (value <= 30) return 'high';
  if (value <= 70) return 'medium';
  return 'low';
}

function priorityTagType(priority) {
  const level = priorityLevel(priority);
  if (level === 'high') return 'danger';
  if (level === 'medium') return 'warning';
  return 'success';
}

function priorityLabel(priority) {
  const value = Number(priority || 50);
  const level = priorityLevel(value);
  if (level === 'high') return `高 ${value}`;
  if (level === 'medium') return `中 ${value}`;
  return `低 ${value}`;
}

function tryParseJson(raw) {
  const text = String(raw || '').trim();
  if (!text || !(text.startsWith('{') || text.startsWith('['))) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function splitList(text) {
  return String(text || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
}

function inferPolicyType(policy, ruleObj) {
  const raw = String(policy?.policyType || ruleObj?.policyType || ruleObj?.type || '').trim().toUpperCase();
  if (['MASKING', 'ACCESS_CONTROL', 'EXPORT_LIMIT', 'AUDIT_GOVERNANCE', 'ALERT_GOVERNANCE'].includes(raw)) {
    return raw;
  }
  const text = `${policy?.name || ''} ${policy?.ruleContent || ''}`.toLowerCase();
  if (text.includes('审计')) return 'AUDIT_GOVERNANCE';
  if (text.includes('告警')) return 'ALERT_GOVERNANCE';
  if (text.includes('导出')) return 'EXPORT_LIMIT';
  if (text.includes('访问') || text.includes('权限')) return 'ACCESS_CONTROL';
  return 'MASKING';
}

function normalizePriority(policy, ruleObj) {
  const v = Number(policy?.priority || ruleObj?.priority || 50);
  if (!Number.isFinite(v)) return 50;
  return Math.min(999, Math.max(1, Math.round(v)));
}

function normalizeScopeDetail(ruleObj) {
  const detail = ruleObj?.scopeDetail || {};
  return {
    departments: Array.isArray(detail.departments) ? detail.departments : [],
    userGroups: Array.isArray(detail.userGroups) ? detail.userGroups : [],
    dataTypes: Array.isArray(detail.dataTypes) ? detail.dataTypes : [],
  };
}

function splitCsv(value) {
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
}

function formatEffectiveScope(policy) {
  const detail = policy.scopeDetail || {};
  const parts = [];
  if ((detail.departments || []).length) {
    parts.push(`部门:${detail.departments.join('、')}`);
  }
  if ((detail.userGroups || []).length) {
    parts.push(`用户组:${detail.userGroups.join('、')}`);
  }
  if ((detail.dataTypes || []).length) {
    parts.push(`数据类型:${detail.dataTypes.join('、')}`);
  }
  if (parts.length === 0) {
    return '默认全量范围';
  }
  return parts.join(' | ');
}

function normalizeBusinessName(name) {
  return String(name || '').trim();
}

function isGenericPolicyName(name) {
  const normalized = normalizeBusinessName(name);
  if (!normalized) return true;
  return /^治理策略[-_\s]*\d+$/i.test(normalized);
}

function inferScopeLabel(scope, scopeDetail) {
  if (String(scope || '').trim() === 'ai_prompt') {
    return '敏感AI指令';
  }
  if ((scopeDetail?.dataTypes || []).length > 0) {
    return `${scopeDetail.dataTypes[0]}`;
  }
  if ((scopeDetail?.departments || []).length > 0) {
    return `${scopeDetail.departments[0]}数据`;
  }
  return '核心数据';
}

function deriveBusinessName(policy, ruleObj, policyType, scopeDetail) {
  const profile = deriveEngineProfile(policy, ruleObj, policyType, scopeDetail);
  if (profile?.businessName) {
    return profile.businessName;
  }
  const original = normalizeBusinessName(policy?.name);
  if (!isGenericPolicyName(original)) {
    return original;
  }
  const target = inferScopeLabel(policy?.scope, scopeDetail);
  if (policyType === 'MASKING') {
    return `${target}脱敏策略`;
  }
  if (policyType === 'ACCESS_CONTROL') {
    return `${target}访问控制策略`;
  }
  if (policyType === 'EXPORT_LIMIT') {
    const maxRows = Number(ruleObj?.maxRows || ruleObj?.exportLimit || 0);
    if (Number.isFinite(maxRows) && maxRows > 0) {
      return `${target}单次导出上限${maxRows}条策略`;
    }
    return `${target}导出次数限制策略`;
  }
  return original || `策略-${policy?.id || 'N/A'}`;
}

function deriveEngineProfile(policy, ruleObj, policyType, scopeDetail) {
  const normalizedName = String(policy?.name || '').trim();
  if (normalizedName === '数据外发脱敏策略') {
    return {
      businessName: '数据外发脱敏策略',
      relatedModules: ['数据导出', '脱敏预览', '审计日志'],
      effectiveScenario: '文件外发时自动检测手机号、身份证并脱敏，脱敏后才允许导出并写入审计日志。',
      impact: { path: '/audit-center', query: { operation: 'export', permissionId: 'data:export' } },
    };
  }
  if (normalizedName === '敏感词拦截策略') {
    return {
      businessName: '敏感词拦截策略',
      relatedModules: ['AI对话', '安全告警', '审计日志'],
      effectiveScenario: 'AI对话中识别违规指令并拦截，自动生成安全告警并沉淀审计证据。',
      impact: { path: '/operations-command', query: { severity: 'high', keyword: '违规指令' } },
    };
  }
  if (normalizedName === '导出次数管控策略' || normalizedName === '导出次数控制策略') {
    return {
      businessName: '导出次数管控策略',
      relatedModules: ['数据导出', '风险预警', '审计日志'],
      effectiveScenario: '限制用户单日导出次数，超过阈值立即阻断并推送风险预警，同时记录审计日志。',
      impact: { path: '/audit-center', query: { operation: 'export', permissionId: 'data:export' } },
    };
  }
  if (normalizedName === 'AI对话审计策略') {
    return {
      businessName: 'AI对话审计策略',
      relatedModules: ['AI对话', '审计日志', '员工AI行为监控'],
      effectiveScenario: '对员工AI对话行为持续留痕，支持按用户、时间、命中规则进行审计追溯。',
      impact: { path: '/audit-center', query: { operation: 'ai', permissionId: 'ai:prompt' } },
    };
  }
  if (normalizedName === '权限变更告警策略') {
    return {
      businessName: '权限变更告警策略',
      relatedModules: ['权限管理', '安全告警', '审计日志'],
      effectiveScenario: '角色与权限发生高风险变更时实时告警，并关联审批与审计链路进行复核。',
      impact: { path: '/operations-command', query: { keyword: '权限变更', severity: 'high' } },
    };
  }

  const nameText = String(policy?.name || '').toLowerCase();
  const ruleText = String(policy?.ruleContent || '').toLowerCase();
  const text = `${nameText} ${ruleText}`;
  const action = String(ruleObj?.action || '').trim().toLowerCase();
  const scope = String(policy?.scope || '').trim();
  const hasAiPromptSignal = scope === 'ai_prompt' || /(prompt|对话|指令|ai|模型)/.test(text);
  const hasExportSignal = /(导出|外发|下载|文件)/.test(text);
  const hasApprovalSignal = /(权限|角色|审批|变更|governance)/.test(text);
  const hasBehaviorSignal = /(行为|异常|审计|anomaly)/.test(text);

  if (hasApprovalSignal) {
    return {
      businessName: '权限变更审批策略',
      relatedModules: ['流程审批', '安全告警', '审计日志'],
      effectiveScenario: '发生权限/角色变更申请时触发治理审批与审计记录。',
      impact: { path: '/operations-command', query: { keyword: '权限变更', severity: 'high' } },
    };
  }
  if (policyType === 'EXPORT_LIMIT' || (hasExportSignal && action === 'block')) {
    return {
      businessName: '导出次数控制策略',
      relatedModules: ['数据导出', '风险预警', '审计日志'],
      effectiveScenario: '导出操作超过策略阈值时触发限制并推送风险预警，同时生成可追溯审计记录。',
      impact: { path: '/audit-center', query: { operation: 'export' } },
    };
  }
  if (hasAiPromptSignal && (action === 'block' || policyType === 'ACCESS_CONTROL')) {
    return {
      businessName: '敏感词拦截策略',
      relatedModules: ['AI对话', '安全告警', '审计日志'],
      effectiveScenario: 'AI对话命中违规指令后立即拦截，并记录行为审计与安全告警事件。',
      impact: { path: '/operations-command', query: { severity: 'high', keyword: '违规指令' } },
    };
  }
  if (hasBehaviorSignal) {
    return {
      businessName: '员工AI行为审计策略',
      relatedModules: ['AI行为分析', '审计日志'],
      effectiveScenario: '识别员工AI使用行为并持续沉淀审计证据链。',
      impact: { path: '/audit-center', query: { operation: 'ai' } },
    };
  }
  return {
    businessName: '数据外发脱敏策略',
    relatedModules: ['脱敏预览', '数据导出', '审计日志'],
    effectiveScenario: '文件外发时自动检测敏感字段并执行脱敏后再输出。',
    impact: { path: '/audit-center', query: { operation: 'export' } },
  };
}

function buildRuleHighlights(policy, ruleObj, policyType) {
  const tags = [];
  const dataTypes = splitCsv(policy?.scopeDataTypes);
  const keywords = Array.isArray(ruleObj?.keywords) ? ruleObj.keywords : [];
  const conditions = Array.isArray(ruleObj?.conditions) ? ruleObj.conditions : [];
  if (policyType === 'MASKING') {
    if (dataTypes.length) {
      dataTypes.slice(0, 3).forEach(item => tags.push(`${item}脱敏`));
    }
    if (keywords.length) {
      keywords.slice(0, 2).forEach(item => tags.push(`${item}命中`));
    }
  }
  if (policyType === 'EXPORT_LIMIT') {
    const maxRows = Number(ruleObj?.maxRows || ruleObj?.exportLimit || 0);
    if (Number.isFinite(maxRows) && maxRows > 0) {
      tags.push(`单次导出上限${maxRows}条`);
    }
  }
  if (policyType === 'ACCESS_CONTROL') {
    if (conditions.length) {
      tags.push(`访问条件${conditions.length}项`);
    }
    if (!conditions.length && keywords.length) {
      tags.push(`关键规则${keywords.length}项`);
    }
  }
  const action = String(ruleObj?.action || '').trim();
  if (action) {
    if (action === 'mask') tags.push('命中后脱敏');
    else if (action === 'block') tags.push('命中后拦截');
    else tags.push(`动作:${action}`);
  }
  if (!tags.length) {
    tags.push('规则详情见悬停');
  }
  return Array.from(new Set(tags)).slice(0, 4);
}

function buildRuleFriendlyText(policy, ruleObj, priority) {
  if (!ruleObj) {
    return `优先级${priority}，规则原文：${String(policy?.ruleContent || '').slice(0, 200)}`;
  }
  const keywords = Array.isArray(ruleObj.keywords) ? ruleObj.keywords : [];
  const conditions = Array.isArray(ruleObj.conditions) ? ruleObj.conditions : [];
  const action = String(ruleObj.action || '').trim() || '按策略执行';
  const parts = [];
  if (keywords.length > 0) {
    parts.push(`关键词：${keywords.slice(0, 5).join('、')}`);
  }
  if (conditions.length > 0) {
    const compactConditions = conditions.slice(0, 3).map(c => `${c?.field || '字段'}${c?.op || ''}${c?.value || ''}`);
    parts.push(`条件：${compactConditions.join('；')}`);
  }
  if (ruleObj.maxRows || ruleObj.exportLimit) {
    const maxRows = Number(ruleObj.maxRows || ruleObj.exportLimit || 0);
    if (Number.isFinite(maxRows) && maxRows > 0) {
      parts.push(`导出上限：${maxRows}条`);
    }
  }
  parts.push(`动作：${action}`);
  parts.push(`优先级：${priority}`);
  return parts.join(' | ');
}

function getAuditKey(policyId) {
  return String(policyId == null ? '' : policyId);
}

function enrichPolicy(policy) {
  const ruleObj = tryParseJson(policy?.ruleContent);
  const policyType = inferPolicyType(policy, ruleObj);
  const priority = normalizePriority(policy, ruleObj);
  const fromRuleScope = normalizeScopeDetail(ruleObj || {});
  const scopeDetail = {
    departments: splitCsv(policy?.scopeDepartments).length > 0 ? splitCsv(policy?.scopeDepartments) : fromRuleScope.departments,
    userGroups: splitCsv(policy?.scopeUserGroups).length > 0 ? splitCsv(policy?.scopeUserGroups) : fromRuleScope.userGroups,
    dataTypes: splitCsv(policy?.scopeDataTypes).length > 0 ? splitCsv(policy?.scopeDataTypes) : fromRuleScope.dataTypes,
  };
  const statusNormalized = normalizeStatus(policy?.status);
  const businessName = deriveBusinessName(policy, ruleObj, policyType, scopeDetail);
  const profile = deriveEngineProfile(policy, ruleObj, policyType, scopeDetail);
  const ruleHighlights = buildRuleHighlights(policy, ruleObj, policyType);
  const audit = auditIndexMap.value.get(getAuditKey(policy?.id)) || null;
  return {
    ...policy,
    businessName,
    policyType,
    priority,
    scopeDetail,
    statusNormalized,
    ruleHighlights,
    ruleFriendlyText: buildRuleFriendlyText(policy, ruleObj, priority),
    lastModifier: audit?.operator || '系统',
    lastModifiedAt: audit?.time || policy?.updateTime || policy?.createTime || null,
    relatedModules: profile?.relatedModules || [],
    effectiveScenario: profile?.effectiveScenario || '按策略规则执行',
    impactTarget: profile?.impact || null,
  };
}

function viewPolicyImpact(row) {
  const target = row?.impactTarget;
  if (!target?.path) {
    ElMessage.warning('未识别到该策略的关联模块入口');
    return;
  }
  router.push({ path: target.path, query: target.query || {} });
}

function openAdd() {
  if (!canSubmitPolicyChange) {
    ElMessage.error('仅主治理管理员(admin)可发起策略结构变更');
    return;
  }
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能新增策略');
    return;
  }
  addForm.value = defaultForm();
  showAdd.value = true;
}

function prepareFormFromPolicy(row) {
  const ruleObj = tryParseJson(row?.ruleContent) || {};
  const detail = normalizeScopeDetail(ruleObj);
  return {
    id: row.id,
    name: row.businessName || row.name || '',
    policyType: row.policyType || inferPolicyType(row, ruleObj),
    status: row.statusNormalized || normalizeStatus(row.status),
    priority: row.priority || normalizePriority(row, ruleObj),
    scope: row.scope || 'ai_prompt',
    departments: String(row?.scopeDepartments || (detail.departments || []).join(',')),
    userGroups: String(row?.scopeUserGroups || (detail.userGroups || []).join(',')),
    dataTypes: String(row?.scopeDataTypes || (detail.dataTypes || []).join(',')),
    ruleContent: String(row.ruleContent || '{"keywords":[],"action":"mask"}')
  };
}

function buildPayloadFromForm(form) {
  const parsedRule = tryParseJson(form.ruleContent) || {};
  parsedRule.policyType = form.policyType;
  parsedRule.priority = Number(form.priority || 50);
  parsedRule.scopeDetail = {
    departments: splitList(form.departments),
    userGroups: splitList(form.userGroups),
    dataTypes: splitList(form.dataTypes),
  };

  if (Array.isArray(parsedRule.keywords)) {
    parsedRule.keywords = parsedRule.keywords.map(item => String(item || '').trim()).filter(Boolean);
  }
  if (Array.isArray(parsedRule.conditions)) {
    parsedRule.conditions = parsedRule.conditions.map((item, index) => ({
      ...item,
      index: Number(item?.index || index + 1),
    }));
  }

  return {
    id: form.id,
    name: form.name,
    policyType: form.policyType,
    priority: Number(form.priority || 50),
    scope: form.scope,
    scopeDepartments: splitList(form.departments).join(','),
    scopeUserGroups: splitList(form.userGroups).join(','),
    scopeDataTypes: splitList(form.dataTypes).join(','),
    status: form.status,
    ruleContent: JSON.stringify(parsedRule),
  };
}

async function fetchPolicyAuditIndex() {
  if (!canViewAudit.value) {
    auditIndexMap.value = new Map();
    return;
  }
  try {
    const res = await request.get('/governance-change/page', {
      params: {
        module: 'POLICY',
        page: 1,
        pageSize: 200,
      },
    });
    const list = Array.isArray(res?.list) ? res.list : [];
    const map = new Map();
    list.forEach(item => {
      const id = extractPolicyId(item);
      if (!id) return;
      const key = getAuditKey(id);
      const existing = map.get(key);
      const itemTime = new Date(item?.approvedAt || item?.createTime || 0).getTime();
      const existingTime = new Date(existing?.time || 0).getTime();
      if (!existing || itemTime > existingTime) {
        const requester = resolveUserDisplay(item?.requesterId);
        const reviewer = resolveUserDisplay(item?.approverId);
        const operator = [requester, reviewer].filter(Boolean).join(' -> ') || '治理服务';
        map.set(key, {
          operator,
          time: item?.approvedAt || item?.createTime || null,
        });
      }
    });
    auditIndexMap.value = map;
  } catch {
    auditIndexMap.value = new Map();
  }
}

function extractPolicyId(change) {
  if (change?.targetId != null) {
    return change.targetId;
  }
  const payload = tryParseJson(change?.payloadJson);
  if (payload?.id != null) {
    return payload.id;
  }
  return null;
}

async function fetchPolicies() {
  loading.value = true;
  try {
    await ensureUserDirectory();
    const res = await request.get('/policy/page', {
      params: {
        page: 1,
        pageSize: 500,
        name: String(query.value.name || '').trim() || undefined,
        status: query.value.status || undefined,
        policyType: query.value.policyType || undefined,
        scope: query.value.scope || undefined,
      },
    });
    const all = Array.isArray(res?.list) ? res.list : [];
    policies.value = all;
    pagination.value.current = 1;
    await fetchPolicyAuditIndex();
    selectedPolicies.value = [];
    tableRef.value?.clearSelection?.();
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

function editPolicy(row) {
  if (!canSubmitPolicyChange) {
    ElMessage.error('仅主治理管理员(admin)可发起策略结构变更');
    return;
  }
  if (!canManageStructure.value) {
    ElMessage.error('当前身份仅可执行策略启停，不能编辑策略结构');
    return;
  }
  editForm.value = prepareFormFromPolicy(row);
  showEdit.value = true;
}

async function addPolicy() {
  if (!canSubmitPolicyChange || !canManageStructure.value || !addFormRef.value) {
    ElMessage.error('当前账号不能新增策略');
    return;
  }
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload = buildPayloadFromForm(addForm.value);
      const reviewPayload = await promptOperatorConfirm('保存策略');
      await request.post('/governance-change/submit', {
        module: 'POLICY',
        action: 'ADD',
        targetId: null,
        payloadJson: JSON.stringify(payload),
        confirmPassword: reviewPayload.confirmPassword,
      });
      ElMessage.success('策略新增申请已提交待复核');
      showAdd.value = false;
      fetchPolicies();
    } catch (err) {
      if (err === 'cancel' || err === 'close') return;
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}

async function updatePolicy() {
  if (!canSubmitPolicyChange || !canManageStructure.value || !editFormRef.value) {
    ElMessage.error('当前账号不能编辑策略');
    return;
  }
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload = buildPayloadFromForm(editForm.value);
      const reviewPayload = await promptOperatorConfirm('更新策略');
      await request.post('/governance-change/submit', {
        module: 'POLICY',
        action: 'UPDATE',
        targetId: editForm.value.id,
        payloadJson: JSON.stringify(payload),
        confirmPassword: reviewPayload.confirmPassword,
      });
      ElMessage.success('策略更新申请已提交待复核');
      showEdit.value = false;
      fetchPolicies();
    } catch (err) {
      if (err === 'cancel' || err === 'close') return;
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}

async function deletePolicy(id) {
  if (!canSubmitPolicyChange || !canManageStructure.value) {
    ElMessage.error('当前账号不能删除策略');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该策略吗？', '提示', { type: 'warning' });
    const reviewPayload = await promptOperatorConfirm('删除策略');
    await request.post('/governance-change/submit', {
      module: 'POLICY',
      action: 'DELETE',
      targetId: id,
      payloadJson: JSON.stringify({ id }),
      confirmPassword: reviewPayload.confirmPassword,
    });
    ElMessage.success('策略删除申请已提交待复核');
    fetchPolicies();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '删除失败');
    }
  }
}

async function togglePolicyStatus(row) {
  if (!canToggleStatus.value) {
    ElMessage.error('当前身份无策略启停权限');
    return;
  }
  const targetStatus = row.statusNormalized === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  const actionLabel = targetStatus === 'ENABLED' ? '启用' : '禁用';
  try {
    if (!canSubmitPolicyChange) {
      ElMessage.error('仅主治理管理员(admin)可发起策略启停变更');
      return;
    }
    const reviewPayload = await promptOperatorConfirm(`${actionLabel}策略`);
    await request.post('/governance-change/submit', {
      module: 'POLICY',
      action: 'UPDATE',
      targetId: row.id,
      payloadJson: JSON.stringify({ enabled: targetStatus === 'ENABLED', status: targetStatus }),
      confirmPassword: reviewPayload.confirmPassword,
    });
    ElMessage.success(`${actionLabel}申请已提交待复核`);
    fetchPolicies();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || `${actionLabel}失败`);
  }
}

async function batchToggleStatus(enable) {
  if (!canToggleStatus.value || selectedPolicies.value.length === 0) {
    return;
  }
  const targetStatus = enable ? 'ENABLED' : 'DISABLED';
  const actionLabel = enable ? '启用' : '禁用';
  try {
    await ElMessageBox.confirm(`确认对已选 ${selectedPolicies.value.length} 条策略执行批量${actionLabel}吗？`, '批量操作确认', { type: 'warning' });
    const reviewPayload = await promptOperatorConfirm(`批量${actionLabel}策略`);
    batchLoading.value = true;
    const jobs = selectedPolicies.value.map(item => request.post('/governance-change/submit', {
      module: 'POLICY',
      action: 'UPDATE',
      targetId: item.id,
      payloadJson: JSON.stringify({ enabled: enable, status: targetStatus }),
      confirmPassword: reviewPayload.confirmPassword,
    }));
    const result = await Promise.allSettled(jobs);
    const successCount = result.filter(item => item.status === 'fulfilled').length;
    const failedCount = result.length - successCount;
    if (failedCount === 0) {
      ElMessage.success(`批量${actionLabel}申请已全部提交待复核`);
    } else {
      ElMessage.warning(`批量${actionLabel}完成：成功 ${successCount} 条，失败 ${failedCount} 条`);
    }
    fetchPolicies();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || `批量${actionLabel}失败`);
    }
  } finally {
    batchLoading.value = false;
  }
}

function handleSelectionChange(rows) {
  selectedPolicies.value = Array.isArray(rows) ? rows : [];
}

function clearSelection() {
  selectedPolicies.value = [];
  tableRef.value?.clearSelection?.();
}

function onPageChange(page) {
  pagination.value.current = page;
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}

async function openAuditDrawer(row) {
  auditTarget.value = row;
  auditDrawerVisible.value = true;
  auditLoading.value = true;
  try {
    await ensureUserDirectory();
    const res = await request.get('/governance-change/page', {
      params: {
        module: 'POLICY',
        page: 1,
        pageSize: 100,
      },
    });
    const list = Array.isArray(res?.list) ? res.list : [];
    let parsedLogs = list
      .filter(item => String(extractPolicyId(item) || '') === String(row.id || ''))
      .map(item => ({
        ...item,
        action: formatAuditAction(item?.action),
        status: formatAuditStatus(item?.status),
        requesterDisplay: resolveUserDisplay(item?.requesterId),
        reviewerDisplay: resolveUserDisplay(item?.approverId),
        operatorDisplay: [resolveUserDisplay(item?.requesterId), resolveUserDisplay(item?.approverId)].filter(Boolean).join(' -> ') || '治理服务',
      }))
      .sort((a, b) => new Date(b?.createTime || 0).getTime() - new Date(a?.createTime || 0).getTime());
    if (!parsedLogs.length) {
      const fallbackLogs = await loadFallbackAuditLogs(row);
      parsedLogs = fallbackLogs;
    }
    auditLogs.value = parsedLogs;
  } catch (err) {
    auditLogs.value = [];
    ElMessage.error(err?.message || '加载审计日志失败');
  } finally {
    auditLoading.value = false;
  }
}

async function loadFallbackAuditLogs(row) {
  try {
    const result = await request.get('/audit-log/search', {
      params: {
        operation: 'policy',
      },
    });
    const list = Array.isArray(result) ? result : [];
    const policyName = String(row?.businessName || row?.name || '').trim();
    const filtered = list.filter(item => {
      const input = String(item?.inputOverview || '').toLowerCase();
      const output = String(item?.outputOverview || '').toLowerCase();
      const op = String(item?.operation || '').toLowerCase();
      if (policyName && (input.includes(policyName.toLowerCase()) || output.includes(policyName.toLowerCase()))) {
        return true;
      }
      return op.includes('policy');
    });
    return filtered.slice(0, 20).map(item => {
      const requester = resolveUserDisplay(item?.userId);
      const isSystem = !requester;
      return {
        id: item?.id,
        action: '系统变更',
        status: String(item?.result || '').toLowerCase() === 'success' ? '已通过' : '已驳回',
        requesterDisplay: requester || '系统任务',
        reviewerDisplay: '-',
        operatorDisplay: requester || '系统任务',
        createTime: item?.operationTime || item?.createTime,
        approvedAt: item?.operationTime || item?.createTime,
        approveNote: isSystem ? '系统任务自动写入策略审计轨迹' : (item?.outputOverview || item?.inputOverview || '-'),
      };
    });
  } catch {
    return [];
  }
}

async function ensureUserDirectory() {
  if (userDirectory.value.size > 0) {
    return;
  }
  try {
    const users = await request.get('/user/list');
    const map = new Map();
    (Array.isArray(users) ? users : []).forEach(item => {
      if (item?.id != null) {
        map.set(String(item.id), item);
      }
    });
    userDirectory.value = map;
  } catch {
    userDirectory.value = new Map();
  }
}

function resolveUserDisplay(userId) {
  if (userId == null) return '';
  const user = userDirectory.value.get(String(userId));
  if (user?.username) {
    return `${user.username}(#${userId})`;
  }
  return `用户#${userId}`;
}

function formatAuditAction(action) {
  const value = String(action || '').toUpperCase();
  if (value === 'ADD') return '新增';
  if (value === 'UPDATE') return '修改';
  if (value === 'DELETE') return '删除';
  return value || '-';
}

function formatAuditStatus(status) {
  const value = String(status || '').toUpperCase();
  if (value === 'PENDING') return '待复核';
  if (value === 'APPROVED') return '已通过';
  if (value === 'REJECTED') return '已驳回';
  if (value === 'REVOKED') return '已撤回';
  return value || '-';
}

onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport, { passive: true });
  fetchPolicies();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>

<style scoped>
.policy-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.policy-count {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.engine-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--color-border-light);
  background: var(--color-fill-light);
  color: var(--color-text-primary);
  font-size: 13px;
}

.policy-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0 12px;
}

.selection-count {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.policy-pagination {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.audit-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--color-text-secondary);
}

.rule-chip-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  max-height: 48px;
  overflow: hidden;
}

.rule-chip {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scenario-cell {
  white-space: normal;
  line-height: 1.5;
}

:deep(.el-table .cell) {
  word-break: break-word;
}

@media (max-width: 900px) {
  .policy-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .policy-toolbar {
    flex-wrap: wrap;
  }
}
</style>