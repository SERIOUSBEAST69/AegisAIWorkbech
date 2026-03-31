<template>
  <el-card>
    <h2>SoD 规则管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="场景">
        <el-input v-model="query.scenario" placeholder="例如 PRIVILEGE_CHANGE_REVIEW" />
      </el-form-item>
      <el-form-item label="启用状态">
        <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchRules">查询</el-button>
        <el-button v-if="canManage" @click="openAdd">新增规则</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rules" v-loading="loading" style="width: 100%" empty-text="暂无记录">
      <el-table-column prop="id" label="ID" width="100" />
      <el-table-column prop="scenario" label="场景" min-width="180" />
      <el-table-column prop="roleCodeA" label="角色A" width="140" />
      <el-table-column prop="roleCodeB" label="角色B" width="140" />
      <el-table-column label="启用" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button v-if="canManage" size="small" @click="editRule(scope.row)">编辑</el-button>
          <el-button v-if="canManage" size="small" type="danger" @click="deleteRule(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 16px">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="pagination.total"
        :current-page="pagination.current"
        :page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onPageSizeChange"
      />
    </div>

    <el-dialog v-model="showEdit" :title="editForm.id ? '编辑 SoD 规则' : '新增 SoD 规则'" width="580px">
      <el-form :model="editForm" :rules="formRules" ref="editFormRef" label-width="90px">
        <el-form-item label="场景" prop="scenario">
          <el-input v-model="editForm.scenario" placeholder="例如 PRIVILEGE_CHANGE_REVIEW" />
        </el-form-item>
        <el-form-item label="角色A" prop="roleCodeA">
          <el-input v-model="editForm.roleCodeA" placeholder="例如 ADMIN" />
        </el-form-item>
        <el-form-item label="角色B" prop="roleCodeB">
          <el-input v-model="editForm.roleCodeB" placeholder="例如 SECOPS" />
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="enabledSwitch" />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { getSession } from '../utils/auth';
import { canManageSodRule } from '../utils/roleBoundary';

const rules = ref([]);
const loading = ref(false);
const saving = ref(false);
const showEdit = ref(false);
const editFormRef = ref();
const query = ref({ scenario: '', enabled: null });
const pagination = ref({ current: 1, pageSize: 10, total: 0 });
const editForm = ref({ id: null, scenario: '', roleCodeA: '', roleCodeB: '', enabled: 1, description: '' });
const enabledSwitch = ref(true);

const canManage = computed(() => canManageSodRule(getSession()?.user));

const formRules = {
  scenario: [{ required: true, message: '场景不能为空', trigger: 'blur' }],
  roleCodeA: [{ required: true, message: '角色A不能为空', trigger: 'blur' }],
  roleCodeB: [{ required: true, message: '角色B不能为空', trigger: 'blur' }],
};

watch(enabledSwitch, value => {
  editForm.value.enabled = value ? 1 : 0;
});

function onPageChange(page) {
  pagination.value.current = page;
  fetchRules();
}

function onPageSizeChange(size) {
  pagination.value.pageSize = size;
  pagination.value.current = 1;
  fetchRules();
}

function openAdd() {
  if (!canManage.value) {
    ElMessage.error('当前身份无 SoD 规则编辑权限');
    return;
  }
  editForm.value = { id: null, scenario: '', roleCodeA: '', roleCodeB: '', enabled: 1, description: '' };
  enabledSwitch.value = true;
  showEdit.value = true;
}

function editRule(row) {
  if (!canManage.value) {
    ElMessage.error('当前身份无 SoD 规则编辑权限');
    return;
  }
  editForm.value = {
    id: row.id,
    scenario: row.scenario || '',
    roleCodeA: row.roleCodeA || '',
    roleCodeB: row.roleCodeB || '',
    enabled: Number(row.enabled || 0) > 0 ? 1 : 0,
    description: row.description || '',
  };
  enabledSwitch.value = editForm.value.enabled > 0;
  showEdit.value = true;
}

async function fetchRules() {
  loading.value = true;
  try {
    const res = await request.get('/sod-rules/page', {
      params: {
        scenario: query.value.scenario,
        enabled: query.value.enabled,
        page: pagination.value.current,
        pageSize: pagination.value.pageSize,
      },
    });
    rules.value = res?.list || [];
    pagination.value.total = Number(res?.total || 0);
  } catch (err) {
    ElMessage.error(err?.message || '加载 SoD 规则失败');
  } finally {
    loading.value = false;
  }
}

async function saveRule() {
  if (!canManage.value) {
    ElMessage.error('当前身份无 SoD 规则编辑权限');
    return;
  }
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/sod-rules/save', {
        ...editForm.value,
        enabled: enabledSwitch.value ? 1 : 0,
      });
      ElMessage.success('SoD 规则保存成功');
      showEdit.value = false;
      pagination.value.current = 1;
      fetchRules();
    } catch (err) {
      ElMessage.error(err?.message || 'SoD 规则保存失败');
    } finally {
      saving.value = false;
    }
  });
}

async function deleteRule(id) {
  if (!canManage.value) {
    ElMessage.error('当前身份无 SoD 规则删除权限');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该 SoD 规则吗？', '提示', { type: 'warning' });
    const pwdPrompt = await ElMessageBox.prompt('请输入当前治理管理员密码确认删除', '敏感操作二次校验', {
      inputType: 'password',
      inputAttributes: { autocomplete: 'current-password', autofocus: 'autofocus' },
      inputPlaceholder: '请输入密码',
      inputValidator: value => (!!value && value.trim().length > 0) || '密码不能为空',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await request.post('/sod-rules/delete', {
      id,
      confirmPassword: pwdPrompt.value,
    });
    ElMessage.success('SoD 规则删除成功');
    pagination.value.current = 1;
    fetchRules();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || 'SoD 规则删除失败');
    }
  }
}

fetchRules();
</script>