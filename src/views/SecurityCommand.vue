<template>
  <el-card>
    <h2>安全指挥台</h2>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 140px">
          <el-option label="pending" value="pending" />
          <el-option label="blocked" value="blocked" />
          <el-option label="ignored" value="ignored" />
        </el-select>
      </el-form-item>
      <el-form-item label="等级">
        <el-select v-model="query.severity" clearable style="width: 140px">
          <el-option label="high" value="high" />
          <el-option label="medium" value="medium" />
          <el-option label="low" value="low" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchEvents">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="events" v-loading="loading" style="width:100%">
      <el-table-column prop="id" label="ID" width="90"/>
      <el-table-column prop="eventType" label="事件类型" width="160"/>
      <el-table-column prop="severity" label="等级" width="100"/>
      <el-table-column prop="status" label="状态" width="100"/>
      <el-table-column prop="employeeId" label="账号" width="140"/>
      <el-table-column prop="hostname" label="设备主机" width="160"/>
      <el-table-column prop="targetAddr" label="目标地址/IP" min-width="180" show-overflow-tooltip/>
      <el-table-column prop="filePath" label="文件路径" min-width="240"/>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button size="small" type="danger" :disabled="scope.row.status==='blocked' || !canBlock" @click="block(scope.row.id)">阻断</el-button>
          <el-button size="small" :disabled="scope.row.status==='ignored' || !canIgnore" @click="ignore(scope.row.id)">忽略</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';
import { canBlockThreatEvent, canIgnoreThreatEvent } from '../utils/roleBoundary';

const userStore = useUserStore();
const canBlock = computed(() => canBlockThreatEvent(userStore.userInfo));
const canIgnore = computed(() => canIgnoreThreatEvent(userStore.userInfo));

const loading = ref(false);
const events = ref([]);
const query = ref({ status: '', severity: '', keyword: '', page: 1, pageSize: 20 });

async function fetchEvents() {
  loading.value = true;
  try {
    const res = await request.get('/security/events', { params: query.value });
    events.value = res?.list || [];
  } catch (err) {
    ElMessage.error(err?.message || '加载安全事件失败');
  } finally {
    loading.value = false;
  }
}

async function block(id) {
  try {
    await request.post('/security/block', { id });
    ElMessage.success('阻断成功');
    fetchEvents();
  } catch (err) {
    ElMessage.error(err?.message || '阻断失败');
  }
}

async function ignore(id) {
  try {
    await request.post('/security/ignore', { id });
    ElMessage.success('忽略成功');
    fetchEvents();
  } catch (err) {
    ElMessage.error(err?.message || '忽略失败');
  }
}

fetchEvents();
</script>
