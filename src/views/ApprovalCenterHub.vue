<template>
  <el-card>
    <ApprovalCenter />
  </el-card>
</template>

<script setup>
import ApprovalCenter from './ApprovalCenter.vue';
</script>

<style scoped>
.merged-tabs :deep(.el-card) {
  border: none;
  box-shadow: none;
  background: transparent;
}
</style>
