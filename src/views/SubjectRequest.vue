<template>
  <div class="page-grid">
    <el-card class="card-glass">
      <div class="card-header">数据主体权利工单</div>
      <el-form :inline="true" @submit.prevent ref="formRef" :model="form" :rules="rules">
        <el-form-item v-if="showUserIdField" label="用户ID" prop="userId"><el-input v-model="form.userId" /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width:140px">
            <el-option label="查询" value="access" />
            <el-option label="导出" value="export" />
            <el-option v-if="canSelectDeleteType" label="删除" value="delete" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="comment"><el-input v-model="form.comment" style="width:220px" /></el-form-item>
        <el-button v-if="canCreateRequest" type="primary" :loading="saving" @click="create">提交申请</el-button>
        <span v-else class="hint-text">当前账号仅可查看工单</span>
      </el-form>
      <el-table :data="list" class="page-table" style="margin-top:12px" v-loading="loading" empty-text="暂无记录">
        <el-table-column prop="id" label="ID" width="250">
          <template #default="scope">
            <div class="cell nowrap">{{ scope.row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" />
        <el-table-column prop="type" label="类型" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="comment" label="备注">
          <template #default="scope">
            <div class="cell">
              {{ getSafeComment(scope.row.comment || '') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="handlerId" label="处理人" />
        <el-table-column label="处理" width="220">
          <template #default="scope">
            <template v-if="canProcessRequest">
              <el-select v-model="scope.row.status" size="small" style="width:140px" @change="update(scope.row)">
                <el-option label="pending" value="pending" />
                <el-option label="processing" value="processing" />
                <el-option label="done" value="done" />
                <el-option label="rejected" value="rejected" />
              </el-select>
              <el-button v-if="canDeleteRequest" size="small" type="danger" @click="remove(scope.row.id)" style="margin-left:8px">删除</el-button>
            </template>
            <span v-else class="hint-text">仅运营角色可处理</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';
import {
  canCreateSubjectRequest,
  canCreateSubjectRequestType,
  canDeleteSubjectRequest,
  canProcessSubjectRequest,
  hasAnyRole,
} from '../utils/roleBoundary';
function getSafeComment(comment) {
  return String(comment || '');
}

const form = ref({ userId: '', type: 'access', comment: '' });
const list = ref([]);
const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const userStore = useUserStore();
const rules = {
  type: [{ required: true, message: '类型不能为空', trigger: 'change' }],
  comment: [{ required: true, message: '备注不能为空', trigger: 'blur' }]
};
const currentUser = computed(() => userStore.userInfo || {});
const showUserIdField = computed(() => hasAnyRole(currentUser.value, ['ADMIN', 'DATA_ADMIN', 'BUSINESS_OWNER']));
const canCreateRequest = computed(() => canCreateSubjectRequest(currentUser.value));
const canSelectDeleteType = computed(() => canCreateSubjectRequestType(currentUser.value, 'delete'));
const canProcessRequest = computed(() => canProcessSubjectRequest(currentUser.value));
const canDeleteRequest = computed(() => canDeleteSubjectRequest(currentUser.value));

function removeRequestFromList(id) {
  list.value = list.value.filter(item => item.id !== id);
}

async function load() {
  loading.value = true;
  try {
    const data = await request.get('/subject-request/list');
    list.value = (Array.isArray(data) ? data : []).map(item => ({
      ...item,
      comment: getSafeComment(item?.comment),
    }));
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!canCreateRequest.value) {
    ElMessage.warning('当前账号仅可查看工单');
    return;
  }
  if (!canCreateSubjectRequestType(currentUser.value, form.value.type)) {
    ElMessage.warning('当前账号不可提交该类型工单');
    return;
  }
  if (!formRef.value) return;
  formRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload = {
        ...form.value,
        userId: form.value.userId || userStore.userInfo?.id || null,
      };
      await request.post('/subject-request/create', payload);
      ElMessage.success('提交成功');
      await load();
    } catch (err) {
      ElMessage.error(err?.message || '提交失败');
    } finally {
      saving.value = false;
    }
  });
}

async function update(row) {
  try {
    const payload = {
      ...row,
      handlerId: row.handlerId || userStore.userInfo?.id || null,
    };
    await request.post('/subject-request/process', payload);
    ElMessage.success('更新成功');
    await load();
  } catch (err) {
    const message = String(err?.message || '');
    if (message.includes('工单不存在')) {
      ElMessage.warning('该工单已不存在，列表已刷新');
      await load();
      return;
    }
    ElMessage.error(err?.message || '更新失败');
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该工单吗？', '提示', { type: 'warning' });
    await request.post('/subject-request/delete', { id });
    removeRequestFromList(id);
    ElMessage.success('删除成功');
    await load();
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err?.message || '删除失败');
  }
}

load();

if (!form.value.userId && userStore.userInfo?.id) {
  form.value.userId = userStore.userInfo.id;
}
</script>

<style scoped>
.page-grid { display: grid; gap: 16px; }
.card-header { font-weight: 600; margin-bottom: 12px; color: var(--color-text); }
.hint-text { color: var(--color-text-secondary); font-size: 12px; }

:deep(.page-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-border-color: var(--color-border-light);
  --el-table-text-color: var(--color-text);
  --el-table-header-text-color: var(--color-text-secondary);
}
</style>
