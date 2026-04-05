<template>
  <el-card>
    <div class="header-row">
      <h2>审计中心</h2>
      <p>统一查看审计日志与审计报告</p>
    </div>
    <el-tabs v-model="activeTab" class="merged-tabs">
      <el-tab-pane label="审计日志" name="log" lazy>
        <AuditLog />
      </el-tab-pane>
      <el-tab-pane label="审计报告" name="report" lazy>
        <AuditReport />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref } from 'vue';
import AuditLog from './AuditLog.vue';
import AuditReport from './AuditReport.vue';

const activeTab = ref('log');
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
