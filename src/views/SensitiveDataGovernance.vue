<template>
  <el-card>
    <div class="header-row">
      <h2>敏感数据治理</h2>
      <p>统一管理敏感扫描与脱敏预览能力</p>
    </div>
    <el-tabs v-model="activeTab" class="merged-tabs">
      <el-tab-pane label="敏感扫描" name="scan" lazy>
        <SensitiveScan />
      </el-tab-pane>
      <el-tab-pane label="脱敏预览" name="desense" lazy>
        <DesensePreview />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref } from 'vue';
import SensitiveScan from './SensitiveScan.vue';
import DesensePreview from './DesensePreview.vue';

const activeTab = ref('scan');
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
