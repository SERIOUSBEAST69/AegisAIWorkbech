<template>
  <el-card class="mgmt-table-exempt policy-page">
    <div class="policy-head">
      <div>
        <h2>合规策略列表</h2>
        <p>策略内容已简化为：策略名称、策略描述、是否启用、风险等级</p>
      </div>
      <el-button type="primary" :disabled="!isAdmin" @click="openCreate">新增策略</el-button>
    </div>

    <el-form :inline="true" @submit.prevent class="query-form">
      <el-form-item label="策略名称">
        <el-input v-model="query.name" clearable placeholder="按策略名称筛选" />
      </el-form-item>
      <el-form-item label="启用状态">
        <el-select v-model="query.enabled" clearable style="width: 140px" placeholder="全部">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="风险等级">
        <el-select v-model="query.riskLevel" clearable style="width: 140px" placeholder="全部">
          <el-option label="高" value="HIGH" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="低" value="LOW" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchPolicies">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="pagedPolicies" row-key="id" v-loading="loading" style="width: 100%">
      <el-table-column label="序号" width="70">
        <template #default="scope">
          {{ (pagination.current - 1) * pagination.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column prop="displayId" label="ID" width="120" />
      <el-table-column prop="name" label="策略名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="策略描述" min-width="340" show-overflow-tooltip />
      <el-table-column label="是否启用" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风险等级" width="110">
        <template #default="scope">
          <el-tag :type="riskTagType(scope.row.riskLevel)">{{ riskText(scope.row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近修改" min-width="180">
        <template #default="scope">
          {{ formatTime(scope.row.lastModifiedAt || scope.row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="scope">
          <el-space>
            <el-button size="small" :disabled="!canOperatePolicy(scope.row)" @click="openEdit(scope.row)">编辑</el-button>
            <el-button
              size="small"
              :type="scope.row.enabled ? 'warning' : 'success'"
              :disabled="!canOperatePolicy(scope.row)"
              @click="toggleEnabled(scope.row)"
            >
              {{ scope.row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="!canOperatePolicy(scope.row) || scope.row.enabled"
              @click="removePolicy(scope.row)"
            >删除</el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div class="policy-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="filteredPolicies.length"
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
      />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增策略' : '编辑策略'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="策略名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="策略描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="600" show-word-limit />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-select v-model="form.riskLevel" style="width: 160px">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { getSession } from '../utils/auth';

const roleCode = String(getSession()?.user?.roleCode || '').trim().toUpperCase();
const username = String(getSession()?.user?.username || '').trim().toLowerCase();
const isAdmin = username === 'admin' || roleCode === 'ADMIN' || roleCode === 'ADMIN_OPS';

const canOperatePolicy = (policy) => {
  return isAdmin && (policy.name === '手机号脱敏' || policy.name === 'mobile-phone-masking' || policy.name === '手机号敏感脱敏');
};

const loading = ref(false);
const saving = ref(false);
const policies = ref([]);
const formRef = ref();

const query = reactive({
  name: '',
  enabled: null,
  riskLevel: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
});

const dialog = reactive({
  visible: false,
  mode: 'create',
});

const form = reactive({
  id: null,
  name: '',
  description: '',
  enabled: true,
  riskLevel: 'MEDIUM',
});

const rules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入策略描述', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
};

const filteredPolicies = computed(() => {
  const keyword = String(query.name || '').trim().toLowerCase();
  return policies.value.filter((item) => {
    if (keyword && !String(item.name || '').toLowerCase().includes(keyword)) return false;
    if (query.enabled !== null && Boolean(item.enabled) !== query.enabled) return false;
    if (query.riskLevel && item.riskLevel !== query.riskLevel) return false;
    return true;
  }).map((item, index) => ({
    ...item,
    displayId: index + 1,
  }));
});

const pagedPolicies = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  return filteredPolicies.value.slice(start, start + pagination.pageSize);
});

function riskText(level) {
  if (level === 'HIGH') return '高';
  if (level === 'LOW') return '低';
  return '中';
}

function riskTagType(level) {
  if (level === 'HIGH') return 'danger';
  if (level === 'LOW') return 'success';
  return 'warning';
}

function formatTime(value) {
  if (!value) return '-';
  const dt = new Date(value);
  if (Number.isNaN(dt.getTime())) return String(value);
  return dt.toLocaleString('zh-CN', { hour12: false });
}

function parseRuleContent(ruleContent) {
  let parsed = {};
  try {
    parsed = JSON.parse(String(ruleContent || '{}')) || {};
  } catch {
    parsed = {};
  }
  const description = String(parsed.description || parsed.desc || '').trim();
  const riskLevel = String(parsed.riskLevel || 'MEDIUM').trim().toUpperCase();
  return {
    description,
    riskLevel: ['HIGH', 'MEDIUM', 'LOW'].includes(riskLevel) ? riskLevel : 'MEDIUM',
  };
}

function normalizePolicy(row) {
  const statusRaw = String(row?.status ?? '').trim().toUpperCase();
  const enabled = statusRaw === '1' || statusRaw === 'ENABLED' || statusRaw === 'TRUE';
  const parsed = parseRuleContent(row?.ruleContent);
  return {
    id: row?.id,
    displayId: 0,
    name: String(row?.name || '').trim(),
    description: parsed.description || '未设置描述',
    enabled,
    riskLevel: parsed.riskLevel,
    updateTime: row?.updateTime,
    lastModifiedAt: row?.lastModifiedAt,
    raw: row,
  };
}

function isSensitiveWordPolicyName(name) {
  const text = String(name || '').trim();
  if (!text) return false;
  return /(敏感词|MASKING|提示词)/i.test(text) && /(过滤|拦截|MASK|策略|规则)/i.test(text);
}

function riskWeight(level) {
  if (level === 'HIGH') return 3;
  if (level === 'MEDIUM') return 2;
  return 1;
}

function mergeSensitiveWordPolicies(items) {
  const list = Array.isArray(items) ? items : [];
  const sensitive = list.filter(item => isSensitiveWordPolicyName(item?.name));
  const others = list.filter(item => !isSensitiveWordPolicyName(item?.name));

  if (sensitive.length <= 1) {
    return list;
  }

  const representative = [...sensitive].sort((a, b) => {
    const ta = new Date(String(b?.lastModifiedAt || b?.updateTime || '').replace(' ', 'T')).getTime();
    const tb = new Date(String(a?.lastModifiedAt || a?.updateTime || '').replace(' ', 'T')).getTime();
    return (Number.isNaN(ta) ? 0 : ta) - (Number.isNaN(tb) ? 0 : tb);
  })[0] || sensitive[0];

  const mergedDescription = [...new Set(
    sensitive
      .map(item => String(item?.description || '').trim())
      .filter(Boolean)
  )].join('；');

  const mergedRiskLevel = sensitive.reduce((current, item) => {
    return riskWeight(item?.riskLevel) > riskWeight(current) ? item.riskLevel : current;
  }, representative?.riskLevel || 'MEDIUM');

  const merged = {
    ...representative,
    name: '敏感词过滤规则',
    description: mergedDescription || representative?.description || '敏感词过滤策略',
    riskLevel: mergedRiskLevel,
    enabled: sensitive.some(item => Boolean(item?.enabled)),
  };

  return [merged, ...others];
}

function toSavePayload(model) {
  return {
    id: model.id,
    name: String(model.name || '').trim(),
    policyType: 'MASKING',
    scope: '全局',
    status: model.enabled ? 'ENABLED' : 'DISABLED',
    priority: model.riskLevel === 'HIGH' ? 30 : model.riskLevel === 'LOW' ? 80 : 50,
    scopeDepartments: '',
    scopeUserGroups: '',
    scopeDataTypes: '',
    ruleContent: JSON.stringify({
      description: String(model.description || '').trim(),
      riskLevel: model.riskLevel,
      enabled: Boolean(model.enabled),
    }),
  };
}

async function collectOperatorConfirmPassword(actionText) {
  const prompt = await ElMessageBox.prompt(`请输入当前账号密码确认发起${actionText}`, '治理变更发起确认', {
    inputType: 'password',
    inputPlaceholder: '当前账号密码',
    inputValidator: (value) => Boolean(String(value || '').trim()) || '密码不能为空',
    confirmButtonText: '确认发起',
    cancelButtonText: '取消',
  });

  return {
    confirmPassword: prompt.value,
  };
}

function fillForm(row) {
  form.id = row?.id || null;
  form.name = String(row?.name || '').trim();
  form.description = String(row?.description || '').trim();
  form.enabled = Boolean(row?.enabled);
  form.riskLevel = ['HIGH', 'MEDIUM', 'LOW'].includes(row?.riskLevel) ? row.riskLevel : 'MEDIUM';
}

function openCreate() {
  fillForm({ id: null, name: '', description: '', enabled: true, riskLevel: 'MEDIUM' });
  dialog.mode = 'create';
  dialog.visible = true;
}

function openEdit(row) {
  fillForm(row);
  dialog.mode = 'edit';
  dialog.visible = true;
}

async function fetchPolicies() {
  loading.value = true;
  try {
    const data = await request.get('/policy/page', {
      params: {
        page: 1,
        pageSize: 500,
      },
    });
    const list = Array.isArray(data?.list) ? data.list : [];
    const normalized = list.map(normalizePolicy);
    const merged = mergeSensitiveWordPolicies(normalized);
    policies.value = merged
      .sort((a, b) => {
        const ta = new Date(String(b?.lastModifiedAt || b?.updateTime || '').replace(' ', 'T')).getTime();
        const tb = new Date(String(a?.lastModifiedAt || a?.updateTime || '').replace(' ', 'T')).getTime();
        return (Number.isNaN(ta) ? 0 : ta) - (Number.isNaN(tb) ? 0 : tb);
      });
    pagination.current = 1;
  } catch (error) {
    ElMessage.error(error?.message || '加载策略列表失败');
  } finally {
    loading.value = false;
  }
}

async function submitForm() {
  if (!isAdmin || !formRef.value) return;
  await formRef.value.validate();
  const creds = await collectOperatorConfirmPassword(dialog.mode === 'create' ? '新增策略' : '更新策略');
  saving.value = true;
  try {
    const policyPayload = toSavePayload(form);
    await request.post('/governance-change/submit', {
      module: 'POLICY',
      action: dialog.mode === 'create' ? 'ADD' : 'UPDATE',
      targetId: dialog.mode === 'create' ? null : form.id,
      payloadJson: JSON.stringify(policyPayload),
      confirmPassword: creds.confirmPassword,
    });
    ElMessage.success(dialog.mode === 'create' ? '新增策略申请已提交待复核' : '更新策略申请已提交待复核');
    dialog.visible = false;
    await fetchPolicies();
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function toggleEnabled(row) {
  if (!isAdmin) return;
  const nextEnabled = !row.enabled;
  const creds = await collectOperatorConfirmPassword(nextEnabled ? '启用策略' : '禁用策略');
  try {
    const updatePayload = {
      ...toSavePayload({ ...row, enabled: nextEnabled }),
      id: row.id,
      enabled: nextEnabled,
      status: nextEnabled ? 'ENABLED' : 'DISABLED',
    };
    await request.post('/governance-change/submit', {
      module: 'POLICY',
      action: 'UPDATE',
      targetId: row.id,
      payloadJson: JSON.stringify(updatePayload),
      confirmPassword: creds.confirmPassword,
    });
    ElMessage.success(nextEnabled ? '启用策略申请已提交待复核' : '禁用策略申请已提交待复核');
    await fetchPolicies();
  } catch (error) {
    ElMessage.error(error?.message || '状态变更失败');
  }
}

async function removePolicy(row) {
  if (!isAdmin) return;
  await ElMessageBox.confirm(`确认删除策略「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  });
  const creds = await collectOperatorConfirmPassword('删除策略');
  try {
    await request.post('/governance-change/submit', {
      module: 'POLICY',
      action: 'DELETE',
      targetId: row.id,
      payloadJson: JSON.stringify({ id: row.id, name: row.name }),
      confirmPassword: creds.confirmPassword,
    });
    ElMessage.success('删除策略申请已提交待复核');
    await fetchPolicies();
  } catch (error) {
    ElMessage.error(error?.message || '删除失败');
  }
}

fetchPolicies();
</script>

<style scoped>
.policy-page {
  display: grid;
  gap: 14px;
}

.policy-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.policy-head h2 {
  margin: 0;
}

.policy-head p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.query-form {
  margin-bottom: 4px;
}

.policy-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
