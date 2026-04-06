<template>
  <el-card>
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
.merged-tabs :deep(.el-card) {
  border: none;
  box-shadow: none;
  background: transparent;
}
</style>
