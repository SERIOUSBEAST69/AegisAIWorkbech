<template>
  <el-card>
    <div class="header-row">
      <h2>审批中心</h2>
      <p>统一处理审批流与治理审批</p>
    </div>
    <el-tabs v-model="activeTab" class="merged-tabs">
      <el-tab-pane label="审批流管理" name="flow" lazy>
        <ApprovalManage />
      </el-tab-pane>
      <el-tab-pane label="治理审批中心" name="governance" lazy>
        <ApprovalCenter />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref } from 'vue';
import ApprovalManage from './ApprovalManage.vue';
import ApprovalCenter from './ApprovalCenter.vue';

const activeTab = ref('governance');
</script>

<style scoped>
.header-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.header-row h2 {
  margin: 0;
}

.header-row p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.merged-tabs :deep(.el-card) {
  border: none;
  box-shadow: none;
  background: transparent;
}
</style>
