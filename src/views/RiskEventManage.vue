<template>
  <el-card>
    <h2>风险事件管理</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="事件类型">
        <el-input v-model="query.type" placeholder="输入事件类型" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchEvents">查询</el-button>
        <el-button @click="openAdd">新增事件</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="events" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="250">
        <template #default="scope">
          <div class="cell nowrap">{{ scope.row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="level" label="风险等级" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="handlerId" label="处置人ID" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editEvent(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteEvent(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="新增风险事件">
      <el-form :model="addForm" :rules="rules" ref="addFormRef">
        <el-alert title="处置人将自动记录为当前操作账号" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form-item label="类型" prop="type"><el-input v-model="addForm.type" /></el-form-item>
        <el-form-item label="风险等级" prop="level"><el-input v-model="addForm.level" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-input v-model="addForm.status" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addEvent">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showEdit" title="编辑风险事件">
      <el-form :model="editForm" :rules="rules" ref="editFormRef">
        <el-alert title="保存后将自动刷新处置人为当前操作账号" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form-item label="类型" prop="type"><el-input v-model="editForm.type" /></el-form-item>
        <el-form-item label="风险等级" prop="level"><el-input v-model="editForm.level" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-input v-model="editForm.status" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updateEvent">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
const events = ref([]);
const loading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const saving = ref(false);
const addForm = ref({ type: '', level: '', status: '' });
const editForm = ref({});
const query = ref({ type: '' });
const addFormRef = ref();
const editFormRef = ref();
const rules = {
  type: [{ required: true, message: '类型不能为空', trigger: 'blur' }],
  level: [{ required: true, message: '风险等级不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'blur' }]
};
async function fetchEvents() {
  loading.value = true;
  try {
    const res = await request.get('/risk-event/list', { params: query.value });
    events.value = res || [];
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}
function openAdd() {
  addForm.value = { type: '', level: '', status: '' };
  showAdd.value = true;
}
async function addEvent() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/risk-event/add', addForm.value);
      ElMessage.success('保存成功');
      showAdd.value = false;
      fetchEvents();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}
function editEvent(row) {
  editForm.value = { ...row };
  showEdit.value = true;
}
async function updateEvent() {
  if (!editFormRef.value) return;
  editFormRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/risk-event/update', editForm.value);
      ElMessage.success('更新成功');
      showEdit.value = false;
      fetchEvents();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}
async function deleteEvent(id) {
  try {
    await ElMessageBox.confirm('确认删除该事件吗？', '提示', { type: 'warning' });
    await request.post('/risk-event/delete', { id });
    ElMessage.success('删除成功');
    fetchEvents();
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err?.message || '删除失败');
  }
}
fetchEvents();
</script>
